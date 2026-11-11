package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.content.CandidateContent
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.model.BetOutcome
import com.example.hrrepayment.core.model.CalendarPosition
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.CareerEvent
import com.example.hrrepayment.core.model.CareerEventKind
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.HiredRecord
import com.example.hrrepayment.core.model.SalaryReport
import com.example.hrrepayment.core.model.PendingStep
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.model.WorkdayRef
import com.example.hrrepayment.core.model.BetSnapshot
import com.example.hrrepayment.core.random.RandomSnapshot
import com.example.hrrepayment.core.random.RandomSource
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.money.applyPerMille
import com.example.hrrepayment.core.money.applyOddsTenths
import com.example.hrrepayment.core.money.timesInt

/**
 * Pure command processor: `(previous state?, command) → (new state, effects)`.
 *
 * It never touches the file system or the system clock. The ViewModel wraps it with
 * persistence, so the order is always
 * **验证 → 计算新状态 → 原子保存 → 发布 UI**（技术文档 §3）。
 *
 * ## Why `previous` is nullable
 *
 * A brand new slot has no predecessor, so [Command.StartNewGame] must be applicable
 * to “nothing”. Modelling this as a parameter (rather than a second code path in the
 * ViewModel) is what removes the P1 inconsistency where the engine kept the old
 * `saveId` while the ViewModel minted a new one. Now exactly one path mints a save.
 *
 * ## RNG discipline
 *
 * Before computing, the generator is restored from the snapshot stored in
 * `previous`. After computing, the resulting position is written back into the new
 * state. Draws therefore happen *outside* any retryable persistence callback and are
 * fully replayable after process death.
 */
