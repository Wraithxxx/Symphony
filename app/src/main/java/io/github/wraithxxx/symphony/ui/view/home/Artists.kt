package io.github.wraithxxx.symphony.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.wraithxxx.symphony.ui.components.ArtistGrid
import io.github.wraithxxx.symphony.ui.components.LoaderScaffold
import io.github.wraithxxx.symphony.ui.helpers.ViewContext

@Composable
fun ArtistsView(context: ViewContext) {
    val isUpdating by context.symphony.groove.artist.isUpdating.collectAsState()
    val artistNames by context.symphony.groove.artist.all.collectAsState()
    val artistsCount by context.symphony.groove.artist.count.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        ArtistGrid(
            context,
            artistName = artistNames,
            artistsCount = artistsCount,
        )
    }
}
