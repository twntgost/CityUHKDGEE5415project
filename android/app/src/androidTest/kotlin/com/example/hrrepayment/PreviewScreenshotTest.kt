package com.example.hrrepayment

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.save.SaveSlotSummary
import com.example.hrrepayment.data.save.SaveSnapshot
import com.example.hrrepayment.presentation.DossierEntry
import com.example.hrrepayment.presentation.MenuPanel
import com.example.hrrepayment.ui.dossier.DossierScreen
import com.example.hrrepayment.ui.menu.MenuScreen
import com.example.hrrepayment.ui.news.NewsPanel
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.ui.components.GameButton
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.CandidateCard
import com.example.hrrepayment.ui.workbench.WorkbenchScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders each v3 screen on the device and saves a PNG.
 *
 * This is both a smoke test (every screen composes without crashing on a real device)
 * and the source of the review screenshots the author asked for: the images are real
 * device renders, not mockups. Files land in the app's external files dir and are
 * pulled out by the test script.
 */
@Composable
private fun FontRow(label: String, style: androidx.compose.ui.text.TextStyle) {
    androidx.compose.foundation.layout.Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("还债的人力资源官 The Debt-Ridden HR Officer 0123", style = style, color = MaterialTheme.colorScheme.onSurface)
    }
}

