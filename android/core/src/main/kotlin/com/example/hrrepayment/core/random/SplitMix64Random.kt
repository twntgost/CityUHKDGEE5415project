package com.example.hrrepayment.core.random

/**
 * SplitMix64 (Steele, Lea & Flood 2014) — a 64-bit counter-based generator.
 *
 * 修复记录（review item #2）：上一版把混合步骤写成 `state xor (state xor (state ushr 31))`，
 * 等价于 `state ushr 31`，再 `ushr 33` 后必然为 0，导致所有抽样恒定取下界。
 * 现在按参考实现逐字转写：
 *
 * ```
 * state += GAMMA
 * z  = state
 * z  = (z xor (z ushr 30)) * MIX1
 * z  = (z xor (z ushr 27)) * MIX2
 * return z xor (z ushr 31)
 * ```
 *
 * Seeds are replayable: `SplitMix64Random(0).nextLong()` is the published first
 * output `0xE220A8397B1DCDAF`, asserted by [com.example.hrrepayment.core.random.SplitMix64RandomTest].
 */
class SplitMix64Random(seed: Long) : RandomSource {

    /**
     * Adopted from the last [restore]. It must be a `var`: the engine rebuilds the
     * generator from a persisted snapshot on every command and then writes
     * `snapshot().seed` back into the save, so a stale seed here would silently
     * rewrite the save's recorded seed on the next command.
     */
    private var seed: Long = seed
    private var state: Long = seed
    private var drawCount: Long = 0L

    override fun nextLong(): Long {
        state += GAMMA
        var z = state
        z = (z xor (z ushr 30)) * MIX1
        z = (z xor (z ushr 27)) * MIX2
        drawCount += 1L
        return z xor (z ushr 31)
    }

    override fun nextInt(from: Int, until: Int): Int {
        val bound = until.toLong() - from.toLong()
        require(bound > 0L) { "until ($until) must be > from ($from)" }
        return (from.toLong() + nextLongBounded(bound)).toInt()
    }

    override fun chance(bps: Int): Boolean {
        require(bps in 0..10_000) { "bps out of range: $bps" }
        if (bps <= 0) return false
        if (bps >= 10_000) return true
        return nextInt(0, 10_000) < bps
    }

    override fun snapshot(): RandomSnapshot = RandomSnapshot(
        algorithm = RandomSource.DEFAULT_ALGORITHM,
        seed = seed,
        state = state,
        drawCount = drawCount,
    )

    override fun restore(snapshot: RandomSnapshot) {
        require(snapshot.algorithm == RandomSource.DEFAULT_ALGORITHM) {
            "Unsupported RNG algorithm: ${snapshot.algorithm}"
        }
        seed = snapshot.seed
        state = snapshot.state
        drawCount = snapshot.drawCount
    }

    /**
     * Unbiased draw in `[0, bound)` using rejection sampling over non-negative
     * 63-bit candidates（candidate = `nextLong() ushr 1`，取值空间是 2^63 个）。
     *
     * 会引入偏差的候选数是 `2^63 mod bound`，即无符号意义上的
     * `remainderUnsigned(Long.MIN_VALUE, bound)`（Long.MIN_VALUE 的无符号值恰为 2^63）；
     * 这些候选（`[0, threshold)`）被拒绝，剩余 `[threshold, 2^63)` 区间可被 bound
     * 整除，再取模即无偏。
     */
    private fun nextLongBounded(bound: Long): Long {
        val threshold = java.lang.Long.remainderUnsigned(Long.MIN_VALUE, bound)
        while (true) {
            val candidate = nextLong() ushr 1
            if (candidate >= threshold) return candidate % bound
        }
    }

    private companion object {
        val GAMMA: Long = 0x9E3779B97F4A7C15UL.toLong()
        val MIX1: Long = 0xBF58476D1CE4E5B9UL.toLong()
        val MIX2: Long = 0x94D049BB133111EBUL.toLong()
    }
}
