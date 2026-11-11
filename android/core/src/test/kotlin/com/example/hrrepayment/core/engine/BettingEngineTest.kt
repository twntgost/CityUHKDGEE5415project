package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlinx.serialization.json.Json
import kotlin.test.*
import org.junit.Test

class BettingEngineTest {
    private fun engine() = GameEngine(TestContent.ref(), SplitMix64Random(1))
    private fun base() = GameState.newGame("bet-test", 1).copy(phase = Phase.DayEntry)
    private fun place(s: GameState, option: String = "home", amount: String = "500", market: String = "m1") =
        engine().apply(s, Command.PlaceBet(s.revision, s.saveId, "place", market, option, amount, 1)).newState

    @Test fun rejectsInvalidAmountsMarketsOptionsWindowsAndDuplicateBets() {
        for (amount in listOf("-1", "499.99", "5000.01", "1.001", "abc", "999999999999999999")) {
            assertFailsWith<EngineError.InvalidCommand> { place(base(), amount = amount) }
        }
        assertFailsWith<EngineError.InvalidCommand> { place(base(), market = "missing") }
        assertFailsWith<EngineError.InvalidCommand> { place(base(), option = "missing") }
        assertFailsWith<EngineError.InvalidCommand> { place(base().copy(position = CalendarPosition(WorkdayRef(2)))) }
        assertFailsWith<EngineError.InvalidCommand> { place(base().copy(cash = Money(49_999))) }
        val placed = place(base())
        assertFailsWith<EngineError> { place(placed) }
        assertFailsWith<EngineError.InvalidCommand> { place(base().copy(settledBetMarketIds = setOf("m1"))) }
        assertFailsWith<EngineError.InvalidCommand> {
            engine().apply(placed, Command.SettleBet(placed.revision, placed.saveId, "early", "m1", "home"))
        }
    }

    @Test fun newsDayAutomaticallySettlesRestoredBetUsingContentRatherThanCallerAnswer() {
        val placed = place(base().copy(attributes = Attributes(luck = 1, luckPeak = 4)))
        assertEquals(23, placed.activeBet!!.oddsTenths)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(GameState.serializer(), placed))
        val result = engine().apply(restored.copy(phase = Phase.ResultAck), Command.AcknowledgeResult(restored.revision, restored.saveId, "next")).newState
        assertEquals(1, result.position.current.index)
        assertNull(result.activeBet)
        assertEquals(Money(665_000), result.cash)
        assertEquals(1, result.successfulBetCount)
        assertEquals(1, result.acceptedBetCount)
        assertEquals(setOf("m1"), result.settledBetMarketIds)
        val repeated = engine().apply(result, Command.EnterRecruiting(result.revision, result.saveId, "recruit")).newState
        assertEquals(result.cash, repeated.cash)
        val tampered = engine().apply(placed.copy(position = CalendarPosition(WorkdayRef(1))), Command.SettleBet(placed.revision, placed.saveId, "tamper", "m1", "a", true)).newState
        assertEquals(Money(665_000), tampered.cash)
    }

    @Test fun fifthCashOutWritesDopamineAndExhaustedCashEndsImmediately() {
        val s = base().copy(attributes = Attributes(luck = 1), successfulBetCount = 4)
        val bet = place(s)
        val result = engine().apply(bet.copy(position = CalendarPosition(WorkdayRef(1))), Command.SettleBet(bet.revision, bet.saveId, "settle", "m1", "home")).newState
        assertEquals(s.attributes.dopamine + 1, result.attributes.dopamine)
        val ended = place(base().copy(cash = Money(50_000)))
        assertEquals(Phase.Ended, ended.phase)
        assertEquals("cash_exhausted", ended.endingReason)
        assertFailsWith<EngineError.WrongPhase> {
            engine().apply(ended, Command.SettleBet(ended.revision, ended.saveId, "late", "m1", "home"))
        }
    }

    @Test fun capUsesExactPowerAndOptionOrderSurvivesSerialization() {
        val placed = place(base().copy(acceptedBetCount = 20))
        val expected = java.math.BigDecimal("500000").multiply(java.math.BigDecimal("1.3").pow(21))
            .setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        assertEquals(Money(expected), placed.bettingCap)
        val fresh = engine().apply(null, Command.StartNewGame(0, Command.NO_SAVE_ID, "start", "save", 12)).newState
        assertEquals(3, fresh.betOptionOrder.size)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(GameState.serializer(), fresh))
        assertEquals(fresh.betOptionOrder, restored.betOptionOrder)
        assertEquals(0, fresh.rngDrawCount)
    }

    @Test fun losingWagersCannotAccumulateMoreThanTenUnitsOfRecruitmentLuck() {
        val placed = place(base().copy(attributes = Attributes(luck = 10, luckPeak = 10)), option = "a")
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(GameState.serializer(), placed))
        val result = engine().apply(restored.copy(position = CalendarPosition(WorkdayRef(1))),
            Command.SettleBet(restored.revision, restored.saveId, "loss", "m1", "home")).newState
        assertEquals(10, result.attributes.luck)
        assertEquals(10, result.attributes.luckPeak)
        assertEquals(Money(550_000), result.cash)
        assertFailsWith<EngineError.InvalidCommand> {
            engine().apply(result, Command.SettleBet(result.revision, result.saveId, "repeat-loss", "m1", "home"))
        }
    }
}
