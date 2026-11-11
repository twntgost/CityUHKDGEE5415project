package com.example.hrrepayment.core.engine

/**
 * 生涯掷点：由**存档种子 + 候选人 id + 盐值**派生的确定性随机。
 *
 * 刻意**不消耗主随机流**（`RandomSource`），理由有两条：
 *  - 主随机流保证“同一次提交重放得到同一个推送概率”。生涯掷点混进去会让抽签次数
 *    随录用次数漂移，脚本化的概率测试与旧存档回放都会被打乱；
 *  - 生涯掷点只依赖已存档的状态（种子 + id），本身就是纯函数，重放天然一致。
 *
 * 放在独立文件里而不是引擎私有方法，是为了让 `core` 的测试能直接验证
 * 10% / 30% 这两条**概率本身**（而不是只验证单个样例）。
 */
internal object CareerRolls {

    /** splitmix64 混合，够用且无分配。 */
    fun bits(seed: Long, candidateId: String, salt: Int): Long {
        var x = seed xor (candidateId.hashCode().toLong() shl 32) xor salt.toLong()
        x += -0x61c8864680b583ebL
        x = (x xor (x ushr 30)) * -0x40a7b892e31b1a47L
        x = (x xor (x ushr 27)) * -0x6b2fb644ecceee15L
        return x xor (x ushr 31)
    }

    /** 伯努利判定：`bps / 10_000` 的概率为真。 */
    fun chance(seed: Long, candidateId: String, salt: Int, bps: Int): Boolean =
        ((bits(seed, candidateId, salt) ushr 1) % 10_000L) < bps

    /** `[0, until)` 的均匀整数。 */
    fun intUntil(seed: Long, candidateId: String, salt: Int, until: Int): Int =
        if (until <= 0) 0 else ((bits(seed, candidateId, salt) ushr 1) % until).toInt()

    const val SALT_RESIGN = 0x51
    const val SALT_PROMOTION = 0x7A
}
