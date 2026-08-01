package io.github.wraithxxx.symphony.ui.view

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeNavigationPagesTest {
    @Test
    fun `default navigation is split into the intended two pages`() {
        val pages = homeNavigationPages(
            setOf(
                HomePage.ForYou,
                HomePage.Songs,
                HomePage.Albums,
                HomePage.Artists,
                HomePage.Playlists,
            ),
        )

        assertEquals(
            listOf(
                HomePage.ForYou,
                HomePage.Songs,
                HomePage.Albums,
                HomePage.Artists,
                HomePage.Playlists,
            ),
            pages[0],
        )
        assertEquals(
            listOf(
                HomePage.AlbumArtists,
                HomePage.Genres,
                HomePage.Browser,
                HomePage.Folders,
                HomePage.Tree,
            ),
            pages[1],
        )
    }

    @Test
    fun `legacy short configuration is completed without dropping its choices`() {
        val pages = homeNavigationPages(setOf(HomePage.Tree, HomePage.Genres))

        assertEquals(5, pages[0].size)
        assertEquals(HomePage.Tree, pages[0][0])
        assertEquals(HomePage.Genres, pages[0][1])
        assertTrue(HomePage.Tree in pages[0])
        assertTrue(HomePage.Genres in pages[0])
    }

    @Test
    fun `both pages form one complete duplicate free partition`() {
        val pages = homeNavigationPages(
            setOf(
                HomePage.Browser,
                HomePage.Folders,
                HomePage.Tree,
                HomePage.Genres,
                HomePage.AlbumArtists,
            ),
        )
        val destinations = pages.flatten()

        assertEquals(listOf(5, 5), pages.map(List<HomePage>::size))
        assertEquals(HomePage.entries.size, destinations.toSet().size)
        assertEquals(HomePage.entries.toSet(), destinations.toSet())
    }
}
