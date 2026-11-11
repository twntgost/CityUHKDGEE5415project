package com.example.hrrepayment.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import com.example.hrrepayment.ui.components.MaterialAssets
import com.example.hrrepayment.ui.components.rememberAssetImage
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

/**
 * 推送结果弹窗。
 *
 * The author asked for: centred (not squeezed at the top), rounded, and a very short
 * stamp animation — “打勾打叉和衬线描这个弹窗的边”, all inside 0.2s.
 *
 * Implementation: one [Animatable] drives both a **stroke trace around the dialog
 * check / cross
 * mark**. 180ms total, no bounce, and the result text is already correct when the
 * animation ends.
 */
/** 弹窗上的印章图形：推送成功打勾、失败打叉、剧情事件用感叹号。 */
enum class StampKind { CHECK, CROSS, BANG }

@Composable
fun ResultStampDialog(
    success: Boolean,
    title: String,
    body: String,
    footer: String?,
    continueLabel: String,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    /** 印章图形；默认按 [success] 取勾或叉，剧情事件传 [StampKind.BANG]。 */
    stamp: StampKind = if (success) StampKind.CHECK else StampKind.CROSS,
    testTag: String = if (success) "tag_result_dialog_success" else "tag_result_dialog_failure",
    /** “继续”按钮的 tag；剧情事件沿用 tag_event_ack，保持既有契约。 */
    continueTestTag: String = "tag_result_continue",
    /** 成功时盖章的音效；失败/事件不传就不出声。 */
    onStampSound: () -> Unit = {},
) {
    val progress = remember(success, title) { Animatable(0f) }
    // 成功时才“盖章”：印章从上方砸下、轻微回弹，同时出声
    val stampIn = remember(success, title) { Animatable(0f) }
    // 每次成功随机一枚磨损变体：章的缺墨、飞白、边缘残缺都不一样
    val stampVariants = if (success) rememberAssetBitmaps(MaterialAssets.STAMP_VARIANTS) else emptyList()
    // 每一枚章的落章角度完全随机（0–360°），再加一点点落章抖动
    val stampRotation = remember(title) { kotlin.random.Random.nextFloat() * 360f }
    val stampBitmap = stampVariants.getOrNull(remember(title) { kotlin.random.Random.nextInt(5) }.coerceIn(0, 4))
    LaunchedEffect(success, title) {
        progress.snapTo(0f)
        stampIn.snapTo(0f)
        progress.animateTo(1f, tween(Tokens.STAMP_MS))
        if (success && stamp == StampKind.CHECK && stampBitmap != null) {
            onStampSound()
            stampIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium))
        }
    }
    val p = progress.value
    val accent = when (stamp) {
        StampKind.CHECK -> Tokens.Success
        StampKind.CROSS -> MaterialTheme.colorScheme.error
        StampKind.BANG -> MaterialTheme.colorScheme.primary
    }

    Dialog(
        onDismissRequest = { /* 必须显式点“继续”，防止手势穿透 */ },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val scale = remember { Animatable(0.94f) }
        LaunchedEffect(Unit) {
            scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium))
        }
        // 全屏容器：这样印章可以**同时压在卡片和背景上**（人工审核盖章的感觉）
        Box(Modifier.fillMaxSize().testTag("tag_result_dialog_scrim")) {
            // 卡片 + 骑缝章同组：章跨在卡片右下边缘，一半在弹窗上一半在背景上
            Box(
                modifier = modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.86f),
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale.value)
                    .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .semantics { this.testTag = testTag },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StampMark(kind = stamp, accent = accent, progress = p)
                Spacer(Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = body,
                    style = AppType.NoticeBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                if (footer != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = footer,
                        style = AppType.NoticeBodyCompact,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                GameButton(
                    text = continueLabel,
                    onClick = onContinue,
                    tone = ButtonTone.FILLED,
                    icon = GameIcons.Push,
                    modifier = Modifier.width(Tokens.ButtonWidth.dp),
                    testTag = continueTestTag,
                )
            }
            // 骑缝章只属于“推送成功”这一种弹窗：剧情事件用 success=true 只为配色，
            // 不能盖章（作者反馈：其他弹窗不应该有推送成功的图章）。
            val showPushSeal = success && stamp == StampKind.CHECK
            if (showPushSeal && stampBitmap != null && stampIn.value > 0f) {
                Image(
                    bitmap = stampBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 44.dp, y = 40.dp)
                        .size(112.dp)
                        .graphicsLayer {
                            alpha = stampIn.value.coerceIn(0f, 1f)
                            scaleX = 1.10f + (1f - stampIn.value) * 0.55f
                            scaleY = scaleX
                            rotationZ = stampRotation + (1f - stampIn.value) * 5f
                        }
                        .testTag("tag_push_stamp"),
                )
            }
            }
        }
    }
}

/** The mark itself: a check or a cross drawn by a moving stroke. */
@Composable
internal fun StampMark(kind: StampKind, accent: Color, progress: Float) {
    androidx.compose.foundation.Canvas(Modifier.size(72.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(color = accent.copy(alpha = 0.10f), radius = w * 0.46f)
        val path = Path()
        when (kind) {
            StampKind.CHECK -> {
                path.moveTo(w * 0.26f, h * 0.54f)
                path.lineTo(w * 0.44f, h * 0.71f)
                path.lineTo(w * 0.76f, h * 0.31f)
            }
            StampKind.CROSS -> {
                path.moveTo(w * 0.31f, h * 0.31f)
                path.lineTo(w * 0.69f, h * 0.69f)
                path.moveTo(w * 0.69f, h * 0.31f)
                path.lineTo(w * 0.31f, h * 0.69f)
            }
            StampKind.BANG -> {
                // 感叹号：竖画 + 点，事件弹窗用
                path.moveTo(w * 0.5f, h * 0.26f)
                path.lineTo(w * 0.5f, h * 0.6f)
                path.moveTo(w * 0.5f, h * 0.72f)
                path.lineTo(w * 0.5f, h * 0.74f)
            }
        }
        val measure = PathMeasure().apply { setPath(path, false) }
        val drawn = if (kind == StampKind.CHECK) 1 else 2
        // 叉与感叹号都是两笔：拆进度让两笔一起长出来。
        if (drawn == 1) {
            drawSegment(measure, 0f, measure.length * progress, accent, w)
        } else {
            val half = measure.length / 2f
            drawSegment(measure, 0f, half * (progress * drawn).coerceAtMost(1f), accent, w)
            drawSegment(
                measure,
                half,
                half + half * ((progress * drawn) - 1f).coerceIn(0f, 1f),
                accent,
                w,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSegment(
    measure: PathMeasure,
    start: Float,
    stop: Float,
    color: Color,
    width: Float,
) {
    if (stop <= start) return
    val segment = Path()
    measure.getSegment(start, stop, segment, true)
    drawPath(
        path = segment,
        color = color,
        style = Stroke(width = width * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
