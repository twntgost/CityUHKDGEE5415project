package com.example.hrrepayment.ui.home

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.ui.theme.AppType
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hrrepayment.core.content.ContentRef
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.presentation.GameViewModel
import com.example.hrrepayment.presentation.Screen
import com.example.hrrepayment.ui.compositionlocals.LocalLang
import com.example.hrrepayment.ui.compositionlocals.LocalStrings
import com.example.hrrepayment.ui.components.ButtonTone
import com.example.hrrepayment.ui.components.GameButton
import com.example.hrrepayment.ui.components.GameCard
import com.example.hrrepayment.ui.dossier.DossierScreen
import com.example.hrrepayment.ui.menu.MenuScreen
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens
import com.example.hrrepayment.ui.workbench.WorkbenchScreen

/**
 * Application root: 菜单 → 开场 → 工作台，外加面试档案袋覆盖层。
 *
 * The language catalogue is provided here, so switching language recomposes the whole
 * tree with the new strings — no screen keeps its own copy of any label.
 */
@Composable
fun HomeScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lang by viewModel.lang.collectAsStateWithLifecycle()
    val strings = remember(lang) { Strings(lang) }

    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalLang provides lang,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AnimatedContent(
                targetState = uiState.screen,
                // Animate between *screens*, not between every state refresh: a run's
                // state object changes on each command and must not re-play the
                // transition.
                contentKey = { it::class.simpleName ?: "" },
                transitionSpec = {
                    (fadeIn(tween(Tokens.PAGE_MS)) + slideInVertically(tween(Tokens.PAGE_MS)) { it / 18 })
                        .togetherWith(fadeOut(tween(Tokens.PAGE_MS)))
                },
                label = "appScreen",
            ) { screen ->
                when (screen) {
                    Screen.Boot -> BootPanel(strings)
                    is Screen.Menu -> MenuScreen(
                        strings = strings,
                        lang = lang,
                        panel = screen.panel,
                        slots = screen.slots,
                        busy = screen.busy,
                        onPanel = viewModel::openPanel,
                        onNewGame = viewModel::requestNewGame,
                        onLoadGame = viewModel::loadGame,
                        onSwitchLanguage = { viewModel.toggleLanguage() },
                        onOpenDossiers = { viewModel.openDossiers() },
                    )
                    is Screen.Run -> RunHost(
                        strings = strings,
                        state = screen.state,
                        content = viewModel.contentRef,
                        busy = screen.busy,
                        viewModel = viewModel,
                    )
                }
            }

            // 面试档案袋覆盖层
            AnimatedVisibility(
                visible = uiState.isDossierOpen,
                enter = fadeIn(tween(Tokens.PANEL_MS)) + slideInVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it / 8 },
                exit = fadeOut(tween(150)),
            ) {
                DossierScreen(
                    strings = strings,
                    entries = uiState.dossier ?: emptyList(),
                    onClose = { viewModel.closeDossiers() },
                    onOpenSound = viewModel::playDossierOpenSound,
                )
            }

            uiState.banner?.let { banner ->
                BannerBar(
                    text = strings.format(banner.key, *banner.args.toTypedArray()),
                    dismissLabel = strings[Key.COMMON_GOT_IT],
                    onDismiss = { viewModel.dismissBanner() },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }

        uiState.pendingOverwrite?.let { slot ->
            AlertDialog(
                onDismissRequest = { viewModel.cancelOverwrite() },
                title = { Text(strings[Key.MENU_OVERWRITE_TITLE]) },
                text = {
                    Text(
                        strings[Key.MENU_OVERWRITE_BODY] + "\n" +
                            strings.format(Key.MENU_SLOT_OVERWRITE, slot.number),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmOverwrite() }) {
                        Text(strings[Key.MENU_OVERWRITE_CONFIRM], color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelOverwrite() }) { Text(strings[Key.COMMON_CANCEL]) }
                },
            )
        }

        uiState.blockProblem?.let { problem ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissProblem() },
                title = { Text(strings[problem.titleKey], color = MaterialTheme.colorScheme.error) },
                text = {
                    val args = if (problem.kind == com.example.hrrepayment.presentation.SaveProblem.Kind.INCOMPATIBLE) {
                        val parts = problem.detail.split("|")
                        arrayOf(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "" })
                    } else {
                        arrayOf(problem.detail)
                    }
                    Text(strings.format(problem.messageKey, *args))
                },
                confirmButton = {
                    if (problem.canRetry) {
                        TextButton(onClick = { viewModel.retryLoad() }) { Text(strings[Key.BOOT_RETRY_LOAD]) }
                    } else {
                        TextButton(onClick = { viewModel.dismissProblem() }) { Text(strings[Key.COMMON_CLOSE]) }
                    }
                },
                dismissButton = {
                    val target = uiState.problemSlot
                    if (problem.canReset && target != null) {
                        TextButton(onClick = { viewModel.deleteSlot(target) }) {
                            Text(strings[Key.BOOT_DISCARD_AND_RESTART], color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun RunHost(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    viewModel: GameViewModel,
) {
    if (state.phase == Phase.Opening) {
        OpeningPanel(strings, state, content, busy, viewModel::acknowledgeOpening)
        return
    }
    val soundEnabledState by viewModel.soundEnabled.collectAsStateWithLifecycle()
    WorkbenchScreen(
        strings = strings,
        state = state,
        content = content,
        busy = busy,
        onEnterRecruiting = { viewModel.enterRecruiting() },
        onBrowse = { viewModel.browseCandidate(it) },
        onSelect = { viewModel.selectCandidate(it) },
        onSubmit = { viewModel.submitCandidate() },
        onAcknowledgeResult = { viewModel.acknowledgeResult() },
        onAcknowledgeEvent = { viewModel.acknowledgeEvent(it) },
        onChooseReward = { viewModel.chooseSuccessReward(it) },
        onFinishIncome = viewModel::finishIncome,
        onRepayDebt = viewModel::repayDebt,
        onDecideSpecialLoan = viewModel::decideSpecialLoan,
        onReadSpecialLoanInvitation = viewModel::readSpecialLoanInvitation,
        onRepaySpecialLoan = viewModel::repaySpecialLoan,
        onFinishEconomy = viewModel::finishEconomy,
        onChooseMonthlySkill = viewModel::chooseMonthlySkill,
        onPlaceBet = viewModel::placeBet,
        onMarkStatement = viewModel::markStatement,
        onUseLuck = viewModel::setLuckUsage,
        onOpenDossiers = { viewModel.openDossiers() },
        soundEnabled = soundEnabledState,
        onToggleSound = viewModel::setSoundEnabled,
        onSwitchLanguage = { viewModel.toggleLanguage() },
        onSaveNow = viewModel::saveGameNow,
        onLoadNow = viewModel::loadGameNow,
        onFlipSound = viewModel::playResumeFlipSound,
        onPenSound = viewModel::playPenCircleSound,
        onStampSound = viewModel::playStampSound,
        onReturnMenu = { viewModel.returnToMenu() },
    )
}

@Composable
private fun BootPanel(strings: Strings) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(strings[Key.BOOT_LOADING], style = MaterialTheme.typography.bodyLarge)
    }
}

/** 开场知情同意：正文单段密排，勾选后才能开始。 */
@Composable
private fun OpeningPanel(
    strings: Strings,
    state: GameState,
    content: ContentRef,
    busy: Boolean,
    onAcknowledge: () -> Unit,
) {

    // ---- 入场动画：自述文字从屏幕下方升到上方，长按加速、轻点跳过。
    val lines = listOf(
        Key.OPENING_LINE_1, Key.OPENING_LINE_2, Key.OPENING_LINE_3, Key.OPENING_LINE_4,
        Key.OPENING_LINE_5, Key.OPENING_LINE_6, Key.OPENING_LINE_7, Key.OPENING_LINE_8,
    )
    val totalSeconds = 26f
    var progress by remember { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var introFinished by remember { mutableStateOf(false) }
    val scrolled = progress >= 1f
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (progress < 1f) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = now
            progress = (progress + dt * (if (holding) 6f else 1f) / totalSeconds).coerceAtMost(1f)
        }
    }

    val heightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 文字整体从底部移到顶部；越往后字号略微放大（“字体变大”），画面同时变暗
    val dim by animateFloatAsState(if (introFinished) 1f else 0.82f, tween(600), label = "introDim")
    val textOffset = (1f - progress) * heightPx * 0.92f
    val baseSize = 20f

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1B1811))
            // 手势只用一个检测器：长按加速、轻点推进阶段。
            // （clickable + pointerInput 同时挂会互相抢事件，曾经把点击吞掉。）
            .pointerInput(scrolled, introFinished) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                    onTap = {
                        if (!scrolled) {
                            progress = 1f
                        } else if (!introFinished) {
                            introFinished = true
                        }
                    },
                )
            }
            .testTag("tag_opening_intro"),
    ) {
        // ---- 滚动自述
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 26.dp)
                .graphicsLayer {
                    translationY = textOffset
                    alpha = if (scrolled) 0f else dim
                },
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Spacer(Modifier.height(40.dp))
            lines.forEach { key ->
                Text(
                    text = emphasized(strings[key]),
                    style = AppType.Serif,
                    fontSize = baseSize.sp,
                    lineHeight = (baseSize * 1.55f).sp,
                    color = Color(0xFFF4EEDF),
                )
            }
        }

        // ---- 进度细线
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0x22FFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(Tokens.SkyAccent),
            )
        }

        if (!scrolled) {
            Text(
                text = strings[Key.OPENING_HOLD_HINT],
                style = MaterialTheme.typography.labelMedium,
                color = Color(0x99F4EEDF),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
                    .testTag("tag_opening_hint"),
            )
        }

        // ---- 动画结束：居中的“游戏开始了”，再轻点一次才出现协议与开始按钮
        if (scrolled) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = Tokens.PageMargin.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val startedTitle = strings[Key.OPENING_STARTED_TITLE]
                // 中文 5 个字可以用很大的字距；英文标题更长，字距与字号同时收一点，
                // 否则 “THE GAME BEGINS” 会折行溢出。
                val latinTitle = startedTitle.length > 8
                Text(
                    text = startedTitle,
                    style = if (latinTitle) MaterialTheme.typography.titleLarge
                    else MaterialTheme.typography.displaySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    letterSpacing = if (latinTitle) 6.sp else 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color(0xFFF4EEDF),
                    modifier = Modifier.fillMaxWidth().testTag("tag_opening_title"),
                )
                Spacer(Modifier.height(14.dp))
                if (!introFinished) {
                    Text(
                        text = strings[Key.OPENING_TAP_START_HINT],
                        style = MaterialTheme.typography.bodyLarge,
                        letterSpacing = 2.sp,
                        color = Color(0x99F4EEDF),
                        modifier = Modifier.testTag("tag_opening_tap_hint"),
                    )
                }
            }
        }

        // ---- 第二次轻点：直接开始（作者要求去掉协议勾选与“开始游戏”按钮）
        if (introFinished) {
            LaunchedEffect(Unit) { onAcknowledge() }
        }
    }
}

@Composable
private fun BannerBar(text: String, dismissLabel: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = Tokens.PageMargin.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

/** 把文案里的 `**重点**` 解析成加粗片段（语气词强调用）。 */
private fun emphasized(text: String): androidx.compose.ui.text.AnnotatedString {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    var index = 0
    while (index < text.length) {
        val start = text.indexOf("**", index)
        if (start < 0) {
            builder.append(text.substring(index))
            break
        }
        val end = text.indexOf("**", start + 2)
        if (end < 0) {
            builder.append(text.substring(index))
            break
        }
        builder.append(text.substring(index, start))
        val bold = androidx.compose.ui.text.AnnotatedString(
            text.substring(start + 2, end),
            androidx.compose.ui.text.SpanStyle(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
        )
        builder.append(bold)
        index = end + 2
    }
    return builder.toAnnotatedString()
}
