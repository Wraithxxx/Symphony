package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioPlaybackSnapshotStateTest {
    @Test
    fun `new track generation publishes identity and initial position together`() {
        val state = RadioPlaybackSnapshotState()
        val staged = snapshot(
            generation = 2L,
            songId = "new",
            played = 0L,
            total = 7_200_000L,
            readiness = RadioPlaybackReadiness.Preparing,
        )

        assertTrue(state.publish(staged))
        assertEquals("new", state.current().songId)
        assertEquals(0L, state.current().position.played)
        assertEquals(7_200_000L, state.current().position.total)
    }

    @Test
    fun `restored generation publishes saved position rather than previous track position`() {
        val state = RadioPlaybackSnapshotState()
        state.publish(snapshot(1L, "old", 900_000L, 1_000_000L))

        state.publish(
            snapshot(
                generation = 2L,
                songId = "restored",
                played = 3_600_000L,
                total = 21_600_000L,
                readiness = RadioPlaybackReadiness.Restoring,
            )
        )

        assertEquals("restored", state.current().songId)
        assertEquals(3_600_000L, state.current().position.played)
    }

    @Test
    fun `late position from an older player generation is ignored`() {
        val state = RadioPlaybackSnapshotState()
        state.publish(snapshot(3L, "current", 10_000L, 100_000L))

        assertFalse(state.publish(snapshot(2L, "old", 90_000L, 100_000L)))
        assertEquals("current", state.current().songId)
        assertEquals(10_000L, state.current().position.played)
    }

    @Test
    fun `same generation accepts advancing playback updates`() {
        val state = RadioPlaybackSnapshotState()
        state.publish(snapshot(4L, "current", 10_000L, 100_000L))

        assertTrue(state.publish(snapshot(4L, "current", 10_100L, 100_000L)))
        assertEquals(10_100L, state.current().position.played)
    }

    private fun snapshot(
        generation: Long,
        songId: String,
        played: Long,
        total: Long,
        readiness: RadioPlaybackReadiness = RadioPlaybackReadiness.Playing,
    ) = RadioPlaybackSnapshot(
        generation = generation,
        songId = songId,
        queueIndex = 0,
        position = RadioPlayer.PlaybackPosition(played, total),
        readiness = readiness,
        isPlaying = readiness == RadioPlaybackReadiness.Playing,
        isPlayPending = false,
    )
}
