package com.example.hrrepayment.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Khaki Sky palette, v2 (2026-10-11 UI overhaul).
 *
 * The author's feedback: “整个界面颜色有点压抑，颜色不变多加一点天蓝色点缀，
 * 天蓝色更蓝一点点”. The khaki family is unchanged; the blue is pushed from a
 * teal-leaning `#2E7D9A` to a real sky blue `#1B7FC4`, and three sky tints were added
 * so small elements (chips, rules, icon plates, the money pattern) can carry the
 * accent without repainting whole surfaces.
 *
 * Only the light scheme exists on purpose: the game must look identical regardless
 * of the device's dark-mode setting.
 */
object Tokens {
    // ------------------------------------------------------------------- sky
    /** Primary action colour. Bluer than v1, still calm at 4.5:1 on white. */
    val Primary = Color(0xFF1B7FC4)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryContainer = Color(0xFFCFE7F8)
    val OnPrimaryContainer = Color(0xFF0B3A57)
    val InversePrimary = Color(0xFF9AD2F2)

    /** Decorative sky tints: dividers, icon plates, chips, pattern strokes. */
    val SkyAccent = Color(0xFF4FA8DE)
    /**
     * 无轮廓的浅蓝按钮底色。作者反馈原来太淡、和整体不协调，加深一档：
     * 从 #E3F2FC 提到 #CDE6FA，配 SkyDeep 文字对比度更稳。
     */
    val SkySoft = Color(0xFFCDE6FA)

    /** 浅蓝按钮的描边：比底色深一点，让无轮廓按钮也有边界感。 */
    val SkySoftBorder = Color(0xFF9CC9E8)

    val SkyDeep = Color(0xFF125F94)

    // ----------------------------------------------------------------- khaki
    val Secondary = Color(0xFF8A7A55)
    val SecondaryContainer = Color(0xFFE6DCC2)
    val OnSecondaryContainer = Color(0xFF3F3720)
    val TertiaryContainer = Color(0xFFD8C9A6)
    val OnTertiaryContainer = Color(0xFF3A3120)

    // --------------------------------------------------------------- surface
    val Surface = Color(0xFFF3ECDD)
    val SurfaceContainerLow = Color(0xFFEDE5D3)
    val SurfaceContainer = Color(0xFFE7DECA)
    val SurfaceContainerHigh = Color(0xFFE0D6C0)
    val SurfaceContainerHighest = Color(0xFFD9CEB6)

    val OnSurface = Color(0xFF33291C)
    val OnSurfaceVariant = Color(0xFF5C5142)
    val Outline = Color(0xFF8C7F66)
    val OutlineVariant = Color(0xFFD5C9AE)

    val InverseSurface = Color(0xFF33291C)
    val InverseOnSurface = Color(0xFFF6F0E3)

    val Error = Color(0xFFB3261E)
    val OnError = Color(0xFFFFFFFF)
    val ErrorContainer = Color(0xFFF9DEDC)
    val OnErrorContainer = Color(0xFF410E0B)

    /** 牛皮纸兜底色（纹理未加载时）与压在纸上的墨色文字。 */
    /** 金额专用墨绿（美钞墨色），让钱的数字和普通文字在视觉上分开。 */
    val MoneyInk = Color(0xFF1D5B3F)

    val KraftFallbackColor = Color(0xFFC4A474)
    val InkInk = Color(0xFF3A2E1E)

    /** 晋升两次的金色边框（暖金，压得住卡其底）。 */
    val Gold = Color(0xFFB8860B)

    /** Low-saturation success green, used only alongside an icon or a word. */
    val Success = Color(0xFF386641)

    /**
     * 结局用色：好结局走绿/天蓝，坏结局走红，灰色结局（逍遥法外）走卡其深色。
     * 让玩家一眼看出这一局是“赢面”还是“输面”，而不是所有结局都一片红。
     */
    fun endingAccent(endingId: String?): Color = when (endingId) {
        "overseas_repaid" -> Success
        "debt_free_restart" -> Primary
        "overseas_indebted" -> Secondary
        else -> Error
    }

    // ------------------------------------------------------------- geometry
    /** 4dp base grid, 16dp page margin. */
    const val PageMargin = 16
    const val BlockGap = 16
    const val InnerGap = 8
    const val RowGap = 8
    const val CellGap = 4

    /** Rounder than v1 on purpose: “圆润友好一点”. */
    const val CornerCard = 24
    const val CornerTile = 18
    const val CornerPanel = 28
    const val CornerButton = 28
    const val ButtonWidth = 240
    /**
     * 按钮高度。实机上 56dp 偏高、与仓促的横向比例不协调，收到 52dp
     * （作者反馈“按钮太高”）。
     */
    const val ButtonHeight = 52

    /**
     * 面板里的主按钮不再拉满整屏：宽度设上限，配合更大的字与更宽的字距，
     * 避免“两个字 + 一条巨长的按钮”的空洞感（作者 2026-10-11 反馈）。
     */
    const val ButtonMaxWidth = 300
    const val ButtonMaxWidthWide = 340

    /**
     * 选择型按钮（加能力、加技能）的宽度：比面板主按钮更窄。
     * 这类按钮是“二选一/三选一”，占满整屏会显得空，收窄后更像可选项。
     */
    const val ButtonChoiceWidth = 268
    const val TouchTarget = 48

    // ------------------------------------------------------------- durations
    /** Every automatic transition stays at or below 300ms (项目书 §15.2). */
    const val PRESS_MS = 90
    const val SELECT_MS = 120
    const val PAGE_MS = 190
    const val PANEL_MS = 220
    const val SLOT_MS = 350
    const val RESULT_MS = 220

    /** The result stamp: the author asked for “非常短，0.2s 以内”. */
    const val STAMP_MS = 180

    /** System-notice entrance: distinct from every other text block. */
    const val NOTICE_MS = 200

    /** Energy-cell strip geometry: dense by design (3dp wide, 12dp tall). */
    const val CELL_WIDTH = 3
    const val CELL_HEIGHT = 12
    const val CELL_SPACING = 1.5f
}
