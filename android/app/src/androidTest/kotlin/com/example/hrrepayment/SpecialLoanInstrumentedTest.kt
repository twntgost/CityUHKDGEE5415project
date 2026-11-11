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
import com.example.hrrepayment.ui.news.NewsPanel
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.WorkbenchScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SpecialLoanInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "special-loan").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun newsInvitationAcceptAndAprilNotice() {
        val content = AssetContentLoader.load(context, Lang.ZH)
        val engine = GameEngine(content, SplitMix64Random(1))
        val state = mutableStateOf(GameState.newGame("loan-ui", 1, contentHash = content.contentHash).copy(
            phase = Phase.DayEntry, pendingStep = PendingStep.None,
            position = CalendarPosition(WorkdayRef(36))))
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(Strings(Lang.ZH), state.value, content, false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = { id ->
                        state.value = engine.apply(state.value, Command.AcknowledgeEvent(state.value.revision, state.value.saveId, "ack", id)).newState
                    }, onChooseReward = {}, onFinishIncome = {}, onRepayDebt = {},
                    onFinishEconomy = {}, onChooseMonthlySkill = {}, onPlaceBet = { _, _, _ -> },
                    onMarkStatement = { _, _ -> }, onUseLuck = {}, onOpenDossiers = {}, onReturnMenu = {},
                    onDecideSpecialLoan = { accept ->
                        state.value = engine.apply(state.value, Command.DecideSpecialLoan(state.value.revision, state.value.saveId, "decide", accept)).newState
                    }, onReadSpecialLoanInvitation = {
                        state.value = engine.apply(state.value, Command.ReadSpecialLoanInvitation(state.value.revision, state.value.saveId, "read")).newState
                    })
            }
        }
        compose.onNodeWithTag("tag_special_loan_invite").performScrollTo().assertIsDisplayed()
        save("01-daily-invitation")
        compose.onNodeWithTag("tag_news_menu").performClick()
        compose.onNodeWithTag("tag_news_special_loan").performClick()
        compose.runOnIdle { org.junit.Assert.assertTrue(state.value.specialLoanInvitationRead) }
        compose.onNodeWithTag("tag_special_loan_collateral").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("tag_special_loan_accept").performScrollTo().assertIsDisplayed()
        save("02-loan-contract")
        compose.onNodeWithTag("tag_special_loan_accept").performClick()
        compose.onNodeWithTag("tag_special_loan_dialog").assertDoesNotExist()
        compose.onNodeWithTag("tag_news_special_loan").assertDoesNotExist()
        compose.onNodeWithTag("tag_news_close").performClick()
        compose.onNodeWithTag("tag_special_loan_due").performScrollTo().assertIsDisplayed()
        save("03-loan-outstanding")
        compose.onNodeWithTag("tag_status_menu").performClick()
        compose.onAllNodesWithText(Strings(Lang.ZH)[Key.LOAN_PRINCIPAL]).onLast().assertIsDisplayed()
        compose.onNodeWithTag("tag_total_debt").assertIsDisplayed()
        save("06-status-outstanding")
        compose.onNodeWithTag("tag_status_close").performClick()
        compose.runOnIdle {
            val march = state.value.copy(position = CalendarPosition(WorkdayRef(53)), phase = Phase.MonthlySkill)
            state.value = engine.apply(march, Command.ChooseMonthlySkill(march.revision, march.saveId, "april", Command.MonthlySkillOption.HEALTH)).newState
        }
        compose.onNodeWithText(Strings(Lang.ZH)[Key.LOAN_CLOSED_TITLE]).assertIsDisplayed()
        compose.onNodeWithTag("tag_event_ack").assertIsDisplayed()
        save("04-april-breaking-news")
        compose.onNodeWithTag("tag_event_ack").performClick()
        compose.onNodeWithTag("tag_special_loan_due").assertDoesNotExist()
        compose.onNodeWithText(Strings(Lang.ZH)[Key.LOAN_WAIVED_RECORD]).performScrollTo().assertIsDisplayed()
        save("07-waived-record")
        compose.onNodeWithTag("tag_news_menu").performClick()
        compose.onNodeWithTag("tag_news_special_loan_closed", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun englishInvitationCanBeDeclined() {
        val strings = Strings(Lang.EN)
        val content = AssetContentLoader.load(context, Lang.EN)
        val state = mutableStateOf(GameState.newGame("loan-en", 1).copy(
            phase = Phase.DayEntry, position = CalendarPosition(WorkdayRef(36))))
        compose.setContent {
            HRPayDebtTheme {
                NewsPanel(strings, state.value, content, false, content.workdayInfo(36).date, {},
                    onDecideSpecialLoan = { accept -> state.value = state.value.copy(specialLoanDecision = accept) })
            }
        }
        compose.onNodeWithTag("tag_news_special_loan").performClick()
        compose.onNodeWithTag("tag_special_loan_decline").performScrollTo().assertIsDisplayed()
        save("05-contract-en")
        compose.onNodeWithTag("tag_special_loan_decline").performClick()
        compose.onNodeWithTag("tag_special_loan_dialog").assertDoesNotExist()
        compose.onNodeWithTag("tag_news_special_loan").assertDoesNotExist()
        assert(state.value.cash == Money(600_000))
    }
}
