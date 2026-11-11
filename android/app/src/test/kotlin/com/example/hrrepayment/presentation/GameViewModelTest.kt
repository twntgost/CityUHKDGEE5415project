package com.example.hrrepayment.presentation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.hrrepayment.AppTestContent
import com.example.hrrepayment.audio.AudioPlayer
import com.example.hrrepayment.audio.AudioVariantPicker
import com.example.hrrepayment.audio.GameAudio
import com.example.hrrepayment.core.engine.GameEngine
import com.example.hrrepayment.core.i18n.Lang
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.core.model.HiredRecord
import com.example.hrrepayment.core.model.Phase
import com.example.hrrepayment.core.random.SplitMix64Random
import com.example.hrrepayment.data.save.FakeDataStore
import com.example.hrrepayment.data.save.SaveEnvelope
import com.example.hrrepayment.data.save.SaveExpectations
import com.example.hrrepayment.data.save.SaveRepository
import com.example.hrrepayment.data.save.SaveSlot
import com.example.hrrepayment.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Presentation-layer contract tests for the v3 shell.
 *
 * Two save slots, a device-wide language and the dossier all live in the ViewModel,
 * so these tests pin the invariants that matter:
 *  - a failed write must **not** publish the new state and must leave a retry;
 *  - a rapid double tap must not mint two saves;
 *  - overwriting an occupied slot requires the explicit confirmation;
 *  - a corrupted slot blocks with the file preserved;
 *  - switching language persists and swaps the content bundle behind the engine;
 *  - the dossier is resolved from content, never stored as text.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val content = AppTestContent.ref()

    private class RecordingAudioPlayer : AudioPlayer {
        val played = mutableListOf<String>()
        override fun play(assetPath: String) { played += assetPath }
        override fun stop() {}
    }

    /** In-memory preferences store; no file, no Android dependency. */
    private class FakePreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }

    private class Fixture(
        val stores: Map<SaveSlot, FakeDataStore>,
        val settings: SettingsRepository,
        val viewModel: GameViewModel,
        val languages: MutableList<Lang>,
    )

    private fun fixture(
        slot1: FakeDataStore = FakeDataStore(),
        slot2: FakeDataStore = FakeDataStore(),
        idGenerator: () -> String = SequentialIds("cmd")::take,
    ): Fixture {
        val stores = mapOf(SaveSlot.SLOT_1 to slot1, SaveSlot.SLOT_2 to slot2)
        val repos = stores.mapValues { (_, store) ->
            SaveRepository(store, SaveExpectations.current())
        }
        val engine = GameEngine(content, SplitMix64Random(seed = 7L))
        val audio = GameAudio(RecordingAudioPlayer(), AudioVariantPicker(SplitMix64Random(seed = 99L)))
        val settings = SettingsRepository(FakePreferences())
        val languages = mutableListOf<Lang>()
        val vm = GameViewModel(
            engine = engine,
            slots = repos,
            settings = settings,
            content = content,
            audio = audio,
            onLanguageChange = { languages += it },
            idGenerator = idGenerator,
            seedGenerator = { 1234L },
        )
        return Fixture(stores, settings, vm, languages)
    }

    private fun menu(vm: GameViewModel): Screen.Menu = vm.uiState.value.screen as Screen.Menu

    // ------------------------------------------------------------------ boot

    @Test
    fun `boot reads both slots and lands on the poster menu`() = runTest(dispatcher) {
        val f = fixture(slot2 = FakeDataStore(SaveEnvelope(state = GameState.newGame("s2", 5).copy(phase = Phase.DayEntry))))
        advanceUntilIdle()

        val screen = menu(f.viewModel)
        assertEquals(MenuPanel.START, screen.panel)
        assertEquals(2, screen.slots.size)
        assertTrue(screen.slots.first { it.slot == SaveSlot.SLOT_1 }.isEmpty)
        assertTrue(screen.slots.first { it.slot == SaveSlot.SLOT_2 }.isPlayable)
    }

    // -------------------------------------------------------------- new game

    @Test
    fun `new game on an empty slot starts a run and persists it there`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()

        f.viewModel.requestNewGame(SaveSlot.SLOT_2)
        advanceUntilIdle()

        val run = f.viewModel.uiState.value.screen as Screen.Run
        assertEquals(SaveSlot.SLOT_2, run.slot)
        assertEquals(Phase.Opening, run.state.phase)
        assertEquals(1, f.stores.getValue(SaveSlot.SLOT_2).writeCount)
        assertEquals(0, f.stores.getValue(SaveSlot.SLOT_1).writeCount)
        assertNull(f.viewModel.uiState.value.pendingOverwrite)
    }

    @Test
    fun `new game on an occupied slot asks first and writes nothing`() = runTest(dispatcher) {
        val occupied = GameState.newGame("old", 3).copy(phase = Phase.DayEntry, revision = 4L)
        val f = fixture(slot1 = FakeDataStore(SaveEnvelope(state = occupied)))
        advanceUntilIdle()

        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()

        assertEquals(SaveSlot.SLOT_1, f.viewModel.uiState.value.pendingOverwrite)
        assertEquals(0, f.stores.getValue(SaveSlot.SLOT_1).writeCount)
        assertTrue(f.viewModel.uiState.value.screen is Screen.Menu)
    }

    @Test
    fun `confirming the overwrite replaces the run in that slot only`() = runTest(dispatcher) {
        val occupied = GameState.newGame("old", 3).copy(phase = Phase.DayEntry, revision = 4L)
        val f = fixture(slot1 = FakeDataStore(SaveEnvelope(state = occupied)))
        advanceUntilIdle()

        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()
        f.viewModel.confirmOverwrite()
        advanceUntilIdle()

        val run = f.viewModel.uiState.value.screen as Screen.Run
        assertEquals(Phase.Opening, run.state.phase)
        assertEquals("cmd-2", run.state.saveId)
        assertNull(f.viewModel.uiState.value.pendingOverwrite)
    }

    // ---------------------------------------------------------------- loading

    @Test
    fun `load game on an empty slot shows the empty banner and stays on the menu`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()

        f.viewModel.loadGame(SaveSlot.SLOT_1)
        advanceUntilIdle()

        assertTrue(f.viewModel.uiState.value.screen is Screen.Menu)
        assertNotNull(f.viewModel.uiState.value.banner)
    }

    @Test
    fun `corrupted slot blocks with the file preserved and retry offered`() = runTest(dispatcher) {
        val store = FakeDataStore(SaveEnvelope(state = null, corruption = "bad json"))
        val f = fixture(slot1 = store)
        advanceUntilIdle()

        f.viewModel.loadGame(SaveSlot.SLOT_1)
        advanceUntilIdle()

        val problem = f.viewModel.uiState.value.blockProblem
        assertNotNull(problem)
        assertEquals(SaveProblem.Kind.CORRUPTED, problem!!.kind)
        assertTrue(problem.canRetry)
        assertTrue(problem.canReset)
        assertEquals(SaveSlot.SLOT_1, f.viewModel.uiState.value.problemSlot)
        // 损坏的槽位没有被改写：清档只发生在玩家确认后。
        assertEquals(0, store.writeCount)
    }

    // ------------------------------------------------------------ write paths

    @Test
    fun `a failed write keeps the previous state on screen and never publishes`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()
        val before = (f.viewModel.uiState.value.screen as Screen.Run).state

        f.stores.getValue(SaveSlot.SLOT_1).writeError = IOException("disk full")
        f.viewModel.acknowledgeOpening()
        advanceUntilIdle()

        val after = f.viewModel.uiState.value.screen as Screen.Run
        assertEquals(before.revision, after.state.revision)
        assertEquals(Phase.Opening, after.state.phase)
        assertNotNull(f.viewModel.uiState.value.banner)
    }

    @Test
    fun `retry after clearing the fault commits exactly once`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()
        val store = f.stores.getValue(SaveSlot.SLOT_1)

        store.writeError = IOException("disk full")
        f.viewModel.acknowledgeOpening()
        advanceUntilIdle()
        val writesAfterFailure = store.writeCount

        store.writeError = null
        f.viewModel.acknowledgeOpening()
        advanceUntilIdle()

        assertEquals(writesAfterFailure + 1, store.writeCount)
        assertEquals(Phase.DayEntry, (f.viewModel.uiState.value.screen as Screen.Run).state.phase)
    }

    @Test
    fun `a double tap creates only one save`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()

        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()

        assertEquals(1, f.stores.getValue(SaveSlot.SLOT_1).writeCount)
    }

    // ------------------------------------------------------------- language

    @Test
    fun `language toggle persists and swaps the content bundle`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        assertEquals(Lang.ZH, f.viewModel.lang.value)

        f.viewModel.toggleLanguage()
        advanceUntilIdle()

        assertEquals(Lang.EN, f.viewModel.lang.value)
        assertEquals(listOf(Lang.EN), f.languages)
        assertEquals(Lang.EN, f.settings.currentLanguage())
    }

    // -------------------------------------------------------------- dossier

    @Test
    fun `dossier resolves hires from content and is empty for a fresh run`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()

        f.viewModel.openDossiers()
        advanceUntilIdle()
        assertEquals(0, f.viewModel.uiState.value.dossier?.size)
    }

    @Test
    fun `dossier lists every successful push of the active run`() = runTest(dispatcher) {
        val hiredId = content.workdayCandidates(0).first().id
        val state = GameState.newGame("run", 11)
            .copy(phase = Phase.DayEntry, hired = listOf(HiredRecord(hiredId, 0)))
        val f = fixture(slot1 = FakeDataStore(SaveEnvelope(state = state)))
        advanceUntilIdle()

        f.viewModel.loadGame(SaveSlot.SLOT_1)
        advanceUntilIdle()
        f.viewModel.openDossiers()
        advanceUntilIdle()

        val entries = f.viewModel.uiState.value.dossier
        assertNotNull(entries)
        assertEquals(1, entries!!.size)
        assertEquals(hiredId, entries[0].candidateId)
        assertTrue(entries[0].name.isNotBlank())
        f.viewModel.closeDossiers()
        advanceUntilIdle()
        assertFalse(f.viewModel.uiState.value.isDossierOpen)
    }

    @Test
    fun `the dossier never opens itself on a save or a language switch`() = runTest(dispatcher) {
        val f = fixture()
        advanceUntilIdle()
        assertFalse(f.viewModel.uiState.value.isDossierOpen)

        f.viewModel.requestNewGame(SaveSlot.SLOT_1)
        advanceUntilIdle()
        assertFalse("保存后档案袋不应自动弹出", f.viewModel.uiState.value.isDossierOpen)

        f.viewModel.acknowledgeOpening()
        advanceUntilIdle()
        assertFalse("推进阶段后档案袋不应自动弹出", f.viewModel.uiState.value.isDossierOpen)

        f.viewModel.toggleLanguage()
        advanceUntilIdle()
        assertFalse("切换语言后档案袋不应自动弹出", f.viewModel.uiState.value.isDossierOpen)

        // 只有显式打开才出现，关闭后保持关闭。
        f.viewModel.openDossiers()
        advanceUntilIdle()
        assertTrue(f.viewModel.uiState.value.isDossierOpen)
        f.viewModel.closeDossiers()
        advanceUntilIdle()
        assertFalse(f.viewModel.uiState.value.isDossierOpen)
        f.viewModel.acknowledgeOpening()
        advanceUntilIdle()
        assertFalse(f.viewModel.uiState.value.isDossierOpen)
    }

    // --------------------------------------------------------------- deletion

    @Test
    fun `delete slot clears only the chosen slot and returns to the menu`() = runTest(dispatcher) {
        val keep = GameState.newGame("keep", 2).copy(phase = Phase.DayEntry)
        val f = fixture(
            slot1 = FakeDataStore(SaveEnvelope(state = GameState.newGame("drop", 1))),
            slot2 = FakeDataStore(SaveEnvelope(state = keep)),
        )
        advanceUntilIdle()

        f.viewModel.deleteSlot(SaveSlot.SLOT_1)
        advanceUntilIdle()

        val screen = menu(f.viewModel)
        assertTrue(screen.slots.first { it.slot == SaveSlot.SLOT_1 }.isEmpty)
        assertTrue(screen.slots.first { it.slot == SaveSlot.SLOT_2 }.isPlayable)
    }
}

/** Deterministic command ids so a test can assert on the minted save id. */
private class SequentialIds(private val prefix: String) {
    private var n = 0
    fun take(): String = "$prefix-${++n}"
}
