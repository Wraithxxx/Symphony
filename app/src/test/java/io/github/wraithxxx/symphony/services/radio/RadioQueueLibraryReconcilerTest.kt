package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioQueueLibraryReconcilerTest {
    @Test
    fun `deleted non-current songs are removed without changing current song`() {
        val result = RadioQueueLibraryReconciler.reconcile(
            originalQueue = listOf("a", "b", "c"),
            currentQueue = listOf("a", "b", "c"),
            currentSongIndex = 1,
            librarySongIds = setOf("b", "c"),
        )

        assertEquals(listOf("b", "c"), result.originalQueue)
        assertEquals(listOf("b", "c"), result.currentQueue)
        assertEquals(0, result.currentSongIndex)
    }

    @Test
    fun `missing current song is retained for active playback`() {
        val result = RadioQueueLibraryReconciler.reconcile(
            originalQueue = listOf("a", "b", "c"),
            currentQueue = listOf("c", "b", "a"),
            currentSongIndex = 1,
            librarySongIds = setOf("a", "c"),
        )

        assertEquals(listOf("a", "b", "c"), result.originalQueue)
        assertEquals(listOf("c", "b", "a"), result.currentQueue)
        assertEquals(1, result.currentSongIndex)
    }

    @Test
    fun `shuffle index follows current song after deleted entries are filtered`() {
        val result = RadioQueueLibraryReconciler.reconcile(
            originalQueue = listOf("a", "b", "c", "d"),
            currentQueue = listOf("d", "c", "b", "a"),
            currentSongIndex = 2,
            librarySongIds = setOf("a", "b", "d"),
        )

        assertEquals(listOf("a", "b", "d"), result.originalQueue)
        assertEquals(listOf("d", "b", "a"), result.currentQueue)
        assertEquals(1, result.currentSongIndex)
    }
}
