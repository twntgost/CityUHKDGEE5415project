package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.model.GameState
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Real DataStore tests (技术文档 §3 / review item #4).
 *
 * These drive the actual `androidx.datastore` runtime against a temporary file —
 * *not* a serialization round-trip — because the P1 defect was a runtime crash on
 * first launch that a codec-only test could never have caught.
 *
 * Coverage demanded by the review:
 *  - 首次无文件 → Empty（不再抛 UnsupportedOperationException）
 *  - 写入后以**新实例**恢复（旧实例先 close 释放文件占用）
 *  - 损坏 / 截断 JSON → Corrupted 且原文件保留
 *  - 未知 schema / rules / content → Incompatible
 *  - I/O 失败 → IoFailure（不崩溃）
 */
class SaveRepositoryDataStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val opened = mutableListOf<SaveRepository>()

    private val saveFile: File get() = File(temporaryFolder.root, "save.json")

    private fun open(
        expectations: SaveExpectations = SaveExpectations.current(),
    ): SaveRepository =
        SaveRepository.create(file = saveFile, expectations = expectations).also { opened += it }

    private suspend fun close(repo: SaveRepository) {
        repo.close()
        opened.remove(repo)
    }

    private fun writeRaw(envelope: SaveEnvelope) {
        saveFile.writeText(Json.encodeToString(SaveEnvelope.serializer(), envelope))
    }

    @After
    fun tearDown() {
        runBlocking {
            opened.forEach { it.close() }
        }
        opened.clear()
    }

    // ----------------------------------------------------------- first launch

    @Test
    fun `a slot with no file yet reads as Empty instead of crashing`() = runTest {
        assertTrue("precondition: no file on disk", !saveFile.exists())

        val snapshot = open().read()

        assertEquals(SaveSnapshot.Empty, snapshot)
    }

    // ----------------------------------------------------- write / re-instance

    @Test
    fun `a written state is restored by a brand new repository instance`() = runTest {
        val state = GameState.newGame(saveId = "slot-1", rngSeed = 4242L)
            .copy(revision = 3L, lastAppliedCommandId = "cmd-3")

        val first = open()
        assertEquals(SaveWriteOutcome.Ok, first.write(state))
        assertTrue("the slot file must exist after a successful write", saveFile.exists())
        close(first)

        val second = open()
        val snapshot = second.read()

        assertTrue("expected Loaded, got $snapshot", snapshot is SaveSnapshot.Loaded)
        val loaded = (snapshot as SaveSnapshot.Loaded).state
        assertEquals(state, loaded)
        assertEquals(3L, loaded.revision)
        assertEquals(4242L, loaded.rngSeed)
        assertEquals(4242L, loaded.rngState)
        assertEquals("cmd-3", loaded.lastAppliedCommandId)
    }

    @Test
    fun `writing a new run over an old one replaces the save identity on disk`() = runTest {
        val repo = open()
        repo.write(GameState.newGame("slot-old", 1L).copy(revision = 5L))
        repo.write(GameState.newGame("slot-new", 99L).copy(revision = 6L))

        val loaded = (repo.read() as SaveSnapshot.Loaded).state
        assertEquals("slot-new", loaded.saveId)
        assertEquals(99L, loaded.rngSeed)
        assertEquals(6L, loaded.revision)
    }

    // ------------------------------------------------------------ corruption

    @Test
    fun `garbage JSON is reported as Corrupted and the original file is preserved`() = runTest {
        val garbage = "{ this is definitely not json"
        saveFile.writeText(garbage)

        val snapshot = open().read()

        assertTrue("expected Corrupted, got $snapshot", snapshot is SaveSnapshot.Corrupted)
        assertEquals("a broken save must not be silently rewritten", garbage, saveFile.readText())
    }

    @Test
    fun `a truncated save is Corrupted and never silently replaced`() = runTest {
        val valid = Json.encodeToString(
            SaveEnvelope.serializer(),
            SaveEnvelope(state = GameState.newGame("truncated", 1L)),
        )
        val truncated = valid.substring(0, valid.length / 2)
        saveFile.writeText(truncated)

        val snapshot = open().read()

        assertTrue("expected Corrupted, got $snapshot", snapshot is SaveSnapshot.Corrupted)
        assertEquals(truncated, saveFile.readText())
    }

    @Test
    fun `an empty file is corruption, not a valid empty save`() = runTest {
        saveFile.writeText("")

        val snapshot = open().read()

        assertTrue("expected Corrupted, got $snapshot", snapshot is SaveSnapshot.Corrupted)
    }

    @Test
    fun `clear can still reset a corrupted slot after the player confirms`() = runTest {
        saveFile.writeText("{ broken")
        val repo = open()
        assertTrue(repo.read() is SaveSnapshot.Corrupted)

        assertEquals(SaveWriteOutcome.Ok, repo.clear())

        assertEquals(SaveSnapshot.Empty, repo.read())
    }

    // ---------------------------------------------------------- version drift

    @Test
    fun `an unknown schema version is Incompatible and is not loaded`() = runTest {
        writeRaw(SaveEnvelope(state = GameState.newGame("future", 1L).copy(saveSchemaVersion = 99)))

        val snapshot = open().read()

        assertTrue("expected Incompatible, got $snapshot", snapshot is SaveSnapshot.Incompatible)
        // This is the exact case the P1 review reproduced: rulesVersion=999 used to Load.
        assertTrue((snapshot as SaveSnapshot.Incompatible).detail.contains("99"))
    }

    @Test
    fun `an unknown rules version is Incompatible and is not loaded`() = runTest {
        writeRaw(SaveEnvelope(state = GameState.newGame("future", 1L).copy(rulesVersion = "999")))

        val snapshot = open().read()

        assertTrue("expected Incompatible, got $snapshot", snapshot is SaveSnapshot.Incompatible)
        assertTrue((snapshot as SaveSnapshot.Incompatible).detail.contains("999"))
    }

    @Test
    fun `an unknown content version is Incompatible and is not loaded`() = runTest {
        writeRaw(SaveEnvelope(state = GameState.newGame("future", 1L).copy(contentVersion = "story-v0")))

        val snapshot = open().read()

        assertTrue("expected Incompatible, got $snapshot", snapshot is SaveSnapshot.Incompatible)
        assertTrue((snapshot as SaveSnapshot.Incompatible).detail.contains("story-v0"))
    }

    @Test
    fun `a content hash mismatch is Incompatible when the build pins a hash`() = runTest {
        writeRaw(SaveEnvelope(state = GameState.newGame("future", 1L).copy(contentHash = "old-hash")))
        val expectations = SaveExpectations.current(contentHash = "new-hash")

        val snapshot = open(expectations).read()

        assertTrue("expected Incompatible, got $snapshot", snapshot is SaveSnapshot.Incompatible)
        assertTrue((snapshot as SaveSnapshot.Incompatible).reason.contains("校验"))
    }

    // ------------------------------------------------------------------- clear

    @Test
    fun `clear empties a healthy slot and a later read is Empty`() = runTest {
        val repo = open()
        assertEquals(SaveWriteOutcome.Ok, repo.write(GameState.newGame("slot-1", 7L)))
        assertTrue(repo.read() is SaveSnapshot.Loaded)

        assertEquals(SaveWriteOutcome.Ok, repo.clear())

        assertEquals(SaveSnapshot.Empty, repo.read())
    }

    // ----------------------------------------------------------------- I/O fail

    @Test
    fun `an unreadable path surfaces as IoFailure instead of crashing the screen`() = runTest {
        // Scalar the save path into a directory so opening it as a file fails.
        saveFile.mkdirs()
        File(saveFile, "occupied.txt").writeText("not a save")

        val snapshot = open().read()

        assertTrue("expected IoFailure, got $snapshot", snapshot is SaveSnapshot.IoFailure)
    }
}
