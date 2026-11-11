package com.example.hrrepayment.ui.components

import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens
import kotlinx.coroutines.delay

/**
 * 设置弹窗（齿轮入口）。
 *
 * 交互与视觉：
 *  - 每个选项是**有轮廓的按钮**（1dp 描边 + 圆角 12dp + 纸面底色），不再是纯文字行；
 *  - 打开时选项**逐个下滑入场**（每个延迟 40ms，slideInVertically + fadeIn），
 *    有层次感且不会一次性闪出来；
 *  - 点弹窗以外任意位置关闭（Dialog dismissedOnClickOutside + 全屏遮罩兜底）。
 */
@Composable
fun SettingsDialog(
    strings: Strings,
    soundEnabled: Boolean,
    busy: Boolean,
    onToggleSound: (Boolean) -> Unit,
    onSwitchLanguage: () -> Unit,
    onSaveNow: () -> Unit,
    onLoadNow: () -> Unit,
    onReturnMenu: () -> Unit,
    onDismiss: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    // 面板整体：从 0.92 倍、下移 14dp 的位置弹性缓出，避免"啪一下弹出来"的僵硬感
    // 只有面板这一层做动画：单节点 offset+alpha，tween 200ms。
    // 之前每行各跑一个 spring，6 个 AnimatedVisibility 每帧重新布局，
    // 在软件渲染的模拟器上会明显掉帧（作者反馈“非常卡顿”）。
    val appear = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "settingsPanelAppear",
    )
    LaunchedEffect(Unit) { entered = true }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() }
                .testTag("tag_settings_scrim"),
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(
                        BorderStroke(1.25.dp, Tokens.Secondary.copy(alpha = 0.35f)),
                        RoundedCornerShape(Tokens.CornerPanel.dp),
                    )
                    .offset { androidx.compose.ui.unit.IntOffset(0, (12.dp * (1f - appear.value)).roundToPx()) }
                    .alpha(appear.value)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .testTag("tag_settings_panel"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        GameIcons.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings[Key.SETTINGS_TITLE],
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(2.dp))

                // 音效开关：同样做成带轮廓的整行按钮
                SettingsOutlineRow(
                    index = 0,
                    entered = entered,
                    tag = "tag_settings_sound_row",
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(GameIcons.Luck, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = strings[Key.SETTINGS_SOUND],
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f).testTag("tag_settings_sound_label"),
                        )
                        Text(
                            text = if (soundEnabled) strings[Key.SETTINGS_SOUND_ON] else strings[Key.SETTINGS_SOUND_OFF],
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = onToggleSound,
                            enabled = !busy,
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("tag_settings_sound_switch"),
                        )
                    }
                }
                SettingsOutlineRow(index = 1, entered = entered, tag = "tag_settings_language",
                    onClick = { onSwitchLanguage(); onDismiss() },
                    icon = GameIcons.Dossier, label = strings[Key.SETTINGS_LANGUAGE]) {}
                SettingsOutlineRow(index = 2, entered = entered, tag = "tag_settings_save",
                    onClick = { onSaveNow(); onDismiss() },
                    icon = GameIcons.Check, label = strings[Key.SETTINGS_SAVE], enabled = !busy) {}
                SettingsOutlineRow(index = 3, entered = entered, tag = "tag_settings_load",
                    onClick = { onLoadNow(); onDismiss() },
                    icon = GameIcons.Dossier, label = strings[Key.SETTINGS_LOAD], enabled = !busy) {}
                SettingsOutlineRow(
                    index = 4,
                    entered = entered,
                    tag = "tag_settings_home",
                    icon = GameIcons.Warning,
                    label = strings[Key.SETTINGS_HOME],
                    enabled = true,
                    danger = true,
                ) {}
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings[Key.SETTINGS_CLOSE],
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable { onDismiss() }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .testTag("tag_settings_close"),
                )
            }
        }
    }
}

/** 一条带轮廓的选项按钮；打开时按 index 依次下滑入场。 */
@Composable
private fun SettingsOutlineRow(
    index: Int,
    entered: Boolean,
    tag: String,
    icon: ImageVector? = null,
    label: String? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // 行本身不做入场动画：面板统一缓存入场，避免 6 个独立动画同帧重排。
    run {
        val visible = true
        if (visible) {
        val stroke = when {
            danger -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else -> Tokens.Secondary.copy(alpha = 0.4f)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(BorderStroke(1.2.dp, stroke), RoundedCornerShape(50))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag(tag),
        ) {
            if (label != null && icon != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                content()
            }
        }
        }
    }
}
