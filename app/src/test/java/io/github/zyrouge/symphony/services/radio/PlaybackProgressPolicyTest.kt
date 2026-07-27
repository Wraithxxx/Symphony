package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackProgressPolicyTest {
    @Test
    fun `disabled retention ignores progress`() {
        assertEquals(
            PlaybackProgressPolicy.Decision.Ignore,
            decide(enabled = false, duration = minutes(60), position = minutes(20)),
        )
    }

    @Test
    fun `track below threshold is ignored`() {
        assertEquals(
            PlaybackProgressPolicy.Decision.Ignore,
            decide(duration = minutes(19), position = minutes(5)),
        )
    }

    @Test
    fun `track at threshold is eligible`() {
        assertTrue(
            decide(duration = minutes(20), position = minutes(5)) is
                    PlaybackProgressPolicy.Decision.Save
        )
    }

    @Test
    fun `position near beginning clears old progress`() {
        assertEquals(
            PlaybackProgressPolicy.Decision.Clear,
            decide(duration = minutes(60), position = 9_999L),
        )
    }

    @Test
    fun `position inside a short track completion window clears progress`() {
        assertEquals(
            PlaybackProgressPolicy.Decision.Clear,
            decide(duration = minutes(60), position = minutes(60) - 30_000L),
        )
    }

    @Test
    fun `one percent completion window handles seven hour tracks`() {
        val duration = minutes(420)
        assertEquals(
            PlaybackProgressPolicy.Decision.Clear,
            decide(duration = duration, position = duration - (duration / 100L)),
        )
    }

    @Test
    fun `valid long track position is restored exactly`() {
        assertEquals(
            minutes(185),
            PlaybackProgressPolicy.restorablePosition(
                enabled = true,
                minimumDurationMs = minutes(20),
                durationMs = minutes(420),
                positionMs = minutes(185),
            ),
        )
    }

    @Test
    fun `completed position is not restored`() {
        assertNull(
            PlaybackProgressPolicy.restorablePosition(
                enabled = true,
                minimumDurationMs = minutes(20),
                durationMs = minutes(60),
                positionMs = minutes(60) - 10_000L,
            )
        )
    }

    @Test
    fun `unchanged file fingerprint remains valid`() {
        assertTrue(
            PlaybackProgressPolicy.matchesFile(
                storedPath = "/podcasts/story.mp3",
                storedDurationMs = minutes(60),
                storedDateModified = 100L,
                storedSize = 200L,
                currentPath = "/podcasts/story.mp3",
                currentDurationMs = minutes(60),
                currentDateModified = 100L,
                currentSize = 200L,
            )
        )
    }

    @Test
    fun `replaced file with reused identity is rejected`() {
        assertEquals(
            false,
            PlaybackProgressPolicy.matchesFile(
                storedPath = "/podcasts/story.mp3",
                storedDurationMs = minutes(60),
                storedDateModified = 100L,
                storedSize = 200L,
                currentPath = "/podcasts/story.mp3",
                currentDurationMs = minutes(62),
                currentDateModified = 101L,
                currentSize = 240L,
            )
        )
    }

    private fun decide(
        enabled: Boolean = true,
        duration: Long,
        position: Long,
    ) = PlaybackProgressPolicy.decide(
        enabled = enabled,
        minimumDurationMs = minutes(20),
        durationMs = duration,
        positionMs = position,
    )

    private fun minutes(value: Int) = value * 60_000L
}
