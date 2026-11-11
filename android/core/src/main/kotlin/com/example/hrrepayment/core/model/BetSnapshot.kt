package com.example.hrrepayment.core.model

import com.example.hrrepayment.core.money.Money
import kotlinx.serialization.Serializable

/** Immutable wager terms captured at submission time. */
@Serializable
data class BetSnapshot(
    val marketId: String,
    val optionId: String,
    val amount: Money,
    val luckAtBet: Int,
    val oddsTenths: Int,
    val capAtBet: Money,
    val settleWorkdayIndex: Int,
)
