package com.example.hrrepayment.core.random

import kotlinx.serialization.Serializable

/**
 * Deterministic, replayable random source (技术文档 §3).
 *
 * The engine never calls [kotlin.random.Random] directly. Everything funnels
 * through a [RandomSource] so that:
 *  - the same snapshot yields the same sequence of draws, letting us reproduce
 *    a report from a saved game;
 *  - submission retries and process death never re-draw (the engine restores the
 *    persisted snapshot before every command and writes the post-draw snapshot
 *    back into the new state);
 *  - tests can supply a fully scripted source.
 *
 * Implementations are thread-confined: the engine owns one instance and drives it
 * from the serial command loop only.
 */
interface RandomSource {
    /** Uniform integer in `[from, until)`. Negative `from` is allowed. */
    fun nextInt(from: Int, until: Int): Int

    /** Next raw 64-bit output. Signed; callers must define their own range. */
    fun nextLong(): Long

    /** Bernoulli draw with probability `bps / 10_000` (0..10_000; 0 ⇒ never, 10_000 ⇒ always). */
    fun chance(bps: Int): Boolean

    /** Capture the exact generator position so it can be persisted. */
    fun snapshot(): RandomSnapshot

    /** Resume from a previously captured snapshot. */
    fun restore(snapshot: RandomSnapshot)

    companion object {
        const val DEFAULT_ALGORITHM = "splitmix64"

        /** Bump when the draw sequence changes so old saves are rejected, not silently reshuffled. */
        const val STATE_VERSION = 1
    }
}

/**
 * Full generator position. `seed` is retained for debugging/display only —
 * resuming uses [state] and [drawCount], so the sequence continues exactly where
 * it stopped (review item #2: “不得在加载时重新以系统时间生成替代”).
 */
@Serializable
data class RandomSnapshot(
    val algorithm: String = RandomSource.DEFAULT_ALGORITHM,
    val seed: Long,
    val state: Long,
    val drawCount: Long,
) {
    companion object {
        fun atSeed(seed: Long): RandomSnapshot = RandomSnapshot(
            algorithm = RandomSource.DEFAULT_ALGORITHM,
            seed = seed,
            state = seed,
            drawCount = 0L,
        )
    }
}
