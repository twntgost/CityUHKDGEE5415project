package com.example.hrrepayment

import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.i18n.Key
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.data.content.AssetContentLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 字体覆盖测试：确认打包进 APK 的子集字体真的覆盖游戏会用到的每一个字。
 *
 * 这一层存在的理由很实际——中日韩字体是**按字符集子集化**的（否则一份思源字体
 * 就是 10–17MB），子集化最容易出的错就是“某个字在游戏里显示成系统字体”，而肉眼
 * 很难在几千屏内容里发现。这里用 `Paint.hasGlyph` 直接问字体“你有没有这个字”。
 *
 * 统计口径：字符集 = 内容包（简历/陈述/核对资料/新闻/竞猜）里的全部字符
 * + `Key` 枚举里的全部界面文案（中英）+ 数字与常用标点。
 */
@RunWith(AndroidJUnit4::class)
class FontCoverageInstrumentedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun typeface(id: Int, style: Int = Typeface.NORMAL): Typeface =
        requireNotNull(ResourcesCompat.getFont(context, id)) { "字体资源加载失败：$id" }
            .let { if (style == Typeface.NORMAL) it else Typeface.create(it, style) }

    /** 游戏实际会用到的全部字符。 */
    private fun charset(): Set<Char> {
        val chars = mutableSetOf<Char>()
        // 界面文案（中英双向，避免只测一种语言）
        Key.entries.forEach { key ->
            chars += key.zh.toList()
            chars += key.en.toList()
            chars += Lang.ZH.code.toList()
        }
        // 内容包：简历、陈述、核对资料、新闻、竞猜问题与选项
        val content = AssetContentLoader.load(context, Lang.ZH)
        for (day in 0 until content.totalWorkdays) {
            chars += content.workdayDisplayDate(day).toList()
            content.newsForWorkday(day).forEach { chars += (it.title + it.body + it.category).toList() }
        }
        for (day in 0 until content.totalWorkdays) {
            content.workdayCandidates(day).forEach { c ->
                chars += (c.name + c.role + c.school + c.major + c.company + c.educationForm +
                    c.genderLabel + c.uniqueLie + c.truth + c.judgementBasis + c.fraudType +
                    c.educationSkills + c.workHistory).toList()
                c.statements.forEach { chars += it.text.toList() }
                c.evidence.forEach { chars += (it.title + it.body).toList() }
            }
        }
        content.bettingMarkets().forEach { m ->
            chars += m.question.toList()
            m.options.forEach { chars += it.text.toList() }
        }
        chars += "0123456789.,:;!?()[]{}%+-—·、。，；：？！（）《》「」¥￥/".toList()
        return chars.filter { !it.isWhitespace() }.toSet()
    }

    private fun missing(typeface: Typeface, chars: Set<Char>): List<Char> {
        val paint = Paint().apply { this.typeface = typeface }
        return chars.filterNot { paint.hasGlyph(it.toString()) }
    }

    @Test
    fun bundledCjkSansCoversEveryGlyphTheGameUses() {
        val chars = charset()
        assertTrue("字符集异常偏小：${chars.size}", chars.size > 1500)
        listOf(
            "noto_sans_sc_400" to R.font.noto_sans_sc_400,
            "noto_sans_sc_500" to R.font.noto_sans_sc_500,
            "noto_sans_sc_700" to R.font.noto_sans_sc_700,
        ).forEach { (name, id) ->
            val gaps = missing(typeface(id), chars)
            assertEquals("$name 缺字：${gaps.take(20)}", emptyList<Char>(), gaps)
        }
    }

    @Test
    fun bundledCjkSerifCoversAllButTheKnownSliceGap() {
        val chars = charset()
        listOf(
            "noto_serif_sc_400" to R.font.noto_serif_sc_400,
            "noto_serif_sc_700" to R.font.noto_serif_sc_700,
        ).forEach { (name, id) ->
            val gaps = missing(typeface(id), chars)
            // 思源宋体分片边界留了极少数缺字，超出这个数就说明子集重新生成时漏了东西
            assertTrue("$name 缺字过多（${gaps.size}）：${gaps.take(20)}", gaps.size <= 12)
        }
    }

    /** 思源黑体同时承担中文与拉丁，必须两套字符都覆盖。 */
    @Test
    fun bundledCjkSansAlsoCoversLatinAlphabetAndDigits() {
        val latin = ('A'..'Z').toSet() + ('a'..'z').toSet() + ('0'..'9').toSet()
        listOf(
            "noto_sans_sc_400" to R.font.noto_sans_sc_400,
            "noto_sans_sc_700" to R.font.noto_sans_sc_700,
        ).forEach { (name, id) ->
            val gaps = missing(typeface(id), latin)
            assertEquals("$name 缺字母或数字：$gaps", emptyList<Char>(), gaps)
        }
    }

    @Test
    fun bundledLatinFontsCoverTheLatinAlphabetAndDigits() {
        val latin = ('A'..'Z').toSet() + ('a'..'z').toSet() + ('0'..'9').toSet()
        // Nunito / Space Grotesk 已按作者要求撤出（英文换回简约版），
        // 拉丁字形现在由思源系字体自带，覆盖度在上面的思源用例里一并校验。
        listOf(
            "source_serif_400" to R.font.source_serif_400,
            "source_serif_600" to R.font.source_serif_600,
            "jetbrains_mono_400" to R.font.jetbrains_mono_400,
            "jetbrains_mono_500" to R.font.jetbrains_mono_500,
        ).forEach { (name, id) ->
            val gaps = missing(typeface(id), latin)
            assertEquals("$name 缺字母或数字：$gaps", emptyList<Char>(), gaps)
        }
    }
}
