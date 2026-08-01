package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioQueueSongRemovalPlannerTest {
    @Test
    fun `removing current selects the following shuffled item`() {
        val result = RadioQueueSongRemovalPlanner.remove(
            songId = "b",
            originalQueue = listOf("a", "b", "c"),
            currentQueue = listOf("c", "b", "a"),
            currentSongIndex = 1,
        )

        assertTrue(result.removedCurrentSong)
        assertEquals(listOf("a", "c"), result.originalQueue)
        assertEquals(listOf("c", "a"), result.currentQueue)
        assertEquals(1, result.replacementIndex)
    }

    @Test
    fun `removing last current selects prior remaining item`() {
        val result = RadioQueueSongRemovalPlanner.remove(
            songId = "c",
            originalQueue = listOf("a", "b", "c"),
            currentQueue = listOf("a", "b", "c"),
            currentSongIndex = 2,
        )

        assertTrue(result.removedCurrentSong)
        assertEquals(1, result.replacementIndex)
    }

    @Test
    fun `removing non-current preserves current identity and adjusts index`() {
        val result = RadioQueueSongRemovalPlanner.remove(
            songId = "a",
            originalQueue = listOf("a", "b", "c"),
            currentQueue = listOf("a", "b", "c"),
            currentSongIndex = 2,
        )

        assertFalse(result.removedCurrentSong)
        assertEquals(listOf("b", "c"), result.currentQueue)
        assertEquals(1, result.currentSongIndex)
    }

    @Test
    fun `all duplicate references are removed`() {
        val result = RadioQueueSongRemovalPlanner.remove(
            songId = "a",
            originalQueue = listOf("a", "b", "a"),
            currentQueue = listOf("b", "a", "a"),
            currentSongIndex = 0,
        )

        assertFalse(result.removedCurrentSong)
        assertEquals(listOf("b"), result.originalQueue)
        assertEquals(listOf("b"), result.currentQueue)
        assertEquals(0, result.currentSongIndex)
    }

    @Test
    fun `batch removal selects first surviving item after current`() {
        val result = RadioQueueSongRemovalPlanner.removeAll(
            songIds = setOf("b", "c"),
            originalQueue = listOf("a", "b", "c", "d"),
            currentQueue = listOf("a", "b", "c", "d"),
            currentSongIndex = 1,
        )

        assertTrue(result.removedCurrentSong)
        assertEquals(listOf("a", "d"), result.currentQueue)
        assertEquals(1, result.replacementIndex)
    }

    @Test
    fun `batch removal falls back to preceding survivor at queue end`() {
        val result = RadioQueueSongRemovalPlanner.removeAll(
            songIds = setOf("c", "d"),
            originalQueue = listOf("a", "b", "c", "d"),
            currentQueue = listOf("a", "b", "c", "d"),
            currentSongIndex = 3,
        )

        assertTrue(result.removedCurrentSong)
        assertEquals(listOf("a", "b"), result.currentQueue)
        assertEquals(1, result.replacementIndex)
    }

    @Test
    fun `batch removal preserves current identity when it survives`() {
        val result = RadioQueueSongRemovalPlanner.removeAll(
            songIds = setOf("a", "b", "d"),
            originalQueue = listOf("a", "b", "c", "d"),
            currentQueue = listOf("d", "b", "c", "a"),
            currentSongIndex = 2,
        )

        assertFalse(result.removedCurrentSong)
        assertEquals(listOf("c"), result.originalQueue)
        assertEquals(listOf("c"), result.currentQueue)
        assertEquals(0, result.currentSongIndex)
    }

    @Test
    fun `batch removal can empty queue`() {
        val result = RadioQueueSongRemovalPlanner.removeAll(
            songIds = setOf("a", "b"),
            originalQueue = listOf("a", "b", "a"),
            currentQueue = listOf("b", "a", "a"),
            currentSongIndex = 0,
        )

        assertTrue(result.removedCurrentSong)
        assertTrue(result.originalQueue.isEmpty())
        assertTrue(result.currentQueue.isEmpty())
        assertEquals(-1, result.replacementIndex)
    }

    @Test
    fun `empty batch leaves queue untouched`() {
        val result = RadioQueueSongRemovalPlanner.removeAll(
            songIds = emptySet(),
            originalQueue = listOf("a", "b"),
            currentQueue = listOf("b", "a"),
            currentSongIndex = 1,
        )

        assertFalse(result.removedCurrentSong)
        assertEquals(listOf("a", "b"), result.originalQueue)
        assertEquals(listOf("b", "a"), result.currentQueue)
        assertEquals(1, result.currentSongIndex)
    }
}
