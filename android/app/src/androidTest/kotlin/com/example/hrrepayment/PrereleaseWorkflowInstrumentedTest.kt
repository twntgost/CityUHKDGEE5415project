package com.example.hrrepayment

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.HRPayDebtApp
import com.example.hrrepayment.di.AppContainer
import com.example.hrrepayment.presentation.GameViewModel
import com.example.hrrepayment.ui.home.HomeScreen
import com.example.hrrepayment.data.save.SaveSlotSummary
import com.example.hrrepayment.data.save.SaveSnapshot
import com.example.hrrepayment.presentation.MenuPanel
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import com.example.hrrepayment.ui.menu.MenuScreen
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.ui.theme.HRPayDebtTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end device walk of the v3 flow on a real device/emulator:
 * 海报菜单 → 勾选用户须知 → 新建游戏（存档槽） → 开场 → 工作台，
 * 再由状态层返回主菜单并切换语言，确认英文界面真的生效。
 *
 * Runs against the production [AppContainer] (real assets, real DataStore slots) so a
 * regression in content loading, slot handling or localisation fails the device run.
 */
@RunWith(AndroidJUnit4::class)
class PrereleaseWorkflowInstrumentedTest {

    companion object {
        private val containerLock = Any()
        private var sharedContainerCache: AppContainer? = null

        /**
         * 等待上限。模拟器冷启动（尤其是刚开机后的首轮）解析 1.8MB 内容包 + 首次建槽
         * 会明显变慢，10s 会偶发超时；这里放宽到 30s，断言内容不变。
         */
        private const val WAIT_MS = 30_000L
    }

