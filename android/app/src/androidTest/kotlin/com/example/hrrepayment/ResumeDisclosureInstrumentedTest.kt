package com.example.hrrepayment

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.core.model.CalendarPosition
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.model.WorkdayRef
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.CandidateCard
import com.example.hrrepayment.ui.workbench.WorkbenchScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ResumeDisclosureInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reviewHintsUnlockAtTwentyOneThirtyOneAndFortyOne() {
        val content = AssetContentLoader.load(InstrumentationRegistry.getInstrumentation().targetContext, Lang.ZH)
        val candidate = content.workdayCandidates(0).first()
        val work = mutableIntStateOf(20)
        compose.setContent {
            HRPayDebtTheme {
                CandidateCard(candidate = candidate, strings = Strings(Lang.ZH), selected = false, workAbility = work.intValue, busy = false, onSelect = {})
            }
        }
        compose.onNodeWithText("复核").assertDoesNotExist()
        compose.onNodeWithText("造假").assertDoesNotExist()
        compose.onNodeWithText("唯一假话").assertDoesNotExist()
        compose.onNodeWithText("真实情况").assertDoesNotExist()

        compose.runOnIdle { work.intValue = 21 }
        compose.onNodeWithText("复核").assertExists()
        compose.onNodeWithText("唯一假话").assertDoesNotExist()
        compose.runOnIdle { work.intValue = 30 }
        compose.onNodeWithText("唯一假话").assertDoesNotExist()
        compose.runOnIdle { work.intValue = 31 }
        compose.onNodeWithText("唯一假话").assertExists()
        compose.onNodeWithText("真实情况").assertDoesNotExist()
        compose.runOnIdle { work.intValue = 40 }
        compose.onNodeWithText("判定依据").assertDoesNotExist()
        compose.runOnIdle { work.intValue = 41 }
        compose.onNodeWithText("真实情况").assertExists()
        compose.onNodeWithText("判定依据").assertExists()
    }

    @Test fun monthEndShowsHealthRiskBeforeWinterAndChoicesCarryCurrentValues() {
        val content = AssetContentLoader.load(InstrumentationRegistry.getInstrumentation().targetContext, Lang.ZH)
        val current = mutableStateOf(GameState.newGame("health-planning", 1, contentHash = content.contentHash).copy(
            phase = Phase.MonthlySkill,
            position = CalendarPosition(WorkdayRef(215)),
            monthsWithoutHealth = 3,
        ))
        var chosen: Command.MonthlySkillOption? = null
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH),
                    state = current.value,
                    content = content,
                    busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {},
                    onChooseMonthlySkill = { chosen = it },
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        compose.onNodeWithText("入冬健康提醒：当前健康 5。留出至少 7 点，才能承受一次 5 点的下滑。").assertExists()
        compose.onNodeWithText("已经 3 个月没运动了。本月再忽略健康，多巴胺会下降 1 点。").assertExists()
        // 按钮文案改短，当前值移到上方的小卡里，两者都要在
        compose.onNodeWithText("健康 +1").assertExists()
        compose.onNodeWithText("多巴胺 +1").assertExists()
        compose.onNodeWithText("英语 +1").assertExists()
        compose.onAllNodesWithText("5").fetchSemanticsNodes().let { nodes ->
            assertTrue("当前属性小卡未显示数值", nodes.isNotEmpty())
        }
        compose.onNodeWithTag("tag_skill_HEALTH").performClick()
        compose.runOnIdle {
            assertEquals(Command.MonthlySkillOption.HEALTH, chosen)
            current.value = current.value.copy(position = CalendarPosition(WorkdayRef(35)))
        }
        compose.waitForIdle()
        compose.onNodeWithText("入冬健康提醒：当前健康 5。留出至少 7 点，才能承受一次 5 点的下滑。").assertDoesNotExist()
    }
}
