package com.example.hrrepayment

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.random.SplitMix64Random
import com.example.hrrepayment.core.money.Money
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BettingContentInstrumentedTest {
    @Test fun packagedNewsAndAll41MarketsLoadAndSettleWithTheirAuthoredAnswers() {
        val content = AssetContentLoader.load(InstrumentationRegistry.getInstrumentation().targetContext, Lang.ZH)
        assertEquals(41, content.bettingMarkets().size)
        assertEquals(504, (0..251).sumOf { content.newsForWorkday(it).size })
        assertEquals("bet_0491_B", content.bettingMarkets().first { it.id == "bet_0491" }.correctOptionId)
        assertTrue((0..251).flatMap { content.newsForWorkday(it) }.first { it.id == "news_0441" }.body.contains("江苏队战胜深圳队"))
        assertTrue((0..251).flatMap { content.newsForWorkday(it) }.first { it.id == "news_0449" }.body.contains("陈梦战胜孙颖莎"))
        content.bettingMarkets().forEach { market ->
            val engine = GameEngine(content, SplitMix64Random(1))
            val s = GameState.newGame("device", 1, contentVersion = content.contentVersion, contentHash = content.contentHash)
                .copy(phase = Phase.DayEntry, position = CalendarPosition(WorkdayRef(market.openWorkdayIndex)), attributes = Attributes(luck = 1))
            var placed = s
            // Date gates run before wagering, including the 2017 cap change.
            repeat(4) { attempt ->
                if (placed.phase == Phase.EventNotice) {
                    placed = engine.apply(placed, Command.AcknowledgeEvent(placed.revision, placed.saveId,
                        "ack-$attempt", placed.pendingEventId!!)).newState
                }
                if (placed.activeBet == null) {
                    placed = engine.apply(placed, Command.PlaceBet(placed.revision, placed.saveId,
                        "place-$attempt", market.id, market.correctOptionId!!, "500", market.settleWorkdayIndex)).newState
                }
            }
            assertNotNull("No wager placed for ${market.id}", placed.activeBet)
            val settled = engine.apply(placed.copy(position = CalendarPosition(WorkdayRef(market.settleWorkdayIndex))),
                Command.SettleBet(placed.revision, placed.saveId, "settle", market.id, "ignored")).newState
            assertNull(settled.activeBet)
            assertEquals(Money(650_000), settled.cash)
        }
    }
}
