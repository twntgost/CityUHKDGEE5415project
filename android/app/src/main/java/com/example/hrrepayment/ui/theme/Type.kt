package com.example.hrrepayment.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.hrrepayment.R

/**
 * 字体素材（2026-10-11 作者要求提供可替换字体）。
 *
 * 全部为 SIL OFL 授权、可商用、可嵌入 APK；中日韩字形已按游戏实际用字子集化
 * （2051 个汉字），整包约 4.9MB。生成脚本：`tools/build_fonts.py`。
 *
 * 每个家族都是「拉丁字体在前、中日韩字体在后」：Android 会按顺序做字形回退，
 * 因此英文走设计字体、中文走子集化的思源系字体。
 */
object AppFonts {

    /**
     * UI 无衬线：**只用思源黑体**（中文 + 它自带的拉丁字形）。
     *
     * 2026-10-11 作者反馈：Nunito 那套圆头英文放在正文里“太难绷了，观感很差”，
     * 要求换回**简约版**。于是撤掉 Nunito 与 Space Grotesk，英文改用思源黑体
     * 自带的中性拉丁字形（Source Sans 系），中英风格统一、密度一致。
     * 字体文件仍保留在 assets/fonts 里，想要圆润风格随时可以换回来。
     */
    val Sans = FontFamily(
        // Inter 排在前面：英文（拉丁）用 Inter，可读性比思源自带的拉丁字形更好；
        // 中文自动回退到思源黑体。
        Font(R.font.inter_400, FontWeight.Normal),
        Font(R.font.noto_sans_sc_400, FontWeight.Normal),
        Font(R.font.inter_500, FontWeight.Medium),
        Font(R.font.noto_sans_sc_500, FontWeight.Medium),
        Font(R.font.inter_600, FontWeight.SemiBold),
        Font(R.font.noto_sans_sc_500, FontWeight.SemiBold),
        Font(R.font.inter_700, FontWeight.Bold),
        Font(R.font.noto_sans_sc_700, FontWeight.Bold),
    )

    /**
     * 海报标题：**保持原样，不要动**。
     *
     * 中文标题“还债的人力资源官”的字形来自这里的回退链（拉丁在前、思源宋体在后），
     * 2026-10-11 我一度把 Space Grotesk 从这里撤掉，中文标题的观感跟着变了，
     * 作者反馈“这几个字怎么给我字体换了？别换啊”——已恢复。
     * 英文正文的简约化只动 [Sans]，与这里无关。
     */
    val Display = FontFamily(
        Font(R.font.space_grotesk_500, FontWeight.Medium),
        Font(R.font.noto_serif_sc_400, FontWeight.Medium),
        Font(R.font.space_grotesk_700, FontWeight.Bold),
        Font(R.font.noto_serif_sc_700, FontWeight.Bold),
    )

    /** 简历与剧情正文：Source Serif 4（拉丁）+ 思源宋体（中文）。 */
    val Serif = FontFamily(
        Font(R.font.source_serif_400, FontWeight.Normal),
        Font(R.font.noto_serif_sc_400, FontWeight.Normal),
        Font(R.font.source_serif_600, FontWeight.SemiBold),
        Font(R.font.noto_serif_sc_700, FontWeight.SemiBold),
    )

    val SerifBold = FontFamily(
        Font(R.font.source_serif_600, FontWeight.Normal),
        Font(R.font.noto_serif_sc_700, FontWeight.Normal),
    )

    /**
     * 结局专用：**细宋 + 高对比衬线**。
     *
     * 参考方向是豪华品牌中文标识的做法——笔画细、字面舒展、字距极大、装饰为零，
     * 靠留白和比例而不是靠书法感撑场面（作者明确不要书法体）。
     * 中文 = 思源宋体 Light/ExtraLight，拉丁 = Cormorant Garamond。
     */
    val Epic = FontFamily(
        Font(R.font.epic_cormorant_500, FontWeight.Normal),
        Font(R.font.epic_song_300, FontWeight.Normal),
        Font(R.font.epic_cormorant_600, FontWeight.Medium),
        Font(R.font.epic_song_200, FontWeight.Medium),
    )

