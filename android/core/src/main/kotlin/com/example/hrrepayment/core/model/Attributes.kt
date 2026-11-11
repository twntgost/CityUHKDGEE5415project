package com.example.hrrepayment.core.model

import kotlinx.serialization.Serializable

/**
 * 六维属性 + 数值常量。
 *
 * 项目书 §4.2 设定:
 *  - 健康 5, 多巴胺 5, 英语 2, 运气 0, 工作能力 1, 信任 3.
 *
 * Month-end growth only ever adds +1 to one of 健康/多巴胺/英语. Trust and
 * 工作能力 change via recruitment rewards or chapter bonuses. 信任<0 立即
 * 结束；规则引擎负责阈值判断，这里只保存数值。
 */
@Serializable
data class Attributes(
    val health: Int = INITIAL_HEALTH,
    val dopamine: Int = INITIAL_DOPAMINE,
    val english: Int = INITIAL_ENGLISH,
    /** One-use recruitment boost, earned by losing wagers and consumed on submission. */
    val luck: Int = INITIAL_LUCK,
    val work: Int = INITIAL_WORK,
    val trust: Int = INITIAL_TRUST,
    /** Highest historical luck used to compute odds; not consumed with the current boost. */
    val luckPeak: Int = INITIAL_LUCK,
) {
    companion object {
        const val INITIAL_HEALTH = 5
        const val INITIAL_DOPAMINE = 5
        const val INITIAL_ENGLISH = 2
        const val INITIAL_LUCK = 0
        const val INITIAL_WORK = 1
        const val INITIAL_TRUST = 3
    }
}
