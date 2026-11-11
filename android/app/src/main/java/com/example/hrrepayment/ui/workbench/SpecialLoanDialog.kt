package com.example.hrrepayment.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Strings
import com.example.hrrepayment.ui.components.GameButton
import com.example.hrrepayment.ui.components.ButtonTone
import com.example.hrrepayment.ui.components.StampKind
import com.example.hrrepayment.ui.components.StampMark
import com.example.hrrepayment.ui.theme.AppType
import com.example.hrrepayment.ui.theme.GameIcons
import com.example.hrrepayment.ui.theme.Tokens

@Composable
fun SpecialLoanDialog(strings: Strings, busy: Boolean, onDecide: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val scale = remember { Animatable(0.94f) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(Tokens.STAMP_MS)) }
    Dialog(onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.86f).heightIn(max = 560.dp).scale(scale.value)
            .clip(RoundedCornerShape(Tokens.CornerPanel.dp))
            .background(MaterialTheme.colorScheme.surface)
            .fingerprintWatermark()
            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp)
            .testTag("tag_special_loan_dialog"), horizontalAlignment = Alignment.CenterHorizontally) {
            StampMark(StampKind.BANG, MaterialTheme.colorScheme.error, progress.value)
            Spacer(Modifier.height(14.dp))
            Text(strings[Key.LOAN_TITLE], style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(strings[Key.LOAN_RULES], style = AppType.NoticeBody,
                color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(strings[Key.LOAN_COLLATERAL], style = AppType.NoticeBodyCompact,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("tag_special_loan_collateral"))
            Spacer(Modifier.height(18.dp))
            Text(strings[Key.LOAN_FINAL_CHOICE], style = AppType.NoticeBodyCompact,
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            GameButton(strings[Key.LOAN_ACCEPT], { onDecide(true) }, enabled = !busy,
                modifier = Modifier.width(Tokens.ButtonWidth.dp),
                icon = GameIcons.Cash, testTag = "tag_special_loan_accept")
            Spacer(Modifier.height(Tokens.InnerGap.dp))
            GameButton(strings[Key.LOAN_DECLINE], { onDecide(false) }, enabled = !busy,
                modifier = Modifier.width(Tokens.ButtonWidth.dp), tone = ButtonTone.TONAL,
                testTag = "tag_special_loan_decline")
            TextButton(onClick = onDismiss, enabled = !busy) { Text(strings[Key.COMMON_CLOSE]) }
        }
    }
}

private fun Modifier.fingerprintWatermark(): Modifier = drawBehind {
    val ink = Color(0xFFB3261E).copy(alpha = 0.12f)
    val side = minOf(size.width * 0.64f, 210.dp.toPx())
    val center = Offset(size.width * 0.70f, size.height * 0.46f)
    rotate(-22f, center) {
        translate(center.x - side / 2, center.y - side / 2) {
            // Nested, open ridges form a thumbprint; small gaps keep it from reading as concentric rings.
            for (ridge in 0..13) {
                val inset = side * (0.045f + ridge * 0.027f)
                val left = inset
                val right = side - inset
                val top = inset * 0.83f
                val bottom = side - inset * 0.35f
                val path = Path().apply {
                    moveTo(left + side * 0.055f, bottom)
                    cubicTo(left - side * 0.10f, side * 0.48f, left, top, side * 0.5f, top)
                    cubicTo(right, top, right + side * 0.05f, side * 0.52f, right - side * 0.07f, bottom - side * 0.08f)
                }
                drawPath(path, ink, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
            }
            val core = Path().apply {
                moveTo(side * 0.42f, side * 0.89f)
                cubicTo(side * 0.53f, side * 0.68f, side * 0.36f, side * 0.40f, side * 0.50f, side * 0.40f)
                cubicTo(side * 0.65f, side * 0.40f, side * 0.61f, side * 0.72f, side * 0.55f, side * 0.91f)
            }
            drawPath(core, ink, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}
