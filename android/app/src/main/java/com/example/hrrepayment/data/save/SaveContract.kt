package com.example.hrrepayment.data.save

import com.example.hrrepayment.core.model.GameState

/**
 * What the running build is willing to load. Kept separate from [SaveRepository]
 * so a test can demand a specific expectation without touching global constants.
 */
data class SaveExpectations(
    val schemaVersion: Int,
    val rulesVersion: String,
    val contentVersion: String,
    /** `null` or blank disables the content-hash check (used before P4 exports exist). */
    val contentHash: String? = null,
) {
    companion object {
        fun current(contentHash: String? = null): SaveExpectations = SaveExpectations(
            schemaVersion = GameState.CURRENT_SCHEMA_VERSION,
            rulesVersion = GameState.EXPECTED_RULES_VERSION,
            contentVersion = GameState.EXPECTED_CONTENT_VERSION,
            contentHash = contentHash,
        )
    }
}

/**
 * Result of reading the slot. [SaveRepository.read] never throws for anything the
 * player can act on — a sealed result keeps the UI honest about *why* it cannot
 * continue (review item: “未知规则版本仍能加载”).
 */
sealed interface SaveSnapshot {
    /** No file yet, or the file holds the empty envelope. */
    data object Empty : SaveSnapshot

    /** A structurally valid save that matches this build. */
    data class Loaded(val state: GameState, val savedAtMillis: Long = 0L) : SaveSnapshot

    /** The file exists but its JSON could not be decoded. The file is untouched. */
    data class Corrupted(val detail: String) : SaveSnapshot

    /** Valid JSON, but written by a build with a different schema/rules/content. */
    data class Incompatible(val reason: String, val detail: String) : SaveSnapshot

    /** The file could not be read at all (permissions, disk, …). */
    data class IoFailure(val detail: String) : SaveSnapshot
}

/** Result of writing the slot. */
sealed interface SaveWriteOutcome {
    data object Ok : SaveWriteOutcome

    /** Nothing was published to the UI when this happens. */
    data class Failed(val detail: String) : SaveWriteOutcome
}

/** Human-readable, never-blank description of a throwable. */
internal fun describe(throwable: Throwable): String =
    throwable.message?.takeIf { it.isNotBlank() }
        ?: throwable::class.simpleName
        ?: "未知错误"
