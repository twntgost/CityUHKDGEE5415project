package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.CalendarPosition
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.PendingStep
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.model.WorkdayRef
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixedEventTest {
    private fun engine() = GameEngine(TestContent.ref(), SplitMix64Random(1))
    private fun at(day: Int, phase: Phase = Phase.ResultAck): GameState =
        GameState.newGame("fixed-events", 1).copy(
            position = CalendarPosition(WorkdayRef(day)),
            phase = phase,
            pendingStep = PendingStep.None,
        )

    private fun nextDay(state: GameState): GameState {
        if (state.position.current.dayOfMonth == 5) {
            val dayEntry = state.copy(
                position = CalendarPosition(WorkdayRef(state.position.current.index + 1)),
                phase = Phase.DayEntry,
                pendingStep = PendingStep.None,
                revision = state.revision + 1,
            )
            return engine().apply(dayEntry, Command.EnterRecruiting(dayEntry.revision, dayEntry.saveId, "enter-${dayEntry.position.current.index}")).newState
        }
        return engine().apply(
            state, Command.AcknowledgeResult(state.revision, state.saveId, "advance-${state.position.current.index}")
        ).newState
    }

    private fun acknowledge(state: GameState): GameState = engine().apply(
        state, Command.AcknowledgeEvent(state.revision, state.saveId, "ack-${state.revision}", state.pendingEventId!!)
    ).newState

    @Test fun managerHandoverOccursOn2018FirstDayAndSurvivesRestore() {
        val before = at(107).copy(attributes = at(107).attributes.copy(trust = 15))
        val notice = nextDay(before)
        assertEquals(108, notice.position.current.index)
        assertEquals(Phase.EventNotice, notice.phase)
        assertEquals(GameEngine.EVENT_MANAGER_HANDOVER, notice.pendingEventId)
        assertEquals(3, notice.attributes.trust)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(notice))
        val after = acknowledge(restored)
        assertEquals(Phase.DayEntry, after.phase)
        assertNull(after.pendingEventId)
        assertEquals(3, after.attributes.trust)
        assertEquals(setOf(GameEngine.EVENT_MANAGER_HANDOVER), after.fixedEventsSeen)
        assertFailsWith<EngineError.WrongPhase> {
            engine().apply(after, Command.AcknowledgeEvent(after.revision, after.saveId, "again", GameEngine.EVENT_MANAGER_HANDOVER))
        }
    }

    @Test fun overseasCheckUsesJuneFirstDayAndFreezesSuccessfulRun() {
        val prepared = at(209).copy(attributes = at(209).attributes.copy(work = 20, english = 10, trust = 10))
        val ended = nextDay(prepared)
        assertEquals(210, ended.position.current.index)
        assertEquals(Phase.Ended, ended.phase)
        assertEquals("overseas_indebted", ended.endingId)
        assertEquals("debt_remaining", ended.endingReason)
        assertEquals(GameEngine.EVENT_OVERSEAS_OFFER, ended.pendingEventId)
        assertFailsWith<EngineError.WrongPhase> {
            engine().apply(ended, Command.EnterRecruiting(ended.revision, ended.saveId, "recruit-after-ending"))
        }

        val missed = nextDay(prepared.copy(attributes = prepared.attributes.copy(english = 9)))
        assertEquals(Phase.EventNotice, missed.phase)
        assertEquals(GameEngine.EVENT_OVERSEAS_MISSED, missed.pendingEventId)
        assertEquals(Phase.DayEntry, acknowledge(missed).phase)
    }

    @Test fun winterColdOccursOnDecemberSecondDayExactlyOnce() {
        val healthy = at(246).copy(attributes = at(246).attributes.copy(health = 7))
        val notice = nextDay(healthy)
        assertEquals(247, notice.position.current.index)
        assertEquals(2, notice.attributes.health)
        assertEquals(Phase.EventNotice, notice.phase)
        val restored = Json.decodeFromString<GameState>(Json.encodeToString(notice))
        assertEquals(2, acknowledge(restored).attributes.health)
        val dead = nextDay(healthy.copy(attributes = healthy.attributes.copy(health = 6)))
        assertEquals(Phase.Ended, dead.phase)
        assertEquals("health_death", dead.endingId)
        assertEquals("cold_health_low", dead.endingReason)
        assertEquals(1, dead.attributes.health)
    }

    @Test fun chapterTwoBonusFollowsActualSuccessAndNoticePrecedesReward() {
        val starting = at(36, Phase.Recruiting).copy(
            stats = at(36).stats.copy(globalSuccessCount = 2),
            selectedCandidateId = TestContent.id(72),
        )
        val e = engine()
        val marked = e.apply(starting, Command.MarkStatement(starting.revision, starting.saveId, "mark", TestContent.id(72), "work-claim")).newState
        val result = e.apply(marked, Command.SubmitCandidate(marked.revision, marked.saveId, "submit", TestContent.id(72), "work-claim")).newState
        assertEquals(Phase.ResultAck, result.phase)
        assertEquals(GameEngine.EVENT_CHAPTER2_SUCCESS, result.pendingEventId)
        assertEquals(5, result.attributes.trust - starting.attributes.trust)
        val notice = engine().apply(result, Command.AcknowledgeResult(result.revision, result.saveId, "result-ack")).newState
        assertEquals(Phase.EventNotice, notice.phase)
        val after = acknowledge(notice)
        assertEquals(Phase.SuccessReward, after.phase)
    }
}
