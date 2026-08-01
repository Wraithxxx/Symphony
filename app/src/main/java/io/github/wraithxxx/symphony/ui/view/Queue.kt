package io.github.wraithxxx.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wraithxxx.symphony.services.groove.Groove
import io.github.wraithxxx.symphony.ui.components.AddToPlaylistDialog
import io.github.wraithxxx.symphony.ui.components.IconButtonPlaceholderSize
import io.github.wraithxxx.symphony.ui.components.NewPlaylistDialog
import io.github.wraithxxx.symphony.ui.components.SongCard
import io.github.wraithxxx.symphony.ui.components.TopAppBarMinimalTitle
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.ui.view.nowPlaying.NothingPlayingBody
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object QueueViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val queue by context.symphony.radio.observatory.queue.collectAsState()
    val queueIndex by context.symphony.radio.observatory.queueIndex.collectAsState()
    val selectedSongIndices = remember { mutableStateListOf<Int>() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = queueIndex,
    )
    var showSaveDialog by remember { mutableStateOf(false) }
    var showAddSelectedDialog by remember { mutableStateOf(false) }
    val allSelected = queue.isNotEmpty() && selectedSongIndices.size == queue.size

    LaunchedEffect(queue.size) {
        selectedSongIndices.removeAll { it !in queue.indices }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle(
                        modifier = Modifier.padding(start = IconButtonPlaceholderSize)
                    ) {
                        Text(
                            when {
                                selectedSongIndices.isNotEmpty() ->
                                    "${selectedSongIndices.size} selected"
                                else -> context.symphony.t.Queue
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.mediumTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                selectedSongIndices.isNotEmpty() ->
                                    selectedSongIndices.clear()
                                else -> context.navController.popBackStack()
                            }
                        }
                    ) {
                        Icon(
                            when {
                                selectedSongIndices.isNotEmpty() -> Icons.Filled.Close
                                else -> Icons.Filled.ExpandMore
                            },
                            when {
                                selectedSongIndices.isNotEmpty() -> "Exit selection"
                                else -> "Close queue"
                            },
                        )
                    }
                },
                actions = {
                    if (selectedSongIndices.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                showAddSelectedDialog = true
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                "Add selected tracks to playlist",
                            )
                        }
                        IconButton(
                            onClick = {
                                when {
                                    allSelected -> selectedSongIndices.clear()
                                    else -> {
                                        selectedSongIndices.clear()
                                        selectedSongIndices.addAll(queue.indices)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.SelectAll,
                                if (allSelected) "Deselect all" else "Select all",
                            )
                        }
                        IconButton(
                            onClick = {
                                context.symphony.radio.queue.remove(selectedSongIndices.toList())
                                selectedSongIndices.clear()
                            }
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                "Remove selected from queue",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                showSaveDialog = !showSaveDialog
                            }
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.PlaylistAdd,
                                "Save queue as playlist",
                            )
                        }
                        IconButton(
                            onClick = {
                                context.symphony.radio.stop()
                                selectedSongIndices.clear()
                            }
                        ) {
                            Icon(
                                Icons.Filled.ClearAll,
                                "Clear queue",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                if (queue.isEmpty()) {
                    NothingPlayingBody(context)
                } else {
                    LazyColumn(state = listState) {
                        itemsIndexed(
                            queue,
                            key = { i, id -> "$i-$id" },
                            contentType = { _, _ -> Groove.Kind.SONG },
                        ) { i, songId ->
                            context.symphony.groove.song.get(songId)?.let { song ->
                                val isCurrent = i == queueIndex
                                val isSelected = selectedSongIndices.contains(i)
                                val isPlayed = i < queueIndex
                                val primary = MaterialTheme.colorScheme.primary
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            alpha = when {
                                                isPlayed && !isSelected -> 0.58f
                                                else -> 1f
                                            }
                                        }
                                        .drawBehind {
                                            if (isCurrent) {
                                                drawRect(primary.copy(alpha = 0.06f))
                                                drawLine(
                                                    color = primary,
                                                    start = androidx.compose.ui.geometry.Offset(
                                                        1.5.dp.toPx(),
                                                        0f,
                                                    ),
                                                    end = androidx.compose.ui.geometry.Offset(
                                                        1.5.dp.toPx(),
                                                        size.height,
                                                    ),
                                                    strokeWidth = 3.dp.toPx(),
                                                    cap = StrokeCap.Butt,
                                                )
                                            }
                                        }
                                ) {
                                    SongCard(
                                        context,
                                        song,
                                        autoHighlight = false,
                                        highlighted = isCurrent,
                                        disableHeartIcon = true,
                                        selected = isSelected,
                                        selectionMode = selectedSongIndices.isNotEmpty(),
                                        leading = {
                                            Box(
                                                modifier = Modifier.width(32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                when {
                                                    isCurrent -> Icon(
                                                        Icons.Filled.GraphicEq,
                                                        "Now playing",
                                                        tint = primary,
                                                    )
                                                    else -> Text(
                                                        (i + 1).toString(),
                                                        style = MaterialTheme.typography.labelMedium
                                                            .copy(
                                                                color = MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                                fontWeight = FontWeight.Medium,
                                                            ),
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        },
                                        onLongClick = {
                                            if (isSelected) {
                                                selectedSongIndices.remove(i)
                                            } else {
                                                selectedSongIndices.add(i)
                                            }
                                        },
                                        onClick = {
                                            when {
                                                selectedSongIndices.isNotEmpty() -> {
                                                    if (isSelected) {
                                                        selectedSongIndices.remove(i)
                                                    } else {
                                                        selectedSongIndices.add(i)
                                                    }
                                                }
                                                else -> {
                                                    context.symphony.radio.jumpTo(i)
                                                    coroutineScope.launch {
                                                        listState.animateScrollToItem(i)
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )

    if (showSaveDialog) {
        NewPlaylistDialog(
            context,
            initialSongIds = queue.toList(),
            onDone = { playlist ->
                showSaveDialog = false
                context.symphony.groove.playlist.add(playlist)
            },
            onDismissRequest = {
                showSaveDialog = false
            }
        )
    }

    if (showAddSelectedDialog) {
        AddToPlaylistDialog(
            context = context,
            songIds = selectedSongIndices
                .sorted()
                .mapNotNull { queue.getOrNull(it) },
            onDismissRequest = {
                showAddSelectedDialog = false
            },
            onAdded = {
                selectedSongIndices.clear()
            },
        )
    }
}
