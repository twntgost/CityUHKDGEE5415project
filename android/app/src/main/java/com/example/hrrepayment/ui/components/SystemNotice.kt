package com.example.hrrepayment.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 系统提示框（作者 2026-10-11 选定的混用方案）。
 *
 * 语义 → 外观：**警告用 A 标签条，普通提示用 B 气泡卡片**。
 *
 * 两套刻意共用同一组度量，读起来像一个系统而不是两种风格：
 *  - 同宽（内容宽度的 [WIDTH_FRACTION]，且不超过 [MAX_WIDTH]）——左右留白，不铺满屏；
 *  - 同内边距 14dp、同圆角 [Tokens.CornerTile]、同底色 surfaceContainerLow；
 *  - 同字号：标题 [AppType.NoticeTitle]（13sp / 字距 1.6）、正文 [AppType.NoticeBody]
 *    （15sp / Medium）、脚注 [AppType.NoticeBodyCompact]（14sp）；
 *  - 只有“强调件”不同：A 是左侧 5dp 强调条，B 是 30dp 圆形图标底；
 *  - 入场都走 [Tokens.NOTICE_MS]（200ms），每次内容变化只播一次。
 *
 * 颜色沿用全局令牌：天蓝 = 信息、绿 = 成功、红 = 警告，底色与其它卡片一致（卡其）。
 */
enum class NoticeStyle {
    /** A. 标签条：左侧强调竖条 + 宽字距标签，从左侧滑入。用于警告。 */
    TAGGED_STRIP,

    /** B. 气泡卡片：圆形强调图标底 + 卡片，弹簧弹出。用于普通提示。 */
    BUBBLE_CARD,

    /** C. 终端条：深色等宽条。已实现但当前未挂到界面（备选）。 */
    TERMINAL_STRIP,
    ;

    companion object {
        /** 作者选定的语义映射：警告用 A，其余用 B。 */
        fun defaultFor(tone: NoticeTone): NoticeStyle =
            if (tone == NoticeTone.DANGER) TAGGED_STRIP else BUBBLE_CARD
    }
}

/** Semantic colour of a notice; every scheme maps it onto its own accent. */
enum class NoticeTone { INFO, SUCCESS, DANGER }

/** 提示框占内容宽度的比例：留白比铺满更容易读。 */
private const val WIDTH_FRACTION = 0.92f
private val MAX_WIDTH = 340.dp

@Composable
fun SystemNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = GameIcons.News,
    tone: NoticeTone = NoticeTone.INFO,
    footer: String? = null,
    style: NoticeStyle = NoticeStyle.defaultFor(tone),
    testTag: String? = null,
) {
    val tag = if (testTag != null) Modifier.semantics { this.testTag = testTag } else Modifier

    // Entrance plays once per distinct message; a recomposition of the same message
    // must not replay it (panels recompose on every command).
    val progress = remember { Animatable(0f) }
    LaunchedEffect(title, body, footer) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(Tokens.NOTICE_MS))
    }
    val p = progress.value

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val inner = Modifier
            .fillMaxWidth(WIDTH_FRACTION)
            .widthIn(max = MAX_WIDTH)
        when (style) {
            NoticeStyle.TAGGED_STRIP -> TaggedStrip(title, body, footer, icon, tone, p, inner.then(tag))
            NoticeStyle.BUBBLE_CARD -> BubbleCard(title, body, footer, icon, tone, p, inner.then(tag))
            NoticeStyle.TERMINAL_STRIP -> TerminalStrip(title, body, footer, icon, tone, p, inner.then(tag))
        }
    }
}

// ---------------------------------------------------------------- 方案 A（警告）

@Composable
private fun TaggedStrip(
    title: String,
    body: String,
    footer: String?,
    icon: ImageVector,
    tone: NoticeTone,
    progress: Float,
    modifier: Modifier,
) {
    val accent = accentOf(tone)
    Row(
        modifier
            .alpha(progress)
            .padding(start = ((1f - progress) * 18f).dp, end = 0.dp)
            .clip(RoundedCornerShape(Tokens.CornerTile.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(Tokens.CornerTile.dp)),
    ) {
        Box(Modifier.width(5.dp).height(Int.MAX_VALUE.dp).background(accent))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            NoticeHeading(title, icon, accent)
            Spacer(Modifier.height(6.dp))
            Text(body, style = AppType.NoticeBody, color = MaterialTheme.colorScheme.onSurface)
            NoticeFooter(footer)
        }
    }
}

