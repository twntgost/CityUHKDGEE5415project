package com.example.hrrepayment.data.save

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.example.hrrepayment.core.model.GameState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Single-slot save layer (技术文档 §3).
 *
 * ## Contract
 *
 *  - A **missing file** is not an error: it reads as [SaveSnapshot.Empty], which is
 *    what the P1 review reproduced as a hard crash before this fix.
 *  - **Broken JSON** yields [SaveSnapshot.Corrupted] and leaves the original file
 *    untouched. There is deliberately **no** `ReplaceFileCorruptionHandler`: auto
 *    clearing the slot is exactly what the review forbade.
 *  - **Version drift** yields [SaveSnapshot.Incompatible] with the concrete versions.
 *  - **IO errors** yield [SaveSnapshot.IoFailure] and are retryable.
 *  - [read] never throws except for coroutine cancellation, so the ViewModel can
 *    always render a decision instead of crashing.
 */
class SaveRepository internal constructor(
    private val dataStore: DataStore<SaveEnvelope>,
    private val expectations: SaveExpectations,
    /** Owned scope; cancelled by [close] to release the file for another instance. */
    private val scope: CoroutineScope? = null,
    private val migration: ((GameState) -> GameState?)? = null,
    private val backingFile: File? = null,
) {

    /**
     * Release the underlying DataStore's claim on the file and wait until it is
     * actually released. DataStore refuses to open the same file twice in one
     * process, so a test that verifies “a new instance restores what was written”
     * must close the first one and await the completion handler. Production never
     * calls this because the app owns exactly one slot for its lifetime.
     */
    suspend fun close() {
        val job = scope?.coroutineContext?.get(Job) ?: return
        job.cancelAndJoin()
    }

    /** Cold flow of the current slot; never completes and never throws. */
    fun observe(): Flow<SaveSnapshot> = dataStore.data
        .map { it.toSnapshot() }
        .catch { emit(it.toFailureSnapshot()) }

    /** One-shot read used on boot and after an explicit retry. */
    suspend fun read(): SaveSnapshot = try {
        val envelope = dataStore.data.first()
        val previous = envelope.state
        val migrated = previous?.let { migration?.invoke(it) }
        if (migrated != null) {
            backingFile?.takeIf { it.exists() }?.let { file ->
                val backup = File(file.parentFile, "save-v1-${previous.saveId}.json.bak")
                if (!backup.exists()) {
                    val temporary = File.createTempFile("save-v1-", ".tmp", file.parentFile)
                    try {
                        FileOutputStream(temporary).use { output ->
                            file.inputStream().use { input -> input.copyTo(output) }
                            output.fd.sync()
                        }
                        if (!temporary.renameTo(backup)) throw IOException("Cannot publish legacy save backup")
                    } finally {
                        temporary.delete()
                    }
                }
            }
            dataStore.updateData { current ->
                if (current.state == previous) current.copy(state = migrated, revision = migrated.revision, savedAtMillis = System.currentTimeMillis()) else current
            }.toSnapshot()
        } else envelope.toSnapshot()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        e.toFailureSnapshot()
    }

    /** Persist the whole state. Returns [SaveWriteOutcome.Failed] instead of throwing. */
    suspend fun write(state: GameState): SaveWriteOutcome = try {
        dataStore.updateData {
            SaveEnvelope(
                state = state,
                savedAtMillis = System.currentTimeMillis(),
                revision = state.revision,
            )
        }
        SaveWriteOutcome.Ok
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        SaveWriteOutcome.Failed(describe(e))
    }

    /**
     * Explicitly remove the slot. Only called after the player confirms, which is
     * why it can succeed even when the on-disk JSON is unreadable (the serializer
     * reports corruption as data rather than throwing).
     */
    suspend fun clear(): SaveWriteOutcome = try {
        dataStore.updateData { SaveEnvelope.Empty }
        SaveWriteOutcome.Ok
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        SaveWriteOutcome.Failed(describe(e))
    }

    // ------------------------------------------------------------- mapping

    private fun SaveEnvelope.toSnapshot(): SaveSnapshot = when {
        corruption != null -> SaveSnapshot.Corrupted(corruption)
        state == null -> SaveSnapshot.Empty
        else -> validate(state, savedAtMillis)
    }

    private fun validate(state: GameState, savedAtMillis: Long): SaveSnapshot {
        if (state.saveSchemaVersion != expectations.schemaVersion) {
            return SaveSnapshot.Incompatible(
                reason = "存档结构版本不匹配",
                detail = "文件为 v${state.saveSchemaVersion}，当前需要 v${expectations.schemaVersion}",
            )
        }
        if (state.rulesVersion != expectations.rulesVersion) {
            return SaveSnapshot.Incompatible(
                reason = "数值规则版本不匹配",
                detail = "文件为 ${state.rulesVersion}，当前需要 ${expectations.rulesVersion}",
            )
        }
        if (state.contentVersion != expectations.contentVersion) {
            return SaveSnapshot.Incompatible(
                reason = "剧情内容版本不匹配",
                detail = "文件为 ${state.contentVersion}，当前需要 ${expectations.contentVersion}",
            )
        }
        val expectedHash = expectations.contentHash
        if (!expectedHash.isNullOrBlank()) {
            // 期望 hash 非空（本构建装载了导出内容）时，存档必须携带同一指纹：
            // 空 hash（占位时期旧档）与错误 hash 一律拒绝，避免静默加载错配内容。
            if (state.contentHash.isBlank()) {
                return SaveSnapshot.Incompatible(
                    reason = "存档缺少剧情内容校验",
                    detail = "该存档没有内容指纹（占位时期旧档），当前需要 $expectedHash",
                )
            }
            if (state.contentHash != expectedHash) {
                return SaveSnapshot.Incompatible(
                    reason = "剧情内容校验失败",
                    detail = "文件为 ${state.contentHash}，当前需要 $expectedHash",
                )
            }
        }
        return SaveSnapshot.Loaded(state = state, savedAtMillis = savedAtMillis)
    }

    private fun Throwable.toFailureSnapshot(): SaveSnapshot = when (this) {
        is CorruptionException -> SaveSnapshot.Corrupted(describe(this))
        is IOException -> SaveSnapshot.IoFailure(describe(this))
        else -> SaveSnapshot.IoFailure("未预期的读取错误：${describe(this)}")
    }

    companion object {
        fun create(
            context: Context,
            slot: SaveSlot = SaveSlot.SLOT_1,
            expectations: SaveExpectations = SaveExpectations.current(),
            migration: ((GameState) -> GameState?)? = null,
        ): SaveRepository {
            val file = context.dataStoreFile(slot.fileName).apply { parentFile?.mkdirs() }
            return create(file = file, expectations = expectations, migration = migration)
        }

        fun create(
            file: File,
            expectations: SaveExpectations = SaveExpectations.current(),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            migration: ((GameState) -> GameState?)? = null,
        ): SaveRepository {
            val ds: DataStore<SaveEnvelope> = DataStoreFactory.create(
                serializer = SaveEnvelopeSerializer(),
                // No corruptionHandler on purpose: DataStore's default no-op handler
                // rethrows, and our serializer reports JSON damage as data.
                produceFile = { file },
                scope = scope,
            )
            return SaveRepository(ds, expectations, scope, migration, file)
        }
    }
}

/**
 * JSON codec for [SaveEnvelope].
 *
 * `defaultValue` is [SaveEnvelope.Empty] — DataStore probes it *before* any file
 * exists, so throwing here (the P1 behaviour) made the very first launch crash
 * (review item #1).
 */
private class SaveEnvelopeSerializer : Serializer<SaveEnvelope> {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue: SaveEnvelope = SaveEnvelope.Empty

    override suspend fun readFrom(input: InputStream): SaveEnvelope {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return SaveEnvelope(corruption = "存档文件为空")
        }
        return try {
            json.decodeFromString(SaveEnvelope.serializer(), bytes.decodeToString())
        } catch (e: SerializationException) {
            SaveEnvelope(corruption = "存档 JSON 解析失败：${describe(e)}")
        } catch (e: IllegalArgumentException) {
            SaveEnvelope(corruption = "存档内容不合法：${describe(e)}")
        }
    }

    override suspend fun writeTo(t: SaveEnvelope, output: OutputStream) {
        // The corruption marker is read-only metadata; never persist it.
        val payload = if (t.isCorrupted) SaveEnvelope.Empty else t
        output.write(json.encodeToString(SaveEnvelope.serializer(), payload).encodeToByteArray())
    }
}
