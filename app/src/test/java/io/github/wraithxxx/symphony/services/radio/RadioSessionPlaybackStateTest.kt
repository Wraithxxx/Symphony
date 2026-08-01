package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioSessionPlaybackStateTest {
    @Test
    fun `silent preparation without play intent is published as paused`() {
        assertEquals(
            RadioSessionPlaybackState.Published.Paused,
            RadioSessionPlaybackState.resolve(
                isPlaying = false,
                readiness = RadioPlaybackReadiness.Preparing,
                isPlayPending = false,
            ),
        )
    }

    @Test
    fun `pending play during restoration is published as buffering`() {
        assertEquals(
            RadioSessionPlaybackState.Published.Buffering,
            RadioSessionPlaybackState.resolve(
                isPlaying = false,
                readiness = RadioPlaybackReadiness.Restoring,
                isPlayPending = true,
            ),
        )
    }

    @Test
    fun `actual playback takes precedence over transient readiness`() {
        assertEquals(
            RadioSessionPlaybackState.Published.Playing,
            RadioSessionPlaybackState.resolve(
                isPlaying = true,
                readiness = RadioPlaybackReadiness.Seeking,
                isPlayPending = true,
            ),
        )
    }
}
