package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.content.BetOption
import com.example.hrrepayment.core.content.BettingMarket
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class SpecialLoanTest {
    private val content = object : ContentRef by TestContent.ref() {
        override fun bettingMarkets() = listOf(35, 36, 37).map { day ->
            BettingMarket("market-$day", "Test", day, day + 1, "news-$day",
                listOf(BetOption("a", "A"), BetOption("b", "B")), "a")
        }
    }
    private fun engine() = GameEngine(content, SplitMix64Random(1))
    private fun at(day: Int, phase: Phase = Phase.DayEntry) = GameState.newGame("loan", 1).copy(
        phase = phase, pendingStep = PendingStep.None, position = CalendarPosition(WorkdayRef(day)),
        cash = Money(30_000_000))
    private fun decide(state: GameState, accept: Boolean) = engine().apply(state,
        Command.DecideSpecialLoan(state.revision, state.saveId, "decide-${state.revision}", accept)).newState
    private fun restore(state: GameState) = Json.decodeFromString<GameState>(Json.encodeToString(state))
    private fun april(state: GameState) = engine().apply(state.copy(position = CalendarPosition(WorkdayRef(53)), phase = Phase.MonthlySkill),
        Command.ChooseMonthlySkill(state.revision, state.saveId, "april", Command.MonthlySkillOption.HEALTH)).newState

    @Test fun acceptedLoanIsSeparateFromOrdinaryDebtAndSurvivesRestore() {
        val before = at(36)
        val after = restore(decide(before, true))
        assertEquals(before.cash + Money(10_000_000), after.cash)
        assertEquals(before.debt, after.debt)
        assertEquals(Money(15_000_000), after.specialLoanDue)
        assertEquals(true, after.specialLoanDecision)
        assertFailsWith<EngineError> { decide(after, true) }
        val repaid = engine().apply(after.copy(phase = Phase.Repayment),
            Command.RepayDebt(after.revision, after.saveId, "ordinary", "0")).newState
        assertNotEquals(Phase.Ended, repaid.phase)
    }

    @Test fun readingInvitationPersistsWithoutDecidingOrChangingCash() {
        val before = at(36)
        assertTrue(before.specialLoanInvitationUnread)
        val read = restore(engine().apply(before,
            Command.ReadSpecialLoanInvitation(before.revision, before.saveId, "read")).newState)
        assertTrue(read.specialLoanInvitationRead)
        assertFalse(read.specialLoanInvitationUnread)
        assertTrue(read.specialLoanInvitationAvailable)
        assertNull(read.specialLoanDecision)
        assertEquals(before.cash, read.cash)
        assertTrue(decide(read, true).specialLoanDecision == true)
        assertFailsWith<EngineError> { engine().apply(read, Command.ReadSpecialLoanInvitation(read.revision, read.saveId, "read-again")) }
        assertFalse(read.copy(position = CalendarPosition(WorkdayRef(37))).specialLoanInvitationAvailable)
    }

    @Test fun newYearInitializationPreservesAnUnsettledWagerSnapshot() {
        val snapshot = BetSnapshot("later", "a", Money(50_000), 3, 22, Money(845_000), 40)
        val before = at(35, Phase.MonthlySkill).copy(activeBet = snapshot, last2016BetCap = Money(845_000), acceptedBetCount = 3)
        val next = restore(engine().apply(before, Command.ChooseMonthlySkill(before.revision, before.saveId, "year-change", Command.MonthlySkillOption.HEALTH)).newState)
        assertEquals(Money(1_690_000), next.bettingCap)
        assertEquals(snapshot, next.activeBet)
    }

    @Test fun rejectionHasNoFinancialEffectAndCannotBeReversed() {
        val before = at(36)
        val after = decide(before, false)
        assertEquals(before.cash, after.cash)
        assertEquals(Money.ZERO, after.specialLoanDue)
        assertFailsWith<EngineError> { decide(after, true) }
        assertFailsWith<EngineError> { decide(at(37), true) }
        assertFailsWith<EngineError> { decide(at(36, Phase.Recruiting), true) }
    }

    @Test fun aprilWaivesDebtWithoutCreditingCashTwiceAndCannotReplay() {
        val accepted = decide(at(36), true)
        val notice = restore(april(accepted))
        assertEquals(54, notice.position.current.index)
        assertEquals(Phase.EventNotice, notice.phase)
        assertEquals(GameEngine.EVENT_SPECIAL_LOAN_CLOSED, notice.pendingEventId)
        assertEquals(Money.ZERO, notice.specialLoanDue)
        assertTrue(notice.specialLoanForgiven)
        assertEquals(accepted.cash, notice.cash)
        assertEquals(accepted.debt, notice.debt)
        val ack = engine().apply(notice, Command.AcknowledgeEvent(notice.revision, notice.saveId, "ack", notice.pendingEventId!!)).newState
        val recruiting = engine().apply(ack, Command.EnterRecruiting(ack.revision, ack.saveId, "work")).newState
        assertEquals(Phase.Recruiting, recruiting.phase)
        assertEquals(accepted.cash, recruiting.cash)
    }

    @Test fun rejectedLoanStillGetsBreakingNewsWithoutForgivenessClaim() {
        val notice = april(decide(at(36), false))
        assertEquals(GameEngine.EVENT_SPECIAL_LOAN_CLOSED, notice.pendingEventId)
        assertFalse(notice.specialLoanForgiven)
        assertEquals(Money.ZERO, notice.specialLoanDue)
    }

    @Test fun earlyRepaymentIsFullAndDoesNotGetRefundedInApril() {
        val accepted = decide(at(36), true).copy(phase = Phase.Repayment)
        val repaid = engine().apply(accepted, Command.RepaySpecialLoan(accepted.revision, accepted.saveId, "repay")).newState
        assertEquals(accepted.cash - Money(15_000_000), repaid.cash)
        assertEquals(Money.ZERO, repaid.specialLoanDue)
        assertEquals(accepted.debt, repaid.debt)
        assertFailsWith<EngineError> { engine().apply(repaid, Command.RepaySpecialLoan(repaid.revision, repaid.saveId, "repay-again")) }
        val notice = april(repaid)
        assertEquals(repaid.cash, notice.cash)
        assertFalse(notice.specialLoanForgiven)
    }

    @Test fun unpaidAtYearEndEndsImmediatelyButWaivedLoanDoesNot() {
        val unpaid = decide(at(36), true).copy(position = CalendarPosition(WorkdayRef(107)), phase = Phase.InterestCheck, debt = Money(1_000_000))
        val ended = engine().apply(unpaid, Command.FinishEconomy(unpaid.revision, unpaid.saveId, "deadline")).newState
        assertEquals(Phase.Ended, ended.phase)
        assertEquals("special_loan_overdue", ended.endingReason)
        val waived = unpaid.copy(specialLoanDue = Money.ZERO, specialLoanForgiven = true)
        assertEquals(Phase.MonthlySkill, engine().apply(waived, Command.FinishEconomy(waived.revision, waived.saveId, "waived")).newState.phase)
    }

    @Test fun newYearCapDoublesLastDisplayedCapThenGrowsThirtyPercent() {
        val before = at(35).copy(acceptedBetCount = 2, bettingCap = Money(845_000), monthlySalary = Money(1_000_000))
        val bet = engine().apply(before, Command.PlaceBet(before.revision, before.saveId, "2016", "market-35", "a", "500", 36)).newState
        assertEquals(Money(845_000), bet.last2016BetCap)
        val january = engine().apply(bet.copy(phase = Phase.MonthlySkill),
            Command.ChooseMonthlySkill(bet.revision, bet.saveId, "new-year", Command.MonthlySkillOption.HEALTH)).newState
        assertEquals(Money(1_690_000), january.bettingCap)
        assertEquals(Money(1_690_000), GameEngine.effectiveBetCap(january))
        val nextBet = engine().apply(restore(january), Command.PlaceBet(january.revision, january.saveId, "2017", "market-36", "a", "500", 37)).newState
        assertEquals(Money(2_197_000), nextBet.bettingCap)
        assertEquals(Money(1_690_000), nextBet.activeBet!!.capAtBet)
        assertEquals(Money(1_200_000), GameEngine.effectiveBetCap(nextBet.copy(monthlySalary = Money(400_000))))
        assertEquals(Money(10_000), GameEngine.effectiveBetCap(nextBet.copy(cash = Money(10_000))))
    }

    @Test fun no2016BetsDoublesInitialCapAndZeroCashWithSpecialDebtIsUnsafe() {
        val before = at(35, Phase.MonthlySkill)
        val january = engine().apply(before, Command.ChooseMonthlySkill(before.revision, before.saveId, "january", Command.MonthlySkillOption.HEALTH)).newState
        assertEquals(Money(1_000_000), january.bettingCap)
        val debtor = at(36, Phase.Repayment).copy(debt = Money.ZERO, cash = Money.ZERO, specialLoanDue = Money(15_000_000))
        val ended = engine().apply(debtor, Command.RepayDebt(debtor.revision, debtor.saveId, "empty", "0")).newState
        assertEquals(Phase.Ended, ended.phase)
    }
}
