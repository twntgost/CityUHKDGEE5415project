package com.example.hrrepayment.ui.menu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.core.i18n.saveSlotStatus
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.save.SaveSlotSummary
import com.example.hrrepayment.presentation.MenuPanel
import com.example.hrrepayment.ui.components.ButtonTone
import com.example.hrrepayment.ui.components.GameButton
import androidx.compose.ui.graphics.graphicsLayer
import com.example.hrrepayment.ui.components.MaterialAssets
import com.example.hrrepayment.ui.components.rememberTiledBrush
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import com.example.hrrepayment.ui.components.rememberAssetImage
import com.example.hrrepayment.ui.components.safeTopInset
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens
import kotlinx.coroutines.launch

/**
 * 开场菜单（m3e-design 五个屏幕：开始界面 / 新建游戏 / 读取游戏 / Start (English) / 用户须知）。
 *
 * Behaviour that matters:
 *  - the three主按钮永远占用同一组垂直槽位：切面板时下方两个先向上叠到第一个上淡出，
 *    再带着新文案展开（约 350ms 弹簧动效），面板切换不会让按钮跳动；
 *  - 未勾选用户须知时，点《用户须知》文字会变红并左右抖动 300ms，且不打开面板；
 *  - 左下 👥 是**真的入口**（面试档案袋），右下 💰 只是极淡的背景装饰。
 */
@Composable
fun MenuScreen(
    strings: Strings,
    lang: Lang,
    panel: MenuPanel,
    slots: List<SaveSlotSummary>,
    busy: Boolean,
    onPanel: (MenuPanel) -> Unit,
    onNewGame: (SaveSlot) -> Unit,
    onLoadGame: (SaveSlot) -> Unit,
    onSwitchLanguage: () -> Unit,
    onOpenDossiers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 勾选状态在旋转/重建后保留；每次冷启动仍默认未勾选。
    var consent by rememberSaveable { mutableStateOf(false) }
    var noticeOpen by rememberSaveable { mutableStateOf(false) }

    // 未勾选时点“新建/读取游戏”：不解禁、不跳转，只让协议红字抖一下。
    val shakeX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun shakeHint() {
        scope.launch {
            val steps = listOf(0f, -7f, 7f, -5f, 5f, -2f, 0f)
            for (step in steps) shakeX.animateTo(step, tween(45))
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeTopInset()
                .navigationBarsPadding()
                .padding(horizontal = Tokens.PageMargin.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PosterBand(lang = lang, strings = strings)
            Spacer(Modifier.height(28.dp))
            SlotColumn(
                strings = strings,
                panel = panel,
                slots = slots,
                busy = busy,
                consent = consent,
                onLockedClick = { shakeHint() },
                onPanel = onPanel,
                onNewGame = onNewGame,
                onLoadGame = onLoadGame,
                onSwitchLanguage = onSwitchLanguage,
            )

            // 「为什么点不动 + 怎么解锁」必须是一整块：
            // 按钮 → 红字（紧贴）→ 勾选控件，间距从 20dp 收到 8dp，
            // 之前红字和勾选框之间空了 130px，读起来是两件不相干的事。
            // 提示行**始终占位**（勾选与否高度一致），只切换透明度——
            // 这样勾选前后三个按钮不会上下跳动（作者反馈的“浮窗位置改变”）。
            Spacer(Modifier.height(12.dp))
            Text(
                text = strings[Key.MENU_CONSENT_HINT],
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = shakeX.value.dp)
                    .alpha(if (consent) 0f else 1f)
                    .testTag("tag_consent_hint"),
            )

            Spacer(Modifier.height(8.dp))
            ConsentRow(
                strings = strings,
                consent = consent,
                onToggle = { consent = it },
                // 条款必须随时可读：未勾选也打开面板，读完点“我已知晓”即视为同意。
                onOpenNotice = { noticeOpen = true },
            )
        }

        // 底部信息条：左边是面试档案袋入口（有底色的药丸，不再是一个飘着的裸图标），
        // 右边是版本号。原来页面下方三分之一全空、入口还和钱袋暗纹叠在一起。
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Tokens.PageMargin.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DossierEntryButton(
                label = strings[Key.MENU_TROPHY],
                onClick = onOpenDossiers,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = strings[Key.MENU_VERSION],
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag("tag_menu_version"),
            )
        }
    }

    NoticePanel(
        visible = noticeOpen,
        strings = strings,
        onAcknowledge = {
            noticeOpen = false
            consent = true
        },
        onDismiss = { noticeOpen = false },
    )
}