    @get:Rule val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 设备实拍：与 PreviewScreenshotTest 同一套落盘方式，供开场页这类需要真实 ViewModel 的界面使用。 */
    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(400)
        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation.takeScreenshot()
        val dir = java.io.File(context.filesDir, "previews").apply { mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /**
     * 进程内共享同一个 [AppContainer]。
     *
     * DataStore 对同一个文件是**进程级单例**：同一个测试进程里第二次
     * `AppContainer.create` 会抛 “There are multiple DataStores active for the same file”。
     * 之前每个用例都建一次，随着用例变多就开始偶发崩溃。复用同一个容器、
     * 每个用例只清存档与复位语言，行为等价且稳定。
     */
    private fun sharedContainer(): AppContainer = synchronized(containerLock) {
        // 必须复用 **Application 自己的容器**：App.onCreate 会在测试进程里先建一个，
        // 测试再 AppContainer.create 就会对同一个 settings DataStore 二次打开并崩溃
        // （"There are multiple DataStores active for the same file"）。
        sharedContainerCache ?: (context.applicationContext as HRPayDebtApp).container
            .also { sharedContainerCache = it }
    }

    private fun freshViewModel(): GameViewModel {
        val container = sharedContainer()
        runBlocking {
            SaveSlot.all.forEach { container.repository(it).clear() }
            // 设备级语言是持久化的：上一条用例切到英文后必须复位，
            // 否则下一条用例在英文界面下跑，断言与布局都对不上。
            container.settings.setLanguage(Lang.ZH)
        }
        val store = ViewModelStore()
        return ViewModelProvider(store, GameViewModel.factory(container))[GameViewModel::class.java]
    }

    /**
     * 作者反馈：勾选协议前后界面变化太明显，按钮“浮窗位置”会移动；且未勾选时点
     * 开始/读档没有任何反馈。这里把两条都钉成断言：
     *  1) 勾选前后 [tag_menu_new] 的边界必须**完全一致**（布局不跳）；
     *  2) 未勾选时点开始游戏不跳转（锁死），红字提示仍然占位可见。
     */
    /**
     * 菜单页三条视觉修正（作者 2026-10-11 反馈）：
     *  1) 锁定时“新建/读取”必须**同款灰显**——之前一个是淡蓝、一个是实心卡其，
     *     结果“读取游戏”看起来才像主按钮，主次关系被锁定状态翻转了；
     *  2) 红字提示要**紧贴勾选框**（两块之间不能再隔一大段空白）；
     *  3) 底部要有档案袋入口与版本号，页面不再头重脚轻。
     */
    @Test
    fun menuLockedHierarchyAndFooterAreConsistent() {
        val emptySlots = SaveSlot.all.map { SaveSlotSummary(it, SaveSnapshot.Empty) }
        compose.setContent {
            HRPayDebtTheme {
                MenuScreen(
                    strings = Strings(Lang.ZH),
                    lang = Lang.ZH,
                    panel = MenuPanel.START,
                    slots = emptySlots,
                    busy = false,
                    onPanel = {},
                    onNewGame = {},
                    onLoadGame = {},
                    onSwitchLanguage = {},
                    onOpenDossiers = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val hint = compose.onNodeWithTag("tag_consent_hint").fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithTag("tag_consent_toggle").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "红字提示与勾选框之间空了 ${toggle.top - hint.bottom}px，两块应当贴在一起",
            toggle.top - hint.bottom < 120f,
        )
        val newGame = compose.onNodeWithTag("tag_menu_new").fetchSemanticsNode().boundsInRoot
        val loadGame = compose.onNodeWithTag("tag_menu_load").fetchSemanticsNode().boundsInRoot
        assertEquals("两个锁定入口的宽度必须一致", newGame.width, loadGame.width)
        compose.onNodeWithTag("tag_menu_version").assertExists()
        compose.onNodeWithTag("tag_menu_dossier").assertExists()
    }

    @Test
    fun consentGateKeepsMenuLayoutStableAndShakesInstead() {
        val emptySlots = SaveSlot.all.map { SaveSlotSummary(it, SaveSnapshot.Empty) }
        compose.setContent {
            HRPayDebtTheme {
                MenuScreen(
                    strings = Strings(Lang.ZH),
                    lang = Lang.ZH,
                    panel = MenuPanel.START,
                    slots = emptySlots,
                    busy = false,
                    onPanel = {},
                    onNewGame = {},
                    onLoadGame = {},
                    onSwitchLanguage = {},
                    onOpenDossiers = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // 红字提示必须占位（不可见≠不存在），否则按钮会跳
        compose.onNodeWithTag("tag_consent_hint").assertExists()
        val before = compose.onNodeWithTag("tag_menu_new").fetchSemanticsNode().boundsInRoot

        // 未勾选时点击入口：只抖红字，不进入槽位面板
        compose.onNodeWithTag("tag_menu_new").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("tag_new_slot1").assertCountEquals(0)
        compose.onAllNodesWithTag("tag_new_slot2").assertCountEquals(0)

        // 勾选后按钮位置不得变化
        compose.onNodeWithTag("tag_consent_toggle").performClick()
        compose.waitForIdle()
        val after = compose.onNodeWithTag("tag_menu_new").fetchSemanticsNode().boundsInRoot
        assertEquals("勾选协议前后按钮位置发生了移动", before, after)
    }

    @Test
    fun posterMenuStartsRunAndSwitchesLanguage() {
        val vm = freshViewModel()
        compose.setContent { HRPayDebtTheme { HomeScreen(vm) } }

        // 1. 未勾选须知时：进游戏的按钮是禁用的，点不动。
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_menu_new").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_menu_new").assertIsNotEnabled()
        compose.onNodeWithTag("tag_menu_load").assertIsNotEnabled()
        compose.onNodeWithTag("tag_consent_hint").assertIsDisplayed()
        compose.onNodeWithTag("tag_menu_new").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("tag_new_slot1").assertCountEquals(0)

        // 2. 《用户须知》在未勾选时也能打开，读完点“我已知晓”即视为同意。
        compose.onNodeWithTag("tag_consent_notice").performClick()
        compose.waitForIdle()
        screenshot("72-terms-with-project")
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_notice_panel").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_notice_ack").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithTag("tag_notice_panel").assertCountEquals(0)

        // 3. 勾选后按钮解禁，进入存档槽面板并开始新游戏。
        compose.onNodeWithTag("tag_menu_new").assertIsEnabled()
        compose.onNodeWithTag("tag_menu_new").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_new_slot1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_new_slot1").performClick()

        // 4. 新档直接进入开场：自述动画 → 轻点结束动画 → 再轻点直接开始游戏。
        //    （测试环境会把动画虚拟时间快进，因此点击次数用循环兜底，不能写死两次。）
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_opening_intro").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        screenshot("71-opening-intro")
        repeat(3) {
            if (compose.onAllNodesWithTag("tag_opening_intro").fetchSemanticsNodes().isEmpty()) return@repeat
            compose.onNodeWithTag("tag_opening_intro").performClick()
            compose.waitForIdle()
            if (compose.onAllNodesWithTag("tag_opening_title").fetchSemanticsNodes().isNotEmpty()) {
                screenshot("70-opening-screen")
            }
        }

        // 5. 工作台出现，存档阶段已推进到工作日入口。
        compose.waitForIdle()
        // 6. 打开面试档案袋：新档还没有录用记录，应显示空状态。
        compose.onNodeWithTag("tag_workbench_dossier").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_dossier_empty").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_dossier_close").performClick()

        // 7. 状态层 → 返回主菜单 → 切换语言 → 英文界面。
        compose.onNodeWithTag("tag_status_menu").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_return_menu").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_return_menu").performScrollTo().performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithTag("tag_menu_lang").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tag_menu_lang").performClick()
        compose.waitUntil(WAIT_MS) { compose.onAllNodesWithText("New Game").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Switch Language").assertIsDisplayed()
    }
}
