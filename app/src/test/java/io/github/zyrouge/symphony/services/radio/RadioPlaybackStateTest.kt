package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioPlaybackStateTest {
    @Test
    fun `play requested during restoration starts only after restored seek`() {
        val state = RadioPlaybackState()

        assertEquals(
            RadioPlaybackReadiness.Restoring,
            state.stage(restoring = true, autostart = false).readiness,
        )
        assertFalse(state.requestPlay())
        assertFalse(state.onPrepared(requiresSeek = true))
        assertEquals(RadioPlaybackReadiness.Seeking, state.snapshot().readiness)
        assertTrue(state.onSeekComplete(isPlaying = false))
    }

    @Test
    fun `manual cancellation prevents pending cold start playback`() {
        val state = RadioPlaybackState()

        state.stage(restoring = true, autostart = false)
        state.requestPlay()
        state.cancelPlay()

        assertFalse(state.onPrepared(requiresSeek = true))
        assertFalse(state.onSeekComplete(isPlaying = false))
        assertFalse(state.snapshot().playPending)
    }

    @Test
    fun `autostart waits for preparation when no restored seek is needed`() {
        val state = RadioPlaybackState()

        state.stage(restoring = false, autostart = true)

        assertTrue(state.onPrepared(requiresSeek = false))
        assertEquals(RadioPlaybackReadiness.Ready, state.snapshot().readiness)
        assertTrue(state.snapshot().playPending)
    }

    @Test
    fun `actual playback clears pending intent and pause returns to ready`() {
        val state = RadioPlaybackState()

        state.stage(restoring = false, autostart = true)
        state.onPrepared(requiresSeek = false)
        state.onPlayingChanged(true)

        assertEquals(RadioPlaybackReadiness.Playing, state.snapshot().readiness)
        assertFalse(state.snapshot().playPending)

        state.onPlayingChanged(false)
        assertEquals(RadioPlaybackReadiness.Ready, state.snapshot().readiness)
    }

    @Test
    fun `stopping or errors clear stale play intent`() {
        val state = RadioPlaybackState()

        state.stage(restoring = true, autostart = true)
        assertEquals(RadioPlaybackReadiness.Error, state.onError().readiness)
        assertFalse(state.snapshot().playPending)

        state.stage(restoring = false, autostart = true)
        assertEquals(RadioPlaybackReadiness.Idle, state.onStopped().readiness)
        assertFalse(state.snapshot().playPending)
    }
}
