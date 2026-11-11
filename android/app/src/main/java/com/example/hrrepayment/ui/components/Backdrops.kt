package com.example.hrrepayment.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * 钱的纹路背景.
 *
 * The author asked the economy panel to carry a coin pattern so it reads as “money”
 * at a glance and separates visually from the attribute panel below it.
 *
 * Drawn with plain `drawBehind` circles and two short strokes per coin — no bitmap,
 * no allocation per frame beyond the loops, and it stays legible under text because
 * everything is at 6–9% alpha.
 */
fun Modifier.moneyPattern(
    color: Color,
    step: Float = 46f,
    radius: Float = 13f,
    alpha: Float = 0.09f,
): Modifier = drawBehind {
    val r = radius.dp.toPx() * 0.5f
    val s = step.dp.toPx()
    val stroke = 1.4.dp.toPx()
    val paint = color.copy(alpha = alpha)
    var row = 0
    var y = r + 2f
    while (y < size.height) {
        var x = if (row % 2 == 0) r + 4f else r + 4f + s / 2f
        while (x < size.width) {
            drawCoin(Offset(x, y), r, paint, stroke)
            x += s
        }
        y += s * 0.78f
        row++
    }
}

/** One coin: an outline, an inner ring and a stylised ¥ cross. */
private fun DrawScope.drawCoin(centre: Offset, r: Float, color: Color, stroke: Float) {
    drawCircle(color = color, radius = r, center = centre, style = Stroke(width = stroke))
    drawCircle(color = color, radius = r * 0.62f, center = centre, style = Stroke(width = stroke * 0.8f))
    val arm = r * 0.34f
    drawLine(
        color = color,
        start = Offset(centre.x, centre.y - arm),
        end = Offset(centre.x, centre.y + arm),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(centre.x - arm, centre.y - arm * 0.5f),
        end = Offset(centre.x + arm, centre.y - arm * 0.5f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(centre.x - arm, centre.y + arm * 0.55f),
        end = Offset(centre.x + arm, centre.y + arm * 0.55f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}
