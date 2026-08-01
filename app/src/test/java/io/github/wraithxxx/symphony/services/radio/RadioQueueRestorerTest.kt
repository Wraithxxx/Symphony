package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RadioQueueRestorerTest {
    @Test
    fun `preserves current song and position when earlier cached entries disappeared`() {
        val previous = RadioQueue.Serialized(
            currentSongIndex = 2,
            playedDuration = 3_600_000L,
            originalQueue = listOf("missing", "b", "c"),
            currentQueue = listOf("missing", "b", "c"),
            shuffled = false,
        )

        val restored = RadioQueueRestorer.filter(previous, setOf("b", "c"))!!

        assertEquals(listOf("b", "c"), restored.currentQueue)
        assertEquals(1, restored.currentSongIndex)
        assertEquals(3_600_000L, restored.playedDuration)
    }

    @Test
    fun `resets position when the previous current song is unavailable`() {
        val previous = RadioQueue.Serialized(
            currentSongIndex = 1,
            playedDuration = 42_000L,
            originalQueue = listOf("a", "missing", "c"),
            currentQueue = listOf("a", "missing", "c"),
            shuffled = false,
        )

        val restored = RadioQueueRestorer.filter(previous, setOf("a", "c"))!!

        assertEquals(0, restored.currentSongIndex)
        assertEquals(0L, restored.playedDuration)
    }

    @Test
    fun `does not restore an empty cached queue`() {
        val previous = RadioQueue.Serialized(
            currentSongIndex = 0,
            playedDuration = 10_000L,
            originalQueue = listOf("missing"),
            currentQueue = listOf("missing"),
            shuffled = false,
        )

        assertNull(RadioQueueRestorer.filter(previous, emptySet()))
    }
}
