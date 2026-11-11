package com.example.hrrepayment.core.model

import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.RandomSnapshot
import com.example.hrrepayment.core.random.RandomSource
import kotlinx.serialization.Serializable

/**
 * One candidate the player successfully pushed (面试档案袋 / Interview Dossiers).
 *
 * Only stable ids are persisted: the name, role and avatar are resolved from the
 * content bundle at render time. That keeps the dossier fully localized — switching
 * language must not leave a Chinese name frozen inside an English save.
 */
@Serializable
data class HiredRecord(
    val candidateId: String,
    val workdayIndex: Int,
    /** 计划离职的工作日；null = 这一位不会离职（约 10% 会有，且落在入职后半年内）。 */
    val resignOn: Int? = null,
    /** 已经发生的晋升次数（每满半年判定一次，最多 2 次）。 */
    val promotions: Int = 0,
    /** 是否已经离职。 */
    val resigned: Boolean = false,
)

/** 人才动态的类型：离职 / 晋升。 */
@Serializable
enum class CareerEventKind { RESIGNED, PROMOTED }

/** 某一天发生在已录用者身上的动态，供每日简报展示。 */
@Serializable
data class CareerEvent(
    val candidateId: String,
    val kind: CareerEventKind,
    val workdayIndex: Int,
)

/** 月底结算后生效的调薪，在次月第一个工作日的每日简报里播报。 */
@Serializable
data class SalaryReport(
    val from: Money,
    val to: Money,
)

/** How the last wager ended; the wording is resolved by the UI. */
enum class BetOutcome { WON, LOST, CANCELLED }

/**
 * Persistent game state (技术文档 §3).
 *
 * Only fields that must survive process death live here; transient UI state stays
 * in the ViewModel. Every field carries a default so that a *partial* JSON blob
 * still deserialises — version rejection then happens on an explicit field check
 * instead of a parse crash.
 *
 * ## Localisation rule (v3)
 *
 * No human-readable sentence is persisted any more. Earlier versions stored
 * `lastResultMessage` / `lastBetMessage` as pre-rendered Chinese text, which made a
 * mid-run language switch impossible; those fields were replaced by the structured
 * data below ([lastResultSuccess], [lastReviewCorrect], [lastLuckUsed],
 * [lastTrustPenalty], [lastBetOutcome], …) and the UI renders them through the
 * bilingual catalogue in `core/i18n`.
 *
 * Adding a field must not change existing defaults, otherwise [expectations] would
 * reject in-flight saves; bump [CURRENT_SCHEMA_VERSION] instead.
 */
