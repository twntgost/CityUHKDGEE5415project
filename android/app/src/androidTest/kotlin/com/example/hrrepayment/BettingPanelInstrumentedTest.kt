package com.example.hrrepayment

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.random.SplitMix64Random
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.BettingPanel
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class BettingPanelInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionAndFullSwipePlaceExactlyOneBet() {
        val content = AssetContentLoader.load(InstrumentationRegistry.getInstrumentation().targetContext, Lang.ZH)
        val engine = GameEngine(content, SplitMix64Random(1))
        val current = mutableStateOf(GameState.newGame("ui-bet", 1, contentHash = content.contentHash).copy(phase = Phase.DayEntry))
        var submissions = 0
        compose.setContent {
            HRPayDebtTheme {
                BettingPanel(strings = Strings(Lang.ZH), state = current.value, content = content, busy = false) { market, option, amount ->
                    val s = current.value
                    submissions++
                    current.value = engine.apply(s, Command.PlaceBet(s.revision, s.saveId, "ui-place", market, option, amount, 1)).newState
                }
            }
        }
        compose.onNodeWithTag("tag_bet_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("tag_bet_option_bet_0003_B").performClick()
        compose.onNodeWithTag("tag_bet_confirm").assertIsEnabled()
        compose.onNodeWithTag("tag_bet_confirm").performTouchInput {
            swipe(centerLeft.copy(x = 4f), center, 400)
        }
        compose.runOnIdle { assertEquals(0, submissions) }
        compose.onNodeWithTag("tag_bet_confirm").performTouchInput {
            swipe(centerLeft.copy(x = 4f), centerRight.copy(x = width - 1f), 500)
        }
        // The panel closes itself once a wager is frozen; the pending line now lives in
        // the news panel that hosts it, so assert on the resulting state instead.
        compose.runOnIdle {
            assertEquals(1, submissions)
            assertEquals("bet_0003_B", current.value.activeBet?.optionId)
        }
    }
}
