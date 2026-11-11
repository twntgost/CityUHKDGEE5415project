package com.example.hrrepayment.core.engine

import com.example.hrrepayment.core.model.GameState

/**
 * Outcome of a single accepted command.
 *
 * [previousState] is nullable because the very first command of a brand new slot
 * has no predecessor ([Command.StartNewGame] applied on an empty store). The
 * ViewModel uses it to keep showing the old run when the follow-up save fails.
 */
data class EngineResult(
    val previousState: GameState?,
    val newState: GameState,
    val effects: List<EngineEffect>,
)

/**
 * Side effects the presentation layer should react to.
 *
 * v3: [Message] carries a bilingual [com.example.hrrepayment.core.i18n.Key] plus
 * format arguments instead of a pre-rendered Chinese sentence, so a notice raised
 * before a language switch still displays in the language chosen afterwards.
 */
sealed interface EngineEffect {
    data class Message(
        val key: com.example.hrrepayment.core.i18n.Key,
        val args: List<Any?> = emptyList(),
    ) : EngineEffect

    data class PhaseChanged(
        val phase: com.example.hrrepayment.core.model.Phase,
        val description: String,
    ) : EngineEffect
}
