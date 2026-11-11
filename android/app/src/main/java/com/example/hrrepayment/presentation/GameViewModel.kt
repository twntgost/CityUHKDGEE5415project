package com.example.hrrepayment.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.hrrepayment.audio.AudioEvent
import com.example.hrrepayment.audio.AudioEventRouter
import com.example.hrrepayment.audio.GameAudio
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.engine.EngineError
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.data.save.SaveRepository
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.save.SaveSlotSummary
import com.example.hrrepayment.data.save.SaveSnapshot
import com.example.hrrepayment.data.save.SaveWriteOutcome
import com.example.hrrepayment.data.save.describe
import com.example.hrrepayment.data.settings.SettingsRepository
import com.example.hrrepayment.di.AppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Boot menu + workbench controller.
 *
 * ## Ordering contract (技术文档 §3)
 *
 * Every action runs as **验证 → 计算新状态 → 原子保存 → 发布 UI**, inside one
 * serial section guarded by [mutex]. The new state is never published before the
 * save lands; on a write failure the previous state stays on screen with a retryable
 * banner. An action already running blocks further actions ([inFlight]), so a rapid
 * double tap cannot mint or overwrite two saves.
 *
 * ## v3 additions
 *
 *  - **Two slots**: [Screen.Menu] carries a [SaveSlotSummary] per slot, so the menu can
 *    show “Empty slot”/progress/disabled without loading a run into the UI.
 *  - **Language**: the chosen [Lang] is persisted device-wide and pushed to
 *    [onLanguageChange], which swaps the content bundle behind the engine.
 *  - **Dossiers**: [DossierEntry] list resolved from the content pack, never stored
 *    as text, so it re-renders correctly after a language switch.
 */
