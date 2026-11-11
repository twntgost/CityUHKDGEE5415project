package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class ReviewRulesTest {
    private fun state() = GameState.newGame("review", 7).copy(phase = Phase.Recruiting)
    private fun submit(s: GameState, correct: Boolean, outcome: Boolean): GameState {
        val engine = GameEngine(TestContent.ref(), ScriptedRandomSource(listOf(outcome)))
        val candidateId = TestContent.id(s.position.current.index * 2)
        val statementId = if (correct) "work-claim" else "school"
        val marked = engine.apply(s, Command.MarkStatement(s.revision, s.saveId, "mark", candidateId, statementId)).newState
        return engine.apply(marked, Command.SubmitCandidate(marked.revision, marked.saveId, "submit",
            candidateId, statementId)).newState
    }

    @Test fun judgmentAndHiringAreIndependentForAllFourOutcomes() {
        for (correct in listOf(false, true)) for (hired in listOf(false, true)) {
            val result = submit(state(), correct, hired)
            assertEquals(correct, result.lastReviewCorrect)
            assertEquals(hired, result.lastResultSuccess)
            assertEquals(if (correct) 7500 else 5000, result.lastHireProbabilityBps)
            assertEquals(1, result.stats.reviewedSubmissionCount)
            assertEquals(if (correct) 1 else 0, result.stats.correctReviewCount)
            assertEquals(0, result.stats.successRewardPending)
            assertEquals(1, result.attributes.work)
        }
    }

    @Test fun disabledLuckSurvivesAndEnabledLuckIsConsumedOnSuccessOrFailure() {
        for (hired in listOf(false, true)) {
            val base = state().copy(attributes = state().attributes.copy(luck = 4, luckPeak = 4))
            val retained = submit(base, true, hired)
            assertEquals(4, retained.attributes.luck)
            assertEquals(7500, retained.lastHireProbabilityBps)
            val spent = submit(base.copy(useLuckOnSubmission = true), true, hired)
            assertEquals(0, spent.attributes.luck)
            assertEquals(4, spent.lastLuckUsed)
            assertEquals(7900, spent.lastHireProbabilityBps)
            assertEquals(4, spent.attributes.luckPeak)
        }
    }

    @Test fun forcedSuccessDoesNotCorrectJudgmentOrConsumeLuck() {
        val base = state().copy(attributes = state().attributes.copy(work = 50, luck = 5),
            guaranteeTier = 50, guaranteePeriodDraws = 2, useLuckOnSubmission = true)
        val result = submit(base, false, false)
        assertEquals(false, result.lastReviewCorrect)
        assertEquals(true, result.lastResultSuccess)
        assertEquals(true, result.lastResultForced)
        assertEquals(5, result.attributes.luck)
        assertEquals(0, result.lastLuckUsed)
        assertEquals(10000, result.lastHireProbabilityBps)
    }

    @Test fun marksArePerCandidateRestorableAndStaleOrUnknownMarksAreRejected() {
        val engine = GameEngine(TestContent.ref(), ScriptedRandomSource(listOf(true)))
        var s = state()
        s = engine.apply(s, Command.MarkStatement(s.revision, s.saveId, "mark-a", TestContent.day0First, "school")).newState
        s = engine.apply(s, Command.MarkStatement(s.revision, s.saveId, "mark-b", TestContent.day0Second, "work-claim")).newState
        s = Json.decodeFromString(Json.encodeToString(s))
        assertEquals(mapOf(TestContent.day0First to "school", TestContent.day0Second to "work-claim"), s.markedStatements)
        assertFailsWith<EngineError.InvalidCommand> {
            engine.apply(s, Command.MarkStatement(s.revision, s.saveId, "bad-candidate", TestContent.id(2), "school"))
        }
        assertFailsWith<EngineError.InvalidCommand> {
            engine.apply(s, Command.MarkStatement(s.revision, s.saveId, "bad-statement", TestContent.day0First, "ghost"))
        }
        assertFailsWith<EngineError.InvalidCommand> {
            engine.apply(s, Command.SubmitCandidate(s.revision, s.saveId, "stale", TestContent.day0First, "work-claim"))
        }
        assertEquals(0, s.stats.reviewedSubmissionCount)
    }

    @Test fun validButUnmarkedStatementCannotBeSubmittedDirectly() {
        val engine = GameEngine(TestContent.ref(), ScriptedRandomSource(listOf(true)))
        val s = state()
        assertFailsWith<EngineError.InvalidCommand> {
            engine.apply(s, Command.SubmitCandidate(s.revision, s.saveId, "unmarked", TestContent.day0First, "school"))
        }
        assertEquals(0, s.stats.reviewedSubmissionCount)
    }

    @Test fun zeroLuckAndGuaranteedTurnsCannotEnableLuck() {
        val engine = GameEngine(TestContent.ref(), ScriptedRandomSource(listOf(true)))
        for (s in listOf(state(), state().copy(attributes = state().attributes.copy(work = 50, luck = 4),
            guaranteeTier = 50, guaranteePeriodDraws = 2))) {
            assertFailsWith<EngineError.InvalidCommand> {
                engine.apply(s, Command.SetLuckUsage(s.revision, s.saveId, "toggle", true))
            }
        }
    }
}
