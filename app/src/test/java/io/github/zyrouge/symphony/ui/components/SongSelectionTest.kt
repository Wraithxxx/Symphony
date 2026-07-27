package io.github.zyrouge.symphony.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SongSelectionTest {
    @Test
    fun `toggle adds an unselected song`() {
        assertEquals(setOf("a", "b"), SongSelection.toggle(setOf("a"), "b"))
    }

    @Test
    fun `toggle removes an already selected song`() {
        assertEquals(setOf("a"), SongSelection.toggle(setOf("a", "b"), "b"))
    }

    @Test
    fun `select all collapses duplicate song ids`() {
        assertEquals(setOf("a", "b"), SongSelection.selectAll(listOf("a", "b", "a")))
    }

    @Test
    fun `retain available removes songs that left the view`() {
        assertEquals(
            setOf("b"),
            SongSelection.retainAvailable(
                selected = setOf("a", "b", "c"),
                availableSongIds = listOf("b", "d"),
            ),
        )
    }

    @Test
    fun `retain available handles an empty view`() {
        assertTrue(SongSelection.retainAvailable(setOf("a"), emptyList()).isEmpty())
    }
}
