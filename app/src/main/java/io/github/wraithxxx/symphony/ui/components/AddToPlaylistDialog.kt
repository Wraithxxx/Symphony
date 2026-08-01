package io.github.wraithxxx.symphony.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.utils.mutate

@Composable
fun AddToPlaylistDialog(
    context: ViewContext,
    songIds: List<String>,
    onDismissRequest: () -> Unit,
    onAdded: () -> Unit = {},
) {
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    val allPlaylistsIds by context.symphony.groove.playlist.all.collectAsState()
    val playlists by remember(allPlaylistsIds) {
        derivedStateOf {
            allPlaylistsIds
                .mapNotNull { context.symphony.groove.playlist.get(it) }
                .filter { it.isNotLocal }
                .toMutableStateList()
        }
    }

    if (!showNewPlaylistDialog) {
        ScaffoldDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(context.symphony.t.AddToPlaylist)
            },
            content = {
                when {
                    playlists.isEmpty() -> SubtleCaptionText(context.symphony.t.NoInAppPlaylistsFound)
                    else -> LazyColumn(modifier = Modifier.padding(bottom = 4.dp)) {
                        items(playlists) { playlist ->
                            val playlistSongIds = playlist.getSongIds(context.symphony)

                            GenericGrooveCard(
                                image = playlist
                                    .createArtworkImageRequest(context.symphony)
                                    .build(),
                                title = {
                                    Text(playlist.title)
                                },
                                trailingContent = {
                                    if (songIds.size == 1) {
                                        SymphonySelectionIndicator(
                                            selected = playlistSongIds.contains(songIds[0])
                                        )
                                    }
                                },
                                options = null,
                                onClick = {
                                    context.symphony.groove.playlist.update(
                                        playlist.id,
                                        playlistSongIds.mutate { addAll(songIds) },
                                    )
                                    onAdded()
                                    onDismissRequest()
                                }
                            )
                        }
                    }
                }
            },
            removeActionsVerticalPadding = true,
            actions = {
                TextButton(
                    modifier = Modifier.offset(y = (-8).dp),
                    onClick = {
                        showNewPlaylistDialog = true
                    }
                ) {
                    Text(context.symphony.t.NewPlaylist)
                }
                Spacer(modifier = Modifier.weight(1f))
            },
        )
    } else {
        NewPlaylistDialog(
            context = context,
            initialSongIds = songIds,
            onDone = { playlist ->
                context.symphony.groove.playlist.add(playlist)
                onAdded()
                onDismissRequest()
            },
            onDismissRequest = {
                showNewPlaylistDialog = false
            }
        )
    }
}
