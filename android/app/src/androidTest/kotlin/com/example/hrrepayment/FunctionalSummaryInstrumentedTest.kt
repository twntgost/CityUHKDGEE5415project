package com.example.hrrepayment

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.*
import com.example.hrrepayment.core.model.*
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.SplitMix64Random
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.data.save.*
import com.example.hrrepayment.presentation.MenuPanel
import com.example.hrrepayment.ui.menu.MenuScreen
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FunctionalSummaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "functional").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun repaymentsAreSeparateAndSpecialRepaymentRequiresConfirmation() {
        val content = AssetContentLoader.load(context, Lang.ZH)
        val strings = Strings(Lang.ZH)
        val engine = GameEngine(content, SplitMix64Random(1))
        val state = mutableStateOf(GameState.newGame("repay", 1, contentHash = content.contentHash).copy(
            phase = Phase.Repayment, position = CalendarPosition(WorkdayRef(41)),
            specialLoanDecision = true, specialLoanDue = Money(15_000_000), cash = Money(20_000_000)))
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(strings, state.value, content, false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {}, onFinishIncome = {},
                    onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {}, onPlaceBet = { _, _, _ -> },
                    onMarkStatement = { _, _ -> }, onUseLuck = {}, onOpenDossiers = {}, onReturnMenu = {},
                    onRepaySpecialLoan = {
                        state.value = engine.apply(state.value, Command.RepaySpecialLoan(state.value.revision, state.value.saveId, "special-repay")).newState
                    })
            }
        }
        compose.onNodeWithText(strings[Key.REPAY_SEPARATE_HINT]).performScrollTo().assertIsDisplayed()
        save("01-separate-repayment")
        compose.onNodeWithTag("tag_special_loan_repay").performScrollTo().performClick()
        compose.onNodeWithText(strings[Key.COMMON_CANCEL]).performClick()
        compose.runOnIdle { assertEquals(Money(15_000_000), state.value.specialLoanDue) }
        compose.onNodeWithTag("tag_special_loan_repay").performClick()
        save("02-repayment-confirm")
        compose.onAllNodesWithText(strings[Key.LOAN_REPAY]).onLast().performClick()
        compose.onNodeWithText(strings[Key.LOAN_REPAID_RECORD]).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(Money(5_000_000), state.value.cash)
            assertEquals(Money(50_000_000), state.value.debt)
            assertEquals(Money.ZERO, state.value.specialLoanDue)
        }
        save("03-repaid-record")
    }

    @Test fun bettingShowsDatesAnd2017CapSource() {
        val content = AssetContentLoader.load(context, Lang.ZH)
        val market = content.bettingMarkets().first { it.openWorkdayIndex >= 36 }
        val state = GameState.newGame("bet-info", 1).copy(phase = Phase.DayEntry,
            position = CalendarPosition(WorkdayRef(market.openWorkdayIndex)),
            betting2017Base = Money(1_000_000), bettingCap = Money(1_300_000))
        compose.setContent { HRPayDebtTheme { BettingDialog(Strings(Lang.ZH), state, content, false, { _, _, _ -> }, {}) } }
        compose.onNodeWithTag("tag_bet_dates").assertIsDisplayed()
        compose.onNodeWithTag("tag_bet_cap_origin").assertIsDisplayed()
        save("04-betting-dates-cap")
    }

    @Test fun loadMenuShowsOnlyPriorityStatusInBothLanguages() {
        val state = GameState.newGame("pending", 1).copy(phase = Phase.EventNotice,
            position = CalendarPosition(WorkdayRef(40)), specialLoanDecision = true,
            specialLoanDue = Money(15_000_000), activeBet = BetSnapshot("pending", "a", Money(50_000), 1, 20, Money(500_000), 42))
        val lang = mutableStateOf(Lang.ZH)
        compose.setContent {
            HRPayDebtTheme {
                MenuScreen(Strings(lang.value), lang.value, MenuPanel.LOAD_GAME,
                    listOf(SaveSlotSummary(SaveSlot.SLOT_1, SaveSnapshot.Loaded(state)),
                        SaveSlotSummary(SaveSlot.SLOT_2, SaveSnapshot.Empty)), false,
                    onPanel = {}, onNewGame = {}, onLoadGame = {}, onSwitchLanguage = {}, onOpenDossiers = {})
            }
        }
        for (language in Lang.entries) {
            compose.runOnIdle { lang.value = language }
            val strings = Strings(language)
            compose.onNodeWithText(strings[Key.SAVE_EVENT_PENDING], substring = true).assertIsDisplayed()
            compose.onNodeWithText(strings[Key.SAVE_BET_PENDING], substring = true).assertDoesNotExist()
            compose.onNodeWithText("150,000.00", substring = true).assertDoesNotExist()
            save("05-save-summary-${language.code}")
        }
    }
}