class GameEngine(
    private val content: ContentRef,
    private val random: RandomSource,
) {
    init {
        require(content.totalWorkdays > 0) { "ContentRef must declare a positive workday count" }
        require(content.contentVersion.isNotBlank()) { "ContentRef must declare a content version" }
    }

    /**
     * Validate and apply one command.
     *
     * @throws EngineError when the command is not legal; the caller must persist
     *   [EngineResult.newState] before publishing any UI change.
     */
    fun apply(previous: GameState?, command: Command): EngineResult {
        validate(previous, command)
        previous?.let { random.restore(it.rngSnapshot()) }

        if (previous?.phase == Phase.DayEntry &&
            (command is Command.EnterRecruiting || command is Command.PlaceBet)
        ) {
            val due = previous.activeBet?.takeIf { previous.position.current.index >= it.settleWorkdayIndex }
            val settled = if (due == null) null else onSettleBet(
                previous, Command.SettleBet(previous.revision, previous.saveId, command.commandId, due.marketId, ""),
            )
            val base = settled?.newState ?: previous
            val gated = enterDay(base)
            if (gated != base || settled != null) {
                return EngineResult(
                    previous,
                    gated.copy(revision = previous.revision + 1).withRng(random.snapshot()).withCommandId(command.commandId),
                    (settled?.effects ?: emptyList()) +
                        if (gated != base) listOf(EngineEffect.PhaseChanged(gated.phase, "fixed event gate")) else emptyList(),
                )
            }
        }

        val result = when (command) {
            is Command.ReadSpecialLoanInvitation -> {
                val state = requirePrevious(previous, command)
                if (!state.specialLoanInvitationAvailable || state.specialLoanInvitationRead)
                    throw EngineError.InvalidCommand("special loan invitation is unavailable or already read")
                EngineResult(state, state.copy(specialLoanInvitationRead = true, revision = state.revision + 1), emptyList())
            }
            is Command.DecideSpecialLoan -> onDecideSpecialLoan(requirePrevious(previous, command), command)
            is Command.RepaySpecialLoan -> onRepaySpecialLoan(requirePrevious(previous, command))
            is Command.StartNewGame -> onStartNewGame(previous, command)
            is Command.AcknowledgeOpening ->
                onAcknowledgeOpening(requirePrevious(previous, command), command)
            is Command.AcknowledgeEvent ->
                onAcknowledgeEvent(requirePrevious(previous, command), command)
            is Command.EnterRecruiting ->
                onEnterRecruiting(requirePrevious(previous, command), command)
            is Command.BrowseCandidate ->
                onBrowseCandidate(requirePrevious(previous, command), command)
            is Command.SelectCandidate ->
                onSelectCandidate(requirePrevious(previous, command), command)
            is Command.SubmitCandidate ->
                onSubmitCandidate(requirePrevious(previous, command), command)
            is Command.MarkStatement -> onMarkStatement(requirePrevious(previous, command), command)
            is Command.SetLuckUsage -> onSetLuckUsage(requirePrevious(previous, command), command)
            is Command.AcknowledgeResult ->
                onAcknowledgeResult(requirePrevious(previous, command), command)
            is Command.ChooseSuccessReward ->
                onChooseSuccessReward(requirePrevious(previous, command), command)
            is Command.FinishIncome -> onFinishIncome(requirePrevious(previous, command), command)
            is Command.RepayDebt -> onRepayDebt(requirePrevious(previous, command), command)
            is Command.FinishEconomy -> onFinishEconomy(requirePrevious(previous, command), command)
            is Command.ChooseMonthlySkill -> onChooseMonthlySkill(requirePrevious(previous, command), command)
            is Command.PlaceBet -> onPlaceBet(requirePrevious(previous, command), command)
            is Command.SettleBet -> onSettleBet(requirePrevious(previous, command), command)
            // P5 handlers remain explicitly rejected until their contracts are implemented.
            else -> throw EngineError.WrongPhase(
                expected = "P3+ handler",
                actual = command::class.simpleName ?: "Command",
            )
        }

        val entered = result.newState
        val due = entered.activeBet?.takeIf { entered.phase == Phase.DayEntry && entered.position.current.index >= it.settleWorkdayIndex }
        val settled = if (due != null) onSettleBet(entered, Command.SettleBet(entered.revision, entered.saveId, command.commandId, due.marketId, "")) else result
        val finalState = if (settled.newState.phase == Phase.DayEntry &&
            (previous?.position?.current != settled.newState.position.current || previous.phase == Phase.Opening)
        ) enterDay(settled.newState) else settled.newState
        return result.copy(
            effects = if (due == null) result.effects else result.effects + settled.effects,
            newState = finalState
                .withRng(random.snapshot())
                .withCommandId(command.commandId),
        )
    }

    // ---------------------------------------------------------------- validation

    private fun validate(previous: GameState?, command: Command) {
        if (command.commandId.isBlank()) {
            throw EngineError.InvalidCommand("commandId must not be blank")
        }
        if (previous == null) {
            if (command !is Command.StartNewGame) {
                throw EngineError.NoSave(command::class.simpleName ?: "Command")
            }
            if (command.saveId != Command.NO_SAVE_ID) {
                throw EngineError.InvalidCommand(
                    "the first command of a new slot must use NO_SAVE_ID, got '${command.saveId}'",
                )
            }
            if (command.expectedRevision != 0L) {
                throw EngineError.RevisionMismatch(expected = 0L, actual = command.expectedRevision)
            }
            return
        }

        if (previous.saveSchemaVersion != GameState.CURRENT_SCHEMA_VERSION) {
            throw EngineError.SchemaUnsupported(previous.saveSchemaVersion)
        }
        if (previous.rulesVersion != GameState.EXPECTED_RULES_VERSION) {
            throw EngineError.RulesUnsupported(
                found = previous.rulesVersion,
                expected = GameState.EXPECTED_RULES_VERSION,
            )
        }
        if (previous.contentVersion != content.contentVersion) {
            throw EngineError.ContentUnsupported(previous.contentVersion, content.contentVersion)
        }
        if (content.contentHash.isNotBlank()) {
            // 本构建绑定了导出内容指纹：存档 hash 为空（占位旧档）或不同
            // 一律拒绝，防止旧档带着占位首月静默进入真实内容。
            if (previous.contentHash != content.contentHash) {
                throw EngineError.ContentUnsupported(
                    found = previous.contentHash.ifBlank { "(空)" },
                    expected = content.contentHash,
                )
            }
        }
        if (command.saveId != previous.saveId) {
            throw EngineError.SaveIdMismatch(expected = previous.saveId, actual = command.saveId)
        }
        if (command.expectedRevision != previous.revision) {
            throw EngineError.RevisionMismatch(
                expected = previous.revision,
                actual = command.expectedRevision,
            )
        }
        if (command.commandId == previous.lastAppliedCommandId) {
            throw EngineError.DuplicateCommand(command.commandId)
        }
    }

    private fun requirePrevious(previous: GameState?, command: Command): GameState =
        previous ?: throw EngineError.NoSave(command::class.simpleName ?: "Command")

    // ------------------------------------------------------------------ handlers

    private fun onStartNewGame(
        previous: GameState?,
        command: Command.StartNewGame,
    ): EngineResult {
        if (command.newSaveId.isBlank()) {
            throw EngineError.InvalidCommand("newSaveId must not be blank")
        }
        // Reset the generator to the requested seed *inside* the command so the
        // persisted snapshot and the generator agree.
        random.restore(RandomSnapshot.atSeed(command.newRngSeed))
        val optionRandom = com.example.hrrepayment.core.random.SplitMix64Random(command.newRngSeed xor 0x5044L)

        val fresh = GameState.newGame(
            saveId = command.newSaveId,
            rngSeed = command.newRngSeed,
            contentVersion = content.contentVersion,
            contentHash = content.contentHash,
        ).copy(
            revision = (previous?.revision ?: -1L) + 1L,
            betOptionOrder = content.bettingMarkets().associate { market ->
                val ids = market.options.map { it.id }
                market.id to if (optionRandom.nextInt(0, 2) == 0) ids else ids.reversed()
            },
        )

        return EngineResult(
            previousState = previous,
            newState = fresh,
            effects = listOf(
                EngineEffect.PhaseChanged(Phase.Opening, "Reset to Opening"),
                EngineEffect.Message(Key.MSG_NEW_GAME),
            ),
        )
    }

    private fun onAcknowledgeOpening(
        state: GameState,
        command: Command.AcknowledgeOpening,
    ): EngineResult {
        if (state.phase != Phase.Opening) {
            throw EngineError.WrongPhase(expected = Phase.Opening.name, actual = state.phase.name)
        }
        val next = state.copy(phase = Phase.DayEntry, pendingStep = PendingStep.None, revision = state.revision + 1L)
        return EngineResult(
            previousState = state,
            newState = next,
            effects = listOf(
                EngineEffect.PhaseChanged(next.phase, "Opening → ${next.phase}"),
            ),
        )
    }

    private fun onAcknowledgeEvent(state: GameState, command: Command.AcknowledgeEvent): EngineResult {
        if (state.phase != Phase.EventNotice || state.pendingStep != PendingStep.ViewEvent) {
            throw EngineError.WrongPhase(expected = "EventNotice", actual = state.phase.name)
        }
        if (state.pendingEventId != command.eventId) {
            throw EngineError.InvalidCommand("event does not match pending notice")
        }
        val next = when (command.eventId) {
            EVENT_CHAPTER2_SUCCESS -> {
                if (state.stats.successRewardPending > 0)
                    state.copy(phase = Phase.SuccessReward, pendingStep = PendingStep.ChooseSuccessReward, pendingEventId = null,
                        stats = state.stats.copy(successRewardPending = state.stats.successRewardPending - 1), revision = state.revision + 1)
                else advanceDay(state.copy(phase = Phase.ResultAck, pendingStep = PendingStep.None, pendingEventId = null)).newState
            }
            EVENT_SPECIAL_LOAN_CLOSED, EVENT_MANAGER_HANDOVER, EVENT_OVERSEAS_MISSED, EVENT_WINTER_COLD ->
                state.copy(phase = Phase.DayEntry, pendingStep = PendingStep.None, pendingEventId = null, revision = state.revision + 1)
            else -> throw EngineError.InvalidCommand("unknown fixed event ${command.eventId}")
        }
        return EngineResult(state, next, listOf(EngineEffect.Message(eventMessageKey(command.eventId)), EngineEffect.PhaseChanged(next.phase, "event acknowledged: ${command.eventId}")))
    }

    // ------------------------------------------------------- P2 招聘六日切片

    private fun onEnterRecruiting(
        state: GameState,
        command: Command.EnterRecruiting,
    ): EngineResult {
        if (state.phase != Phase.DayEntry) {
            throw EngineError.WrongPhase(expected = Phase.DayEntry.name, actual = state.phase.name)
        }
        todayCandidates(state) // 拒绝内容缺失的日子，而不是编造简历
        var next = state.copy(
            phase = Phase.Recruiting,
            pendingStep = PendingStep.ViewResume,
            browsedCandidateIndex = 0,
            selectedCandidateId = todayCandidates(state).first().id,
            revision = state.revision + 1L,
        )
        return EngineResult(
            previousState = state,
            newState = next,
            effects = listOf(
                EngineEffect.PhaseChanged(Phase.Recruiting, "DayEntry → Recruiting"),
            ),
        )
    }

    private fun onBrowseCandidate(
        state: GameState,
        command: Command.BrowseCandidate,
    ): EngineResult {
        if (state.phase != Phase.Recruiting) {
            throw EngineError.WrongPhase(expected = Phase.Recruiting.name, actual = state.phase.name)
        }
        val candidates = todayCandidates(state)
        if (command.index !in candidates.indices) {
            throw EngineError.InvalidCommand(
                "browse index ${command.index} out of range (0..${candidates.lastIndex})",
            )
        }
        val next = state.copy(
            browsedCandidateIndex = command.index,
            selectedCandidateId = candidates[command.index].id,
            revision = state.revision + 1L,
        )
        return EngineResult(previousState = state, newState = next, effects = emptyList())
    }

    private fun onSelectCandidate(
        state: GameState,
        command: Command.SelectCandidate,
    ): EngineResult {
        if (state.phase != Phase.Recruiting) {
            throw EngineError.WrongPhase(expected = Phase.Recruiting.name, actual = state.phase.name)
        }
        val candidates = todayCandidates(state)
        if (candidates.none { it.id == command.candidateId }) {
            throw EngineError.InvalidCommand(
                "candidate '${command.candidateId}' is not offered on workday ${state.position.current.index}",
            )
        }
        val next = state.copy(
            selectedCandidateId = command.candidateId,
            browsedCandidateIndex = candidates.indexOfFirst { it.id == command.candidateId },
            pendingStep = PendingStep.ChooseCandidate,
            revision = state.revision + 1L,
        )
        return EngineResult(previousState = state, newState = next, effects = emptyList())
    }

    private fun onMarkStatement(state: GameState, command: Command.MarkStatement): EngineResult {
        if (state.phase != Phase.Recruiting) throw EngineError.WrongPhase("Recruiting", state.phase.name)
        val candidate = todayCandidates(state).firstOrNull { it.id == command.candidateId }
            ?: throw EngineError.InvalidCommand("candidate is not offered today")
        if (candidate.statements.none { it.id == command.statementId }) throw EngineError.InvalidCommand("unknown statement")
        return EngineResult(state, state.copy(
            markedStatements = state.markedStatements + (candidate.id to command.statementId),
            revision = state.revision + 1,
        ), emptyList())
    }

    private fun onSetLuckUsage(state: GameState, command: Command.SetLuckUsage): EngineResult {
        if (state.phase != Phase.Recruiting) throw EngineError.WrongPhase("Recruiting", state.phase.name)
        if (command.enabled && (state.attributes.luck <= 0 || isForcedNext(state))) {
            throw EngineError.InvalidCommand("luck is unavailable or unnecessary")
        }
        return EngineResult(state, state.copy(useLuckOnSubmission = command.enabled, revision = state.revision + 1), emptyList())
    }

    /**
     * 推送（项目书 §4.2 招聘规则）。
     *
     *  1. 档位以**提交前**工作能力决定：≥50 → 每3次，≥30 → 每4次，≥20 → 每5次；
     *     只取最高档。进入/升级档位重新计数，第一笔从 1 开始；本次成功领奖升档
     *     后从下一次提交开始新周期。
     *  2. 随机成功不重置固定周期；固定周期到期为强制成功并重新计数。
     *  3. 强制成功不做概率抽取（不消耗 RNG 位置）。
     *  4. 概率使用导出内容的 `successBps`，加上本次可用运气（每点 100bps，最多10点），
     *     最终不超过 9500bps；运气在这次提交后消耗。
     *  5. 每 3 次全局成功 → 当场强制二选一（工作+1 / 信任+1）；余数跨月保留。
     *  6. 失败惩罚：连续第 2 次失败信任 -1；第 4 次及之后每逢偶数连败 -2；
     *     成功清零连败计数。跨月不清零。
     *  7. 信任 < 0 立即结束（债务>0 → exposed；债清 → debt_free_restart）。
     */
    private fun onSubmitCandidate(
        state: GameState,
        command: Command.SubmitCandidate,
    ): EngineResult {
        if (state.phase != Phase.Recruiting) {
            throw EngineError.WrongPhase(expected = Phase.Recruiting.name, actual = state.phase.name)
        }
        val selectedId = todayCandidates(state).getOrNull(state.browsedCandidateIndex)?.id
            ?: throw EngineError.InvalidCommand("no visible candidate")
        if (command.candidateId != selectedId) {
            throw EngineError.InvalidCommand(
                "submit '$command.candidateId' does not match the selected '$selectedId'",
            )
        }
        val candidate = todayCandidates(state).firstOrNull { it.id == selectedId }
            ?: throw EngineError.InvalidCommand(
                "selected candidate '$selectedId' is not offered on workday ${state.position.current.index}",
            )
        val storedMark = state.markedStatements[candidate.id]
        if (storedMark == null || storedMark != command.statementId ||
            candidate.statements.none { it.id == storedMark }) {
            throw EngineError.InvalidCommand("mark the visible candidate's statement before submitting")
        }
        // 判定按**字段**比较：玩家只做「背景 / 项目」二选一，不再逐句挑。
        // 同一字段下具体标哪一条不影响对错，所以 UI 可以只给两个选项，
        // 而命令结构（statementId）保持不变，旧存档与既有测试契约都不受影响。
        val lieField = candidate.statements.firstOrNull { it.id == candidate.lieStatementId }?.field
        val markedField = candidate.statements.firstOrNull { it.id == command.statementId }?.field
        val reviewCorrect = lieField != null && markedField == lieField

        // ---- 保底（提交前工作能力决定档位）
        val workBefore = state.attributes.work
        val tier = when {
            workBefore >= PITY_TIER_TOP -> PITY_TIER_TOP
            workBefore >= PITY_TIER_MID -> PITY_TIER_MID
            workBefore >= PITY_TIER_LOW -> PITY_TIER_LOW
            else -> 0
        }
        val period = when (tier) {
            PITY_TIER_TOP -> PITY_PERIOD_TOP
            PITY_TIER_MID -> PITY_PERIOD_MID
            PITY_TIER_LOW -> PITY_PERIOD_LOW
            else -> 0
        }
        val tierChanged = tier != state.guaranteeTier
        var draws = if (tierChanged) 0 else state.guaranteePeriodDraws
        draws += 1
        val forced = period > 0 && draws >= period
        if (forced) draws = 0

        // ---- 概率抽取（强制成功不抽签）
        val eventForced = state.position.current.index == CHAPTER2_FIRST_DAY && EVENT_CHAPTER2_SUCCESS !in state.fixedEventsSeen
        val luckUsed = if (state.useLuckOnSubmission && !forced && !eventForced)
            state.attributes.luck.coerceIn(0, MAX_LUCK_BOOST) else 0
        val chance = (candidate.successBps + (if (reviewCorrect) REVIEW_SUCCESS_BPS else 0) + luckUsed * LUCK_SUCCESS_BPS)
            .coerceAtMost(MAX_SUCCESS_BPS)
        val success = forced || eventForced || random.chance(chance)

        // ---- 计数
        val successCount = state.stats.globalSuccessCount + if (success) 1 else 0
        val rewardPending = if (success && successCount % SUCCESS_REWARD_EVERY == 0) 1 else 0
        val failureCount = state.stats.globalFailureCount + if (success) 0 else 1
        val consecutiveFailures = if (success) 0 else state.stats.successiveFailureCount + 1

        // ---- 信任惩罚（只在失败时；0 / 1 / 2）
        val trustPenalty = if (!success) {
            when {
                consecutiveFailures == 2 -> 1
                consecutiveFailures >= 4 && consecutiveFailures % 2 == 0 -> 2
                else -> 0
            }
        } else {
            0
        }
        val trust = state.attributes.trust - trustPenalty

        // ---- 结果（文本由 UI 依据结构化字段渲染，存档不保存句子）
        var next = state.copy(
            phase = Phase.ResultAck,
            pendingStep = PendingStep.ViewResult,
            revision = state.revision + 1L,
            guaranteeTier = tier,
            guaranteePeriodDraws = draws,
            attributes = state.attributes.copy(trust = trust, luck = if (luckUsed > 0) 0 else state.attributes.luck),
            stats = state.stats.copy(
                globalSuccessCount = successCount,
                globalFailureCount = failureCount,
                successiveFailureCount = consecutiveFailures,
                successRewardPending = rewardPending,
                reviewedSubmissionCount = state.stats.reviewedSubmissionCount + 1,
                correctReviewCount = state.stats.correctReviewCount + if (reviewCorrect) 1 else 0,
            ),
            lastResultSuccess = success,
            lastResultForced = forced || eventForced,
            lastResultTrustPenalty = trustPenalty,
            lastResultCandidateId = candidate.id,
            lastReviewCorrect = reviewCorrect,
            lastReviewStatementId = command.statementId,
            lastLuckUsed = luckUsed,
            lastHireProbabilityBps = if (forced || eventForced) 10000 else chance,
        )

        // ---- 面试档案袋：录用成功者入库（同一笔状态转换内完成，重启不重复）
        // 入职时一次性掷定“会不会离职、哪天离职”：约 10% 会，日期落在半年内。
        // 掷点走引擎的 RandomSource，因此存档可复现、重放不会重新抽签。
        if (success) {
            val hiredOn = state.position.current.index
            val resignOn = if (CareerRolls.chance(state.rngSeed, candidate.id, CareerRolls.SALT_RESIGN, RESIGN_BPS)) {
                hiredOn + 1 + CareerRolls.intUntil(state.rngSeed, candidate.id, CareerRolls.SALT_RESIGN, HALF_YEAR_WORKDAYS)
            } else {
                null
            }
            next = next.withHire(HiredRecord(candidate.id, hiredOn, resignOn = resignOn))
        }

        val outcomeEffect = EngineEffect.Message(if (success) Key.MSG_SUCCESS else Key.MSG_FAILURE)

        // ---- 信任 < 0 立即结束（优先于一切后续步骤）
        if (trust < 0) {
            next = next.copy(
                phase = Phase.Ended,
                pendingStep = PendingStep.AcknowledgeEnding,
                endingId = if (state.debt.cents > 0L) ENDING_EXPOSED else ENDING_DEBT_FREE_RESTART,
                endingReason = ENDING_REASON_TRUST_NEGATIVE,
            )
            return EngineResult(
                previousState = state,
                newState = next,
                effects = listOf(
                    outcomeEffect,
                    EngineEffect.PhaseChanged(Phase.Ended, "trust $trust < 0 → ${next.endingId}"),
                ),
            )
        }

        if (success && state.position.current.index == CHAPTER2_FIRST_DAY && EVENT_CHAPTER2_SUCCESS !in next.fixedEventsSeen) {
            next = next.copy(
                attributes = next.attributes.copy(trust = next.attributes.trust + 5),
                pendingEventId = EVENT_CHAPTER2_SUCCESS,
                fixedEventsSeen = next.fixedEventsSeen + EVENT_CHAPTER2_SUCCESS,
            )
        }

        return EngineResult(
            previousState = state,
            newState = next,
            effects = listOf(
                outcomeEffect,
                EngineEffect.PhaseChanged(Phase.ResultAck, "Recruiting → ResultAck"),
            ),
        )
    }

    private fun onAcknowledgeResult(
        state: GameState,
        command: Command.AcknowledgeResult,
    ): EngineResult {
        if (state.phase != Phase.ResultAck) {
            throw EngineError.WrongPhase(expected = Phase.ResultAck.name, actual = state.phase.name)
        }
        if (state.pendingEventId == EVENT_CHAPTER2_SUCCESS) {
            val next = state.copy(phase = Phase.EventNotice, pendingStep = PendingStep.ViewEvent, revision = state.revision + 1L)
            return EngineResult(state, next, listOf(EngineEffect.PhaseChanged(Phase.EventNotice, "chapter 2 success notice")))
        }
        // 每 3 次全局成功 → 当场二选一（余数跨月保留）
        // Only the successful result that creates the milestone may open the
        // reward panel. A later failure must never replay a stale milestone.
        if (state.lastResultSuccess == true && state.stats.successRewardPending > 0 &&
            state.stats.globalSuccessCount % SUCCESS_REWARD_EVERY == 0
        ) {
            val next = state.copy(
                phase = Phase.SuccessReward,
                pendingStep = PendingStep.ChooseSuccessReward,
                stats = state.stats.copy(successRewardPending = state.stats.successRewardPending - 1),
                revision = state.revision + 1L,
            )
            return EngineResult(
                previousState = state,
                newState = next,
                effects = listOf(
                    EngineEffect.PhaseChanged(Phase.SuccessReward, "ResultAck → SuccessReward"),
                ),
            )
        }
        val cleared = if (state.lastResultSuccess == false && state.stats.successRewardPending > 0) {
            state.copy(stats = state.stats.copy(successRewardPending = 0))
        } else state
        return advanceDay(cleared)
    }

    private fun onChooseSuccessReward(
        state: GameState,
        command: Command.ChooseSuccessReward,
    ): EngineResult {
        if (state.phase != Phase.SuccessReward) {
            throw EngineError.WrongPhase(expected = Phase.SuccessReward.name, actual = state.phase.name)
        }
        val withReward = when (command.reward) {
            Command.SuccessRewardOption.WORK ->
                state.copy(attributes = state.attributes.copy(work = state.attributes.work + 1))
            Command.SuccessRewardOption.TRUST ->
                state.copy(attributes = state.attributes.copy(trust = state.attributes.trust + 1))
        }
        return advanceDay(withReward)
    }

    private fun onFinishIncome(state: GameState, command: Command.FinishIncome): EngineResult {
        if (state.phase != Phase.MonthIncome) throw EngineError.WrongPhase(Phase.MonthIncome.name, state.phase.name)
        val month = state.position.current.monthIndex
        val chapterEnd = month % WorkdayRef.CHAPTER_MONTHS == WorkdayRef.CHAPTER_MONTHS - 1
        val annualEnd = month % 12 == 5
        val chapter = state.position.current.chapter
        val raise = if (chapterEnd && state.attributes.trust >= 10 && chapter !in state.chapterTrustedBonuses) 200_000 else 0
        val promotion = state.stats.promotionDelta - state.promotionSalaryApplied
        val nextSalary = Money(state.monthlySalary.cents + raise + promotion * 100)
        val bonus = if (annualEnd) nextSalary.timesInt(3) else Money.ZERO
        val next = state.copy(
            phase = Phase.Repayment,
            pendingStep = PendingStep.ConfirmRepayment,
            cash = state.cash + state.monthlySalary + bonus,
            nextMonthlySalary = nextSalary,
            pendingRaise = raise / 100,
            lastSalarySettledMonthIndex = month,
            promotionSalaryApplied = state.stats.promotionDelta,
            chapterTrustedBonuses = if (raise > 0) state.chapterTrustedBonuses + chapter else state.chapterTrustedBonuses,
            lastSalaryPaid = state.monthlySalary,
            lastBonusPaid = bonus,
            revision = state.revision + 1,
        )
        return EngineResult(state, next, listOf(EngineEffect.PhaseChanged(Phase.Repayment, "income settled")))
    }

    private fun onRepayDebt(state: GameState, command: Command.RepayDebt): EngineResult {
        if (state.phase != Phase.Repayment) throw EngineError.WrongPhase(Phase.Repayment.name, state.phase.name)
        val amount = Money.parseYuan(command.amountYuan).getOrElse { throw EngineError.InvalidCommand("invalid repayment amount") }
        if (amount > state.cash || amount > state.debt) throw EngineError.InvalidCommand("repayment exceeds available balance")
        val after = state.copy(cash = state.cash - amount, debt = state.debt - amount)
        if (after.debt.cents > GameState.ORDINARY_DEBT_CAP_CENTS || ((after.debt.cents > 0 || after.specialLoanDue > Money.ZERO) && after.cash.cents == 0L)) {
            val ended = after.copy(phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding, endingId = "bankruptcy", endingReason = if (after.debt.cents > GameState.ORDINARY_DEBT_CAP_CENTS) "debt_cap_exceeded" else "cash_exhausted", revision = state.revision + 1)
            return EngineResult(state, ended, listOf(EngineEffect.PhaseChanged(Phase.Ended, "bankruptcy")))
        }
        val finalMonth = state.position.current.index == WorkdayRef.LAST.index
        val next = after.copy(phase = if (state.position.current.monthIndex % 12 == 5) Phase.InterestCheck else if (finalMonth) Phase.FinalCheck else Phase.MonthlySkill, pendingStep = PendingStep.ChooseMonthlySkill, revision = state.revision + 1)
        return EngineResult(state, next, listOf(EngineEffect.PhaseChanged(next.phase, "repayment accepted")))
    }

    private fun onDecideSpecialLoan(state: GameState, command: Command.DecideSpecialLoan): EngineResult {
        if (state.phase != Phase.DayEntry || state.position.current.index != CHAPTER2_FIRST_DAY || state.specialLoanDecision != null)
            throw EngineError.InvalidCommand("special loan invitation is unavailable")
        val next = state.copy(
            specialLoanDecision = command.accept,
            specialLoanInvitationRead = true,
            cash = if (command.accept) state.cash + Money(SPECIAL_LOAN_PRINCIPAL) else state.cash,
            specialLoanDue = if (command.accept) Money(SPECIAL_LOAN_TOTAL) else Money.ZERO,
            revision = state.revision + 1,
        )
        return EngineResult(state, next, emptyList())
    }

    private fun onRepaySpecialLoan(state: GameState): EngineResult {
        if (state.phase != Phase.Repayment || state.specialLoanDue == Money.ZERO || state.cash < state.specialLoanDue)
            throw EngineError.InvalidCommand("special loan cannot be repaid")
        val cash = state.cash - state.specialLoanDue
        val exhausted = cash == Money.ZERO && state.debt > Money.ZERO
        val next = state.copy(cash = cash, specialLoanDue = Money.ZERO,
            phase = if (exhausted) Phase.Ended else state.phase,
            pendingStep = if (exhausted) PendingStep.AcknowledgeEnding else state.pendingStep,
            endingId = if (exhausted) "bankruptcy" else state.endingId,
            endingReason = if (exhausted) "cash_exhausted" else state.endingReason,
            revision = state.revision + 1)
        return EngineResult(state, next, emptyList())
    }

    private fun onFinishEconomy(state: GameState, command: Command.FinishEconomy): EngineResult {
        if (state.phase != Phase.InterestCheck) throw EngineError.WrongPhase("FinishEconomy/${Phase.InterestCheck.name}", state.phase.name)
        val year = 2016 + state.position.current.monthIndex / 12
        if (year == 2017 && state.specialLoanDue > Money.ZERO) {
            val ended = state.copy(phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding,
                endingId = "bankruptcy", endingReason = "special_loan_overdue", revision = state.revision + 1)
            return EngineResult(state, ended, emptyList())
        }
        val rate = if (year == 2016) 90 else 180
        val debt = state.debt + state.debt.applyPerMille(rate)
        if (debt.cents > GameState.ORDINARY_DEBT_CAP_CENTS || (debt.cents > 0 && state.cash.cents == 0L)) {
            val ended = state.copy(debt = debt, lastInterestSettledYear = year, phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding, endingId = "bankruptcy", endingReason = "debt_cap_exceeded", revision = state.revision + 1)
            return EngineResult(state, ended, listOf(EngineEffect.PhaseChanged(Phase.Ended, "bankruptcy")))
        }
        val settled = state.copy(debt = debt, lastInterestSettledYear = year)
        if (state.position.current == WorkdayRef.LAST) return finishMonth(settled, null)
        return EngineResult(state, settled.copy(phase = Phase.MonthlySkill, pendingStep = PendingStep.ChooseMonthlySkill, revision = state.revision + 1), listOf(EngineEffect.PhaseChanged(Phase.MonthlySkill, "interest settled")))
    }

    private fun onChooseMonthlySkill(state: GameState, command: Command.ChooseMonthlySkill): EngineResult {
        if (state.phase != Phase.MonthlySkill) throw EngineError.WrongPhase(Phase.MonthlySkill.name, state.phase.name)
        return finishMonth(state, command.skill)
    }

    private fun onPlaceBet(state: GameState, command: Command.PlaceBet): EngineResult {
        if (state.phase != Phase.DayEntry) throw EngineError.WrongPhase("PlaceBet/${Phase.DayEntry.name}", state.phase.name)
        if (state.activeBet != null) throw EngineError.InvalidCommand("an unsettled bet already exists")
        val market = content.bettingMarkets().firstOrNull { it.id == command.marketId }
            ?: throw EngineError.InvalidCommand("unknown betting market")
        if (market.openWorkdayIndex != state.position.current.index) throw EngineError.InvalidCommand("market is not open today")
        if (market.options.none { it.id == command.optionId }) throw EngineError.InvalidCommand("unknown betting option")
        if (market.settleWorkdayIndex != command.settleWorkdayIndex) throw EngineError.InvalidCommand("settlement date does not match content")
        if (command.marketId.isBlank() || command.optionId.isBlank()) throw EngineError.InvalidCommand("market and option are required")
        if (command.settleWorkdayIndex <= state.position.current.index || command.settleWorkdayIndex !in 0 until WorkdayRef.TOTAL_WORKDAYS) throw EngineError.InvalidCommand("settlement day must be after the current day")
        val amount = Money.parseYuan(command.amountYuan).getOrElse { throw EngineError.InvalidCommand("invalid bet amount") }
        if (amount.cents < MIN_BET_CENTS) throw EngineError.InvalidCommand("bet is below the minimum")
        val effectiveCap = effectiveBetCap(state)
        if (amount > effectiveCap) throw EngineError.InvalidCommand("bet exceeds the available cap")
        if (amount > state.cash) throw EngineError.InvalidCommand("bet exceeds cash")
        if (state.settledBetMarketIds.contains(command.marketId)) throw EngineError.InvalidCommand("market already settled")
        val odds = INITIAL_ODDS_TENTHS + maxOf(state.attributes.luckPeak, state.attributes.luck)
        val snapshot = BetSnapshot(command.marketId, command.optionId, amount, state.attributes.luck, odds, effectiveCap, command.settleWorkdayIndex)
        val exhausted = amount == state.cash && (state.debt.cents > 0 || state.specialLoanDue > Money.ZERO)
        val next = state.copy(cash = state.cash - amount, activeBet = snapshot,
            phase = if (exhausted) Phase.Ended else Phase.DayEntry,
            pendingStep = if (exhausted) PendingStep.AcknowledgeEnding else state.pendingStep,
            endingId = if (exhausted) "bankruptcy" else state.endingId,
            endingReason = if (exhausted) "cash_exhausted" else state.endingReason,
            acceptedBetCount = state.acceptedBetCount + 1,
            last2016BetCap = if (state.position.current.index < CHAPTER2_FIRST_DAY) effectiveCap else state.last2016BetCap,
            bettingCap = capForState(state, state.acceptedBetCount + 1),
            lastBetOutcome = null, lastBetPayout = null, revision = state.revision + 1)
        return EngineResult(state, next, listOf(EngineEffect.Message(Key.BET_PLACED)))
    }

    private fun onSettleBet(state: GameState, command: Command.SettleBet): EngineResult {
        if (state.phase != Phase.DayEntry) throw EngineError.WrongPhase("SettleBet/${Phase.DayEntry.name}", state.phase.name)
        val bet = state.activeBet ?: throw EngineError.InvalidCommand("no active bet")
        if (bet.marketId != command.marketId) throw EngineError.InvalidCommand("market does not match active bet")
        if (state.position.current.index < bet.settleWorkdayIndex) throw EngineError.InvalidCommand("market has not settled")
        val market = content.bettingMarkets().firstOrNull { it.id == bet.marketId }
            ?: throw EngineError.InvalidCommand("missing betting result content")
        if (market.cancelled) {
            val count = (state.acceptedBetCount - 1).coerceAtLeast(0)
            val refunded = state.copy(cash = state.cash + bet.amount, activeBet = null,
                acceptedBetCount = count, bettingCap = capForState(state, count),
                lastBetOutcome = BetOutcome.CANCELLED, lastBetPayout = bet.amount,
                todayBetOutcome = BetOutcome.CANCELLED, todayBetPayout = bet.amount,
                settledBetMarketIds = state.settledBetMarketIds + bet.marketId, revision = state.revision + 1)
            return EngineResult(state, refunded, listOf(EngineEffect.Message(Key.BET_CANCELLED)))
        }
        val guessed = bet.optionId == market.correctOptionId
        val payout = if (guessed) bet.amount.applyOddsTenths(bet.oddsTenths) else Money.ZERO
        val currentLuck = state.attributes.luck.coerceIn(0, MAX_LUCK_BOOST)
        val nextLuck = if (guessed) currentLuck else (currentLuck + 1).coerceAtMost(MAX_LUCK_BOOST)
        val wins = state.successfulBetCount + if (guessed) 1 else 0
        val dopamine = state.attributes.dopamine + if (guessed && wins % 5 == 0) 1 else 0
        val outcome = if (guessed) BetOutcome.WON else BetOutcome.LOST
        val next = state.copy(cash = state.cash + payout, activeBet = null, settledBetMarketIds = state.settledBetMarketIds + bet.marketId, successfulBetCount = wins, lastBetOutcome = outcome, lastBetPayout = payout, todayBetOutcome = outcome, todayBetPayout = payout, attributes = state.attributes.copy(luck = nextLuck, dopamine = dopamine, luckPeak = maxOf(state.attributes.luckPeak, nextLuck, state.attributes.luck)), revision = state.revision + 1)
        val effect = if (guessed) EngineEffect.Message(Key.BET_WON, listOf(payout.formatYuan()))
                     else EngineEffect.Message(Key.BET_LOST, listOf(nextLuck))
        return EngineResult(state, next, listOf(effect))
    }

    private fun capForState(state: GameState, count: Int): Money = state.betting2017Base?.let { base ->
        Money(java.math.BigDecimal(base.cents).multiply(java.math.BigDecimal("1.3").pow((count - state.betting2017StartCount).coerceAtLeast(0)))
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact())
    } ?: capForCount(count)

    private fun capForCount(count: Int): Money = Money(
        java.math.BigDecimal("500000").multiply(java.math.BigDecimal("1.3").pow(count))
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact(),
    )

    private fun finishMonth(state: GameState, skill: Command.MonthlySkillOption?): EngineResult {
        var attrs = state.attributes
        val choseHealth = skill == Command.MonthlySkillOption.HEALTH
        when (skill) {
            Command.MonthlySkillOption.HEALTH -> attrs = attrs.copy(health = attrs.health + 1)
            Command.MonthlySkillOption.DOPAMINE -> attrs = attrs.copy(dopamine = attrs.dopamine + 1)
            Command.MonthlySkillOption.ENGLISH -> attrs = attrs.copy(english = attrs.english + 1)
            null -> Unit
        }
        val withoutHealth = if (choseHealth) 0 else state.monthsWithoutHealth + if (skill == null) 0 else 1
        if (!choseHealth && withoutHealth >= 4) attrs = attrs.copy(dopamine = attrs.dopamine - 1)
        val chapterEnd = state.position.current.monthIndex % WorkdayRef.CHAPTER_MONTHS == WorkdayRef.CHAPTER_MONTHS - 1
        val chapterHealth = state.chapterHealthChosen || choseHealth
        val chapterEnglish = state.chapterEnglishChosen || skill == Command.MonthlySkillOption.ENGLISH
        if (chapterEnd && chapterHealth && chapterEnglish) attrs = attrs.copy(dopamine = attrs.dopamine + 1)
        val lowMonths = if (attrs.dopamine < 3) state.lowDopamineMonths + 1 else 0
        val base = state.copy(attributes = attrs, monthsWithoutHealth = withoutHealth, lowDopamineMonths = lowMonths, chapterHealthChosen = if (chapterEnd) false else chapterHealth, chapterEnglishChosen = if (chapterEnd) false else chapterEnglish)
        if (lowMonths >= 7) return EngineResult(state, base.copy(phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding, endingId = "low_dopamine", endingReason = "prolonged_low", revision = state.revision + 1), listOf(EngineEffect.PhaseChanged(Phase.Ended, "low dopamine")))
        if (state.position.current.chapter == 6 && state.position.current == WorkdayRef.LAST && attrs.health < 2) return EngineResult(state, base.copy(phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding, endingId = "health_death", endingReason = "final_health_low", revision = state.revision + 1), listOf(EngineEffect.PhaseChanged(Phase.Ended, "final health")))
        if (state.position.current == WorkdayRef.LAST) return EngineResult(state, base.copy(phase = Phase.Ended, pendingStep = PendingStep.AcknowledgeEnding, endingId = if (base.debt.cents == 0L) "debt_free_restart" else "indebted_layoff", endingReason = "final_check", revision = state.revision + 1), listOf(EngineEffect.PhaseChanged(Phase.Ended, "final check")))
        val nextIndex = state.position.current.index + 1
        val nextRef = WorkdayRef(nextIndex)
        // ---- 人才动态：离职（入职时掷定）与晋升（每满半年判定一次，最多 2 次）
        val (withCareers, careerEvents, trustDelta) = applyCareerEvents(base, nextIndex)
        val nextSalary = state.nextMonthlySalary
        val salaryReport = if (nextRef.dayOfMonth == 0 && nextSalary != state.monthlySalary) {
            SalaryReport(from = state.monthlySalary, to = nextSalary)
        } else {
            null
        }
        val next = withCareers.copy(
            phase = Phase.DayEntry,
            pendingStep = PendingStep.None,
            position = state.position.copy(current = nextRef),
            browsedCandidateIndex = 0,
            selectedCandidateId = null,
            markedStatements = emptyMap(),
            useLuckOnSubmission = false,
            monthlySalary = nextSalary,
            pendingRaise = 0,
            attributes = withCareers.attributes.copy(
                trust = (withCareers.attributes.trust + trustDelta).coerceAtMost(MAX_TRUST),
            ),
            todayCareerEvents = careerEvents,
            salaryReport = salaryReport,
            // 竞猜播报只留当天
            todayBetOutcome = null,
            todayBetPayout = null,
            revision = state.revision + 1,
        )
        return EngineResult(state, next, listOf(EngineEffect.PhaseChanged(Phase.DayEntry, "next workday")))
    }

    /**
     * 结算某一天的**人才动态**：离职与晋升。
     *
     * 规则（作者 2026-10-11 定）：
     *  - 成功推送的人约 **10%** 会在**入职半年内**离职；日期在入职时一次掷定；
     *  - 在职者**每满半年**以 **30%** 的概率晋升一次，**最多 2 次**；
     *  - 每离职一人 **信任 −1**，每晋升一次 **信任 +1**（信任 < 0 仍会照常触发结局）。
     *
     * 只处理“就是今天”的事件，所以同一天重复推进不会重复计数。
     */
    private fun applyCareerEvents(
        state: GameState,
        today: Int,
    ): Triple<GameState, List<CareerEvent>, Int> {
        if (state.hired.isEmpty()) return Triple(state, emptyList(), 0)
        val events = mutableListOf<CareerEvent>()
        var delta = 0
        val updated = state.hired.map { record ->
            if (record.resigned) return@map record
            if (record.resignOn == today) {
                events += CareerEvent(record.candidateId, CareerEventKind.RESIGNED, today)
                delta -= 1
                return@map record.copy(resigned = true)
            }
            val elapsed = today - record.workdayIndex
            val dueForReview = elapsed > 0 &&
                elapsed % HALF_YEAR_WORKDAYS == 0 &&
                record.promotions < MAX_PROMOTIONS
            // 每次半年评审用不同盐值，所以两次晋升是两次独立掷点
            val salt = CareerRolls.SALT_PROMOTION + record.promotions
            if (dueForReview && CareerRolls.chance(state.rngSeed, record.candidateId, salt, PROMOTION_BPS)) {
                events += CareerEvent(record.candidateId, CareerEventKind.PROMOTED, today)
                delta += 1
                return@map record.copy(promotions = record.promotions + 1)
            }
            record
        }
        if (events.isEmpty()) return Triple(state, emptyList(), 0)
        return Triple(state.copy(hired = updated), events, delta)
    }

    /**
     * 推进到下一个工作日；首月第 6 日之后进入 MonthIncome（月末经济 P3 接线，
     * P2 以占位面板明确标注，不预演任何数值）。
     */
    private fun advanceDay(state: GameState): EngineResult {
        val current = state.position.current
        if (current.dayOfMonth == content.daysPerMonth - 1) {
            val next = state.copy(
                phase = Phase.MonthIncome,
                pendingStep = PendingStep.None,
                revision = state.revision + 1L,
            )
            return EngineResult(
                previousState = state,
                newState = next,
                effects = listOf(
                    EngineEffect.PhaseChanged(Phase.MonthIncome, "month complete (P3 pending)"),
                ),
            )
        }
        val next = state.copy(
            phase = Phase.DayEntry,
            pendingStep = PendingStep.None,
            position = state.position.copy(current = WorkdayRef(current.index + 1)),
            browsedCandidateIndex = 0,
            selectedCandidateId = null,
            markedStatements = emptyMap(),
            useLuckOnSubmission = false,
            revision = state.revision + 1L,
        )
        return EngineResult(
            previousState = state,
            newState = next,
            effects = listOf(
                EngineEffect.PhaseChanged(Phase.DayEntry, "next workday ${current.index + 2}"),
            ),
        )
    }

    /** Apply a fixed-date event exactly once, atomically with its numeric effect. */
    private fun enterDay(state: GameState): GameState {
        val index = state.position.current.index
        if (index >= CHAPTER2_FIRST_DAY && state.betting2017Base == null) {
            val base = (state.last2016BetCap ?: state.bettingCap).timesInt(2)
            return enterDay(state.copy(betting2017Base = base, bettingCap = base, betting2017StartCount = state.acceptedBetCount))
        }
        if (index == SPECIAL_LOAN_CLOSED_DAY && EVENT_SPECIAL_LOAN_CLOSED !in state.fixedEventsSeen) {
            return state.copy(phase = Phase.EventNotice, pendingStep = PendingStep.ViewEvent,
                pendingEventId = EVENT_SPECIAL_LOAN_CLOSED,
                fixedEventsSeen = state.fixedEventsSeen + EVENT_SPECIAL_LOAN_CLOSED,
                specialLoanForgiven = state.specialLoanDue > Money.ZERO,
                specialLoanDue = Money.ZERO)
        }
        if (index == MANAGER_HANDOVER_DAY && EVENT_MANAGER_HANDOVER !in state.fixedEventsSeen) {
            return state.copy(
                phase = Phase.EventNotice, pendingStep = PendingStep.ViewEvent,
                pendingEventId = EVENT_MANAGER_HANDOVER,
                fixedEventsSeen = state.fixedEventsSeen + EVENT_MANAGER_HANDOVER,
                attributes = state.attributes.copy(trust = 3),
            )
        }
        if (index == OVERSEAS_DAY && EVENT_OVERSEAS_OFFER !in state.fixedEventsSeen) {
            val id = if (state.attributes.work >= 20 && state.attributes.english >= 10 && state.attributes.trust >= 10) EVENT_OVERSEAS_OFFER else EVENT_OVERSEAS_MISSED
            val ended = id == EVENT_OVERSEAS_OFFER
            return state.copy(
                phase = if (ended) Phase.Ended else Phase.EventNotice,
                pendingStep = if (ended) PendingStep.AcknowledgeEnding else PendingStep.ViewEvent,
                pendingEventId = id,
                fixedEventsSeen = state.fixedEventsSeen + EVENT_OVERSEAS_OFFER,
                endingId = if (ended) if (state.debt.cents == 0L) "overseas_repaid" else "overseas_indebted" else state.endingId,
                endingReason = if (ended) if (state.debt.cents == 0L) "debt_zero" else "debt_remaining" else state.endingReason,
            )
        }
        if (index == WINTER_COLD_DAY && EVENT_WINTER_COLD !in state.fixedEventsSeen) {
            val health = state.attributes.health - 5
            val dead = health < 2
            return state.copy(
                phase = if (dead) Phase.Ended else Phase.EventNotice,
                pendingStep = if (dead) PendingStep.AcknowledgeEnding else PendingStep.ViewEvent,
                pendingEventId = EVENT_WINTER_COLD,
                fixedEventsSeen = state.fixedEventsSeen + EVENT_WINTER_COLD,
                attributes = state.attributes.copy(health = health),
                endingId = if (dead) "health_death" else state.endingId,
                endingReason = if (dead) "cold_health_low" else state.endingReason,
            )
        }
        return state
    }

    private fun eventMessageKey(id: String): Key = when (id) {
        EVENT_CHAPTER2_SUCCESS -> Key.ENGINE_EVENT_CHAPTER2_SUCCESS
        EVENT_MANAGER_HANDOVER -> Key.ENGINE_EVENT_MANAGER_HANDOVER
        EVENT_OVERSEAS_MISSED -> Key.ENGINE_EVENT_OVERSEAS_MISSED
        EVENT_WINTER_COLD -> Key.ENGINE_EVENT_WINTER_COLD
        else -> Key.COMMON_GOT_IT
    }

    private fun todayCandidates(state: GameState): List<CandidateContent> {
        val candidates = content.workdayCandidates(state.position.current.index)
        if (candidates.size != 2) {
            throw EngineError.InvalidCommand("workday ${state.position.current.index} must offer exactly two candidates")
        }
        return candidates
    }

    companion object {
        const val EVENT_CHAPTER2_SUCCESS = "chapter2_success"
        const val EVENT_MANAGER_HANDOVER = "manager_handover"
        const val EVENT_OVERSEAS_OFFER = "overseas_offer"
        const val EVENT_OVERSEAS_MISSED = "overseas_missed"
        const val EVENT_WINTER_COLD = "winter_cold"
        const val CHAPTER2_FIRST_DAY = 36
        const val MANAGER_HANDOVER_DAY = 108
        const val OVERSEAS_DAY = 210
        const val WINTER_COLD_DAY = 247
        const val MIN_BET_CENTS = 50_000L
        const val INITIAL_ODDS_TENTHS = 19
        const val CAP_GROWTH_PER_MILLE = 300
        // 保底档位（工作能力阈值）与周期
        const val PITY_TIER_LOW = 20
        const val PITY_TIER_MID = 30
        const val PITY_TIER_TOP = 50
        const val PITY_PERIOD_LOW = 5
        const val PITY_PERIOD_MID = 4
        const val PITY_PERIOD_TOP = 3
        const val SUCCESS_REWARD_EVERY = 3
        const val LUCK_SUCCESS_BPS = 100

        /**
         * v3 rule: a correct review adds 25 percentage points (was 15) and the普通
         * probability is capped at 90% (was 95%). Both values are contract constants
         * shared with the UI so the displayed bonus can never drift from the engine.
         */
        const val REVIEW_SUCCESS_BPS = 2_500
        const val MAX_LUCK_BOOST = 10
        const val MAX_SUCCESS_BPS = 9_000

        fun effectiveBetCap(state: GameState): Money =
            minOf(state.bettingCap, state.monthlySalary.timesInt(3), state.cash)

        fun isForcedNext(state: GameState): Boolean {
            if (state.position.current.index == CHAPTER2_FIRST_DAY && EVENT_CHAPTER2_SUCCESS !in state.fixedEventsSeen) return true
            val tier = when {
                state.attributes.work >= 50 -> 50
                state.attributes.work >= 30 -> 30
                state.attributes.work >= 20 -> 20
                else -> 0
            }
            val period = pityPeriodFor(tier)
            return period > 0 && (if (tier == state.guaranteeTier) state.guaranteePeriodDraws else 0) + 1 >= period
        }

        /** UI 显示“保底还差几笔”用的纯函数；档位 0（未激活）返回 0。 */
        fun pityPeriodFor(tier: Int): Int = when (tier) {
            PITY_TIER_TOP -> PITY_PERIOD_TOP
            PITY_TIER_MID -> PITY_PERIOD_MID
            PITY_TIER_LOW -> PITY_PERIOD_LOW
            else -> 0
        }

        const val ENDING_EXPOSED = "exposed"
        const val SPECIAL_LOAN_PRINCIPAL = 10_000_000L
        const val SPECIAL_LOAN_TOTAL = 15_000_000L
        const val SPECIAL_LOAN_CLOSED_DAY = 54
        const val EVENT_SPECIAL_LOAN_CLOSED = "special_loan_closed"
        const val ENDING_DEBT_FREE_RESTART = "debt_free_restart"
        /** 半年 ≈ 126 个工作日；晋升每满半年判定一次。 */
        const val HALF_YEAR_WORKDAYS = 126

        /** 成功推送的人里约 10% 会在半年内离职。 */
        const val RESIGN_BPS = 1_000

        /** 每满半年，在职者以 30% 的概率晋升一次。 */
        const val PROMOTION_BPS = 3_000

        /** 晋升上限：最多 2 次（第二次起档案袋边框变金色）。 */
        const val MAX_PROMOTIONS = 2

        /** 信任上限；下限不设限——信任 < 0 会立即触发结局。 */
        const val MAX_TRUST = 20

        const val ENDING_REASON_TRUST_NEGATIVE = "trust_negative"
    }
}
