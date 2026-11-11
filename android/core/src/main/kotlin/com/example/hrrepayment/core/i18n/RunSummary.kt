package com.example.hrrepayment.core.i18n

import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.money.Money

fun Strings.saveSlotStatus(state: GameState): String? = when {
    state.phase == Phase.Ended -> null
    state.phase == Phase.EventNotice -> get(Key.SAVE_EVENT_PENDING)
    state.specialLoanInvitationAvailable -> get(if (state.specialLoanInvitationUnread) Key.LOAN_NEW else Key.LOAN_UNDECIDED)
    state.specialLoanDue > Money.ZERO -> get(Key.SAVE_LOAN_PENDING)
    state.activeBet != null -> get(Key.SAVE_BET_PENDING)
    else -> null
}

fun Strings.loanStatus(state: GameState): String? = when {
    state.specialLoanForgiven -> get(Key.LOAN_WAIVED_RECORD)
    state.specialLoanDue > Money.ZERO -> format(Key.LOAN_OUTSTANDING, state.specialLoanDue.formatYuan())
    state.specialLoanDecision == true -> get(Key.LOAN_REPAID_RECORD)
    state.specialLoanDecision == false -> get(Key.LOAN_DECLINED_RECORD)
    state.specialLoanInvitationAvailable -> get(if (state.specialLoanInvitationUnread) Key.LOAN_NEW else Key.LOAN_UNDECIDED)
    else -> null
}

fun Strings.runSummary(state: GameState): String = buildList {
    add(get(Key.valueOf("PHASE_${state.phase.name}")))
    if (state.phase != Phase.Ended) {
        if (state.phase == Phase.EventNotice) add(get(Key.SAVE_EVENT_PENDING))
        loanStatus(state)?.let { add(it) }
        if (state.activeBet != null) add(get(Key.SAVE_BET_PENDING))
    }
}.joinToString(" · ")
