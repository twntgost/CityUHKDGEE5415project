package com.example.hrrepayment.core.model

import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.money.applyPerMille
import com.example.hrrepayment.core.random.RandomSnapshot
import com.example.hrrepayment.core.random.RandomSource
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {

    @Test
    fun `a fresh game starts with the documented economy and attributes`() {
        val s = GameState.newGame(saveId = "test-save", rngSeed = 1234L)

        assertEquals(0L, s.revision)
        assertEquals(Phase.Opening, s.phase)
        assertEquals(PendingStep.AcknowledgeOpening, s.pendingStep)
        assertEquals(WorkdayRef.FIRST, s.position.current)

        assertEquals(Money(600_000L), s.cash) // 6,000.00 元
        assertEquals(Money(50_000_000L), s.debt) // 500,000.00 元
        assertEquals(Money(600_000L), s.monthlySalary)
        assertEquals(Money(600_000L), s.nextMonthlySalary)
        assertEquals(Money(500_000L), s.bettingCap)

        assertEquals(5, s.attributes.health)
        assertEquals(5, s.attributes.dopamine)
        assertEquals(2, s.attributes.english)
        assertEquals(0, s.attributes.luck)
        assertEquals(1, s.attributes.work)
        assertEquals(3, s.attributes.trust)

        assertEquals(0, s.stats.globalSuccessCount)
        assertEquals(0, s.stats.globalFailureCount)
        assertEquals(0, s.stats.successiveFailureCount)

        assertNull(s.endingId)
        assertNull(s.lastAppliedCommandId)
    }

    @Test
    fun `rng position is persisted in the initial state`() {
        val s = GameState.newGame("s", rngSeed = 777L)
        assertEquals(777L, s.rngSeed)
        assertEquals(777L, s.rngState)
        assertEquals(0L, s.rngDrawCount)
        assertEquals(RandomSource.DEFAULT_ALGORITHM, s.rngAlgorithm)
        assertEquals(RandomSource.STATE_VERSION, s.rngStateVersion)
        assertEquals(RandomSnapshot.atSeed(777L), s.rngSnapshot())
    }

    @Test
    fun `the year-end interest rate starts at the 2016 rate and nothing is settled yet`() {
        val s = GameState.newGame("s", rngSeed = 1L)
        assertEquals(90, s.interestRatePerMille) // 9%
        assertEquals(2015, s.lastInterestSettledYear) // first settlement is 2016
        assertEquals(-1, s.lastSalarySettledMonthIndex)
    }

    @Test
    fun `interest on the opening debt matches the project spec`() {
        // 500,000.00 元 × 9% = 45,000.00 元 in 2016.
        assertEquals(Money(4_500_000L), GameState.newGame("s", 1L).debt.applyPerMille(90))
    }

    @Test
    fun `revision bump and rng write-back are pure`() {
        val s = GameState.newGame("s", rngSeed = 5L)
        val bumped = s.bumpRevision()
        assertEquals(1L, bumped.revision)
        assertEquals(0L, s.revision)

        val advanced = s.withRng(s.rngSnapshot().copy(state = 99L, drawCount = 4L))
        assertEquals(99L, advanced.rngState)
        assertEquals(4L, advanced.rngDrawCount)
        assertEquals(5L, s.rngState)

        val stamped = s.withCommandId("cmd-1")
        assertEquals("cmd-1", stamped.lastAppliedCommandId)
        assertNull(s.lastAppliedCommandId)
    }

    @Test
    fun `serialisation round-trips the whole state`() {
        val s = GameState.newGame("round-trip", rngSeed = 99L).copy(
            phase = Phase.Recruiting,
            pendingStep = PendingStep.ViewResume,
            cash = Money(123_456L),
            debt = Money(54_321_000L),
            browsedCandidateIndex = 1,
            selectedCandidateId = "resume-07",
            guaranteeTier = 30,
            guaranteePeriodDraws = 2,
            bettingCap = Money(750_000L),
            rngState = 4242L,
            rngDrawCount = 9L,
            lastAppliedCommandId = "cmd-9",
        )
        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(GameState.serializer(), s)
        assertEquals(s, json.decodeFromString(GameState.serializer(), encoded))
    }

    /**
     * A truncated/older save must still parse so the version check — not the JSON
     * decoder — is what rejects it. This is what the review probe relied on:
     * `{"saveId":"future","rulesVersion":"999"}`.
     */
    @Test
    fun `a partial JSON blob still deserialises so versions can be checked explicitly`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            GameState.serializer(),
            """{"saveId":"future","rulesVersion":"999"}""",
        )
        assertEquals("future", decoded.saveId)
        assertEquals("999", decoded.rulesVersion)
        assertEquals(GameState.CURRENT_SCHEMA_VERSION, decoded.saveSchemaVersion)
        assertEquals(Phase.Opening, decoded.phase)
        assertEquals(Money(600_000L), decoded.cash)
    }

    @Test
    fun `a save written by a future build keeps its unknown fields out of the model`() {
        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(
            GameState.serializer(),
            """{"saveId":"s","rulesVersion":"1.2","futureField":42}""",
        )
        assertEquals("s", decoded.saveId)
    }

    @Test
    fun `workday indices outside the 252-day range are rejected`() {
        assertFailsWith<IllegalArgumentException> { WorkdayRef(-1) }
        assertFailsWith<IllegalArgumentException> { WorkdayRef(252) }
        assertEquals(0, WorkdayRef.FIRST.monthIndex)
        assertEquals(5, WorkdayRef(5).dayOfMonth)
        assertEquals(42, WorkdayRef.TOTAL_WORKDAYS / WorkdayRef.DAYS_PER_MONTH)
        assertEquals(7, (WorkdayRef.TOTAL_WORKDAYS / WorkdayRef.DAYS_PER_MONTH) / 6)
        assertTrue(WorkdayRef.LAST.chapter == 6)
    }

    @Test
    fun `negative money counters are rejected at construction`() {
        assertFailsWith<IllegalArgumentException> {
            GameState.newGame("s", 1L).copy(browsedCandidateIndex = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            GameState.newGame("s", 1L).copy(guaranteePeriodDraws = -1)
        }
    }
}
