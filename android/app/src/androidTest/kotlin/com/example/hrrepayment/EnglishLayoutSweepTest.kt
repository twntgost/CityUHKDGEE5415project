package com.example.hrrepayment

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.Attributes
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.data.content.AssetContentLoader
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.save.SaveSlotSummary
import com.example.hrrepayment.data.save.SaveSnapshot
import com.example.hrrepayment.presentation.MenuPanel
import com.example.hrrepayment.ui.menu.MenuScreen
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import com.example.hrrepayment.ui.workbench.WorkbenchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 英文长文案布局压力测试。
 *
 * 为什么必须单独做一遍：中文两个字的信息量，英文要 8–15 个字母。这一轮已经因此踩到
 * 三次真实问题——顶栏 “Calendar” 被挤成竖排、岗位需求被截断、开场页“开始游戏”
 * 按钮掉出屏幕。中文界面全绿不代表英文界面没问题。
 *
 * 这套用例做两件事：
 *  1. **断言**关键控件必须完整落在首屏内（bounding box 不越界），并断言必须单行的
 *     标签确实只有一行高（用高度间接判断，绕开行、就说明换行了）；
 *  2. 每个界面**存一张英文实拍图**，供人工一眼扫过——因为“文字被省略号截断”这件事
 *     测试 API 观察不到，截图是唯一可靠的证据。这点局限写在方法注释里，不假装覆盖了。
 */