@RunWith(AndroidJUnit4::class)
class PreviewScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content by lazy { AssetContentLoader.load(context, Lang.ZH) }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun slots(occupied: Boolean = false): List<SaveSlotSummary> = SaveSlot.all.map { slot ->
        val snapshot = if (occupied) {
            SaveSnapshot.Loaded(
                GameState.newGame("slot-${slot.number}", 3).copy(phase = Phase.DayEntry, revision = 7),
                savedAtMillis = 1_700_000_000_000L,
            )
        } else {
            SaveSnapshot.Empty
        }
        SaveSlotSummary(slot, snapshot)
    }

    @Test
    fun startMenuChineseAndEnglish() {
        val panel = mutableStateOf(MenuPanel.START)
        val lang = mutableStateOf(Lang.ZH)
        compose.setContent {
            HRPayDebtTheme {
                MenuScreen(
                    strings = Strings(lang.value),
                    lang = lang.value,
                    panel = panel.value,
                    slots = slots(),
                    busy = false,
                    onPanel = { panel.value = it },
                    onNewGame = {}, onLoadGame = {}, onSwitchLanguage = {}, onOpenDossiers = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 未勾选：进游戏的按钮禁用，并在按钮下给出原因。
        save("01-menu-zh-locked")

        // 条款随时可读（不需要先勾选），面板从底部升起。
        compose.onNodeWithTag("tag_consent_notice").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("tag_notice_panel").fetchSemanticsNodes().isNotEmpty() }
        save("02-terms-panel")

        // “我已知晓”关闭面板并同时勾选。
        compose.onNodeWithTag("tag_notice_ack").performClick()
        compose.waitForIdle()
        save("02b-menu-zh-consented")

        compose.runOnIdle {
            lang.value = Lang.EN
            panel.value = MenuPanel.LOAD_GAME
        }
        save("03-menu-en-slots")
    }

    @Test
    fun dossierDenseAndZoomed() {
        val candidates = (0 until 30).flatMap { content.workdayCandidates(it) }
        val entries = candidates.map {
            DossierEntry(it.id, it.name, it.role, it.avatarAsset, it.workdayIndex)
        }
        compose.setContent {
            HRPayDebtTheme { DossierScreen(strings = Strings(Lang.ZH), entries = entries, onClose = {}) }
        }
        save("04-dossier-5-per-row")
        compose.onNodeWithTag("tag_dossier_grid").performTouchInput {
            pinch(
                start0 = center + Offset(-40f, 0f),
                end0 = center + Offset(-260f, 0f),
                start1 = center + Offset(40f, 0f),
                end1 = center + Offset(260f, 0f),
                durationMillis = 500,
            )
        }
        save("05-dossier-3-per-row")
    }

    @Test
    fun workbenchStatusEnergyCellsAndNewsBetting() {
        val state = GameState.newGame("preview", 5).copy(
            phase = Phase.DayEntry,
            attributes = com.example.hrrepayment.core.model.Attributes(
                health = 7, dopamine = 4, english = 6, luck = 5, work = 23, trust = 9, luckPeak = 6,
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = state, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("06-day-entry")

        compose.onNodeWithTag("tag_status_menu").performClick()
        save("07-status-energy-cells")
        compose.onNodeWithTag("tag_status_close").performClick()

        compose.onNodeWithTag("tag_news_menu").performClick()
        save("08-news-panel")
        compose.onNodeWithTag("tag_news_close").performClick()

        // 竞猜是独立弹窗：从入口卡进入，下注/取消后关闭（作者 2026-10-11 定）
        compose.onNodeWithTag("tag_bet_entry").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tag_bet_dialog").assertExists()
        compose.onNodeWithTag("tag_bet_panel").assertExists()
        save("09-betting-dialog")
        compose.onNodeWithTag("tag_bet_cancel").performClick()
        compose.waitForIdle()
        // 取消后必须消失
        compose.onAllNodesWithTag("tag_bet_dialog").assertCountEquals(0)
        // 新闻面板里不该再有竞猜入口
        compose.onNodeWithTag("tag_news_menu").performClick()
        compose.onAllNodesWithTag("tag_bet_panel").assertCountEquals(0)
        compose.onAllNodesWithTag("tag_bet_open").assertCountEquals(0)
        save("08b-news-without-betting")
    }

    @Test
    fun noticeSchemes() {
        compose.setContent {
            HRPayDebtTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                ) {
                    Text("系统提示框方案 A / B / C", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    com.example.hrrepayment.ui.components.SystemNoticeGallery()
                }
            }
        }
        save("00-notice-schemes")
    }

    @Test
    fun workbenchEconomyAndRecruitingV2() {
        val state = GameState.newGame("preview2", 9).copy(
            phase = Phase.Recruiting,
            attributes = com.example.hrrepayment.core.model.Attributes(
                health = 7, dopamine = 4, english = 6, luck = 5, work = 23, trust = 9, luckPeak = 6,
            ),
            selectedCandidateId = content.workdayCandidates(0).first().id,
            markedStatements = mapOf(content.workdayCandidates(0).first().id to "school"),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = state, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("20-recruiting-serif-peek")
    }

    @Test
    fun economyPanelsV2() {
        val state = GameState.newGame("preview3", 3).copy(
            phase = Phase.Repayment,
            monthlySalary = com.example.hrrepayment.core.money.Money(800_000L),
            nextMonthlySalary = com.example.hrrepayment.core.money.Money(820_000L),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = state, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("21-repayment-money-pattern")
    }

    @Test
    fun resultStampDialogs() {
        val success = GameState.newGame("ok", 4).copy(
            phase = Phase.ResultAck,
            lastResultSuccess = true,
            lastReviewCorrect = true,
            lastResultCandidateId = content.workdayCandidates(0).first().id,
            stats = com.example.hrrepayment.core.model.Stats(globalSuccessCount = 3, globalFailureCount = 1),
        )
        val failure = GameState.newGame("bad", 4).copy(
            phase = Phase.ResultAck,
            lastResultSuccess = false,
            lastReviewCorrect = false,
            lastResultCandidateId = content.workdayCandidates(0).first().id,
            lastResultTrustPenalty = 1,
            stats = com.example.hrrepayment.core.model.Stats(globalSuccessCount = 2, globalFailureCount = 3),
        )
        val current = mutableStateOf(success)
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = current.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("22-result-success-stamp")
        compose.runOnIdle { current.value = failure }
        save("23-result-failure-stamp")
    }

    @Test
    fun newsDialogExpandAndCollapse() {
        val state = GameState.newGame("news", 6).copy(phase = Phase.DayEntry)
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = state, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        compose.onNodeWithTag("tag_news_menu").performClick()
        save("24-news-dialog-centered")
        compose.onNodeWithTag("tag_news_toggle").performClick()
        save("25-news-dialog-collapsed")
    }

    /**
     * 11 套结局正文（8 个结局 ID × 原因码）逐屏渲染 **并断言**：
     * 每个组合的结局名与实际显示的正文片段都要对得上，且不能串到别的结局去。
     */
    @Test
    fun allEndingScreens() {
        data class Case(
            val id: String,
            val reason: String,
            val event: String?,
            val title: String,
            val fragment: String,
            val debtFree: Boolean,
        )
        val cases = listOf(
            Case("overseas_repaid", "debt_zero", null, "远赴重洋·人生逆袭", "欠款是零。我又核对了一次", true),
            Case("debt_free_restart", "final_debt_zero", null, "一身轻松·苦涩重启", "我核对完最后一张账单", true),
            Case("debt_free_restart", "exposed_debt_zero", null, "一身轻松·苦涩重启", "我每天都在判断别人的材料", true),
            Case("debt_free_restart", "trust_negative", null, "一身轻松·苦涩重启", "公司通知我终止任职", true),
            Case("overseas_indebted", "debt_remaining", "overseas_offer", "凭空消失·逍遥法外", "我没有收到任何一张结清证明", false),
            Case("bankruptcy", "cash_exhausted", null, "深渊滚雪球·赌徒末路", "我反复刷新余额", false),
            Case("bankruptcy", "debt_cap_exceeded", null, "深渊滚雪球·赌徒末路", "年底的利息记进了账单", false),
            Case("indebted_layoff", "final_debt_remaining", null, "时代一粒灰·困顿底层", "裁员通知到了", false),
            Case("health_death", "cold_health_low", "winter_cold", "时空交错·时代尘埃", "后面的字，我没有读完", false),
            Case("health_death", "final_health_low", null, "时空交错·时代尘埃", "屏幕上的日期停在十二月的最后一天", false),
            Case("low_dopamine", "prolonged_low", null, "烂命一条", "我没有再点开", false),
            Case("exposed", "exposed_debt_remaining", null, "露出马脚", "下一份工资却不能再照着原来的数往下算", false),
        )
        val current = mutableStateOf(
            GameState.newGame("ending", 1).copy(phase = Phase.Ended, endingId = cases[0].id, endingReason = cases[0].reason),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = current.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        cases.forEachIndexed { index, case ->
            compose.runOnIdle {
                current.value = GameState.newGame("ending", 1).copy(
                    phase = Phase.Ended,
                    endingId = case.id,
                    endingReason = case.reason,
                    pendingEventId = case.event,
                    cash = if (case.debtFree) com.example.hrrepayment.core.money.Money(320_000L) else com.example.hrrepayment.core.money.Money(0L),
                    debt = if (case.debtFree) com.example.hrrepayment.core.money.Money(0L) else com.example.hrrepayment.core.money.Money(83_000_000L),
                    attributes = com.example.hrrepayment.core.model.Attributes(
                        health = 3, dopamine = 2, english = 12, luck = 2, work = 52, trust = 6, luckPeak = 5,
                    ),
                    stats = com.example.hrrepayment.core.model.Stats(
                        globalSuccessCount = 137, globalFailureCount = 114,
                    ),
                )
            }
            compose.waitForIdle()
            // —— 断言：结局名显示、正文片段存在、结束语存在、返回按钮可用
            compose.onNodeWithText(case.title).assertIsDisplayed()
            compose.onNodeWithText(case.fragment, substring = true).assertExists()
            compose.onNodeWithText("游戏结束了。").assertExists()
            compose.onNodeWithTag("tag_return_home").assertExists()
            save("40-ending-%02d-%s-%s".format(index + 1, case.id, case.reason))
        }
    }

    /**
     * 档案袋职业状态：已离职（灰头像 + 红字）、晋升 1 次（绿框）、晋升 2 次（金框）。
     * 这是作者新增的随机性在 UI 上的落点，必须逐条断言而不是只看图。
     */
    @Test
    fun dossierCareerStates() {
        val entries = listOf(
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_001", "孙兰", "招聘运营专员", "avatars/resume_001.png", 3,
                resigned = true, promotions = 1,
            ),
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_002", "郑明", "招聘运营专员", "avatars/resume_002.png", 8, promotions = 1,
            ),
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_003", "何春", "招聘运营专员", "avatars/resume_003.png", 11, promotions = 2,
            ),
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_004", "周琳", "招聘运营专员", "avatars/resume_004.png", 15,
            ),
        )
        compose.setContent {
            HRPayDebtTheme { DossierScreen(strings = Strings(Lang.ZH), entries = entries, onClose = {}) }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tag_dossier_resigned_resume_001").assertExists()
        compose.onNodeWithTag("tag_dossier_promoted_resume_002").assertExists()
        compose.onNodeWithTag("tag_dossier_promoted_resume_003").assertExists()
        // 没离职也没晋升的人不该有状态标注
        compose.onAllNodesWithTag("tag_dossier_resigned_resume_004").assertCountEquals(0)
        compose.onAllNodesWithTag("tag_dossier_promoted_resume_004").assertCountEquals(0)
        save("80-dossier-career-states")
    }

    /** 剧情事件（离职/入冬等）走与推送结果同一套弹窗形式，印章是感叹号。 */
    @Test
    fun eventNoticeUsesStampDialog() {
        val eventState = GameState.newGame("event", 5).copy(
            phase = Phase.EventNotice,
            pendingEventId = com.example.hrrepayment.core.engine.GameEngine.EVENT_WINTER_COLD,
            attributes = com.example.hrrepayment.core.model.Attributes(health = 3),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = eventState, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        compose.waitForIdle()
        // 事件弹窗用与结果弹窗相同的容器，但 tag 是事件自己的
        compose.onNodeWithTag("tag_event_panel").assertExists()
        compose.onNodeWithTag("tag_event_ack").assertExists()
        compose.waitUntil(5_000) { true }
        save("81-event-stamp-dialog")
    }

    /** 竞猜结算后，结果必须出现在**每日简报**里（作者要求），且隔天不再显示。 */
    @Test
    fun betResultShowsInDailyBriefing() {
        val base = GameState.newGame("bet", 4).copy(
            phase = Phase.DayEntry,
            attributes = com.example.hrrepayment.core.model.Attributes(
                health = 6, dopamine = 4, english = 5, luck = 3, work = 22, trust = 8,
            ),
        )
        val current = mutableStateOf(
            base.copy(
                todayBetOutcome = com.example.hrrepayment.core.model.BetOutcome.WON,
                todayBetPayout = com.example.hrrepayment.core.money.Money(125_000L),
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = current.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("tag_brief_bet").assertExists()
        compose.onNodeWithText("猜中，派彩 1,250.00。").assertExists()
        // 结算后当天不该再有“去竞猜”入口，避免看起来能重复下注
        compose.onAllNodesWithTag("tag_bet_entry").assertCountEquals(0)
        save("10b-briefing-bet-won")

        // 输：文案换成运气 +N
        compose.runOnIdle {
            current.value = base.copy(
                todayBetOutcome = com.example.hrrepayment.core.model.BetOutcome.LOST,
                todayBetPayout = com.example.hrrepayment.core.money.Money(0L),
            )
        }
        compose.waitForIdle()
        compose.onNodeWithText("没猜中，运气 +3。").assertExists()

        // 隔天：结算标记被清空，简报里不再有竞猜行
        compose.runOnIdle {
            current.value = base.copy(
                todayBetOutcome = null, todayBetPayout = null,
                activeBet = null,
            )
        }
        compose.waitForIdle()
        compose.onAllNodesWithTag("tag_brief_bet").assertCountEquals(0)
    }

    /** 选择型面板：奖励二选一与月度技能三选一（按钮已收窄）。 */
    @Test
    fun choicePanels() {
        val state = mutableStateOf(
            GameState.newGame("choice", 2).copy(
                phase = Phase.SuccessReward,
                attributes = com.example.hrrepayment.core.model.Attributes(
                    health = 7, dopamine = 4, english = 6, luck = 3, work = 23, trust = 9, luckPeak = 6,
                ),
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.ZH), state = state.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("60-reward-choice-buttons")
        compose.runOnIdle { state.value = state.value.copy(phase = Phase.MonthlySkill, position = com.example.hrrepayment.core.model.CalendarPosition(com.example.hrrepayment.core.model.WorkdayRef(30))) }
        save("61-skill-choice-buttons")
    }

    /** 结局三种排版方案对照（同一结局 + 一个好结局）。 */
    @Test
    fun endingStyleOptions() {
        val bad = GameState.newGame("e", 1).copy(
            phase = Phase.Ended, endingId = "bankruptcy", endingReason = "debt_cap_exceeded",
            cash = com.example.hrrepayment.core.money.Money(0L),
            debt = com.example.hrrepayment.core.money.Money(83_000_000L),
            stats = com.example.hrrepayment.core.model.Stats(globalSuccessCount = 137, globalFailureCount = 114),
        )
        val good = GameState.newGame("e", 1).copy(
            phase = Phase.Ended, endingId = "overseas_repaid", endingReason = "debt_zero",
            cash = com.example.hrrepayment.core.money.Money(320_000L),
            debt = com.example.hrrepayment.core.money.Money(0L),
            stats = com.example.hrrepayment.core.model.Stats(globalSuccessCount = 212, globalFailureCount = 40),
        )
        val current = mutableStateOf(bad)
        val style = mutableStateOf(com.example.hrrepayment.ui.ending.EndingStyle.DARK_CURTAIN)
        compose.setContent {
            HRPayDebtTheme {
                com.example.hrrepayment.ui.ending.EndingPanel(
                    strings = Strings(Lang.ZH),
                    state = current.value,
                    onReturnMenu = {},
                    style = style.value,
                )
            }
        }
        com.example.hrrepayment.ui.ending.EndingStyle.entries.forEach { option ->
            compose.runOnIdle { style.value = option; current.value = bad }
            compose.waitForIdle()
            save("50-ending-style-A-bad-" + option.name)
        }
        compose.runOnIdle {
            style.value = com.example.hrrepayment.ui.ending.EndingStyle.STELE
            current.value = good
        }
        save("51-ending-style-good")
    }

    @Test
    fun fontSampleSheet() {
        compose.setContent {
            HRPayDebtTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(18.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp),
                ) {
                    Text("字体素材对照", style = MaterialTheme.typography.headlineMedium)
                    FontRow("海报 Display", AppType.PosterTitle.copy(fontSize = 30.sp, lineHeight = 36.sp))
                    FontRow("UI 无衬线 Sans", MaterialTheme.typography.bodyLarge)
                    FontRow("简历衬线 Serif", AppType.Serif)
                    FontRow("等宽 Mono", AppType.NoticeMono)
                    Text("按钮（限宽 + 大字距）", style = MaterialTheme.typography.labelLarge)
                    GameButton(
                        text = "继续 Continue",
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        wide = true,
                    )
                }
            }
        }
        save("30-font-samples")
    }

    @Test
    fun englishHeaderDoesNotWrap() {
        val state = GameState.newGame("en", 8).copy(
            phase = Phase.DayEntry,
            attributes = com.example.hrrepayment.core.model.Attributes(
                health = 7, dopamine = 4, english = 6, luck = 5, work = 23, trust = 9, luckPeak = 6,
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = Strings(Lang.EN), state = state, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        save("26-workbench-en-header")
    }

    @Test
    fun candidateCardRevealLevels() {
        val candidate = content.workdayCandidates(0).first()
        val work = mutableStateOf(45)
        compose.setContent {
            HRPayDebtTheme {
                CandidateCard(
                    candidate = candidate,
                    strings = Strings(Lang.ZH),
                    selected = true,
                    workAbility = work.value,
                    busy = false,
                    onSelect = {},
                    markedStatementId = candidate.lieStatementId,
                )
            }
        }
        save("10-candidate-revealed")
        compose.runOnIdle { work.value = 15 }
        save("11-candidate-hidden")
    }
}
