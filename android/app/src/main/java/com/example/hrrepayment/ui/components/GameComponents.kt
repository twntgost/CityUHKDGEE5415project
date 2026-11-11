package com.example.hrrepayment.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.Tokens

// ---------------------------------------------------------------- containers

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Tokens.CellGap.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

/** A flat, shadow-free surface: the design forbids elevation on content panels. */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    corner: Dp = Tokens.CornerCard.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(corner))
            .padding(Tokens.BlockGap.dp),
    ) { content() }
}

// ------------------------------------------------------------------- buttons

/**
 * Button hierarchy (v2).
 *
 *  - [FILLED] — sky blue with white text: the single most important action on a screen.
 *  - [TONAL] — khaki container: equally available, visibly secondary.
 *  - [SKY_SOFT] — pale sky plate with deep blue text: in-place actions such as
 *    “核对资料 / 参与竞猜”, so they read as interactive without competing with FILLED.
 *  - [OUTLINED] — hairline outline: navigation (“返回”).
 */
enum class ButtonTone { FILLED, TONAL, SKY_SOFT, OUTLINED }

/**
 * The one button used everywhere on the menu and the panels.
 *
 * The press feedback is a 90ms scale/colour change — a bounded, allocation-free
 * animation that gives an obvious “selected” feel without a heavyweight ripple.
 */
@Composable
fun GameButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ButtonTone = ButtonTone.FILLED,
    icon: ImageVector? = null,
    danger: Boolean = false,
    testTag: String? = null,
    /**
     * 面板级主按钮：宽度有上限（不再拉满整屏），文字用更大字号与更宽字距。
     * 传了 `fillMaxWidth()` 的调用方请用 `wide = true`，并把所在 Column 设为居中。
     */
    wide: Boolean = false,
    /**
     * 视觉上禁用、但仍然吃点击：用于“未勾选协议时点开始游戏”——按钮灰着，
     * 但点下去会触发 [onLockedClick]（让协议红字跳动），而不是静默无反应。
     */
    locked: Boolean = false,
    onLockedClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(Tokens.PRESS_MS),
        label = "buttonScale",
    )
    val tag = if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier
    val height = Tokens.ButtonHeight.dp
    val shape = RoundedCornerShape(height / 2)
    val sized = if (wide) {
        modifier.widthIn(max = Tokens.ButtonMaxWidthWide.dp).then(tag)
    } else {
        modifier.then(tag)
    }
    val label: @Composable () -> Unit = { ButtonLabel(text, icon, wide = wide) }
    val appearanceEnabled = enabled && !locked

    if (locked && onLockedClick != null) {
        // 灰显但可点：不吃命令，只反馈“先去勾选协议”。
        // 语义上必须仍然报“已禁用”，否则 TalkBack 会把它念成可点按钮。
        Box {
            LockedButton(text, icon, tone, danger, sized.semantics { disabled() }, height, shape, wide)
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onLockedClick() },
            )
        }
        return
    }

    when (tone) {
        ButtonTone.FILLED -> Button(
            onClick = onClick,
            modifier = sized.height(height).scale(scale),
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (danger) Tokens.Error else MaterialTheme.colorScheme.primary,
                contentColor = if (danger) Tokens.OnError else MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            enabled = appearanceEnabled,
        ) { label() }

        ButtonTone.TONAL -> Button(
            onClick = onClick,
            modifier = sized.height(height).scale(scale),
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            enabled = appearanceEnabled,
        ) { label() }

        ButtonTone.SKY_SOFT -> Button(
            onClick = onClick,
            modifier = sized.height(height).scale(scale),
            shape = shape,
            interactionSource = interaction,
            colors = ButtonDefaults.buttonColors(
                containerColor = Tokens.SkySoft,
                contentColor = Tokens.SkyDeep,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            enabled = appearanceEnabled,
        ) { label() }

        ButtonTone.OUTLINED -> OutlinedButton(
            onClick = onClick,
            modifier = sized.height(height).scale(scale),
            shape = shape,
            interactionSource = interaction,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            enabled = appearanceEnabled,
        ) { label() }
    }
}

