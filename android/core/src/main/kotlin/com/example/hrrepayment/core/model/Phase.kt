package com.example.hrrepayment.core.model

/**
 * Phases represent the engine's serialised state-machine step. Only one
 * pending phase exists at a time so that dialogs and save/recover can
 * always tell which screen to restore (技术文档 §3 / §4).
 */
enum class Phase {
    /** Brand new save, before [Opening] has been acknowledged. */
    Opening,
    /** Per-workday entry: settle expired bets, run fixed events, allow daily recruitment. */
    DayEntry,
    /** A fixed-date story/event notice awaiting explicit acknowledgement. */
    EventNotice,
    /** Recruiting (candidate 1 of 2 selected) awaiting submission. */
    Recruiting,
    /** Awaiting recruitment result acknowledgement. */
    ResultAck,
    /** Awaiting 3-success reward choice (work+1 or trust+1). */
    SuccessReward,
    /** Month income then chapter/year-end raise + bonus. */
    MonthIncome,
    /** Repayment input/confirm. */
    Repayment,
    /** Year-end interest & bankruptcy check. */
    InterestCheck,
    /** Month-end skill point distribution (健康/多巴胺/英语). */
    MonthlySkill,
    /** Chapter-end growth check (health/dopamine bookkeeping). */
    GrowthCheck,
    /** End-of-year / end-of-game finalisation before loop. */
    FinalCheck,
    /** Game ended; only AcknowledgeEnding allowed. */
    Ended,
}

/**
 * Persisted flags that ride alongside [Phase] so save/restores can survive an
 * attempted-during-pending-step kill.
 */
enum class PendingStep {
    None,
    AcknowledgeOpening,
    ViewEvent,
    ViewResume,
    ChooseCandidate,
    ViewResult,
    ChooseSuccessReward,
    ChooseMonthlySkill,
    AcknowledgeEnding,
    ConfirmRepayment,
    PlaceBet,
}
