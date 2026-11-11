package com.example.hrrepayment.data.save

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Fully controllable in-memory [DataStore] for ViewModel tests.
 *
 * The review asked for “可控仓库/作用域” rather than a serialization round-trip
 * pretending to be a storage test. [readError]/[writeError] let a test force the
 * I/O-failure paths deterministically; [writeCount] proves that a failed command
 * never reached the disk.
 */
class FakeDataStore(
    initial: SaveEnvelope = SaveEnvelope.Empty,
    var readError: Throwable? = null,
    var writeError: Throwable? = null,
) : DataStore<SaveEnvelope> {

    val value = MutableStateFlow(initial)

    var readCount: Int = 0
        private set

    var writeCount: Int = 0
        private set

    override val data: Flow<SaveEnvelope> = flow {
        readCount += 1
        readError?.let { throw it }
        emitAll(value)
    }

    override suspend fun updateData(
        transform: suspend (SaveEnvelope) -> SaveEnvelope,
    ): SaveEnvelope {
        writeError?.let { throw it }
        writeCount += 1
        val updated = transform(value.value)
        value.value = updated
        return updated
    }
}
