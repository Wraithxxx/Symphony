package io.github.zyrouge.symphony.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.services.groove.repositories.SongRepository
import io.github.zyrouge.symphony.services.radio.Radio
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.SettingsViewRoute
import io.github.zyrouge.symphony.ui.view.settings.GrooveSettingsViewRoute
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.launch

enum class SongListType {
    Default,
    Playlist,
    Album,
}

@Composable
fun SongList(
    context: ViewContext,
    songIds: List<String>,
    songsCount: Int? = null,
    leadingContent: (LazyListScope.() -> Unit)? = null,
    trailingContent: (LazyListScope.() -> Unit)? = null,
    trailingOptionsContent: (@Composable ColumnScope.(Int, Song, () -> Unit) -> Unit)? = null,
    cardThumbnailLabel: (@Composable (Int, Song) -> Unit)? = null,
    cardThumbnailLabelStyle: SongCardThumbnailLabelStyle = SongCardThumbnailLabelStyle.Default,
    type: SongListType = SongListType.Default,
    disableHeartIcon: Boolean = false,
    enableAddMediaFoldersHint: Boolean = false,
) {
    val repositoryRevision by context.symphony.groove.song.id.collectAsState()
    val sortBy by type.getLastUsedSortBy(context).flow.collectAsState()
    val sortReverse by type.getLastUsedSortReverse(context).flow.collectAsState()
    val sortedSongIds by remember(songIds, sortBy, sortReverse, repositoryRevision) {
        derivedStateOf {
            context.symphony.groove.song.sort(songIds, sortBy, sortReverse)
        }
    }
    var selectedSongIds by remember { mutableStateOf(emptySet<String>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val selectionMode = selectedSongIds.isNotEmpty()
    val uniqueVisibleSongIds by remember(sortedSongIds) {
        derivedStateOf { sortedSongIds.distinct() }
    }

    LaunchedEffect(songIds) {
        selectedSongIds = SongSelection.retainAvailable(selectedSongIds, songIds)
    }
    BackHandler(enabled = selectionMode && !isDeleting) {
        selectedSongIds = emptySet()
    }

    MediaSortBarScaffold(
        mediaSortBar = {
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    (
                        fadeIn(tween(160)) +
                            slideInVertically(tween(160)) { height -> height / 5 }
                        ).togetherWith(
                        fadeOut(tween(120)) +
                            slideOutVertically(tween(120)) { height -> -height / 5 }
                    )
                },
                label = "song-selection-toolbar",
            ) { selectionActive ->
                when {
                    selectionActive -> SongSelectionBar(
                        selectedCount = selectedSongIds.size,
                        allSelected = selectedSongIds.size == uniqueVisibleSongIds.size,
                        isDeleting = isDeleting,
                        onClose = { selectedSongIds = emptySet() },
                        onSelectAll = {
                            selectedSongIds = when {
                                selectedSongIds.size == uniqueVisibleSongIds.size -> emptySet()
                                else -> SongSelection.selectAll(uniqueVisibleSongIds)
                            }
                        },
                        onDelete = { showBatchDeleteDialog = true },
                    )

                    else -> MediaSortBar(
                        context,
                        reverse = sortReverse,
                        onReverseChange = {
                            type.setLastUsedSortReverse(context, it)
                        },
                        sort = sortBy,
                        sorts = SongRepository.SortBy.entries
                            .associateWith { x -> ViewContext.parameterizedFn { x.label(it) } },
                        onSortChange = {
                            type.setLastUsedSortBy(context, it)
                        },
                        label = {
                            Text(context.symphony.t.XSongs((songsCount ?: songIds.size).toString()))
                        },
                        onShufflePlay = {
                            context.symphony.radio.shorty.playQueue(sortedSongIds, shuffle = true)
                        }
                    )
                }
            }
        },
        content = {
            when {
                songIds.isEmpty() -> IconTextBody(
                    icon = { modifier ->
                        Icon(Icons.Filled.MusicNote, null, modifier = modifier)
                    },
                    content = {
                        Text(context.symphony.t.DamnThisIsSoEmpty)
                        if (enableAddMediaFoldersHint) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                context.symphony.t.HintAddMediaFolders,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .clickable {
                                        context.navController.navigate(
                                            GrooveSettingsViewRoute(SettingsViewRoute.ELEMENT_MEDIA_FOLDERS)
                                        )
                                    }
                                    .padding(2.dp),
                            )
                        }
                    }
                )

                else -> {
                    val lazyListState = rememberLazyListState()

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.drawScrollBar(lazyListState)
                    ) {
                        leadingContent?.invoke(this)
                        itemsIndexed(
                            sortedSongIds,
                            key = { i, x -> "$i-$x" },
                            contentType = { _, _ -> Groove.Kind.SONG }
                        ) { i, songId ->
                            context.symphony.groove.song.get(songId)?.let { song ->
                                SongCard(
                                    context,
                                    song = song,
                                    selected = song.id in selectedSongIds,
                                    selectionMode = selectionMode,
                                    thumbnailLabel = cardThumbnailLabel?.let {
                                        { it(i, song) }
                                    },
                                    thumbnailLabelStyle = cardThumbnailLabelStyle,
                                    disableHeartIcon = disableHeartIcon,
                                    trailingOptionsContent = trailingOptionsContent?.let {
                                        { onDismissRequest -> it(i, song, onDismissRequest) }
                                    },
                                    onLongClick = {
                                        if (!isDeleting) {
                                            selectedSongIds = selectedSongIds + song.id
                                        }
                                    },
                                ) {
                                    when {
                                        selectionMode && !isDeleting -> {
                                            selectedSongIds =
                                                SongSelection.toggle(selectedSongIds, song.id)
                                        }

                                        !selectionMode -> {
                                            context.symphony.radio.shorty.playQueue(
                                                sortedSongIds,
                                                Radio.PlayOptions(index = i)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        trailingContent?.invoke(this)
                    }
                }
            }
        }
    )

    if (showBatchDeleteDialog) {
        val selectedSongs = selectedSongIds.mapNotNull(context.symphony.groove.song::get)
        BatchDeleteConfirmationDialog(
            context = context,
            songs = selectedSongs,
            onDismissRequest = { showBatchDeleteDialog = false },
            onConfirm = {
                showBatchDeleteDialog = false
                isDeleting = true
                val requestedIds = selectedSongIds
                coroutineScope.launch {
                    try {
                        val result = context.symphony.groove.deletion.deleteMany(requestedIds)
                        selectedSongIds -= result.deletedSongIds
                        if (result.failures.isNotEmpty()) {
                            Logger.warn(
                                "SongList",
                                "silent batch deletion left ${result.failures.size} item(s)",
                            )
                        }
                    } catch (err: Exception) {
                        Logger.error("SongList", "batch deletion failed", err)
                    } finally {
                        isDeleting = false
                    }
                }
            },
        )
    }
}

@Composable
private fun SongSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    isDeleting: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !isDeleting,
                    onClick = onClose,
                ) {
                    Icon(Icons.Filled.Close, "Exit selection")
                }
                Text(
                    "$selectedCount selected",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    enabled = !isDeleting,
                    onClick = onSelectAll,
                ) {
                    Icon(
                        Icons.Filled.SelectAll,
                        if (allSelected) "Deselect all" else "Select all",
                    )
                }
                IconButton(
                    enabled = !isDeleting,
                    onClick = onDelete,
                ) {
                    when {
                        isDeleting -> CircularProgressIndicator(modifier = Modifier.size(22.dp))
                        else -> Icon(
                            Icons.Filled.DeleteForever,
                            "Delete selected tracks",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun BatchDeleteConfirmationDialog(
    context: ViewContext,
    songs: List<Song>,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Delete ${songs.size} tracks from device?") },
        content = {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Text("These audio files will be permanently deleted. This cannot be undone.")
                Column(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    songs.forEach { song ->
                        Text(
                            song.title,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            song.path,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(context.symphony.t.Cancel)
            }
            TextButton(
                enabled = songs.isNotEmpty(),
                onClick = onConfirm,
            ) {
                Text(
                    "Delete permanently",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

fun SongRepository.SortBy.label(context: ViewContext) = when (this) {
    SongRepository.SortBy.CUSTOM -> context.symphony.t.Custom
    SongRepository.SortBy.TITLE -> context.symphony.t.Title
    SongRepository.SortBy.ARTIST -> context.symphony.t.Artist
    SongRepository.SortBy.ALBUM -> context.symphony.t.Album
    SongRepository.SortBy.DURATION -> context.symphony.t.Duration
    SongRepository.SortBy.DATE_MODIFIED -> context.symphony.t.LastModified
    SongRepository.SortBy.COMPOSER -> context.symphony.t.Composer
    SongRepository.SortBy.ALBUM_ARTIST -> context.symphony.t.AlbumArtist
    SongRepository.SortBy.YEAR -> context.symphony.t.Year
    SongRepository.SortBy.FILENAME -> context.symphony.t.Filename
    SongRepository.SortBy.TRACK_NUMBER -> context.symphony.t.TrackNumber
}

fun SongListType.getLastUsedSortBy(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortBy
    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortBy
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortBy
}

fun SongListType.setLastUsedSortBy(context: ViewContext, sort: SongRepository.SortBy) =
    when (this) {
        SongListType.Default -> context.symphony.settings.lastUsedSongsSortBy.setValue(sort)
        SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortBy.setValue(sort)
        SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortBy.setValue(sort)
    }

fun SongListType.getLastUsedSortReverse(context: ViewContext) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortReverse
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortReverse
    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortReverse
}

fun SongListType.setLastUsedSortReverse(context: ViewContext, reverse: Boolean) = when (this) {
    SongListType.Default -> context.symphony.settings.lastUsedSongsSortReverse.setValue(reverse)
    SongListType.Playlist -> context.symphony.settings.lastUsedPlaylistSongsSortReverse.setValue(
        reverse
    )

    SongListType.Album -> context.symphony.settings.lastUsedAlbumSongsSortReverse.setValue(reverse)
}
