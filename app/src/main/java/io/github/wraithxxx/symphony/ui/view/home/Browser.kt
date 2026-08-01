package io.github.wraithxxx.symphony.ui.view.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.wraithxxx.symphony.ui.components.LoaderScaffold
import io.github.wraithxxx.symphony.ui.components.SongExplorerList
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.utils.SimplePath

@Composable
fun BrowserView(context: ViewContext) {
    val isUpdating by context.symphony.groove.song.isUpdating.collectAsState()
    val id by context.symphony.groove.song.id.collectAsState()
    val explorer = context.symphony.groove.song.explorer
    val lastUsedFolderPath by context.symphony.settings.lastUsedBrowserPath.flow.collectAsState()

    LoaderScaffold(context, isLoading = isUpdating) {
        SongExplorerList(
            context,
            initialPath = lastUsedFolderPath?.let { SimplePath(it) },
            key = id,
            explorer = explorer,
            onPathChange = { path ->
                context.symphony.settings.lastUsedBrowserPath.setValue(path.pathString)
            }
        )
    }
}
