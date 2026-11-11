package com.example.hrrepayment.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import com.example.hrrepayment.ui.theme.GameIcons
import kotlin.random.Random

/**
 * 背景暗纹：文件夹 / 钱袋 / 报纸三种剪影随机摆放。
 *
 * 作者要求：三种图标**最多一共出现 4 个**，大小与方向全部随机，作为背景暗纹或剪影，
 * 铺在主玩法（推送简历）、月度结算、能力技能与结局四个背景上。
 *
 * 两个实现要点：
 *  - **不能每帧重抽**。位置/大小/角度由 [seed] 决定，`remember(seed)` 固定下来；
 *    调用方用「阶段 + 工作日」这类稳定值当种子，于是同一屏稳定、切屏才换布局。
 *  - 位置不是纯随机撒点，而是从 2×3 的六个锚位里挑不重复的格子再做抖动，
 *    保证四个剪影散得开、不叠成一坨，也避开正中央（正文所在处）。
 */
private data class Mark(
    val icon: ImageVector,
    val color: Color,
    val fx: Float,
    val fy: Float,
    val sizeDp: Float,
    val rotation: Float,
)

private val MARK_COLORS = listOf(
    Color(0xFFC9A227), // 黄色文件夹
    Color(0xFFB08A3E), // 钱袋（暖赭）
    Color(0xFF8C7F66), // 报纸（卡其灰）
)

private fun buildMarks(seed: Int): List<Mark> {
    val rnd = Random(seed)
    val icons = listOf(GameIcons.Folder, GameIcons.MoneyBag, GameIcons.News)
    val anchors = listOf(
        0.06f to 0.10f, 0.62f to 0.06f,
        0.02f to 0.42f, 0.66f to 0.40f,
        0.10f to 0.74f, 0.60f to 0.78f,
    ).shuffled(rnd)

    val count = 3 + rnd.nextInt(2) // 3 或 4：最多一共 4 个
    // 三种图标各出现一次（打乱顺序），若有第 4 个再随机补一个，避免抽成两个钱袋
    val iconOrder = icons.shuffled(rnd).toMutableList()
    if (count > iconOrder.size) iconOrder += icons[rnd.nextInt(icons.size)]
    return (0 until count).map { i ->
        val (ax, ay) = anchors[i]
        Mark(
            icon = iconOrder[i],
            color = MARK_COLORS[rnd.nextInt(MARK_COLORS.size)],
            fx = (ax + rnd.nextFloat() * 0.26f).coerceIn(0f, 0.86f),
            fy = (ay + rnd.nextFloat() * 0.16f).coerceIn(0f, 0.86f),
            sizeDp = 84f + rnd.nextFloat() * 86f,      // 84–170dp
            rotation = -30f + rnd.nextFloat() * 60f,   // -30°–30°
        )
    }
}

/**
 * 暗纹覆盖层：**放在内容之后绘制**，因此卡片、面板、弹窗都盖不住它
 * （作者要求“背景暗纹不能被方框覆盖”）。
 *
 * 用法：
 * ```
 * Box {
 *     Content()                      // 先画内容
 *     WatermarkOverlay(seed)         // 再画暗纹，永远在最上层
 * }
 * ```
 * 覆盖层不参与点击（`indication = null` 且不出现在语义树里），透明度很低，
 * 压在文字上也只是纸张纹理的感觉。
 */
@Composable
fun WatermarkOverlay(
    seed: Int,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
    /** 避让区（根坐标系）：落在其中的暗纹不画，让"电脑屏幕"这类实心面板压住水印。 */
    avoid: androidx.compose.ui.geometry.Rect? = null,
) {
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .watermarks(seed, onDark, alpha = if (onDark) 0.10f else 0.075f, avoid = avoid),
    )
}

/**
 * 把暗纹画在**本层背景**上（最先绘制，会被上层内容遮住）。
 * 需要“盖不住”时请用 [WatermarkOverlay]。
 *
 * @param seed 稳定种子：同一屏不变，换屏才换布局
 * @param onDark 深色背景（结局的深墨方案）用更亮的剪影 + 更低透明度
 */
@Composable
fun Modifier.watermarks(
    seed: Int,
    onDark: Boolean = false,
    alpha: Float = if (onDark) 0.11f else 0.115f,
    avoid: androidx.compose.ui.geometry.Rect? = null,
): Modifier {
    val marks = remember(seed) { buildMarks(seed) }
    val painters = marks.map { mark ->
        rememberVectorPainter(mark.icon) to mark
    }
    return drawBehind {
        painters.forEach { (painter, mark) ->
            val side = mark.sizeDp.dp.toPx()
            val left = mark.fx * (size.width - side)
            val top = mark.fy * (size.height - side)
            // 落在避让区里的暗纹直接跳过（面板会自己盖住它）
            if (avoid != null) {
                val hit = avoid.overlaps(
                    androidx.compose.ui.geometry.Rect(
                        left, top, left + side, top + side,
                    ),
                )
                if (hit) return@forEach
            }
            val tint = if (onDark) mark.color.copy(alpha = 1f) else mark.color
            translate(left, top) {
                rotate(mark.rotation, pivot = Offset(side / 2f, side / 2f)) {
                    with(painter) {
                        draw(
                            size = Size(side, side),
                            alpha = alpha,
                            colorFilter = ColorFilter.tint(tint),
                        )
                    }
                }
            }
        }
    }
}

/** 各屏的种子：阶段 + 工作日，保证同屏稳定、换屏换布局。 */
fun watermarkSeed(phase: Int, dayIndex: Int): Int = phase * 131 + dayIndex * 7 + 17

/** 面板级种子。 */
fun watermarkSeed(tag: Int): Int = tag * 977 + 41