class GameViewModel(
    private val engine: GameEngine,
    private val slots: Map<SaveSlot, SaveRepository>,
    private val settings: SettingsRepository,
    val content: ContentRef,
    private val audio: GameAudio,
    private val onLanguageChange: (Lang) -> Unit = {},
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val seedGenerator: () -> Long = { System.nanoTime() },
) : ViewModel() {

    private val mutex = Mutex()

    /** True while a command is in flight; checked synchronously to drop double taps. */
    private var inFlight = false

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _lang = MutableStateFlow(Lang.DEFAULT)
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    /** 音效开关（设置面板）。关闭后所有音效静音，且不打断正在播放的声音。 */
    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    /** Slot last opened; the dossier falls back to it when the menu is showing. */
    private var activeSlot: SaveSlot = SaveSlot.DEFAULT

    /** Slot summaries mirrored outside the UI state so actions can consult them. */
    private var slotSummaries: List<SaveSlotSummary> = SaveSlot.all.map { SaveSlotSummary(it, SaveSnapshot.Empty) }

    /** Content bundle the UI reads opening copy / asset paths from. */
    val contentRef: ContentRef get() = content

    init {
        dispatch {
            val restored = settings.currentLanguage()
            applyLanguage(restored)
            val summaries = readSlots()
            slotSummaries = summaries
            _uiState.value.copy(
                screen = Screen.Menu(panel = MenuPanel.START, slots = summaries),
                dossier = null,
                banner = null,
            )
        }
    }

    // ------------------------------------------------------------- menu actions

    /** Switch between the start / new-game / load-game panels. */
    fun openPanel(panel: MenuPanel) = dispatch {
        val menu = _uiState.value.screen as? Screen.Menu ?: return@dispatch _uiState.value
        _uiState.value.copy(screen = menu.copy(panel = panel))
    }

    /** Flip the language and reload the content bundle behind it. */
    fun toggleLanguage() = dispatch {
        applyLanguage(_lang.value.other)
        _uiState.value.copy(dossier = refreshDossier(_uiState.value))
    }

    fun setLanguage(lang: Lang) = dispatch {
        applyLanguage(lang)
        _uiState.value.copy(dossier = refreshDossier(_uiState.value))
    }

    /**
     * “New Game” on [slot]. An occupied slot first shows the overwrite dialog; the
     * confirmation goes through [confirmOverwrite] so a single tap can never destroy
     * an existing run.
     */
    fun requestNewGame(slot: SaveSlot) = dispatch {
        val summaries = readSlots()
        slotSummaries = summaries
        val occupied = summaries.first { it.slot == slot }.isPlayable
        if (occupied) {
            _uiState.value.copy(
                screen = (_uiState.value.screen as? Screen.Menu)?.copy(slots = summaries) ?: _uiState.value.screen,
                pendingOverwrite = slot,
            )
        } else {
            startNewGameInternal(slot, summaries)
        }
    }

    fun cancelOverwrite() = dispatch { _uiState.value.copy(pendingOverwrite = null) }

    fun confirmOverwrite() = dispatch {
        val slot = _uiState.value.pendingOverwrite ?: return@dispatch _uiState.value
        startNewGameInternal(slot, readSlots())
    }

    /** “Load Game” on [slot]: enter the run, or explain why the slot cannot be used. */
    fun loadGame(slot: SaveSlot) = dispatch {
        when (val snapshot = slots.getValue(slot).read()) {
            is SaveSnapshot.Loaded -> {
                activeSlot = slot
                _uiState.value.copy(
                    screen = Screen.Run(slot, snapshot.state, snapshot.savedAtMillis),
                    banner = null,
                    blockProblem = null,
                )
            }
            SaveSnapshot.Empty -> _uiState.value.copy(
                banner = Banner(Key.MENU_SLOT_EMPTY),
                problemSlot = slot,
            )
            else -> _uiState.value.copy(blockProblem = snapshot.toProblem(), problemSlot = slot)
        }
    }

    /** Back to the poster menu from a run (progress is already persisted). */
    fun returnToMenu() = dispatch {
        _uiState.value.copy(
            screen = Screen.Menu(panel = MenuPanel.START, slots = readSlots()),
            dossier = null,
            banner = null,
        )
    }

    /** Explicit, confirmed deletion of one slot. */
    fun deleteSlot(slot: SaveSlot) = dispatch {
        when (val outcome = slots.getValue(slot).clear()) {
            SaveWriteOutcome.Ok -> _uiState.value.copy(
                screen = Screen.Menu(panel = MenuPanel.START, slots = readSlots()),
                blockProblem = null,
                problemSlot = null,
                banner = Banner(Key.BOOT_SLOT_CLEARED),
            )
            is SaveWriteOutcome.Failed -> _uiState.value.copy(
                banner = Banner(Key.BOOT_SAVE_FAILED, listOf(outcome.detail)),
            )
        }
    }

    /** Retry after a transient read failure. */
    fun retryLoad() = dispatch {
        val slot = activeSlot
        when (val snapshot = slots.getValue(slot).read()) {
            is SaveSnapshot.Loaded -> _uiState.value.copy(
                screen = Screen.Run(slot, snapshot.state, snapshot.savedAtMillis),
                blockProblem = null,
            )
            SaveSnapshot.Empty -> _uiState.value.copy(
                screen = Screen.Menu(MenuPanel.START, readSlots()),
                blockProblem = null,
            )
            else -> _uiState.value.copy(blockProblem = snapshot.toProblem(), problemSlot = slot)
        }
    }

    fun dismissProblem() = dispatch { _uiState.value.copy(blockProblem = null, problemSlot = null) }

    // ------------------------------------------------------------- dossier

    fun openDossiers() = dispatch { _uiState.value.copy(dossier = buildDossier(_uiState.value)) }

    fun closeDossiers() = dispatch { _uiState.value.copy(dossier = null) }

    // ------------------------------------------------------------- run actions

    fun acknowledgeOpening() = dispatch { acknowledgeOpeningInternal() }

    fun enterRecruiting() = dispatch {
        runSimple { Command.EnterRecruiting(it.revision, it.saveId, idGenerator()) }
    }

    fun browseCandidate(index: Int) = dispatch {
        runSimple { Command.BrowseCandidate(it.revision, it.saveId, idGenerator(), index) }
    }

    fun selectCandidate(candidateId: String) = dispatch {
        runSimple { Command.SelectCandidate(it.revision, it.saveId, idGenerator(), candidateId) }
    }

    /** 推送当前页简历（保存成功后才发音频事件；当前播放器静音）。 */
    fun submitCandidate() = dispatch { submitCandidateInternal() }

    fun markStatement(candidateId: String, statementId: String) = dispatch {
        runSimple { Command.MarkStatement(it.revision, it.saveId, idGenerator(), candidateId, statementId) }
    }

    fun setLuckUsage(enabled: Boolean) = dispatch {
        runSimple { Command.SetLuckUsage(it.revision, it.saveId, idGenerator(), enabled) }
    }

    fun acknowledgeResult() = dispatch {
        runSimple { Command.AcknowledgeResult(it.revision, it.saveId, idGenerator()) }
    }

    fun acknowledgeEvent(eventId: String) = dispatch {
        runSimple { Command.AcknowledgeEvent(it.revision, it.saveId, idGenerator(), eventId) }
    }

    fun chooseSuccessReward(option: Command.SuccessRewardOption) = dispatch {
        runSimple { Command.ChooseSuccessReward(it.revision, it.saveId, idGenerator(), option) }
    }

    fun finishIncome() = dispatch {
        runSimple { Command.FinishIncome(it.revision, it.saveId, idGenerator()) }
    }

    fun repayDebt(amountYuan: String) = dispatch {
        runSimple { Command.RepayDebt(it.revision, it.saveId, idGenerator(), amountYuan) }
    }

    fun decideSpecialLoan(accept: Boolean) = dispatch {
        runSimple { Command.DecideSpecialLoan(it.revision, it.saveId, idGenerator(), accept) }
    }

    fun readSpecialLoanInvitation() = dispatch {
        runSimple { Command.ReadSpecialLoanInvitation(it.revision, it.saveId, idGenerator()) }
    }

    fun repaySpecialLoan() = dispatch {
        runSimple { Command.RepaySpecialLoan(it.revision, it.saveId, idGenerator()) }
    }

    fun finishEconomy() = dispatch {
        runSimple { Command.FinishEconomy(it.revision, it.saveId, idGenerator()) }
    }

    fun chooseMonthlySkill(skill: Command.MonthlySkillOption) = dispatch {
        runSimple { Command.ChooseMonthlySkill(it.revision, it.saveId, idGenerator(), skill) }
    }

    /** Freeze a market's terms and stake after the caller's confirmation gesture. */
    fun placeBet(marketId: String, optionId: String, amountYuan: String) = dispatch {
        runSimple(audioOnSuccess = { before, after ->
            // 下注成功才响；失败/参数不合法时 after 不变，这里自然不播
            if (after.activeBet != before.activeBet) sfx(AudioEvent.BetStart)
        }) { current ->
            Command.PlaceBet(
                expectedRevision = current.revision,
                saveId = current.saveId,
                commandId = idGenerator(),
                marketId = marketId,
                optionId = optionId,
                amountYuan = amountYuan,
                settleWorkdayIndex = content.bettingMarkets().first { it.id == marketId }.settleWorkdayIndex,
            )
        }
    }

    fun dismissBanner() = dispatch { _uiState.value.copy(banner = null) }

    // ---------------------------------------------------------- UI 材质音效
    // 这几个音效不改变任何游戏状态，只由界面动作触发（打开档案袋/翻简历/红笔圈/盖章）。

    /** 解开牛皮纸档案袋的声音。 */
    /** 音效统一出口：设置里关掉音效后一个都不播。 */
    private fun sfx(event: AudioEvent) {
        if (_soundEnabled.value) audio.play(event)
    }

    fun setSoundEnabled(enabled: Boolean) {
        _soundEnabled.value = enabled
        if (!enabled) audio.stop()
        viewModelScope.launch { settings.setSoundEnabled(enabled) }
    }

    /** 手动存档：把当前 Run 状态原样写回存档槽（不改游戏状态）。 */
    fun saveGameNow() = dispatch {
        val current = _uiState.value
        val run = current.screen as? Screen.Run ?: return@dispatch current
        when (val outcome = slots.getValue(run.slot).write(run.state)) {
            SaveWriteOutcome.Ok -> {
                slotSummaries = readSlots()
                current.copy(banner = Banner(Key.SETTINGS_SAVED))
            }
            is SaveWriteOutcome.Failed -> current.withBanner(Banner(Key.BOOT_SAVE_FAILED, listOf(outcome.detail)))
        }
    }

    /** 手动读档：把存档槽的状态读回屏幕（未保存的变化会被丢弃）。 */
    fun loadGameNow() = dispatch {
        val current = _uiState.value
        val slot = current.currentSlot() ?: activeSlot
        when (val snapshot = slots.getValue(slot).read()) {
            is SaveSnapshot.Loaded -> current.copy(
                screen = Screen.Run(slot, snapshot.state, snapshot.savedAtMillis),
                dossier = refreshDossier(current, snapshot.state),
                banner = Banner(Key.SETTINGS_LOADED),
                blockProblem = null,
            )
            SaveSnapshot.Empty -> current.copy(banner = Banner(Key.MENU_SLOT_EMPTY))
            else -> current.withBanner(Banner(Key.BOOT_SAVE_FAILED, listOf("read")))
        }
    }

    fun playDossierOpenSound() = sfx(AudioEvent.DossierOpen)

    /** 两份简历之间翻页的极短纸声。 */
    fun playResumeFlipSound() = sfx(AudioEvent.ResumeFlip)

    /** 红笔圈出造假部分时的笔尖摩擦声。 */
    fun playPenCircleSound() = sfx(AudioEvent.PenCircle)

    /** 推送成功弹窗的盖章声。 */
    fun playStampSound() = sfx(AudioEvent.Stamp)

    // ------------------------------------------------------------- internal

    private fun dispatch(block: suspend () -> AppUiState) {
        if (inFlight) return
        inFlight = true
        _uiState.value = _uiState.value.asBusy(true)

        viewModelScope.launch {
            val next = try {
                mutex.withLock { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Last-resort net: never crash the screen, never lose the old state.
                _uiState.value.withBanner(Banner(Key.BOOT_ACTION_FAILED, listOf(describe(e))))
            } finally {
                inFlight = false
            }
            _uiState.value = next.asBusy(false)
        }
    }

    private suspend fun readSlots(): List<SaveSlotSummary> =
        SaveSlot.all.map { SaveSlotSummary(it, slots.getValue(it).read()) }

    private suspend fun applyLanguage(lang: Lang) {
        if (_lang.value != lang) {
            settings.setLanguage(lang)
            onLanguageChange(lang)
            _lang.value = lang
        }
    }

    private suspend fun startNewGameInternal(slot: SaveSlot, summaries: List<SaveSlotSummary>): AppUiState {
        val previous = (summaries.firstOrNull { it.slot == slot }?.loadedOrNull)?.state
        val command = Command.StartNewGame(
            expectedRevision = previous?.revision ?: 0L,
            saveId = previous?.saveId ?: Command.NO_SAVE_ID,
            commandId = idGenerator(),
            newSaveId = idGenerator(),
            newRngSeed = seedGenerator(),
        )
        val outcome = try {
            engine.apply(previous, command)
        } catch (e: EngineError) {
            return _uiState.value.withBanner(e.toBanner())
        }
        return persist(slot = slot, newState = outcome.newState, previousState = previous)
    }

    private suspend fun acknowledgeOpeningInternal(): AppUiState {
        val current = _uiState.value.currentState()
            ?: return _uiState.value.withBanner(Banner(Key.ENGINE_ERR_NO_SAVE))
        val command = Command.AcknowledgeOpening(current.revision, current.saveId, idGenerator())
        val outcome = try {
            engine.apply(current, command)
        } catch (e: EngineError) {
            return _uiState.value.withBanner(e.toBanner())
        }
        return persist(_uiState.value.currentSlot() ?: activeSlot, outcome.newState, current)
    }

    /**
     * 推送：保存成功后才发音频事件（结局优先，其次简历选择音）。
     * 保存失败/命令被拒（含重复点击）一律不出声。
     */
    private suspend fun submitCandidateInternal(): AppUiState {
        val current = _uiState.value.currentState()
            ?: return _uiState.value.withBanner(Banner(Key.ENGINE_ERR_NO_SAVE))
        val selectedId = content.workdayCandidates(current.position.current.index)
            .getOrNull(current.browsedCandidateIndex)?.id
            ?: return _uiState.value.withBanner(Banner(Key.BOOT_CANDIDATE_UNAVAILABLE))
        val statementId = current.markedStatements[selectedId]
            ?: return _uiState.value.withBanner(Banner(Key.BOOT_MARK_FIRST))
        val command = Command.SubmitCandidate(
            expectedRevision = current.revision,
            saveId = current.saveId,
            commandId = idGenerator(),
            candidateId = selectedId,
            statementId = statementId,
        )
        val outcome = try {
            engine.apply(current, command)
        } catch (e: EngineError) {
            return _uiState.value.withBanner(e.toBanner())
        }
        val slot = _uiState.value.currentSlot() ?: activeSlot
        val next = persist(slot, outcome.newState, current)
        if ((next.screen as? Screen.Run)?.state === outcome.newState) {
            val today = content.workdayCandidates(current.position.current.index)
            val pushed = today.firstOrNull { it.id == selectedId }
            if (pushed != null) {
                audio.play(
                    AudioEventRouter.forSubmission(
                        todayGenders = today.map { it.gender },
                        pushedGender = pushed.gender,
                        ended = outcome.newState.phase == Phase.Ended,
                        endingId = outcome.newState.endingId,
                    ),
                )
            }
        }
        return next
    }

    /**
     * 无“提交推送”音频副作用的命令：构建 → 应用 → 持久化。
     *
     * [audioOnSuccess] 用来接非推送类的音效（下注、竞猜结算等）：只有状态真的推进了
     * 才会调用，异常/被拒绝的命令不会误播。
     */
    private suspend fun runSimple(
        audioOnSuccess: ((before: GameState, after: GameState) -> Unit)? = null,
        buildCommand: (GameState) -> Command,
    ): AppUiState {
        val current = _uiState.value.currentState()
            ?: return _uiState.value.withBanner(Banner(Key.ENGINE_ERR_NO_SAVE))
        val outcome = try {
            engine.apply(current, buildCommand(current))
        } catch (e: EngineError) {
            return _uiState.value.withBanner(e.toBanner())
        }
        if (outcome.newState !== current) {
            audioOnSuccess?.invoke(current, outcome.newState)
            // 竞猜结算音：结算发生在日推进里，统一在这里补播，避免每个命令各接一次
            playBetSettlement(current, outcome.newState)
        }
        return persist(_uiState.value.currentSlot() ?: activeSlot, outcome.newState, current)
    }

    /** 竞猜刚结算时补一条音效；没有新结算就什么都不做。 */
    private fun playBetSettlement(before: GameState, after: GameState) {
        if (after.todayBetOutcome == null || after.todayBetOutcome == before.todayBetOutcome) return
        when (after.todayBetOutcome) {
            com.example.hrrepayment.core.model.BetOutcome.WON -> {
                sfx(AudioEvent.CashShower)
                sfx(AudioEvent.BetWin)
            }
            com.example.hrrepayment.core.model.BetOutcome.LOST -> sfx(AudioEvent.BetLose)
            // 退款不配音，避免误以为赢/输
            com.example.hrrepayment.core.model.BetOutcome.CANCELLED, null -> Unit
        }
    }

    /**
     * 原子保存 → 发布。A failed write keeps [previousState] on screen so the player
     * can retry instead of silently losing the run.
     */
    private suspend fun persist(
        slot: SaveSlot,
        newState: GameState,
        previousState: GameState?,
    ): AppUiState = when (val outcome = slots.getValue(slot).write(newState)) {
        SaveWriteOutcome.Ok -> {
            activeSlot = slot
            slotSummaries = readSlots()
            _uiState.value.copy(
                screen = Screen.Run(slot, newState, System.currentTimeMillis()),
                dossier = refreshDossier(_uiState.value, newState),
                banner = null,
                pendingOverwrite = null,
                blockProblem = null,
            )
        }
        is SaveWriteOutcome.Failed -> {
            // Nothing is published: the previous state stays on screen, with a retry banner.
            val savedAt = (_uiState.value.screen as? Screen.Run)?.lastSavedAtMillis ?: 0L
            val base = previousState?.let { Screen.Run(slot, it, savedAt) } ?: _uiState.value.screen
            _uiState.value.copy(screen = base).withBanner(Banner(Key.BOOT_SAVE_FAILED, listOf(outcome.detail)))
        }
    }

    // ------------------------------------------------------------- dossier build

    /**
     * Rebuild the dossier **only when it is already open**.
     *
     * Returning `emptyList()` for a closed dossier would be indistinguishable from
     * “open but empty”, and the overlay would pop up on its own after every save or
     * language switch — `null` is the only representation of “closed”.
     */
    private fun refreshDossier(state: AppUiState, override: GameState? = null): List<DossierEntry>? =
        if (state.dossier == null) null else buildDossier(state, override)

    private fun buildDossier(
        state: AppUiState = _uiState.value,
        override: GameState? = null,
    ): List<DossierEntry> {
        val run = override ?: state.currentState() ?: slotSummaries
            .firstOrNull { it.slot == activeSlot }?.loadedOrNull?.state
        val hired = run?.hired ?: return emptyList()
        return hired.mapNotNull { record ->
            val candidate = runCatching { content.candidate(record.candidateId) }.getOrNull()
                ?: return@mapNotNull null
            DossierEntry(
                candidateId = candidate.id,
                name = candidate.name,
                role = candidate.role,
                avatarAsset = candidate.avatarAsset,
                workdayIndex = record.workdayIndex,
                resigned = record.resigned,
                promotions = record.promotions,
            )
        }
    }

    // ------------------------------------------------------------- mapping

    private fun SaveSnapshot.toProblem(): SaveProblem = when (this) {
        is SaveSnapshot.Corrupted -> SaveProblem(
            kind = SaveProblem.Kind.CORRUPTED,
            titleKey = Key.BOOT_BLOCKED_CORRUPTED_TITLE,
            messageKey = Key.BOOT_BLOCKED_CORRUPTED_BODY,
            detail = detail,
            canRetry = true,
            canReset = true,
        )
        is SaveSnapshot.Incompatible -> SaveProblem(
            kind = SaveProblem.Kind.INCOMPATIBLE,
            titleKey = Key.BOOT_BLOCKED_INCOMPATIBLE_TITLE,
            messageKey = Key.BOOT_BLOCKED_INCOMPATIBLE_BODY,
            detail = "$reason|$detail",
            canRetry = false,
            canReset = true,
        )
        is SaveSnapshot.IoFailure -> SaveProblem(
            kind = SaveProblem.Kind.IO_FAILURE,
            titleKey = Key.BOOT_BLOCKED_IO_TITLE,
            messageKey = Key.BOOT_BLOCKED_IO_BODY,
            detail = detail,
            canRetry = true,
            canReset = false,
        )
        else -> SaveProblem(
            kind = SaveProblem.Kind.IO_FAILURE,
            titleKey = Key.BOOT_ACTION_FAILED,
            messageKey = Key.BOOT_ACTION_FAILED,
            detail = "",
            canRetry = true,
            canReset = false,
        )
    }

    private fun EngineError.toBanner(): Banner = Banner(messageKey, messageArgs)

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GameViewModel(
                    engine = container.gameEngine,
                    slots = container.saveSlots,
                    settings = container.settings,
                    content = container.contentRef,
                    audio = container.gameAudio,
                    onLanguageChange = container::applyLanguage,
                )
            }
        }
    }
}

// --------------------------------------------------------------- extensions

internal fun AppUiState.withBanner(banner: Banner?): AppUiState = copy(banner = banner)
