package io.github.wraithxxx.symphony.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import io.github.wraithxxx.symphony.services.groove.MediaDeletionService
import io.github.wraithxxx.symphony.services.groove.Song
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.ui.view.AlbumArtistViewRoute
import io.github.wraithxxx.symphony.ui.view.AlbumViewRoute
import io.github.wraithxxx.symphony.ui.view.ArtistViewRoute
import io.github.wraithxxx.symphony.utils.Logger
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongCard(
    context: ViewContext,
    song: Song,
    highlighted: Boolean = false,
    autoHighlight: Boolean = true,
    disableHeartIcon: Boolean = false,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    leading: @Composable () -> Unit = {},
    thumbnailLabel: (@Composable () -> Unit)? = null,
    thumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
    trailingOptionsContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val repositoryRevision by context.symphony.groove.song.id.collectAsState()
    val liveSong = remember(song.id, repositoryRevision) {
        context.symphony.groove.song.get(song.id) ?: song
    }
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val isCurrentPlaying by remember(autoHighlight, liveSong, queue) {
        derivedStateOf { autoHighlight && liveSong.id == queue.getOrNull(queueIndex) }
    }
    val favoriteSongIds by context.symphony.groove.playlist.favorites.collectAsState()
    val isFavorite by remember(favoriteSongIds, liveSong) {
        derivedStateOf { favoriteSongIds.contains(liveSong.id) }
    }
    val pendingDeletionIds by context.symphony.groove.deletion.pendingSongIds.collectAsState()
    val isDeleting = liveSong.id in pendingDeletionIds
    val selectionProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "song-selection",
    )
    val deletionAlpha by animateFloatAsState(
        targetValue = if (isDeleting) 0.42f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "song-deletion",
    )
    val artworkAlpha by animateFloatAsState(
        targetValue = if (selectionMode && !selected) 0.82f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "song-artwork-selection",
    )
    val selectionColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = deletionAlpha }
            .drawBehind {
                if (selectionProgress > 0f) {
                    drawRect(selectionColor.copy(alpha = 0.08f * selectionProgress))
                    drawLine(
                        color = selectionColor.copy(alpha = selectionProgress),
                        start = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 0f),
                        end = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Butt,
                    )
                }
            }
            .combinedClickable(
                enabled = !isDeleting,
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .semantics {
                if (selectionMode) {
                    this.selected = selected
                }
            },
    ) {
        Box(modifier = Modifier.padding(12.dp, 12.dp, 4.dp, 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading()
                Box {
                    AsyncImage(
                        liveSong.createArtworkImageRequest(context.symphony).build(),
                        null,
                        modifier = Modifier
                            .size(45.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .graphicsLayer { alpha = artworkAlpha },
                    )
                    thumbnailLabel?.let { it ->
                        val backgroundColor =
                            thumbnailLabelStyle.backgroundColor(MaterialTheme.colorScheme)
                        val contentColor =
                            thumbnailLabelStyle.contentColor(MaterialTheme.colorScheme)

                        Box(
                            modifier = Modifier
                                .offset(y = 8.dp)
                                .align(Alignment.BottomCenter)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        backgroundColor,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(3.dp, 0.dp)
                            ) {
                                ProvideTextStyle(
                                    MaterialTheme.typography.labelSmall.copy(
                                        color = contentColor
                                    )
                                ) { it() }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        liveSong.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = when {
                                highlighted || isCurrentPlaying -> MaterialTheme.colorScheme.primary
                                else -> LocalTextStyle.current.color
                            }
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (liveSong.artists.isNotEmpty()) {
                        Text(
                            liveSong.artists.joinToString(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(15.dp))

                Row {
                    if (selectionMode) {
                        SymphonySelectionIndicator(selected = selected)
                    } else if (!disableHeartIcon && isFavorite) {
                        IconButton(
                            modifier = Modifier.offset(4.dp, 0.dp),
                            enabled = !isDeleting,
                            onClick = {
                                context.symphony.groove.playlist.unfavorite(liveSong.id)
                            }
                        ) {
                            Icon(
                                Icons.Filled.Favorite,
                                null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (!selectionMode) {
                        var showOptionsMenu by remember { mutableStateOf(false) }
                        IconButton(
                            enabled = !isDeleting,
                            onClick = { showOptionsMenu = !showOptionsMenu }
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                null,
                                modifier = Modifier.size(24.dp),
                            )
                            SongDropdownMenu(
                                context,
                                liveSong,
                                isFavorite = isFavorite,
                                trailingContent = trailingOptionsContent,
                                expanded = showOptionsMenu,
                                onDismissRequest = {
                                    showOptionsMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongDropdownMenu(
    context: ViewContext,
    song: Song,
    isFavorite: Boolean,
    trailingContent: (@Composable ColumnScope.(() -> Unit) -> Unit)? = null,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMetadataEditor by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val hasRememberedPosition = expanded &&
            context.symphony.radio.progress.hasRestorablePosition(song)

    SymphonyMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        SymphonySheetHeader(
            title = song.title,
            subtitle = song.artists.joinToString(),
            leadingContent = {
                AsyncImage(
                    model = song.createArtworkImageRequest(context.symphony).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
            },
        )
        SymphonyQuickActionRow {
            SymphonyQuickAction(
                icon = Icons.Filled.Favorite,
                label = if (isFavorite) context.symphony.t.Unfavorite
                else context.symphony.t.Favorite,
                onClick = {
                    onDismissRequest()
                    context.symphony.groove.playlist.run {
                        when {
                            isFavorite -> unfavorite(song.id)
                            else -> favorite(song.id)
                        }
                    }
                },
            )
            SymphonyQuickAction(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                label = context.symphony.t.PlayNext,
                onClick = {
                    onDismissRequest()
                    context.symphony.radio.queue.add(
                        song.id,
                        context.symphony.radio.queue.currentSongIndex + 1
                    )
                },
            )
            SymphonyQuickAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = context.symphony.t.AddToQueue,
                onClick = {
                    onDismissRequest()
                    context.symphony.radio.queue.add(song.id)
                },
            )
            SymphonyQuickAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = context.symphony.t.AddToPlaylist,
                onClick = {
                    onDismissRequest()
                    showAddToPlaylistDialog = true
                },
            )
        }
        SymphonySheetSectionDivider()
        if (hasRememberedPosition) {
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.RestartAlt, null)
                },
                text = {
                    Text("Play from beginning")
                },
                onClick = {
                    onDismissRequest()
                    context.symphony.radio.playFromBeginning(song.id)
                }
            )
        }
        song.artists.forEach { artistName ->
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Person, null)
                },
                text = {
                    Text("${context.symphony.t.ViewArtist}: $artistName")
                },
                onClick = {
                    onDismissRequest()
                    context.navController.navigate(ArtistViewRoute(artistName))
                }
            )
        }
        song.albumArtists.forEach { albumArtist ->
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Person, null)
                },
                text = {
                    Text("${context.symphony.t.ViewAlbumArtist}: $albumArtist")
                },
                onClick = {
                    onDismissRequest()
                    context.navController.navigate(AlbumArtistViewRoute(albumArtist))
                }
            )
        }
        context.symphony.groove.album.getIdFromSong(song)?.let { albumId ->
            DropdownMenuItem(
                leadingIcon = {
                    Icon(Icons.Filled.Album, null)
                },
                text = {
                    Text(context.symphony.t.ViewAlbum)
                },
                onClick = {
                    onDismissRequest()
                    context.navController.navigate(AlbumViewRoute(albumId))
                }
            )
        }
        SymphonySheetSectionDivider()
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Share, null)
            },
            text = {
                Text(context.symphony.t.ShareSong)
            },
            onClick = {
                onDismissRequest()
                try {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_STREAM, song.uri)
                        type = context.activity.contentResolver.getType(song.uri)
                    }
                    context.activity.startActivity(intent)
                } catch (err: Exception) {
                    Logger.error("SongCard", "share failed", err)
                    context.symphony.uiMessages.show(
                        context.symphony.t.ShareFailedX(err.localizedMessage ?: err.toString())
                    )
                }
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Info, null)
            },
            text = {
                Text(context.symphony.t.Details)
            },
            onClick = {
                onDismissRequest()
                showInfoDialog = true
            }
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(Icons.Filled.Edit, null)
            },
            text = {
                Text("Edit details")
            },
            onClick = {
                onDismissRequest()
                showMetadataEditor = true
            }
        )
        SymphonySheetSectionDivider()
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    Icons.Filled.DeleteForever,
                    null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            text = {
                Text(
                    "Delete from device",
                    color = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDismissRequest()
                showDeleteDialog = true
            }
        )
        trailingContent?.invoke(this, onDismissRequest)
    }

    if (showInfoDialog) {
        SongInformationDialog(
            context,
            song = song,
            onDismissRequest = {
                showInfoDialog = false
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            context,
            songIds = listOf(song.id),
            onDismissRequest = {
                showAddToPlaylistDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        ScaffoldDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete from device") },
            content = {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                ) {
                    Text("Permanently delete \"${song.title}\" from the device? This cannot be undone.")
                    Text(
                        song.path,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            actions = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(context.symphony.t.Cancel)
                }
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        coroutineScope.launch {
                            val result = context.symphony.groove.deletion.delete(song.id)
                            if (result is MediaDeletionService.Result.Success) {
                                Toast.makeText(
                                    context.symphony.applicationContext,
                                    "1 file deleted from storage",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                Logger.warn(
                                    "SongCard",
                                    "silent deletion did not complete: $result",
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        "Delete permanently",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }

    if (showMetadataEditor) {
        SongMetadataEditorDialog(
            context = context,
            song = song,
            onDismissRequest = { showMetadataEditor = false },
        )
    }
}

enum class SongCardThumbnailLabelStyle {
    Default,
    Subtle,
}

private fun SongCardThumbnailLabelStyle.backgroundColor(colorScheme: ColorScheme) = when (this) {
    SongCardThumbnailLabelStyle.Default -> colorScheme.surfaceVariant
    SongCardThumbnailLabelStyle.Subtle -> colorScheme.surfaceVariant
}

private fun SongCardThumbnailLabelStyle.contentColor(colorScheme: ColorScheme) = when (this) {
    SongCardThumbnailLabelStyle.Default -> colorScheme.primary
    SongCardThumbnailLabelStyle.Subtle -> colorScheme.onSurfaceVariant
}
