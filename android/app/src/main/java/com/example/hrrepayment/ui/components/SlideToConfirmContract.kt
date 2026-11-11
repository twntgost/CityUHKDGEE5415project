package com.example.hrrepayment.ui.components

/**
 * Pure Kotlin state machine for [SlideToConfirmBar].  Keeping the commit gate
 * outside Compose gesture callbacks makes the important rule testable: once a
 * gesture has committed, another gesture cannot commit until the host exposes
 * a new state token.
 */
data class SlideToConfirmState(
    val progress: Float = 0f,
    val token: Any? = null,
    val dragging: Boolean = false,
    val locked: Boolean = false,
    val enabled: Boolean = true,
)

sealed interface SlideToConfirmEvent {
    data class Begin(val token: Any?) : SlideToConfirmEvent
    data class Move(val token: Any?, val delta: Float) : SlideToConfirmEvent
    data class Release(val token: Any?) : SlideToConfirmEvent
    data class Cancel(val token: Any?) : SlideToConfirmEvent
    data class AccessibilityConfirm(val token: Any?) : SlideToConfirmEvent
    data class TokenChanged(val token: Any?) : SlideToConfirmEvent
    data class EnabledChanged(val enabled: Boolean) : SlideToConfirmEvent
}

data class SlideToConfirmTransition(
    val state: SlideToConfirmState,
    val confirmed: Boolean = false,
)

object SlideToConfirmContract {
    const val COMMIT_THRESHOLD = 1f

    fun reduce(
        state: SlideToConfirmState,
        event: SlideToConfirmEvent,
    ): SlideToConfirmTransition {
        fun sameToken(token: Any?): Boolean = token == state.token

        return when (event) {
            is SlideToConfirmEvent.Begin -> {
                if (!state.enabled || state.locked || !sameToken(event.token)) {
                    SlideToConfirmTransition(state.copy(dragging = false, progress = 0f))
                } else {
                    SlideToConfirmTransition(state.copy(dragging = true))
                }
            }

            is SlideToConfirmEvent.Move -> {
                if (!state.enabled || state.locked || !state.dragging || !sameToken(event.token)) {
                    SlideToConfirmTransition(state)
                } else {
                    SlideToConfirmTransition(
                        state.copy(progress = (state.progress + event.delta).coerceIn(0f, 1f)),
                    )
                }
            }

            is SlideToConfirmEvent.Release -> {
                if (!state.enabled || state.locked || !state.dragging || !sameToken(event.token)) {
                    SlideToConfirmTransition(state.copy(dragging = false, progress = 0f))
                } else if (state.progress >= COMMIT_THRESHOLD) {
                    SlideToConfirmTransition(
                        state.copy(progress = 1f, dragging = false, locked = true),
                        confirmed = true,
                    )
                } else {
                    SlideToConfirmTransition(state.copy(progress = 0f, dragging = false))
                }
            }

            is SlideToConfirmEvent.Cancel ->
                SlideToConfirmTransition(state.copy(progress = 0f, dragging = false))

            is SlideToConfirmEvent.AccessibilityConfirm -> {
                if (!state.enabled || state.locked || !sameToken(event.token)) {
                    SlideToConfirmTransition(state)
                } else {
                    SlideToConfirmTransition(
                        state.copy(progress = 0f, dragging = false, locked = true),
                        confirmed = true,
                    )
                }
            }

            is SlideToConfirmEvent.TokenChanged ->
                if (sameToken(event.token)) SlideToConfirmTransition(state) else SlideToConfirmTransition(
                    state.copy(token = event.token, progress = 0f, dragging = false, locked = false),
                )

            is SlideToConfirmEvent.EnabledChanged ->
                SlideToConfirmTransition(
                    state.copy(
                        enabled = event.enabled,
                        progress = if (event.enabled) state.progress else 0f,
                        dragging = if (event.enabled) state.dragging else false,
                    ),
                )
        }
    }
}
