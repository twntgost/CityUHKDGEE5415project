package com.example.hrrepayment

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hrrepayment.core.model.GameState
import com.example.hrrepayment.data.save.SaveExpectations
import com.example.hrrepayment.data.save.SaveRepository
import com.example.hrrepayment.data.save.SaveSnapshot
import com.example.hrrepayment.data.save.SaveWriteOutcome
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * On-device storage recovery, using the real DataStore runtime in the app's files
 * directory. `aWrittenRunSurvivesANewRepositoryInstance` is the in-process proxy
 * for “杀进程再开”: the same bytes on disk are decoded by a fresh instance.
 */
@RunWith(AndroidJUnit4::class)
class SaveRecoveryInstrumentedTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val file: File
        get() = File(context.filesDir, "instrumented_save.json")

    private val opened = mutableListOf<SaveRepository>()

    private fun open(): SaveRepository =
        SaveRepository.create(
            file = file,
            expectations = SaveExpectations.current(),
        ).also { opened += it }

    @After
    fun cleanUp() {
        runBlocking {
            opened.forEach { it.close() }
        }
        opened.clear()
        file.delete()
    }

    @Test
    fun aFirstLaunchWithNoFileReadsAsEmpty() {
        runBlocking {
            file.delete()

            assertEquals(SaveSnapshot.Empty, open().read())
        }
    }

    @Test
    fun aWrittenRunSurvivesANewRepositoryInstance() {
        runBlocking {
            file.delete()
            val state = GameState.newGame(saveId = "device-slot", rngSeed = 2026L)
                .copy(revision = 2L, lastAppliedCommandId = "cmd-2",
                    specialLoanDecision = true,
                    specialLoanInvitationRead = true,
                    specialLoanDue = com.example.hrrepayment.core.money.Money(15_000_000),
                    betting2017Base = com.example.hrrepayment.core.money.Money(1_000_000),
                    betting2017StartCount = 1,
                    acceptedBetCount = 1,
                    betOptionOrder = mapOf("market" to listOf("away", "home")),
                    activeBet = com.example.hrrepayment.core.model.BetSnapshot("market", "home",
                        com.example.hrrepayment.core.money.Money(50_000), 1, 20,
                        com.example.hrrepayment.core.money.Money(500_000), 1))

            val first = open()
            assertEquals(SaveWriteOutcome.Ok, first.write(state))
            first.close()
            opened.remove(first)

            val restored = open().read()

            assertTrue("expected Loaded, got $restored", restored is SaveSnapshot.Loaded)
            assertEquals(state, (restored as SaveSnapshot.Loaded).state)
            assertEquals("device-slot", restored.state.saveId)
            assertTrue(file.exists())
        }
    }

    @Test
    fun corruptedJsonStaysOnDiskAndBlocksInsteadOfCrashing() {
        runBlocking {
            val garbage = "{ not json at all"
            file.writeText(garbage)

            val snapshot = open().read()

            assertTrue("expected Corrupted, got $snapshot", snapshot is SaveSnapshot.Corrupted)
            assertEquals(garbage, file.readText())
        }
    }
}
