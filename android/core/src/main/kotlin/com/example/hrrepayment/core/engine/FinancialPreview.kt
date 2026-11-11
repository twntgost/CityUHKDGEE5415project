package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.money.applyPerMille

data class RepaymentPreview(
    val cashAfter: Money,
    val debtAfter: Money,
    val debtAfterInterest: Money,
    val annualInterest: Boolean,
    val cashExhausted: Boolean,
    val debtCapExceeded: Boolean,
)

enum class RepaymentPreset { NONE, KEEP_6000, KEEP_SALARY, MAXIMUM }

object FinancialPreview {
    private const val DEBT_CAP = GameState.ORDINARY_DEBT_CAP_CENTS

    fun preset(state: GameState, preset: RepaymentPreset): Money {
        val reserve = when (preset) {
            RepaymentPreset.NONE -> state.cash.cents
            RepaymentPreset.KEEP_6000 -> 600_000L
            RepaymentPreset.KEEP_SALARY -> state.monthlySalary.cents
            RepaymentPreset.MAXIMUM -> if (state.cash >= state.debt) 0L else 1L
        }
        return Money(minOf(state.debt.cents, (state.cash.cents - reserve).coerceAtLeast(0)))
    }

    fun repayment(state: GameState, amount: Money): RepaymentPreview {
        require(amount <= state.cash && amount <= state.debt)
        val cash = state.cash - amount
        val debt = state.debt - amount
        val annual = state.position.current.monthIndex % 12 == 5
        val rate = if (state.position.current.monthIndex == 5) 90 else 180
        val afterInterest = if (annual) debt + debt.applyPerMille(rate) else debt
        return RepaymentPreview(cash, debt, afterInterest, annual,
            (debt.cents > 0 || state.specialLoanDue > Money.ZERO) && cash.cents == 0L, afterInterest.cents > DEBT_CAP)
    }

    fun minimumAnnualRepayment(state: GameState): Money? {
        if (state.position.current.monthIndex % 12 != 5) return null
        val rate = if (state.position.current.monthIndex == 5) 90 else 180
        var low = 0L
        var high = state.debt.cents
        while (low < high) {
            val mid = low + (high - low) / 2
            val remaining = Money(state.debt.cents - mid)
            if ((remaining + remaining.applyPerMille(rate)).cents <= DEBT_CAP) high = mid else low = mid + 1
        }
        return Money(low)
    }
}