    /** 结局正文：比标题略重一点的宋体，长文可读性优先。 */
    val EpicBody = FontFamily(
        Font(R.font.epic_cormorant_300, FontWeight.Normal),
        Font(R.font.noto_serif_sc_400, FontWeight.Normal),
        Font(R.font.epic_cormorant_500, FontWeight.Medium),
        Font(R.font.noto_serif_sc_700, FontWeight.Medium),
    )

    /** 系统提示（终端条方案）与简历编号：JetBrains Mono。 */
    val Mono = FontFamily(
        Font(R.font.jetbrains_mono_400, FontWeight.Normal),
        Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    )
}

/**
 * Type scale, v3.
 *
 * 与 v2 的区别：字体族换成 [AppFonts]（可替换素材），按钮文字单独加档并放宽字距，
 * 让短标签也能撑住按钮；其余字号/字重沿用 v2（整体比默认粗一档）。
 */
private val Sans = AppFonts.Sans

object AppType {

    /** Resume / verification-record text: serif, slightly looser leading. */
    val Serif = TextStyle(
        fontFamily = AppFonts.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    )

    val SerifSmall = TextStyle(
        fontFamily = AppFonts.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )

    val SerifTitle = TextStyle(
        fontFamily = AppFonts.SerifBold,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )

    /** System notice headings: small, wide-tracked, upper-cased in Latin layouts. */
    val NoticeTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.6.sp,
    )

    /** System notice body: medium weight, generous leading, never justified. */
    val NoticeBody = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.15.sp,
    )

    val NoticeBodyCompact = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )

    /** Monospace variant used by the “terminal strip” notice scheme. */
    val NoticeMono = TextStyle(
        fontFamily = AppFonts.Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    )

    val NoticeMonoTitle = TextStyle(
        fontFamily = AppFonts.Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp,
    )

    /**
     * 按钮文字：比正文大一档，字距放宽，让短标签（“继续”“确认还款”）也能撑住按钮。
     * 中文没有词间空格，靠字号与字距拉开密度；英文同样受益于放宽的字距。
     */
    val ButtonLabel = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.2.sp,
    )

    /** 紧凑按钮（菜单里的三连按钮、对话框按钮）用略小的一档。 */
    val ButtonLabelCompact = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.6.sp,
    )

    /**
     * 面板标题（状态概览 / 还款 / 技能 / 奖励…）：比 M3 的 headlineMedium 再大两档。
     * 作者反馈“字体还这么小，标题字体要变大”。
     */
    val PanelTitle = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
    )

    /** 顶栏日期：主玩法里最大的一行字。 */
    val DayDate = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
    )

    /** 海报标题专用：几何无衬线（拉丁）+ 宋体（中文）。 */
    val PosterTitle = TextStyle(
        fontFamily = AppFonts.Display,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 54.sp,
    )

    /** 结局名：细宋 + 6sp 字距，像品牌标识一样摊开。 */
    val EpicTitle = TextStyle(
        fontFamily = AppFonts.Epic,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 46.sp,
        letterSpacing = 5.sp,
    )

    /** 结局页的小标签（“结局 / ENDING”），全大写、宽字距。 */
    val EpicKicker = TextStyle(
        fontFamily = AppFonts.Epic,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 5.sp,
    )

    /** 结局正文：细宋、行高 1.75，长段落也舒展。 */
    val EpicProse = TextStyle(
        fontFamily = AppFonts.EpicBody,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.8.sp,
    )

    /** Narrative prose: serif, comfortable measure. */
    val Prose = TextStyle(
        fontFamily = AppFonts.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp,
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AppFonts.Display, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = AppFonts.Display, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp,
    ),
)
