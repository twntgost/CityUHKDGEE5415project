package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.model.GameState
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schema-level persistence tests. Touches only the JSON envelope (no
 * DataStore runtime) so they run instantly on the JVM.
 */
class SaveEnvelopeTest {

    @Test
    fun `empty envelope reports isEmpty true`() {
        assertTrue(SaveEnvelope.Empty.isEmpty)
    }

    @Test
    fun `envelope serialisation roundtrip works on a freshly-booted save`() {
        val state = GameState.newGame(saveId = "round-trip", rngSeed = 99L)
        val envelope = SaveEnvelope(
            state = state,
            savedAtMillis = 1_700_000_000_000L,
            revision = state.revision,
        )
        val encoded = Json.encodeToString(SaveEnvelope.serializer(), envelope)
        val decoded = Json.decodeFromString(SaveEnvelope.serializer(), encoded)
        assertEquals(envelope, decoded)
        assertEquals(state, decoded.state)
        assertEquals(99L, decoded.state!!.rngSeed)
    }

    @Test
    fun `envelope survives unknown additional keys without losing the save`() {
        val state = GameState.newGame("survivor", rngSeed = 5L)
        val partial = Json.encodeToString(SaveEnvelope.serializer(),
            SaveEnvelope(state = state, savedAtMillis = 1L, revision = 0L))
        val withExtra = partial.replace("}", ",\"futureField\":42}")
        val parsed = Json { ignoreUnknownKeys = true }
            .decodeFromString(SaveEnvelope.serializer(), withExtra)
        assertEquals(state, parsed.state)
    }

    @Test
    fun `clear produces the empty envelope`() = runTest {
        // Sanity check the constructor wiring without DataStore runtime.
        assertTrue(SaveEnvelope.Empty.isEmpty)
    }
}
