package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.i18n.Key

/**
 * Every rejection the engine can produce. Using one sealed hierarchy means the
 * command loop can `catch (e: EngineError)` without also swallowing unrelated
 * programming errors.
 *
 * v3: each case exposes a bilingual [messageKey] (+ [messageArgs]) instead of a
 * pre-rendered Chinese sentence, so the UI can display the rejection in whichever
 * language the player has selected. [message] stays the English technical
 * description used in tests and logs.
 */
sealed class EngineError(message: String) : Exception(message) {

    /** Key resolved through `core/i18n`. */
    open val messageKey: Key get() = Key.ENGINE_ERR_UNKNOWN

    /** Arguments for [messageKey]'s placeholders. */
    open val messageArgs: List<Any?> get() = emptyList()

    /** No save exists, so this command cannot be dispatched at all. */
    class NoSave(commandName: String) : EngineError("no save for $commandName") {
        override val messageKey: Key = Key.ENGINE_ERR_NO_SAVE
    }

    /** The command was built for a different save slot. */
    class SaveIdMismatch(expected: String, actual: String) : EngineError(
        "saveId mismatch: command=$actual state=$expected",
    ) {
        override val messageKey: Key = Key.ENGINE_ERR_SAVE_ID
    }

    /** Stale command: another command already advanced the state. */
    class RevisionMismatch(expected: Long, actual: Long) : EngineError(
        "revision mismatch: command=$actual state=$expected",
    ) {
        override val messageKey: Key = Key.ENGINE_ERR_REVISION
    }

    /** The exact same command id was already applied. */
    class DuplicateCommand(commandId: String) : EngineError("command $commandId was already applied") {
        override val messageKey: Key = Key.ENGINE_ERR_DUPLICATE
    }

    /** Save was written by a newer/older app build. */
    class SchemaUnsupported(val found: Int) : EngineError("unsupported save schema: $found") {
        override val messageKey: Key = Key.ENGINE_ERR_SCHEMA
        override val messageArgs: List<Any?> get() = listOf(found)
    }

    class RulesUnsupported(val found: String, val expected: String) : EngineError(
        "unsupported rules version: $found (expected $expected)",
    ) {
        override val messageKey: Key = Key.ENGINE_ERR_RULES
        override val messageArgs: List<Any?> get() = listOf(found, expected)
    }

    class ContentUnsupported(val found: String, val expected: String) : EngineError(
        "unsupported content version: $found (expected $expected)",
    ) {
        override val messageKey: Key = Key.ENGINE_ERR_CONTENT
        override val messageArgs: List<Any?> get() = listOf(found, expected)
    }

    /** Structurally invalid command (blank id, wrong sentinel, …). */
    class InvalidCommand(val reason: String) : EngineError("invalid command: $reason") {
        override val messageKey: Key = Key.ENGINE_ERR_INVALID
    }

    /** Command is valid but not legal in the current phase/pending step. */
    class WrongPhase(val expected: String, val actual: String) : EngineError(
        "phase mismatch: expected=$expected actual=$actual",
    ) {
        override val messageKey: Key = Key.ENGINE_ERR_PHASE
    }

    /** Persisting the new state failed; nothing was published to the UI. */
    class PersistenceFailure(val detail: String) : EngineError("persistence failure: $detail") {
        override val messageKey: Key = Key.ENGINE_ERR_PERSIST
        override val messageArgs: List<Any?> get() = listOf(detail)
    }
}
