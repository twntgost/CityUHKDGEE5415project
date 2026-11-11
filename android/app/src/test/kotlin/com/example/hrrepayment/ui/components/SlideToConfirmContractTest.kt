package com.example.hrrepayment.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideToConfirmContractTest {
    private val token = "revision-1"

    private fun fullRelease(): SlideToConfirmTransition {
        var t = SlideToConfirmTransition(SlideToConfirmState(token = token))
        t = SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Begin(token))
        t = SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Move(token, 1f))
        return SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Release(token))
    }

    @Test
    fun releaseBeforeEndSnapsBackWithoutCommit() {
        var t = SlideToConfirmTransition(SlideToConfirmState(token = token))
        t = SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Begin(token))
        t = SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Move(token, .4f))
        t = SlideToConfirmContract.reduce(t.state, SlideToConfirmEvent.Release(token))

        assertFalse(t.confirmed)
        assertEquals(0f, t.state.progress)
        assertFalse(t.state.dragging)
        assertFalse(t.state.locked)
    }

    @Test
    fun fullReleaseCommitsOnceAndLocksUntilTokenChanges() {
        val committed = fullRelease()
        assertTrue(committed.confirmed)
        assertEquals(1f, committed.state.progress)
        assertTrue(committed.state.locked)

        val second = SlideToConfirmContract.reduce(
            committed.state,
            SlideToConfirmEvent.AccessibilityConfirm(token),
        )
        assertFalse(second.confirmed)
        assertTrue(second.state.locked)

        val reset = SlideToConfirmContract.reduce(
            committed.state,
            SlideToConfirmEvent.TokenChanged("revision-2"),
        )
        assertFalse(reset.state.locked)
        assertEquals(0f, reset.state.progress)
    }

    @Test
    fun staleTokenAndDisabledStateCannotCommit() {
        val stale = SlideToConfirmContract.reduce(
            SlideToConfirmState(token = token),
            SlideToConfirmEvent.Begin("old"),
        )
        assertFalse(stale.state.dragging)

        val disabled = SlideToConfirmContract.reduce(
            SlideToConfirmState(token = token, enabled = false),
            SlideToConfirmEvent.AccessibilityConfirm(token),
        )
        assertFalse(disabled.confirmed)
    }

    @Test
    fun accessibilityAndDragShareTheSameCommitGate() {
        val click = SlideToConfirmContract.reduce(
            SlideToConfirmState(token = token),
            SlideToConfirmEvent.AccessibilityConfirm(token),
        )
        assertTrue(click.confirmed)
        val dragAfterClick = SlideToConfirmContract.reduce(
            click.state,
            SlideToConfirmEvent.Begin(token),
        )
        assertFalse(dragAfterClick.state.dragging)
    }
}
