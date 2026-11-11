package com.example.hrrepayment.core.i18n

import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import kotlin.test.*

class RunSummaryTest {
    @Test fun saveSlotStatusKeepsOnlyTheMostUrgentActionInBothLanguages() {
        Lang.entries.forEach { lang ->
            val strings = Strings(lang)
            val wager = state.copy(specialLoanDecision = true,
                activeBet = BetSnapshot("market", "a", Money(50_000), 1, 20, Money(500_000), 40))
            val loan = wager.copy(specialLoanDue = Money(15_000_000))
            assertEquals(strings[Key.SAVE_EVENT_PENDING], strings.saveSlotStatus(loan.copy(phase = Phase.EventNotice)))
            assertEquals(strings[Key.LOAN_NEW], strings.saveSlotStatus(state))
            assertEquals(strings[Key.LOAN_UNDECIDED], strings.saveSlotStatus(state.copy(specialLoanInvitationRead = true)))
            assertEquals(strings[Key.SAVE_LOAN_PENDING], strings.saveSlotStatus(loan))
            assertEquals(strings[Key.SAVE_BET_PENDING], strings.saveSlotStatus(wager))
            assertNull(strings.saveSlotStatus(loan.copy(phase = Phase.Ended)))
            assertNull(strings.saveSlotStatus(wager.copy(activeBet = null, specialLoanForgiven = true)))
        }
    }
    private val state = GameState.newGame("summary", 1).copy(phase = Phase.DayEntry,
        position = CalendarPosition(WorkdayRef(36)))

    @Test fun loanRecordsDistinguishEveryDecisionAndFinancialStatusInBothLanguages() {
        Lang.entries.forEach { lang ->
            val strings = Strings(lang)
            assertEquals(strings[Key.LOAN_NEW], strings.loanStatus(state))
            assertEquals(strings[Key.LOAN_UNDECIDED], strings.loanStatus(state.copy(specialLoanInvitationRead = true)))
            assertEquals(strings[Key.LOAN_DECLINED_RECORD], strings.loanStatus(state.copy(specialLoanDecision = false)))
            assertEquals(strings[Key.LOAN_REPAID_RECORD], strings.loanStatus(state.copy(specialLoanDecision = true)))
            assertEquals(strings[Key.LOAN_WAIVED_RECORD], strings.loanStatus(state.copy(specialLoanDecision = true, specialLoanForgiven = true)))
            assertTrue(strings.loanStatus(state.copy(specialLoanDecision = true, specialLoanDue = Money(15_000_000)))!!.contains("150,000.00"))
        }
    }

    @Test fun saveSummaryShowsPhasePendingLoanAndWagerButEndsWithoutPendingActions() {
        Lang.entries.forEach { lang ->
            val strings = Strings(lang)
            val pending = state.copy(phase = Phase.EventNotice, specialLoanDecision = true, specialLoanDue = Money(15_000_000),
                activeBet = BetSnapshot("market", "a", Money(50_000), 1, 20, Money(500_000), 40))
            val summary = strings.runSummary(pending)
            assertTrue(summary.contains(strings[Key.SAVE_EVENT_PENDING]))
            assertTrue(summary.contains(strings[Key.SAVE_BET_PENDING]))
            assertTrue(summary.contains("150,000.00"))
            assertEquals(strings[Key.PHASE_Ended], strings.runSummary(pending.copy(phase = Phase.Ended)))
        }
    }
}
