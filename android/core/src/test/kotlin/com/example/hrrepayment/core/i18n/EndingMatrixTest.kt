package com.example.hrrepayment.core.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 技术文档 §12.2 的「8 个结局 ID × 11 套原因正文」矩阵测试。
 *
 * 这一层回答的是“文案有没有接错线”：每个组合都必须有中英双语正文，同一结局的
 * 不同原因必须给出不同文本，不同结局之间不能共用正文，指定疫情句只能出现在
 * 两个正常终局里。
 */
class EndingMatrixTest {

    private data class Combo(val id: String, val reason: String)

    private val combos = listOf(
        Combo("overseas_repaid", "debt_zero"),
        Combo("debt_free_restart", "final_debt_zero"),
        Combo("debt_free_restart", "exposed_debt_zero"),
        Combo("debt_free_restart", "trust_negative"),
        Combo("overseas_indebted", "debt_remaining"),
        Combo("bankruptcy", "cash_exhausted"),
        Combo("bankruptcy", "debt_cap_exceeded"),
        Combo("indebted_layoff", "final_debt_remaining"),
        Combo("health_death", "cold_health_low"),
        Combo("health_death", "final_health_low"),
        Combo("low_dopamine", "prolonged_low"),
        Combo("exposed", "exposed_debt_remaining"),
    )

    private val endingIds = combos.map { it.id }.distinct()

    @Test
    fun `eight ending ids and twelve reason combinations are all covered`() {
        assertEquals(8, endingIds.size)
        assertEquals(12, combos.size)
        endingIds.forEach { id ->
            assertTrue(Story.endingTitle(Lang.ZH, id).isNotBlank(), "缺少中文结局名：$id")
            assertTrue(Story.endingTitle(Lang.EN, id).isNotBlank(), "缺少英文结局名：$id")
        }
    }

    @Test
    fun `every combination has bilingual body text`() {
        combos.forEach { (id, reason) ->
            val zh = Story.endingBody(Lang.ZH, id, reason)
            val en = Story.endingBody(Lang.EN, id, reason)
            assertTrue(zh.length > 40, "$id/$reason 的中文正文过短：${zh.length}")
            assertTrue(en.length > 40, "$id/$reason 的英文正文过短：${en.length}")
            assertTrue(en.none { it.code in 0x3400..0x9fff }, "$id/$reason 的英文正文仍含中文")
        }
    }

    /**
     * 同一结局内部的分支结构（技术文档 §12.2 + 项目书 §07）：
     *  - 债清重启：普通终局与“身份暴露”两套；**信任负值但已债清**也走暴露那套（规则如此）；
     *  - 破产：资金耗尽与债务越线两套；
     *  - 健康死亡：感冒致死与章末健康过低两套。
     */
    @Test
    fun `reason branches inside one ending are distinct and follow the documented split`() {
        fun text(id: String, reason: String) = Story.endingBody(Lang.ZH, id, reason)

        assertTrue(
            text("debt_free_restart", "final_debt_zero") != text("debt_free_restart", "exposed_debt_zero"),
            "债清普通终局与债清暴露必须是两套文本",
        )
        assertEquals(
            text("debt_free_restart", "exposed_debt_zero"),
            text("debt_free_restart", "trust_negative"),
            "信任负值且债清时走的就是债清暴露文本",
        )
        assertTrue(
            text("bankruptcy", "cash_exhausted") != text("bankruptcy", "debt_cap_exceeded"),
            "破产的两套原因文本必须不同",
        )
        assertTrue(
            text("health_death", "cold_health_low") != text("health_death", "final_health_low"),
            "健康死亡的两套原因文本必须不同",
        )
    }

    @Test
    fun `different endings never share a body`() {
        val cleanExposed = Story.endingBody(Lang.ZH, "debt_free_restart", "exposed_debt_zero")
        val caughtOut = Story.endingBody(Lang.ZH, "exposed", "exposed_debt_remaining")
        assertTrue(cleanExposed != caughtOut, "债清暴露与身份暴露串稿了")

        val byEnding = endingIds.associateWith { id ->
            Story.endingBody(Lang.ZH, id, combos.first { it.id == id }.reason)
        }
        assertEquals(byEnding.size, byEnding.values.distinct().size, "不同结局之间存在重复正文")
    }

    @Test
    fun `the pandemic coda appears in exactly the two normal endings`() {
        val coda = "那一天来了，一切的计划消失。"
        val hits = combos.filter { Story.endingBody(Lang.ZH, it.id, it.reason).contains(coda) }
            .map { it.id to it.reason }
            .toSet()
        val expected = setOf(
            "debt_free_restart" to "final_debt_zero",
            "indebted_layoff" to "final_debt_remaining",
        )
        assertEquals(expected, hits, "疫情句只应出现在两个正常终局里")
        val codaEn = "That day came, and every plan disappeared."
        val hitsEn = combos.filter { Story.endingBody(Lang.EN, it.id, it.reason).contains(codaEn) }
            .map { it.id to it.reason }
            .toSet()
        assertEquals(hits, hitsEn, "英文疫情句的出现位置与中文不一致")
    }
}