@Serializable
data class GameState(
    val saveSchemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val rulesVersion: String = EXPECTED_RULES_VERSION,
    val contentVersion: String = EXPECTED_CONTENT_VERSION,
    /** Hash of the exported content bundle; blank means "not bound yet". */
    val contentHash: String = "",

    val saveId: String,
    val revision: Long = 0L,
    /**
     * Idempotency key of the last applied command. A replayed command id is
     * rejected with [com.example.hrrepayment.core.engine.EngineError.DuplicateCommand]
     * so a double tap or a post-save retry cannot apply twice.
     */
    val lastAppliedCommandId: String? = null,

    val phase: Phase = Phase.Opening,
    val pendingStep: PendingStep = PendingStep.AcknowledgeOpening,
    /** Stable text id of the event currently occupying the notice panel, if any. */
    val pendingEventId: String? = null,
    /** Fixed-date events already applied; persisted so restore cannot replay effects. */
    val fixedEventsSeen: Set<String> = emptySet(),

    val position: CalendarPosition = CalendarPosition(current = WorkdayRef.FIRST),

    val cash: Money = Money(600_000L), // 初始现金 6,000.00 元
    val debt: Money = Money(ORDINARY_DEBT_CAP_CENTS), // 初始债务 500,000.00 元
    val specialLoanDecision: Boolean? = null,
    val specialLoanInvitationRead: Boolean = false,
    val specialLoanDue: Money = Money.ZERO,
    val specialLoanForgiven: Boolean = false,

    val attributes: Attributes = Attributes(),
    val stats: Stats = Stats(),

    /** Months without choosing health (项目书 §4.4). 0 means "selected this month". */
    val monthsWithoutHealth: Int = 0,
    /** Running count of low-dopamine months for the 7-month endpoint. */
    val lowDopamineMonths: Int = 0,
    /** Health and English chosen at least once in the current chapter. */
    val chapterHealthChosen: Boolean = false,
    val chapterEnglishChosen: Boolean = false,

    /** Month-end raise accrued but not yet paid. */
    val pendingRaise: Int = 0,
    /** Permanent +2000 from chapter-end trust ≥ 10, keyed by chapter index (0..6). */
    val chapterTrustedBonuses: Set<Int> = emptySet(),
    /** Base 6000 plus raise plus promotion total — the salary that is now in force. */
    val monthlySalary: Money = Money(600_000L),
    /** Salary that takes effect next month. */
    val nextMonthlySalary: Money = Money(600_000L),
    /** Month index (0..41) whose payroll has been settled; -1 means none yet. */
    val lastSalarySettledMonthIndex: Int = -1,

    /** 2016 → 9%, 2017 onwards → 18%; stored per-mille (90 / 180). */
    val interestRatePerMille: Int = 90,
    /**
     * Last year whose interest was already settled. 2015 means no interest has
     * been processed yet; the first settlement is 2016.
     */
    val lastInterestSettledYear: Int = 2015,

    /** Next stake cap: 5000 yuan * 1.3^acceptedBetCount, rounded once to cents. */
    val bettingCap: Money = Money(500_000L),
    val last2016BetCap: Money? = null,
    val betting2017Base: Money? = null,
    val betting2017StartCount: Int = 0,
    /** One unsettled wager at most; terms are frozen when placed. */
    val activeBet: BetSnapshot? = null,
    val settledBetMarketIds: Set<String> = emptySet(),
    val successfulBetCount: Int = 0,
    val acceptedBetCount: Int = 0,
    val betOptionOrder: Map<String, List<String>> = emptyMap(),
    /** Structured outcome of the last settlement; text is rendered by the UI. */
    val lastBetOutcome: BetOutcome? = null,
    val lastBetPayout: Money? = null,

    /** Index of the visible resume; the fixed submit button must use this candidate. */
    val browsedCandidateIndex: Int = 0,
    /** Visible candidate mirror retained for navigation and legacy saves. */
    val selectedCandidateId: String? = null,
    val markedStatements: Map<String, String> = emptyMap(),
    val useLuckOnSubmission: Boolean = false,
    val lastReviewCorrect: Boolean? = null,
    val lastReviewStatementId: String? = null,
    val lastLuckUsed: Int = 0,
    val lastHireProbabilityBps: Int? = null,
    val promotionSalaryApplied: Int = 0,
    val lastSalaryPaid: Money? = null,
    val lastBonusPaid: Money? = null,
    /** 保底档位: 0 = none, otherwise the work-ability threshold 20/30/50. */
    val guaranteeTier: Int = 0,
    /** Submissions counted inside the current guarantee period. */
    val guaranteePeriodDraws: Int = 0,

    // ------------------------------------------------- P2 招聘结果（可恢复展示）
    /** Result of the most recent submission; null before the first submit. */
    val lastResultSuccess: Boolean? = null,
    /** 保底强制成功（区别于概率成功，UI 可标注）。 */
    val lastResultForced: Boolean = false,
    /** 信任惩罚已随该结果扣减（0 / 1 / 2），用于恢复结果区的附言。 */
    val lastResultTrustPenalty: Int = 0,
    /** 最近一次被推送候选人的姓名（推送按钮与结果区展示用）。 */
    val lastResultCandidateId: String? = null,

    // ------------------------------------------------------ 面试档案袋（v3 新增）
    /**
     * Every candidate that was successfully pushed, in submission order. Drives the
     * 面试档案袋 screen, grouped by job role.
     */
    val hired: List<HiredRecord> = emptyList(),

    val rngSeed: Long = 0L,
    val rngAlgorithm: String = RandomSource.DEFAULT_ALGORITHM,
    val rngStateVersion: Int = RandomSource.STATE_VERSION,
    /** Exact generator position; see [RandomSnapshot]. */
    val rngState: Long = 0L,
    val rngDrawCount: Long = 0L,

    /** 今天发生的离职/晋升，只用于当天每日简报；推进到下一个工作日时重置。 */
    val todayCareerEvents: List<CareerEvent> = emptyList(),

    /** 本月起生效的调薪，在次月第一个工作日的每日简报里播报。 */
    val salaryReport: SalaryReport? = null,

    /** 今天结算的竞猜结果，只用于当天每日简报；推进到下一个工作日时重置。 */
    val todayBetOutcome: BetOutcome? = null,
    val todayBetPayout: Money? = null,

    val endingId: String? = null,
    val endingReason: String? = null,
) {
    val specialLoanInvitationAvailable: Boolean
        get() = phase == Phase.DayEntry && position.current.index == 36 && specialLoanDecision == null
    val specialLoanInvitationUnread: Boolean
        get() = specialLoanInvitationAvailable && !specialLoanInvitationRead

    init {
        require(cash.cents >= 0L) { "Cash must be non-negative" }
        require(debt.cents >= 0L) { "Debt must be non-negative" }
        require(browsedCandidateIndex >= 0) { "browsedCandidateIndex must be non-negative" }
        require(guaranteePeriodDraws >= 0) { "guaranteePeriodDraws must be non-negative" }
    }

    /**
     * Return a copy with an incremented revision; commands must persist the new
     * revision before publishing UI updates (技术文档 §3 串行处理器要求).
     */
    fun bumpRevision(): GameState = copy(revision = revision + 1)

    /** The exact generator position this state represents. */
    fun rngSnapshot(): RandomSnapshot = RandomSnapshot(
        algorithm = rngAlgorithm,
        seed = rngSeed,
        state = rngState,
        drawCount = rngDrawCount,
    )

    /** Store a generator position back into the state (used after a command runs). */
    fun withRng(snapshot: RandomSnapshot): GameState = copy(
        rngAlgorithm = snapshot.algorithm,
        rngSeed = snapshot.seed,
        rngState = snapshot.state,
        rngDrawCount = snapshot.drawCount,
    )

    /** Record the idempotency key of the command that produced this state. */
    fun withCommandId(commandId: String): GameState = copy(lastAppliedCommandId = commandId)

    /** Add a successful placement to the dossier; duplicates are ignored. */
    fun withHire(candidateId: String, workdayIndex: Int): GameState =
        withHire(HiredRecord(candidateId, workdayIndex))

    fun withHire(record: HiredRecord): GameState =
        if (hired.any { it.candidateId == record.candidateId }) this
        else copy(hired = hired + record)

    companion object {
        const val ORDINARY_DEBT_CAP_CENTS = 50_000_000L
        const val CURRENT_SCHEMA_VERSION = 3
        const val EXPECTED_RULES_VERSION = "3.0"

        /**
         * 内容标记（四份维护文档合同值）。v4 起内容包按语言拆分为
         * `month1.json`（中文）与 `month1.en.json`（英文），两者 contentVersion 相同，
         * 存档指纹使用语言无关的 manifest.contentHash。
         */
        const val EXPECTED_CONTENT_VERSION = "story-v4-i18n"

        /**
         * Factory for a fresh save. The [saveId] and [rngSeed] are supplied by the
         * caller (the engine takes them from [Command.StartNewGame]) so tests can
         * pin both.
         */
        fun newGame(
            saveId: String,
            rngSeed: Long,
            contentVersion: String = EXPECTED_CONTENT_VERSION,
            contentHash: String = "",
        ): GameState = GameState(
            saveId = saveId,
            revision = 0L,
            phase = Phase.Opening,
            pendingStep = PendingStep.AcknowledgeOpening,
            position = CalendarPosition(current = WorkdayRef.FIRST),
            cash = Money(600_000L),
            debt = Money(ORDINARY_DEBT_CAP_CENTS),
            attributes = Attributes(),
            stats = Stats(),
            monthlySalary = Money(600_000L),
            nextMonthlySalary = Money(600_000L),
            bettingCap = Money(500_000L),
            rngSeed = rngSeed,
            rngState = rngSeed,
            rngDrawCount = 0L,
            contentVersion = contentVersion,
            contentHash = contentHash,
        )
    }
}