@RunWith(AndroidJUnit4::class)
class EnglishLayoutSweepTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val content: ContentRef by lazy { AssetContentLoader.load(context, Lang.EN) }
    private val strings: Strings get() = Strings(Lang.EN)

    private fun screen(): Rect {
        val node = compose.onNodeWithTag("tag_screen_root", useUnmergedTree = true)
        return if (compose.onAllNodesWithTag("tag_screen_root").fetchSemanticsNodes().isEmpty()) {
            Rect(0f, 0f, 1080f, 2400f)
        } else {
            node.fetchSemanticsNode().boundsInRoot
        }
    }

    private fun save(name: String) {
        compose.waitForIdle()
        Thread.sleep(350)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(context.filesDir, "previews").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** 关键控件必须完整在屏幕内，且高度不能超过一行（否则就是换行了）。 */
    private fun assertFitsOnOneLine(tag: String, maxHeightPx: Float, note: String) {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        val b = node.boundsInRoot
        val screen = screen()
        assertTrue(
            "$note：$tag 掉出屏幕（top=${b.top} bottom=${b.bottom} 屏高=${screen.height}）",
            b.top >= -1f && b.bottom <= screen.height + 1f,
        )
        assertTrue(
            "$note：$tag 高度 ${b.height}px 超过单行上限 ${maxHeightPx}px，说明换行了",
            b.height <= maxHeightPx,
        )
    }

    @Test
    fun englishMenuFitsAndLabelsStayOnOneLine() {
        val emptySlots = SaveSlot.all.map { SaveSlotSummary(it, SaveSnapshot.Empty) }
        compose.setContent {
            HRPayDebtTheme {
                MenuScreen(
                    strings = strings, lang = Lang.EN, panel = MenuPanel.START,
                    slots = emptySlots, busy = false, onPanel = {}, onNewGame = {},
                    onLoadGame = {}, onSwitchLanguage = {}, onOpenDossiers = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 顶栏英文最长的是 “Interview Dossiers”，底部入口必须还是单行药丸
        assertFitsOnOneLine("tag_menu_dossier", 120f, "英文菜单底部入口")
        assertFitsOnOneLine("tag_menu_new", 200f, "英文菜单主按钮")
        compose.onNodeWithTag("tag_menu_version").assertIsDisplayed()
        save("90-en-menu")
    }

    /** 英文名在 5 列布局里很窄，必须换行完整显示，而不是截成 “Zheng M...”。 */
    @Test
    fun englishDossierNamesWrapInsteadOfEllipsizing() {
        val entries = listOf(
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_001", "Sun Lan", "Recruiting Operations Specialist",
                "avatars/resume_001.png", 3, promotions = 1,
            ),
            com.example.hrrepayment.presentation.DossierEntry(
                "resume_002", "Zheng Ming", "Recruiting Operations Specialist",
                "avatars/resume_002.png", 8, resigned = true,
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                com.example.hrrepayment.ui.dossier.DossierScreen(
                    strings = strings, entries = entries, onClose = {},
                )
            }
        }
        compose.waitForIdle()
        // 名字节点必须比一行高（说明换行了），但不超过三行
        val name = compose.onNodeWithText("Zheng Ming", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("英文名没换行，可能又被截断了：高度 ${name.height}px", name.height >= 40f)
        assertTrue("英文名超过三行：高度 ${name.height}px", name.height <= 190f)
        save("94-en-dossier-names")
    }

    @Test
    fun englishEndingTitleStaysOnOneLine() {
        val state = GameState.newGame("en", 1).copy(
            phase = Phase.Ended, endingId = "debt_free_restart", endingReason = "final_debt_zero",
            stats = com.example.hrrepayment.core.model.Stats(globalSuccessCount = 120, globalFailureCount = 90),
        )
        compose.setContent {
            HRPayDebtTheme {
                com.example.hrrepayment.ui.ending.EndingPanel(
                    strings = strings, state = state, onReturnMenu = {},
                    style = com.example.hrrepayment.ui.ending.EndingStyle.STELE,
                )
            }
        }
        compose.waitForIdle()
        // 英文结局名比中文长得多，自适应字号必须保证仍然单行
        val title = com.example.hrrepayment.core.i18n.Story.endingTitle(Lang.EN, "debt_free_restart")
        val node = compose.onNodeWithText(title).fetchSemanticsNode()
        assertTrue(
            "英文结局名换行了：高度 ${node.boundsInRoot.height}px",
            node.boundsInRoot.height <= 130f,
        )
        save("91-en-ending")
    }

    @Test
    fun englishWorkbenchHeaderAndButtonsStayInside() {
        val state = mutableStateOf(
            GameState.newGame("en", 2).copy(
                phase = Phase.DayEntry,
                attributes = Attributes(health = 7, dopamine = 5, english = 6, luck = 3, work = 23, trust = 9),
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = strings, state = state.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        // 顶栏三个入口在英文下最容易换行/挤压
        listOf("tag_status_menu", "tag_workbench_dossier", "tag_news_menu").forEach { tag ->
            assertFitsOnOneLine(tag, 120f, "英文工作台顶栏")
        }
        // 这一条正是曾经掉出屏幕的那类问题：英文正文变长把按钮顶下去
        assertFitsOnOneLine("tag_day_entry_next", 200f, "英文工作日入口按钮")
        save("92-en-workbench")
    }

    @Test
    fun englishRecruitingShowsDemandWithoutClippingTheCard() {
        val state = mutableStateOf(
            GameState.newGame("en", 3).copy(
                phase = Phase.Recruiting,
                attributes = Attributes(health = 7, dopamine = 5, english = 6, luck = 3, work = 23, trust = 9),
            ),
        )
        compose.setContent {
            HRPayDebtTheme {
                WorkbenchScreen(
                    strings = strings, state = state.value, content = content, busy = false,
                    onEnterRecruiting = {}, onBrowse = {}, onSelect = {}, onSubmit = {},
                    onAcknowledgeResult = {}, onAcknowledgeEvent = {}, onChooseReward = {},
                    onFinishIncome = {}, onRepayDebt = {}, onFinishEconomy = {}, onChooseMonthlySkill = {},
                    onPlaceBet = { _, _, _ -> }, onMarkStatement = { _, _ -> }, onUseLuck = {},
                    onOpenDossiers = {}, onReturnMenu = {},
                )
            }
        }
        val screen = screen()
        val pager = compose.onNodeWithTag("tag_candidate_pager").fetchSemanticsNode().boundsInRoot
        save("93-en-recruiting")
        assertTrue("英文简历卡掉出屏幕：bottom=${pager.bottom}", pager.bottom <= screen.height + 1f)
        assertTrue("英文简历卡被压扁：height=${pager.height}", pager.height > 300f)
        // “Background” 曾经被写死的 34dp 挤成 “Backgrou/nd”——标签必须只有一行高
        listOf("tag_claim_label_Background", "tag_claim_label_Project").forEach { tag ->
            // 翻页器会同时渲染相邻那张卡，所以同一 tag 可能有两个节点，取第一个
            val b = compose.onAllNodesWithTag(tag, useUnmergedTree = true)[0]
                .fetchSemanticsNode().boundsInRoot
            assertTrue("英文标签 $tag 被折行了：高度 ${b.height}px", b.height <= 80f)
        }
        save("93-en-recruiting")
    }
}
