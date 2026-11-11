package com.example.hrrepayment.core.model

import kotlinx.serialization.Serializable

/**
 * A player intent. Commands are the only way to move the state machine forward
 * (技术文档 §3: “UI 不维护状态机，只发命令”).
 *
 * ## Identity contract (review item: 命令身份 / 重复操作保护)
 *
 * Every command carries three identity fields, and
 * [com.example.hrrepayment.core.engine.GameEngine] validates all of them before
 * dispatching:
 *
 *  - [saveId] — binds the intent to one save slot, so a command built for save A can
 *    never be applied to save B (e.g. after the slot was overwritten).
 *  - [expectedRevision] — optimistic concurrency. A stale command (queued behind
 *    another command, or issued before a process-death reload) is rejected instead
 *    of being applied on top of a newer state.
 *  - [commandId] — idempotency key generated once per user action. Replaying the
 *    same key is rejected, so a double tap or a “retry after save failure” cannot
 *    apply the same intent twice.
 *
 * For the very first command of a brand new slot (no save exists yet) use
 * [NO_SAVE_ID] together with `expectedRevision = 0`, and only with [StartNewGame].
 */
sealed interface Command {
    val expectedRevision: Long
    val saveId: String
    val commandId: String

    data class ReadSpecialLoanInvitation(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    data class DecideSpecialLoan(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val accept: Boolean,
    ) : Command

    data class RepaySpecialLoan(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** Boot-time handshake: the opening copy has been shown and acknowledged. */
    @Serializable
    data class AcknowledgeOpening(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** A fixed event panel has been read and dismissed. */
    @Serializable
    data class AcknowledgeEvent(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val eventId: String,
    ) : Command

    /** DayEntry → Recruiting：今天开始看简历。 */
    @Serializable
    data class EnterRecruiting(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** 浏览卡片索引变化；固定推送按钮绑定新的可见候选人。 */
    @Serializable
    data class BrowseCandidate(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val index: Int,
    ) : Command

    /** 通过候选人ID切换可见页，保留程序化导航接口。 */
    @Serializable
    data class SelectCandidate(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val candidateId: String,
    ) : Command

    /** A resume has been chosen from the two candidates / the carousel. */
    @Serializable
    data class SubmitCandidate(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val candidateId: String,
        val statementId: String = "",
    ) : Command

    @Serializable
    data class MarkStatement(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val candidateId: String,
        val statementId: String,
    ) : Command

    @Serializable
    data class SetLuckUsage(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val enabled: Boolean,
    ) : Command

    /** The recruitment result panel has been acknowledged. */
    @Serializable
    data class AcknowledgeResult(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** Pick work+1 or trust+1 after three successes. */
    @Serializable
    data class ChooseSuccessReward(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val reward: SuccessRewardOption,
    ) : Command

    /** Salary and bonus notification finished. */
    @Serializable
    data class FinishIncome(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** Repayment (or “repay nothing”) submitted. [amountYuan] is raw user text. */
    @Serializable
    data class RepayDebt(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val amountYuan: String,
    ) : Command

    /** Year-end interest and bankruptcy check finished. */
    @Serializable
    data class FinishEconomy(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /** Month-end skill point assigned. */
    @Serializable
    data class ChooseMonthlySkill(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val skill: MonthlySkillOption,
    ) : Command

    @Serializable
    data class PlaceBet(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val marketId: String,
        val optionId: String,
        val amountYuan: String,
        val settleWorkdayIndex: Int,
    ) : Command

    @Serializable
    data class SettleBet(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val marketId: String,
        // Legacy caller hints retained for compatibility; authoritative results come from ContentRef.
        val outcomeOptionId: String = "",
        val eventCancelled: Boolean = false,
    ) : Command

    /** Ending panel acknowledged; the run is committed to history. */
    @Serializable
    data class AcknowledgeEnding(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
    ) : Command

    /**
     * Start a brand new run.
     *
     * With `previous == null` this is the boot-time “no save on disk” path:
     * [saveId] must be [NO_SAVE_ID] and [expectedRevision] must be `0`.
     * With an existing state this is the *confirmed overwrite* path: [saveId] and
     * [expectedRevision] must match the loaded save, which is what makes a single
     * accidental tap unable to destroy a run.
     *
     * [newSaveId] and [newRngSeed] are supplied by the caller so tests can pin both;
     * the engine never reads the system clock.
     */
    @Serializable
    data class StartNewGame(
        override val expectedRevision: Long,
        override val saveId: String,
        override val commandId: String,
        val newSaveId: String,
        val newRngSeed: Long,
    ) : Command

    /** The +1 chosen at a promotion success reward. */
    @Serializable
    enum class SuccessRewardOption { WORK, TRUST }

    /** The +1 chosen at month end. */
    @Serializable
    enum class MonthlySkillOption { HEALTH, DOPAMINE, ENGLISH }

    companion object {
        /** Sentinel [saveId] for “there is no save on disk yet”. */
        const val NO_SAVE_ID = ""
    }
}
