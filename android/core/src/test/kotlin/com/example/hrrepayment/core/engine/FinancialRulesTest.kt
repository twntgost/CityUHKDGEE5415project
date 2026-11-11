package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlin.test.*

class FinancialRulesTest {
    @Test fun ordinaryDebtHasAFiveHundredThousandLimitAndSpecialDebtIsSeparate() {
        for (cents in listOf(50_000_000L, 50_000_001L)) {
            val s = at(5, Phase.Repayment).copy(debt = Money(cents), specialLoanDue = Money(15_000_000))
            val result = engine().apply(s, Command.RepayDebt(s.revision, s.saveId, "cap", "0")).newState
            assertEquals(if (cents == 50_000_000L) Phase.MonthlySkill else Phase.Ended, result.phase)
            if (result.phase == Phase.Ended) assertEquals("debt_cap_exceeded", result.endingReason)
        }
    }
    private fun engine() = GameEngine(TestContent.ref(), SplitMix64Random(1))
    private fun at(day: Int, phase: Phase) = GameState.newGame("financial", 1).copy(
        position = CalendarPosition(WorkdayRef(day)), phase = phase)

    @Test fun annualMinimumRepaymentHasAnExactOneCentBoundary() {
        val s = at(35, Phase.Repayment).copy(cash = Money(6_000_000))
        val minimum = FinancialPreview.minimumAnnualRepayment(s)!!
        assertEquals(Money(4_128_440), minimum)
        assertFalse(FinancialPreview.repayment(s, minimum).debtCapExceeded)
        assertTrue(FinancialPreview.repayment(s, Money(minimum.cents - 1)).debtCapExceeded)
        assertNull(FinancialPreview.minimumAnnualRepayment(at(5, Phase.Repayment)))
        val later = at(107, Phase.Repayment).copy(cash = Money(10_000_000))
        val next = FinancialPreview.minimumAnnualRepayment(later)!!
        assertFalse(FinancialPreview.repayment(later, next).debtCapExceeded)
        assertTrue(FinancialPreview.repayment(later, Money(next.cents - 1)).debtCapExceeded)
    }

    @Test fun presetsRespectDebtAndKeepOneCentIfFullRepaymentIsNotPossible() {
        val s = at(5, Phase.Repayment).copy(cash = Money(1_200_000))
        assertEquals(Money.ZERO, FinancialPreview.preset(s, RepaymentPreset.NONE))
        assertEquals(Money(600_000), FinancialPreview.preset(s, RepaymentPreset.KEEP_6000))
        assertEquals(Money(600_000), FinancialPreview.preset(s, RepaymentPreset.KEEP_SALARY))
        assertEquals(Money(1_199_999), FinancialPreview.preset(s, RepaymentPreset.MAXIMUM))
        assertEquals(Money(100_000), FinancialPreview.preset(s.copy(debt = Money(100_000)), RepaymentPreset.MAXIMUM))
    }

    @Test fun promotionAndChapterRaisesApplyOnceNextMonthAndBonusUsesTheNewSalary() {
        val s = at(35, Phase.MonthIncome).copy(stats = Stats(globalSuccessCount = 50), attributes = Attributes(trust = 10))
        val paid = engine().apply(s, Command.FinishIncome(s.revision, s.saveId, "income")).newState
        assertEquals(Money(600_000), paid.monthlySalary)
        assertEquals(Money(1_100_000), paid.nextMonthlySalary)
        assertEquals(Money(600_000), paid.lastSalaryPaid)
        assertEquals(Money(3_300_000), paid.lastBonusPaid)
        assertEquals(Money(4_500_000), paid.cash)
        assertEquals(3000, paid.promotionSalaryApplied)
        assertEquals(setOf(0), paid.chapterTrustedBonuses)
        assertFailsWith<EngineError.WrongPhase> {
            engine().apply(paid, Command.FinishIncome(paid.revision, paid.saveId, "repeat"))
        }
        val later = at(41, Phase.MonthIncome).copy(monthlySalary = paid.nextMonthlySalary,
            nextMonthlySalary = paid.nextMonthlySalary, promotionSalaryApplied = paid.promotionSalaryApplied,
            stats = paid.stats, chapterTrustedBonuses = paid.chapterTrustedBonuses)
        val second = engine().apply(later, Command.FinishIncome(later.revision, later.saveId, "second")).newState
        assertEquals(Money(1_100_000), second.nextMonthlySalary)
        assertEquals(Money.ZERO, second.lastBonusPaid)
    }

    @Test fun newBetsUseCurrentSalaryWithoutChangingAnAcceptedBet() {
        val s = at(0, Phase.DayEntry).copy(cash = Money(5_000_000), bettingCap = Money(9_000_000),
            monthlySalary = Money(600_000), nextMonthlySalary = Money(1_000_000))
        assertEquals(Money(1_800_000), GameEngine.effectiveBetCap(s))
        assertFailsWith<EngineError.InvalidCommand> {
            engine().apply(s, Command.PlaceBet(s.revision, s.saveId, "too-much", "m1", "home", "18000.01", 1))
        }
        val placed = engine().apply(s, Command.PlaceBet(s.revision, s.saveId, "bet", "m1", "home", "18000", 1)).newState
        assertEquals(Money(1_800_000), placed.activeBet!!.capAtBet)
        val changed = placed.copy(attributes = Attributes(luck = 10, luckPeak = 10), monthlySalary = Money(1_000_000))
        assertEquals(placed.activeBet, changed.activeBet)
        assertEquals(19, changed.activeBet!!.oddsTenths)
    }

    @Test fun availableBetCapAlsoTracksCashWithoutChangingTheRuleCap() {
        val s = at(0, Phase.DayEntry).copy(cash = Money(80_000))
        assertEquals(Money(80_000), GameEngine.effectiveBetCap(s))
        val placed = engine().apply(s, Command.PlaceBet(s.revision, s.saveId, "cash-cap", "m1", "home", "800", 1)).newState
        assertEquals(Money(80_000), placed.activeBet!!.capAtBet)
        assertEquals(Money(650_000), placed.bettingCap)
        assertEquals(Money.ZERO, placed.cash)
        assertEquals(Phase.Ended, placed.phase)
        assertEquals(Money(49_999), GameEngine.effectiveBetCap(s.copy(cash = Money(49_999))))
    }

    @Test fun finalMonthDoesNotGrantSkillOrIncrementMonthsWithoutHealth() {
        val s = at(251, Phase.InterestCheck).copy(debt = Money.ZERO, monthsWithoutHealth = 3,
            attributes = Attributes(health = 7, dopamine = 5))
        val result = engine().apply(s, Command.FinishEconomy(s.revision, s.saveId, "final")).newState
        assertEquals(Phase.Ended, result.phase)
        assertEquals("debt_free_restart", result.endingId)
        assertEquals(3, result.monthsWithoutHealth)
        assertEquals(5, result.attributes.dopamine)
        assertEquals(7, result.attributes.health)
    }

    @Test fun zeroCashAndZeroDebtIsSafeButAnyRemainingDebtIsNot() {
        for (debt in listOf(Money(50_000), Money(50_001))) {
            val s = at(5, Phase.Repayment).copy(cash = Money(50_000), debt = debt)
            val result = engine().apply(s, Command.RepayDebt(s.revision, s.saveId, "repay", "500")).newState
            assertEquals(if (debt.cents == 50_000L) Phase.MonthlySkill else Phase.Ended, result.phase)
            if (result.phase == Phase.Ended) assertEquals("cash_exhausted", result.endingReason)
        }
    }
}
