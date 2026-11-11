package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.model.GameState
import kotlinx.serialization.Serializable

/**
 * On-disk shape of the single save slot.
 *
 * ## Why there is a `corruption` field in a *persisted* class
 *
 * 技术文档 §3 forbids auto-clearing a broken save: “存档错误不能静默重置…用户确认前
 * 保留旧文件”. DataStore's `ReplaceFileCorruptionHandler { Empty }` would have
 * silently replaced the file the instant a decode failed, so it is gone.
 *
 * Instead [SaveRepository]'s serializer never throws for JSON problems: it returns
 * an envelope carrying [corruption] so that
 *  - the flow/`updateData` path keeps working (an explicit reset can still write),
 *  - the UI can tell the player the file is preserved, and
 *  - nothing rewrites the file until the player confirms.
 *
 * [corruption] is never written to disk; [SaveRepository]'s serializer maps it back
 * to [Empty] on write.
 */
@Serializable
data class SaveEnvelope(
    val state: GameState? = null,
    val savedAtMillis: Long = 0L,
    val revision: Long = 0L,
    val corruption: String? = null,
) {
    val isEmpty: Boolean get() = state == null && corruption == null

    val isCorrupted: Boolean get() = corruption != null

    companion object {
        val Empty = SaveEnvelope()
    }
}