// ------------------------------------------------------------ 方案 B（普通提示）

@Composable
private fun BubbleCard(
    title: String,
    body: String,
    footer: String?,
    icon: ImageVector,
    tone: NoticeTone,
    progress: Float,
    modifier: Modifier,
) {
    val accent = accentOf(tone)
    Row(
        modifier
            .scale(0.94f + 0.06f * progress)
            .alpha(progress)
            .clip(RoundedCornerShape(Tokens.CornerTile.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(Tokens.CornerTile.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Tokens.OnPrimary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            NoticeHeading(title, icon = null, accent = accent)
            Spacer(Modifier.height(5.dp))
            Text(body, style = AppType.NoticeBody, color = MaterialTheme.colorScheme.onSurface)
            NoticeFooter(footer)
        }
    }
}

// ------------------------------------------------------- 方案 C（备选，未挂界面）

@Composable
private fun TerminalStrip(
    title: String,
    body: String,
    footer: String?,
    icon: ImageVector,
    tone: NoticeTone,
    progress: Float,
    modifier: Modifier,
) {
    val accent = accentOf(tone)
    Column(
        modifier
            .clip(RoundedCornerShape(Tokens.CornerTile.dp))
            .background(Tokens.InverseSurface),
    ) {
        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(accent))
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(title.uppercase(), style = AppType.NoticeMonoTitle, color = accent)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = AppType.NoticeMono,
                color = Tokens.InverseOnSurface,
                modifier = Modifier.alpha(0.35f + 0.65f * progress),
            )
            footer?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = AppType.NoticeMonoTitle, color = Tokens.InverseOnSurface.copy(alpha = 0.72f))
            }
        }
    }
}

// --------------------------------------------------------------------- 共用件

/** 同一枚标题：A 与 B 用完全相同的字体、字号与字距。 */
@Composable
private fun NoticeHeading(title: String, icon: ImageVector?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = title.uppercase(),
            style = AppType.NoticeTitle,
            color = accent,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun NoticeFooter(footer: String?) {
    if (footer == null) return
    Spacer(Modifier.height(6.dp))
    Text(footer, style = AppType.NoticeBodyCompact, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun accentOf(tone: NoticeTone): Color = when (tone) {
    NoticeTone.INFO -> MaterialTheme.colorScheme.primary
    NoticeTone.SUCCESS -> Tokens.Success
    NoticeTone.DANGER -> MaterialTheme.colorScheme.error
}

/**
 * Preview helper: the two shipped pairings plus the unused third scheme, so the
 * screenshot suite always reflects the current mapping.
 */
@Composable
fun SystemNoticeGallery(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("警告 → A 标签条", style = MaterialTheme.typography.labelLarge)
        SystemNotice(
            title = "提醒",
            body = "已经 3 个月没运动了。本月再忽略健康，多巴胺会下降 1 点。",
            footer = "技能面板 · 每月一次",
            icon = GameIcons.Warning,
            tone = NoticeTone.DANGER,
        )
        Text("普通提示 → B 气泡卡片", style = MaterialTheme.typography.labelLarge)
        SystemNotice(
            title = "今日",
            body = "今天的两份简历已经送到。看仔细一点。",
            icon = GameIcons.News,
            tone = NoticeTone.INFO,
        )
        Text("成功提示 → B 气泡卡片（绿）", style = MaterialTheme.typography.labelLarge)
        SystemNotice(
            title = "上次结果",
            body = "审阅判断正确，推送概率提高25个百分点。",
            footer = "累计成功 3 · 累计失败 0",
            icon = GameIcons.Check,
            tone = NoticeTone.SUCCESS,
        )
        Text("备选 C 终端条（未挂到界面）", style = MaterialTheme.typography.labelLarge)
        SystemNotice(
            title = "系统提示",
            body = "审阅判断正确，推送概率提高25个百分点。",
            footer = "累计成功 3 · 累计失败 0",
            icon = GameIcons.Check,
            tone = NoticeTone.SUCCESS,
            style = NoticeStyle.TERMINAL_STRIP,
        )
    }
}
