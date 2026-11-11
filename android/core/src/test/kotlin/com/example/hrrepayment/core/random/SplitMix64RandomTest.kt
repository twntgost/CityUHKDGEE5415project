package com.example.hrrepayment.core.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Regression suite for the “random is constant” defect. The old implementation
 * collapsed to `state ushr 31` and then `ushr 33`, so every draw returned the lower
 * bound: ten thousand Bernoulli trials all succeeded and every range draw returned
 * `from`.
 *
 * Reference values below were produced by an independent SplitMix64 transcription
 * (Steele/Lea/Flood) and cross-checked against the published first output
 * `0xE220A8397B1DCDAF` for seed 0.
 */
class SplitMix64RandomTest {

    // ------------------------------------------------------- reference vectors

    @Test
    fun `seed zero matches the published SplitMix64 reference output`() {
        assertEquals(0xE220A8397B1DCDAFUL.toLong(), SplitMix64Random(0L).nextLong())
    }

    @Test
    fun `seed zero sequence is frozen as a regression guard`() {
        val rng = SplitMix64Random(0L)
        val actual = List(6) { rng.nextLong() }
        val expected = listOf(
            0xE220A8397B1DCDAFUL,
            0x6E789E6AA1B965F4UL,
            0x06C45D188009454FUL,
            0xF88BB8A8724C81ECUL,
            0x1B39896A51A8749BUL,
            0x53CB9F0C747EA2EAUL,
        ).map { it.toLong() }
        assertEquals(expected, actual)
    }

    // ----------------------------------------------------------- non-constancy

    @Test
    fun `a hundred consecutive draws are not constant`() {
        val rng = SplitMix64Random(0L)
        val draws = List(100) { rng.nextInt(0, 1_000) }
        assertTrue(
            draws.toSet().size >= 90,
            "expected >= 90 distinct values out of 100 draws, got ${draws.toSet().size}",
        )
    }

    @Test
    fun `a chance draw with a non-trivial probability is actually probabilistic`() {
        val rng = SplitMix64Random(2024L)
        val hits = (0 until 10_000).count { rng.chance(5_000) }
        assertTrue(hits in 4_600..5_400, "expected ~5000 hits out of 10000, got $hits")
    }

    @Test
    fun `nextLong is never a constant run and spans both signs`() {
        val rng = SplitMix64Random(5L)
        val draws = List(200) { rng.nextLong() }
        assertEquals(200, draws.toSet().size)
        assertTrue(draws.any { it < 0L } && draws.any { it >= 0L })
    }

    @Test
    fun `different seeds diverge`() {
        assertNotEquals(SplitMix64Random(1L).nextLong(), SplitMix64Random(2L).nextLong())
    }

    @Test
    fun `the same seed reproduces the same sequence`() {
        val first = SplitMix64Random(999L)
        val second = SplitMix64Random(999L)
        assertEquals(List(20) { first.nextLong() }, List(20) { second.nextLong() })
    }

    // ------------------------------------------------------------ range bounds

    @Test
    fun `nextInt stays inside the requested interval and covers it`() {
        val rng = SplitMix64Random(7L)
        val draws = List(5_000) { rng.nextInt(-5, 5) }
        assertTrue(draws.all { it in -5 until 5 }, "out-of-range draw present")
        assertEquals((-5 until 5).toSet(), draws.toSet())
    }

    @Test
    fun `nextInt with a single-value interval always returns that value`() {
        val rng = SplitMix64Random(7L)
        assertEquals(List(50) { 3 }, List(50) { rng.nextInt(3, 4) })
    }

    @Test
    fun `nextInt handles the largest supported interval`() {
        val rng = SplitMix64Random(11L)
        val draws = List(1_000) { rng.nextInt(0, Int.MAX_VALUE) }
        assertTrue(draws.all { it in 0 until Int.MAX_VALUE }, "out-of-range draw present")
        assertEquals(1_000, draws.toSet().size)
    }

    @Test
    fun `nextInt rejects an empty interval`() {
        assertFailsWith<IllegalArgumentException> { SplitMix64Random(1L).nextInt(5, 5) }
        assertFailsWith<IllegalArgumentException> { SplitMix64Random(1L).nextInt(5, 4) }
    }

    @Test
    fun `chance short-circuits at the extremes`() {
        val rng = SplitMix64Random(3L)
        assertTrue(List(100) { rng.chance(0) }.none { it })
        assertTrue(List(100) { rng.chance(10_000) }.all { it })
        assertFailsWith<IllegalArgumentException> { rng.chance(10_001) }
        assertFailsWith<IllegalArgumentException> { rng.chance(-1) }
    }

    // -------------------------------------------------------- snapshot restore

    @Test
    fun `restoring a snapshot resumes the exact same sequence`() {
        val rng = SplitMix64Random(42L)
        repeat(3) { rng.nextInt(0, 100) }
        val snapshot = rng.snapshot()
        assertEquals(3L, snapshot.drawCount)

        val afterSnapshot = List(10) { rng.nextLong() }
        assertEquals(13L, rng.snapshot().drawCount)

        rng.restore(snapshot)
        assertEquals(3L, rng.snapshot().drawCount)
        assertEquals(afterSnapshot, List(10) { rng.nextLong() })
    }

    @Test
    fun `snapshot at a seed equals a freshly constructed generator`() {
        val restored = SplitMix64Random(0L).also { it.restore(RandomSnapshot.atSeed(0L)) }
        val fresh = SplitMix64Random(0L)
        assertEquals(fresh.nextLong(), restored.nextLong())
    }

    /**
     * Regression: `restore` used to keep the constructor's seed, so a generator
     * built once and then resumed from another save reported the wrong `seed` and
     * the engine wrote that stale seed back into the save file.
     */
    @Test
    fun `a restored generator reports the seed it was restored from`() {
        val rng = SplitMix64Random(seed = 1L)
        rng.restore(RandomSnapshot.atSeed(4242L))

        val snapshot = rng.snapshot()
        assertEquals(4242L, snapshot.seed)
        assertEquals(4242L, snapshot.state)
        assertEquals(0L, snapshot.drawCount)
    }

    @Test
    fun `restore rejects an unknown algorithm so saves are never silently reshuffled`() {
        val rng = SplitMix64Random(1L)
        assertFailsWith<IllegalArgumentException> {
            rng.restore(RandomSnapshot(algorithm = "mystery", seed = 1L, state = 1L, drawCount = 0L))
        }
    }
}
