package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.CalendarPosition
import com.example.hrrepayment.core.model.Command
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.model.WorkdayRef
import com.example.hrrepayment.core.money.Money
import com.example.hrrepayment.core.random.RandomSnapshot
import com.example.hrrepayment.core.random.RandomSource
import com.example.hrrepayment.core.random.SplitMix64Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameEngineTest {

    private val content = TestContent.ref()

    private fun bootEngine(random: RandomSource = SplitMix64Random(seed = 1L)) =
        GameEngine(content = content, random = random)

    private fun ack(
        state: GameState,
        commandId: String = "cmd-ack",
        revision: Long = state.revision,
        saveId: String = state.saveId,
    ) = Command.AcknowledgeOpening(
        expectedRevision = revision,
        saveId = saveId,
        commandId = commandId,
    )

    private fun freshCommand(
        newSaveId: String = "new-save",
        seed: Long = 1L,
        commandId: String = "cmd-new",
        saveId: String = Command.NO_SAVE_ID,
        revision: Long = 0L,
    ) = Command.StartNewGame(
        expectedRevision = revision,
        saveId = saveId,
        commandId = commandId,
        newSaveId = newSaveId,
        newRngSeed = seed,
    )

    // ------------------------------------------------------------- happy path

    @Test
    fun `startNewGame creates the first save of an empty slot at revision zero`() {
        val result = bootEngine().apply(null, freshCommand(newSaveId = "slot-1"))

        assertEquals("slot-1", result.newState.saveId)
        assertEquals(0L, result.newState.revision)
        assertEquals(Phase.Opening, result.newState.phase)
        assertEquals("cmd-new", result.newState.lastAppliedCommandId)
        assertNull(result.previousState)
    }

    @Test
    fun `acknowledgeOpening moves Opening to DayEntry and bumps revision`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand()).newState

        val result = engine.apply(fresh, ack(fresh))

        assertEquals(Phase.DayEntry, result.newState.phase)
        assertEquals(1L, result.newState.revision)
        assertEquals(fresh, result.previousState)
        assertEquals("cmd-ack", result.newState.lastAppliedCommandId)
        assertTrue(
            result.effects.any { it is EngineEffect.PhaseChanged && it.phase == Phase.DayEntry },
        )
    }

    // ---------------------------------------------------------------- identity

    @Test
    fun `revision mismatch is rejected with EngineError instead of IllegalArgumentException`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand()).newState

        assertFailsWith<EngineError.RevisionMismatch> {
            engine.apply(fresh, ack(fresh, revision = 99L))
        }
    }

    @Test
    fun `a command built for another save slot is rejected`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand(newSaveId = "slot-A")).newState

        assertFailsWith<EngineError.SaveIdMismatch> {
            engine.apply(fresh, ack(fresh, saveId = "slot-B"))
        }
    }

    @Test
    fun `replaying the same command id is rejected so a double tap cannot apply twice`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand()).newState
        val once = engine.apply(fresh, ack(fresh, commandId = "tap-1")).newState

        assertFailsWith<EngineError.DuplicateCommand> {
            engine.apply(once, ack(once, commandId = "tap-1"))
        }
    }

    @Test
    fun `a blank command id is rejected`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand()).newState

        assertFailsWith<EngineError.InvalidCommand> {
            engine.apply(fresh, ack(fresh, commandId = "   "))
        }
    }

    @Test
    fun `the first command of an empty slot must use the NO_SAVE_ID sentinel`() {
        assertFailsWith<EngineError.InvalidCommand> {
            bootEngine().apply(null, freshCommand(saveId = "someone-elses-save"))
        }
    }

    @Test
    fun `the first command of an empty slot must still declare revision zero`() {
        assertFailsWith<EngineError.RevisionMismatch> {
            bootEngine().apply(null, freshCommand(revision = 5L))
        }
    }

    @Test
    fun `any command other than StartNewGame is refused when no save exists`() {
        val engine = bootEngine()
        val someState = GameState.newGame("s", 1L)

        assertFailsWith<EngineError.NoSave> {
            engine.apply(null, Command.FinishEconomy(0L, "s", "cmd-1"))
        }
        assertFailsWith<EngineError.NoSave> { engine.apply(null, ack(someState)) }
    }

    // ----------------------------------------------------------- version guards

    @Test
    fun `a save with an unknown rules version is refused by the engine`() {
        val engine = bootEngine()
        val tampered = GameState.newGame("s", 1L).copy(rulesVersion = "999")

        assertFailsWith<EngineError.RulesUnsupported> { engine.apply(tampered, ack(tampered)) }
    }

    @Test
    fun `a save with an unknown schema version is refused by the engine`() {
        val engine = bootEngine()
        val tampered = GameState.newGame("s", 1L).copy(saveSchemaVersion = 99)

        assertFailsWith<EngineError.SchemaUnsupported> { engine.apply(tampered, ack(tampered)) }
    }

    @Test
    fun `a save bound to different content is refused by the engine`() {
        val engine = bootEngine()
        val tampered = GameState.newGame("s", 1L).copy(contentVersion = "story-v0")

        assertFailsWith<EngineError.ContentUnsupported> { engine.apply(tampered, ack(tampered)) }
    }

    // ----------------------------------------------------------- deferred work

    @Test
    fun `unimplemented commands are rejected with a descriptive WrongPhase error`() {
        val engine = bootEngine()
        val fresh = engine.apply(null, freshCommand()).newState

        val ex = assertFailsWith<EngineError.WrongPhase> {
            engine.apply(fresh, Command.FinishEconomy(0L, fresh.saveId, "cmd-x"))
        }
        assertTrue(ex.message!!.contains("FinishEconomy"))
    }

    // ----------------------------------------------------------- restart path

    @Test
    fun `restarting an existing save mints a new save id and keeps counting revisions`() {
        val engine = bootEngine()
        val started = engine.apply(null, freshCommand(newSaveId = "slot-old")).newState
        val dirty = started.copy(
            phase = Phase.DayEntry,
            position = CalendarPosition(current = WorkdayRef(50)),
            cash = Money(1L),
            revision = 7L,
        )

        val result = engine.apply(
            dirty,
            freshCommand(
                newSaveId = "slot-new",
                seed = 99L,
                commandId = "cmd-restart",
                saveId = "slot-old",
                revision = 7L,
            ),
        )

        assertEquals("slot-new", result.newState.saveId)
        assertEquals(8L, result.newState.revision)
        assertEquals(WorkdayRef.FIRST, result.newState.position.current)
        assertEquals(Phase.Opening, result.newState.phase)
        assertEquals(Money(600_000L), result.newState.cash)
        assertEquals(dirty, result.previousState)
    }

    @Test
    fun `restarting requires the loaded save identity so a stray tap cannot wipe a run`() {
        val engine = bootEngine()
        val started = engine.apply(null, freshCommand(newSaveId = "slot-old")).newState

        assertFailsWith<EngineError.SaveIdMismatch> {
            engine.apply(
                started,
                freshCommand(newSaveId = "slot-new", saveId = "wrong", revision = 0L),
            )
        }
    }

    // ------------------------------------------------------------ rng plumbing

    @Test
    fun `the engine restores the persisted rng position before every command`() {
        val recorder = RecordingRandomSource()
        val engine = bootEngine(random = recorder)

        val started = engine.apply(null, freshCommand(seed = 4242L)).newState
        recorder.restores.clear()

        engine.apply(started, ack(started))

        assertEquals(listOf(RandomSnapshot.atSeed(4242L)), recorder.restores)
    }

    @Test
    fun `the post-command rng position is written back into the new state`() {
        val engine = bootEngine(random = AdvancingRandomSource(step = 1L))
        val fresh = engine.apply(null, freshCommand(seed = 100L)).newState
        // StartNewGame resets to the requested seed, then apply() records +1.
        assertEquals(101L, fresh.rngState)

        val acked = engine.apply(fresh, ack(fresh)).newState
        // The generator resumes from the persisted 101 and records 102.
        assertEquals(102L, acked.rngState)
        assertEquals(101L, fresh.rngState)
    }

    @Test
    fun `acknowledging does not draw so the position of a real generator is untouched`() {
        val engine = bootEngine(random = SplitMix64Random(1L))
        val fresh = engine.apply(null, freshCommand(seed = 100L)).newState
        val acked = engine.apply(fresh, ack(fresh)).newState

        assertEquals(100L, acked.rngState)
        assertEquals(0L, acked.rngDrawCount)
        assertEquals(100L, acked.rngSeed)
    }

    /** Records every [restore] so “restore before command” is observable. */
    private class RecordingRandomSource : RandomSource {
        val restores = mutableListOf<RandomSnapshot>()
        private val delegate = SplitMix64Random(0L)

        override fun nextInt(from: Int, until: Int): Int = delegate.nextInt(from, until)
        override fun nextLong(): Long = delegate.nextLong()
        override fun chance(bps: Int): Boolean = delegate.chance(bps)
        override fun snapshot(): RandomSnapshot = delegate.snapshot()
        override fun restore(snapshot: RandomSnapshot) {
            restores += snapshot
            delegate.restore(snapshot)
        }
    }

    /**
     * Reports a position that is always one step ahead of what it was restored to,
     * making the engine's snapshot write-back directly observable.
     */
    private class AdvancingRandomSource(private val step: Long) : RandomSource {
        private var state: Long = 0L
        private var drawCount: Long = 0L

        override fun nextInt(from: Int, until: Int): Int = from
        override fun nextLong(): Long = state
        override fun chance(bps: Int): Boolean = false

        override fun snapshot(): RandomSnapshot = RandomSnapshot(
            seed = state,
            state = state + step,
            drawCount = drawCount,
        )

        override fun restore(snapshot: RandomSnapshot) {
            state = snapshot.state
            drawCount = snapshot.drawCount
        }
    }
}