/**
 * 只画外观、不接受命令的“锁定态”按钮：容器与文字都用禁用色。
 * 点击由外层透明层接管，用来触发协议提示的抖动。
 */
@Composable
private fun LockedButton(
    text: String,
    icon: ImageVector?,
    tone: ButtonTone,
    danger: Boolean,
    modifier: Modifier,
    height: androidx.compose.ui.unit.Dp,
    shape: androidx.compose.ui.graphics.Shape,
    wide: Boolean,
) {
    // 锁定态**不按语气配色**：三个按钮长相一致，只有顺序体现主次。
    // 之前 FILLED 是淡蓝、TONAL 是实心卡其，结果“读取游戏”看着比“新建游戏”更像是
    // 主按钮——锁定状态把主次关系整个翻转了（作者反馈的问题）。
    val container = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Box(
        modifier = modifier.height(height).clip(shape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (wide) 10.dp else 6.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (wide) 20.dp else 18.dp),
                )
            }
            Text(
                text = text,
                style = if (wide) AppType.ButtonLabel else AppType.ButtonLabelCompact,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?, wide: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (wide) 10.dp else 6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (wide) 20.dp else 18.dp))
        }
        Text(
            text = text,
            style = if (wide) AppType.ButtonLabel else AppType.ButtonLabelCompact,
            textAlign = TextAlign.Center,
            // 按钮高度是固定的，标签一旦换行就会被裁掉（实机上“确认还款”被切字）。
            // 强制单行 + 超出省略，宁可省略也不吞字。
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

// -------------------------------------------------------------- energy cells

/**
 * A dense strip of energy cells: one cell per point.
 *
 * This replaces bare numbers in the attribute panel. Values above [reference] are
 * shown as “+n” so an unbounded attribute (work ability) never draws an unbounded
 * row; the strip itself is a plain Row of small Boxes, which keeps allocation per
 * frame at zero once composed.
 */
@Composable
fun EnergyStrip(
    value: Int,
    reference: Int,
    modifier: Modifier = Modifier,
    cellWidth: Dp = Tokens.CELL_WIDTH.dp,
    cellHeight: Dp = Tokens.CELL_HEIGHT.dp,
    spacing: Dp = Tokens.CELL_SPACING.dp,
    filled: Color = MaterialTheme.colorScheme.primary,
    empty: Color = MaterialTheme.colorScheme.outlineVariant,
    testTag: String? = null,
) {
    val visible = value.coerceIn(0, reference)
    val tag = if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier
    Row(
        modifier = modifier.then(tag),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(reference) { index ->
            val on = index < visible
            val color by animateColorAsState(
                targetValue = if (on) filled else empty,
                animationSpec = tween(Tokens.SELECT_MS),
                label = "cell$index",
            )
            Box(
                Modifier
                    .width(cellWidth)
                    .height(cellHeight)
                    .background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}

/**
 * One attribute line: icon, localized label, dense energy strip, and the exact value.
 *
 * `reference` is the strip length; the trailing “+n” keeps the number visible for
 * attribute values that exceed the strip (work ability above 50, for example).
 */
@Composable
fun AttributeRow(
    icon: ImageVector,
    label: String,
    value: Int,
    reference: Int,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    testTag: String? = null,
) {
    val color = if (danger) Tokens.Error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (danger) Tokens.Error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        EnergyStrip(
            value = value,
            reference = reference,
            filled = if (danger) Tokens.Error else MaterialTheme.colorScheme.primary,
            testTag = testTag,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (value > reference) "$value (+${value - reference})" else value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// -------------------------------------------------------------------- misc

@Composable
fun PillLabel(text: String, modifier: Modifier = Modifier, container: Color = MaterialTheme.colorScheme.surfaceContainerHigh) {
    Box(
        modifier = modifier
            .background(container, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Thin divider that matches the flat poster look (no shadows, one hairline). */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * The far-left of the workbench header: a small text button that matches the poster
 * style. Used for 状态 (status), 日历 (news) and 面试档案袋 (dossiers).
 */
@Composable
fun HeaderAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    /** 牛皮纸质感（档案袋入口用），默认是天蓝药丸。 */
    paperTexture: Boolean = false,
) {
    val tag = if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier
    val kraft = if (paperTexture) rememberTiledBrush(MaterialAssets.KRAFT) else null
    // 以前是纯文字按钮，在卡其底上几乎“浮着”。改成浅蓝天蓝药丸底 + 深蓝字，
    // 与整体天蓝点缀一致（作者：蓝色无轮廓按钮要更蓝、更协调）。
    Box(
        modifier = modifier
            .then(tag)
            // 作者要求：**原先无边框的按钮保持无边框**。这里只给一层浅蓝底提高
            // 可读性与协调度，不加描边；高度与其它控制件对齐（40dp 命中高度）。
            .clip(RoundedCornerShape(if (paperTexture) 10.dp else 50.dp))
            .then(
                if (kraft != null) Modifier.background(kraft)
                else Modifier.background(if (paperTexture) Tokens.KraftFallbackColor else Tokens.SkySoft),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 顶栏按钮压矮一档：部分机型状态栏较高，40dp 时容易被压住
            .heightIn(min = 36.dp)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (paperTexture) Tokens.InkInk else Tokens.SkyDeep,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (paperTexture) Tokens.InkInk else Tokens.SkyDeep,
            // 顶栏标签永不换行：英文 “Calendar” 曾被挤成竖排的字母塔。
            maxLines = 1,
            softWrap = false,
        )
        }
    }
}

/** A bordered “field” row used by the dossier and status panels. */
@Composable
fun OutlinedBlock(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Tokens.CornerCard.dp))
            .padding(12.dp),
    ) { content() }
}


// ------------------------------------------------------------- widget 元素

/**
 * A small “widget” tile: icon plate + label + value.
 *
 * The status / economy panels mix [StatTile]s in rows and columns instead of one
 * long top-aligned list, which is what the author asked for (“像手机UI小组件一样
 * 有横有竖排列”).
 */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasised: Boolean = false,
    testTag: String? = null,
    /** 金额专用样式：墨绿 + 数字字体（与普通数值区分）。 */
    money: Boolean = false,
) {
    val tag = if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier
    Column(
        modifier
            .then(tag)
            .clip(RoundedCornerShape(Tokens.CornerTile.dp))
            .background(
                if (emphasised) Tokens.SkySoft else MaterialTheme.colorScheme.surfaceContainerLow,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (money) Tokens.MoneyInk else valueColor,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A small circular sky plate; used for headers and inline actions. */
@Composable
fun IconPlate(icon: ImageVector, modifier: Modifier = Modifier, container: Color = Tokens.SkySoft) {
    Box(
        modifier
            .size(30.dp)
            .clip(RoundedCornerShape(50))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** A hairline that fades from sky to transparent — a cheap “design” accent. */
@Composable
fun SkyRule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Tokens.SkyAccent, Tokens.SkyAccent.copy(alpha = 0f)),
                ),
            ),
    )
}


/**
 * 换页提示：两个小胶囊 + “1 / 2”。
 *
 * Placed **below** the resume carousel (the author asked for the indicator to move
 * down), and it doubles as the hint that the two resumes are swipeable.
 */
@Composable
fun PageIndicator(current: Int, total: Int, modifier: Modifier = Modifier) {
    val strings = com.example.hrrepayment.ui.compositionlocals.LocalStrings.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .semantics { testTag = "tag_pager_position" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(total) { index ->
                val on = index == current
                Box(
                    Modifier
                        .height(6.dp)
                        .width(if (on) 18.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
        Text(
            text = strings.format(com.example.hrrepayment.core.i18n.Key.PAGE_OF, current + 1, total),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