// ------------------------------------------------------------------ 海报色带

@Composable
private fun PosterBand(lang: Lang, strings: Strings) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        // 海报背景：印刷海报质感（暖卡其渐变 + 中心柔光 + 极淡横纹 + 细边框），
        // 不再是牛皮纸纹理（作者反馈：开始界面的图形不应该是牛皮纸）。
        Box(
            Modifier
                .matchParentSize()
                .drawBehind { drawPosterBackdrop() },
        )
        if (lang == Lang.ZH) {
            val line = strings[Key.MENU_TITLE_1]
            val tail = line.takeLast(1)
            val head = line.dropLast(1)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = buildAnnotatedString {
                        append(head)
                        withStyle(
                            SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            ),
                        ) { append(tail) }
                    },
                    style = AppType.PosterTitle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = strings[Key.MENU_TITLE_2],
                    style = AppType.PosterTitle,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = strings[Key.MENU_TITLE_1],
                    style = AppType.PosterTitle.copy(fontSize = 38.sp, lineHeight = 46.sp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = strings[Key.MENU_TITLE_2],
                    style = AppType.PosterTitle.copy(fontSize = 38.sp, lineHeight = 46.sp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 按钮槽位

private data class SlotSpec(
    val label: String,
    val caption: String?,
    val enabled: Boolean,
    val tone: ButtonTone,
    val onClick: () -> Unit,
    val testTag: String,
    /** 未满足前置条件：灰显但仍可点，点下去只抖一下提示。 */
    val locked: Boolean = false,
)

/**
 * The three fixed button slots.
 *
 * Collapse choreography: on a panel change the lower two slots travel up onto the
 * first one while fading out, the labels swap, then they travel back down — all
 * inside one spring animation, so the slots never jump.
 */
@Composable
private fun SlotColumn(
    strings: Strings,
    panel: MenuPanel,
    slots: List<SaveSlotSummary>,
    busy: Boolean,
    consent: Boolean,
    onLockedClick: () -> Unit,
    onPanel: (MenuPanel) -> Unit,
    onNewGame: (SaveSlot) -> Unit,
    onLoadGame: (SaveSlot) -> Unit,
    onSwitchLanguage: () -> Unit,
) {
    fun slotCaption(summary: SaveSlotSummary): String {
        val loaded = summary.loadedOrNull
            ?: return strings[Key.MENU_SLOT_EMPTY]
        val state = loaded.state
        val progress = if (state.phase == Phase.Ended) {
            strings[Key.MENU_SLOT_FINISHED]
        } else {
            strings.format(
                Key.MENU_SLOT_PROGRESS,
                state.position.current.monthIndex + 1,
                state.position.current.dayOfMonth + 1,
            )
        }
        return listOfNotNull(progress, strings.saveSlotStatus(state)).joinToString("\n")
    }

    // 进入游戏的一切路径都要求先勾选须知；只有“切换语言”保持可用，
    // 否则非中文玩家无法先把界面切成英文再读条款。
    val mayEnterGame = !busy && consent
    val specs: List<SlotSpec> = when (panel) {
        MenuPanel.START -> listOf(
            SlotSpec(strings[Key.MENU_NEW_GAME], null, mayEnterGame, ButtonTone.FILLED, { onPanel(MenuPanel.NEW_GAME) }, "tag_menu_new", locked = !mayEnterGame),
            SlotSpec(strings[Key.MENU_LOAD_GAME], null, mayEnterGame, ButtonTone.TONAL, { onPanel(MenuPanel.LOAD_GAME) }, "tag_menu_load", locked = !mayEnterGame),
            SlotSpec(strings[Key.MENU_SWITCH_LANGUAGE], null, !busy, ButtonTone.OUTLINED, onSwitchLanguage, "tag_menu_lang"),
        )
        MenuPanel.NEW_GAME -> listOf(
            SlotSpec(strings[Key.MENU_BACK], null, !busy, ButtonTone.OUTLINED, { onPanel(MenuPanel.START) }, "tag_menu_back"),
            SlotSpec(strings.format(Key.MENU_SLOT_OVERWRITE, 1), slotCaption(slots[0]), mayEnterGame, ButtonTone.FILLED, { onNewGame(SaveSlot.SLOT_1) }, "tag_new_slot1", locked = !mayEnterGame),
            SlotSpec(strings.format(Key.MENU_SLOT_OVERWRITE, 2), slotCaption(slots[1]), mayEnterGame, ButtonTone.TONAL, { onNewGame(SaveSlot.SLOT_2) }, "tag_new_slot2", locked = !mayEnterGame),
        )
        MenuPanel.LOAD_GAME -> listOf(
            SlotSpec(strings[Key.MENU_BACK], null, !busy, ButtonTone.OUTLINED, { onPanel(MenuPanel.START) }, "tag_menu_back"),
            SlotSpec(
                strings.format(Key.MENU_SLOT_LOAD, 1), slotCaption(slots[0]),
                mayEnterGame && slots[0].isPlayable, ButtonTone.FILLED,
                { onLoadGame(SaveSlot.SLOT_1) }, "tag_load_slot1",
            ),
            SlotSpec(
                strings.format(Key.MENU_SLOT_LOAD, 2), slotCaption(slots[1]),
                mayEnterGame && slots[1].isPlayable, ButtonTone.TONAL,
                { onLoadGame(SaveSlot.SLOT_2) }, "tag_load_slot2",
            ),
        )
    }

    // Collapse progress: 1 = the lower two buttons have folded onto the first one.
    val collapse = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(panel) }
    LaunchedEffect(panel) {
        if (panel != shown) {
            collapse.animateTo(1f, tween(120))
            shown = panel
            collapse.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val slotHeight = Tokens.ButtonHeight.dp
    val gap = 12.dp

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        specs.forEachIndexed { index, spec ->
            val travel = if (index == 0) 0f else -(index * (slotHeight + gap).value) * collapse.value
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = travel.dp)
                    .alpha(if (index == 0) 1f else 1f - collapse.value),
            ) {
                GameButton(
                    text = spec.label,
                    onClick = spec.onClick,
                    enabled = spec.enabled,
                    tone = spec.tone,
                    modifier = Modifier.width(Tokens.ButtonWidth.dp),
                    testTag = spec.testTag,
                    locked = spec.locked,
                    onLockedClick = onLockedClick,
                )
                if (spec.caption != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = spec.caption,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = Tokens.ButtonWidth.dp),
                    )
                }
            }
            if (index < specs.lastIndex) Spacer(Modifier.height(gap))
        }
    }
}

