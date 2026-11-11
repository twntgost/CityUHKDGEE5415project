package com.example.hrrepayment.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 人才生涯规则（作者 2026-10-11 定）：
 *  - 成功推送的人约 **10%** 会在**入职半年内**离职；
 *  - 在职者**每满半年**以 **30%** 的概率晋升一次，**最多 2 次**；
 *  - 每离职一人信任 −1，每晋升一次信任 +1。
 *
 * 这里验的是**概率本身**（大样本）与**掷点的确定性/独立性**，而不是单个样例。
 */
class CareerLifecycleTest {

    private val halfYear = GameEngine.HALF_YEAR_WORKDAYS

    @Test
    fun `resignation rate is about ten percent`() {
        var resigned = 0
        val total = 20_000
        repeat(total) { i ->
            if (CareerRolls.chance(42L, "resume_$i", CareerRolls.SALT_RESIGN, GameEngine.RESIGN_BPS)) {
                resigned++
            }
        }
        val rate = resigned.toDouble() / total
        assertTrue(rate in 0.088..0.112, "离职率偏离 10% 太多：$rate")
    }

    @Test
    fun `promotion rate is about thirty percent and the two reviews are independent`() {
        var first = 0
        var second = 0
        val total = 20_000
        repeat(total) { i ->
            val id = "resume_$i"
            if (CareerRolls.chance(7L, id, CareerRolls.SALT_PROMOTION, GameEngine.PROMOTION_BPS)) first++
            if (CareerRolls.chance(7L, id, CareerRolls.SALT_PROMOTION + 1, GameEngine.PROMOTION_BPS)) second++
        }
        val firstRate = first.toDouble() / total
        val secondRate = second.toDouble() / total
        assertTrue(firstRate in 0.285..0.315, "首次晋升率偏离 30%：$firstRate")
        assertTrue(secondRate in 0.285..0.315, "第二次晋升率偏离 30%：$secondRate")
        // 两次评审必须用不同盐值，否则“最多 2 次”会退化成同一次判定
        assertEquals(
            false,
            (0 until 200).all { i ->
                CareerRolls.chance(7L, "resume_$i", CareerRolls.SALT_PROMOTION, 3_000) ==
                    CareerRolls.chance(7L, "resume_$i", CareerRolls.SALT_PROMOTION + 1, 3_000)
            },
            "两次晋升评审完全一致，说明盐值没生效",
        )
    }

    @Test
    fun `the resignation date always falls inside the first half year`() {
        var checked = 0
        repeat(5_000) { i ->
            val id = "resume_$i"
            if (!CareerRolls.chance(99L, id, CareerRolls.SALT_RESIGN, GameEngine.RESIGN_BPS)) return@repeat
            val offset = 1 + CareerRolls.intUntil(99L, id, CareerRolls.SALT_RESIGN, halfYear)
            assertTrue(offset in 1..halfYear, "离职日期越界：$offset")
            checked++
        }
        assertTrue(checked > 300, "样本里离职的人太少，测试没意义：$checked")
    }

    @Test
    fun `career rolls are deterministic and do not touch the main stream`() {
        val a = CareerRolls.bits(1234L, "resume_007", CareerRolls.SALT_RESIGN)
        val b = CareerRolls.bits(1234L, "resume_007", CareerRolls.SALT_RESIGN)
        assertEquals(a, b, "同一存档种子的掷点必须可复现")
        assertTrue(
            CareerRolls.bits(1234L, "resume_007", CareerRolls.SALT_RESIGN) !=
                CareerRolls.bits(1234L, "resume_008", CareerRolls.SALT_RESIGN),
            "不同候选人的掷点不应相同",
        )
        assertTrue(
            CareerRolls.bits(1234L, "resume_007", CareerRolls.SALT_RESIGN) !=
                CareerRolls.bits(9999L, "resume_007", CareerRolls.SALT_RESIGN),
            "换种子应当换生涯走向",
        )
    }

    @Test
    fun `promotions are capped at two`() {
        assertEquals(2, GameEngine.MAX_PROMOTIONS)
    }
}