// ------------------------------------------------------------------ 勾选行

@Composable
private fun ConsentRow(
    strings: Strings,
    consent: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenNotice: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onToggle(!consent) }
                .testTag("tag_consent_toggle"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        if (consent) MaterialTheme.colorScheme.primary else Color.Transparent,
                        RoundedCornerShape(2.dp),
                    )
                    .border(
                        1.dp,
                        if (consent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(2.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (consent) {
                    Icon(
                        GameIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onOpenNotice() }
                .padding(vertical = 12.dp, horizontal = 4.dp)
                .testTag("tag_consent_notice"),
        ) {
            Icon(
                GameIcons.Dossier,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = strings[Key.MENU_TERMS],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

// ------------------------------------------------------------------ 用户须知

@Composable
private fun NoticePanel(
    visible: Boolean,
    strings: Strings,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(120)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it },
                exit = slideOutVertically(tween(180)) { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.82f)
                        .widthIn(max = 380.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(topStart = Tokens.CornerPanel.dp, topEnd = Tokens.CornerPanel.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* swallow taps inside the sheet */ }
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .testTag("tag_notice_panel"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                    Text(
                        text = strings[Key.MENU_TERMS_TITLE],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    listOf(
                        Key.MENU_TERMS_1, Key.MENU_TERMS_2, Key.MENU_TERMS_3, Key.MENU_TERMS_4, Key.MENU_TERMS_5,
                    ).forEach { key ->
                        Text(
                            text = strings[key],
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    // ---- 关于这个项目：一张规整说明卡（左对齐，校徽与文字基线一致）
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Tokens.CornerTile.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        val logo = rememberAssetImage("brand/cityu_dongguan.png")
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (logo != null) {
                                Image(
                                    bitmap = logo,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .widthIn(max = 128.dp)
                                        .testTag("tag_about_logo"),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "EE5415",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = strings[Key.ABOUT_TITLE],
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = strings[Key.ABOUT_BODY],
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 19.sp,
                            modifier = Modifier.testTag("tag_about_body"),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings[Key.ABOUT_AUTHOR],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("tag_about_author"),
                        )
                        Text(
                            text = strings[Key.ABOUT_GITHUB],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .testTag("tag_about_github"),
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = strings[Key.ABOUT_STACK],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            modifier = Modifier.testTag("tag_about_stack"),
                        )
                        Text(
                            text = strings[Key.ABOUT_SIZE],
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .padding(top = 3.dp)
                                .testTag("tag_about_size"),
                        )
                    }
                    }
                    Spacer(Modifier.height(12.dp))
                    GameButton(
                        text = strings[Key.MENU_TERMS_ACK],
                        onClick = onAcknowledge,
                        modifier = Modifier.width(Tokens.ButtonWidth.dp),
                        testTag = "tag_notice_ack",
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------- 档案袋入口

@Composable
private fun DossierEntryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 档案袋入口做成**牛皮纸档案袋标签**：平铺纸纹 + 轻微的纸角折边
    val kraft = rememberTiledBrush(MaterialAssets.KRAFT)
    val pressed = remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (pressed.value) 0.96f else 1f
                scaleY = scaleX
                rotationZ = if (pressed.value) -1.2f else 0f
            }
            .clip(RoundedCornerShape(8.dp, 14.dp, 8.dp, 8.dp))
            .then(
                if (kraft != null) Modifier.background(kraft)
                else Modifier.background(Tokens.KraftFallbackColor),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                pressed.value = true
                onClick()
            }
            .heightIn(min = 42.dp)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .testTag("tag_menu_dossier"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            GameIcons.Dossier,
            contentDescription = null,
            tint = Tokens.InkInk,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Tokens.InkInk,
            maxLines = 1,
            softWrap = false,
        )
    }
    // 松手后回弹（一次性的按压动画）
    LaunchedEffect(pressed.value) {
        if (pressed.value) {
            kotlinx.coroutines.delay(140)
            pressed.value = false
        }
    }
}

/**
 * 开始菜单海报的印刷质感背景。
 *
 * 刻意**不用牛皮纸纹理**：海报要的是“印刷品”，靠渐层、柔光、极淡横纹和细边框
 * 营造厚度，而不是纸纤维。文字层不受影响（只画在背景层）。
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPosterBackdrop() {
    val w = size.width
    val h = size.height

    // 1) 暖卡其竖直渐变：上浅下深，像受光不均的印刷纸
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0xFFF2E9D6),
                androidx.compose.ui.graphics.Color(0xFFE4D6B8),
                androidx.compose.ui.graphics.Color(0xFFDCCBA9),
            ),
        ),
    )

    // 2) 中心偏上的柔光，把标题托起来
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0x66FFF8E8),
                androidx.compose.ui.graphics.Color(0x00FFF8E8),
            ),
            center = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.42f),
            radius = w * 0.62f,
        ),
        radius = w * 0.62f,
        center = androidx.compose.ui.geometry.Offset(w / 2f, h * 0.42f),
    )

    // 3) 极淡横纹：印刷网点/机刻感的替代，2.5dp 一条，透明度压到几乎看不见
    val gap = 2.5.dp.toPx()
    var y = 0f
    while (y < h) {
        drawLine(
            color = androidx.compose.ui.graphics.Color(0x0A5C4A2A),
            start = androidx.compose.ui.geometry.Offset(0f, y),
            end = androidx.compose.ui.geometry.Offset(w, y),
            strokeWidth = 1f,
        )
        y += gap
    }

    // 4) 四角轻微压暗，让海报像一张有厚度的纸
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(
                androidx.compose.ui.graphics.Color(0x00000000),
                androidx.compose.ui.graphics.Color(0x1A4A3A20),
            ),
            center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f),
            radius = size.maxDimension * 0.75f,
        ),
    )

    // 5) 内嵌细边框（双线，间距 6dp），像印刷品的压印边
    val inset = 10.dp.toPx()
    drawRoundRect(
        color = androidx.compose.ui.graphics.Color(0x66B89B62),
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(w - inset * 2, h - inset * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx()),
    )
    val inset2 = inset + 5.dp.toPx()
    drawRoundRect(
        color = androidx.compose.ui.graphics.Color(0x33B89B62),
        topLeft = androidx.compose.ui.geometry.Offset(inset2, inset2),
        size = androidx.compose.ui.geometry.Size(w - inset2 * 2, h - inset2 * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
    )
}
