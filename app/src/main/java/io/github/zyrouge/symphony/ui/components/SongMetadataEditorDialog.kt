package io.github.zyrouge.symphony.ui.components

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.MediaMetadataEditingService
import io.github.zyrouge.symphony.services.groove.MediaMetadataEditPolicy
import io.github.zyrouge.symphony.services.groove.MediaFilenamePolicy
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun SongMetadataEditorDialog(
    context: ViewContext,
    song: Song,
    onDismissRequest: () -> Unit,
) {
    val service = context.symphony.groove.metadataEditing
    val coroutineScope = rememberCoroutineScope()
    var draft by remember(song.id) {
        mutableStateOf<MediaMetadataEditingService.Draft?>(null)
    }
    var loadError by remember(song.id) { mutableStateOf<String?>(null) }
    val songPath = remember(song.path) { SimplePath(song.path) }
    var filenameBase by remember(song.id, song.path) {
        mutableStateOf(songPath.nameWithoutExtension)
    }
    var artists by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtists by remember { mutableStateOf("") }
    var composers by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var trackNumber by remember { mutableStateOf("") }
    var trackTotal by remember { mutableStateOf("") }
    var discNumber by remember { mutableStateOf("") }
    var discTotal by remember { mutableStateOf("") }
    var lyrics by remember { mutableStateOf("") }
    var artworkChange by remember {
        mutableStateOf<MediaMetadataEditingService.ArtworkChange>(
            MediaMetadataEditingService.ArtworkChange.Keep
        )
    }
    var artworkLabel by remember { mutableStateOf<String?>(null) }

    fun populate(value: MediaMetadataEditingService.Draft) {
        artists = value.artists
        album = value.album
        albumArtists = value.albumArtists
        composers = value.composers
        genres = value.genres
        date = value.date
        trackNumber = value.trackNumber
        trackTotal = value.trackTotal
        discNumber = value.discNumber
        discTotal = value.discTotal
        lyrics = value.lyrics
    }

    LaunchedEffect(song.id) {
        when (val result = service.load(song.id)) {
            is MediaMetadataEditingService.LoadResult.Success -> {
                draft = result.draft
                populate(result.draft)
            }

            MediaMetadataEditingService.LoadResult.NotFound ->
                loadError = "The audio file could not be found."
            MediaMetadataEditingService.LoadResult.PermissionDenied ->
                loadError = "Read permission for this file is unavailable."
            MediaMetadataEditingService.LoadResult.Unsupported ->
                loadError = "This file does not expose editable embedded tags."
            is MediaMetadataEditingService.LoadResult.Failed ->
                loadError = result.reason
        }
    }

    val artworkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val selected = runCatching {
                withContext(Dispatchers.IO) {
                    readArtwork(context, uri)
                }
            }
            selected.onSuccess { (bytes, mimeType) ->
                artworkChange = MediaMetadataEditingService.ArtworkChange.Replace(bytes, mimeType)
                artworkLabel = "New artwork selected (${bytes.size / 1024} KB)"
            }.onFailure {
                context.symphony.uiMessages.show(
                    "Artwork could not be loaded: ${it.localizedMessage ?: it}"
                )
            }
        }
    }

    val numericFieldsValid = listOf(trackNumber, trackTotal, discNumber, discTotal).all {
        MediaMetadataEditPolicy.isOptionalNonNegativeInteger(it)
    }
    val filenameValidation = MediaFilenamePolicy.buildDisplayName(song.filename, filenameBase)
    val filenameChanged = filenameValidation is MediaFilenamePolicy.Result.Valid
    val filenameValid = filenameChanged ||
            filenameValidation is MediaFilenamePolicy.Result.Unchanged
    val tagEditingSupported = draft?.tagEditingSupported == true

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Edit details") },
        contentHeight = 1f,
        content = {
            when {
                loadError != null -> Text(
                    loadError!!,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )

                draft == null -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetadataField(
                        label = "Filename",
                        value = filenameBase,
                        supportingText = when {
                            songPath.extension.isEmpty() ->
                                "The audio format is unchanged."
                            else ->
                                "The .${songPath.extension} extension is preserved."
                        },
                        onValueChange = {
                            filenameBase = it.replace("\r", "").replace("\n", "")
                        },
                    )
                    if (!tagEditingSupported) {
                        Text("Embedded tags are unavailable for this format, but the filename can still be changed.")
                    }
                    MetadataField(
                        "Artists",
                        artists,
                        enabled = tagEditingSupported,
                        supportingText = "Separate multiple values with semicolons.",
                    ) { artists = it }
                    MetadataField("Album", album, enabled = tagEditingSupported) { album = it }
                    MetadataField(
                        "Album artists",
                        albumArtists,
                        enabled = tagEditingSupported,
                        supportingText = "Separate multiple values with semicolons.",
                    ) { albumArtists = it }
                    MetadataField(
                        "Composers",
                        composers,
                        enabled = tagEditingSupported,
                        supportingText = "Separate multiple values with semicolons.",
                    ) { composers = it }
                    MetadataField(
                        "Genres",
                        genres,
                        enabled = tagEditingSupported,
                        supportingText = "Separate multiple values with semicolons.",
                    ) { genres = it }
                    MetadataField(
                        "Date or year",
                        date,
                        enabled = tagEditingSupported,
                    ) { date = it }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetadataNumberField(
                            modifier = Modifier.weight(1f),
                            label = "Track",
                            value = trackNumber,
                            enabled = tagEditingSupported,
                            onValueChange = { trackNumber = it },
                        )
                        MetadataNumberField(
                            modifier = Modifier.weight(1f),
                            label = "Track total",
                            value = trackTotal,
                            enabled = tagEditingSupported,
                            onValueChange = { trackTotal = it },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MetadataNumberField(
                            modifier = Modifier.weight(1f),
                            label = "Disc",
                            value = discNumber,
                            enabled = tagEditingSupported,
                            onValueChange = { discNumber = it },
                        )
                        MetadataNumberField(
                            modifier = Modifier.weight(1f),
                            label = "Disc total",
                            value = discTotal,
                            enabled = tagEditingSupported,
                            onValueChange = { discTotal = it },
                        )
                    }
                    MetadataField(
                        label = "Lyrics",
                        value = lyrics,
                        singleLine = false,
                        minLines = 4,
                        maxLines = 8,
                        enabled = tagEditingSupported,
                        onValueChange = { lyrics = it },
                    )
                    Text(
                        artworkLabel ?: when {
                            artworkChange is MediaMetadataEditingService.ArtworkChange.Remove ->
                                "Embedded artwork will be removed."
                            draft?.hasArtwork == true -> "This file has embedded artwork."
                            else -> "This file has no embedded artwork."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            enabled = tagEditingSupported,
                            onClick = { artworkPicker.launch("image/*") },
                        ) {
                            Text("Choose artwork")
                        }
                        TextButton(
                            enabled = tagEditingSupported && (
                                    draft?.hasArtwork == true ||
                                            artworkChange is
                                            MediaMetadataEditingService.ArtworkChange.Replace
                                    ),
                            onClick = {
                                artworkChange = MediaMetadataEditingService.ArtworkChange.Remove
                                artworkLabel = null
                            },
                        ) {
                            Text("Remove artwork")
                        }
                    }
                    Text(
                        "Filename and metadata changes are written into the audio file. The extension and encoded audio format are unchanged."
                    )
                }
            }
        },
        actions = {
            TextButton(onClick = onDismissRequest) {
                Text(context.symphony.t.Cancel)
            }
            TextButton(
                enabled = draft != null &&
                        loadError == null &&
                        filenameValid &&
                        (tagEditingSupported || filenameChanged) &&
                        numericFieldsValid,
                onClick = {
                    val loadedDraft = draft ?: return@TextButton
                    val requestedEdit = MediaMetadataEditingService.Edit(
                        filenameBase = filenameBase,
                        artists = artists,
                        album = album,
                        albumArtists = albumArtists,
                        composers = composers,
                        genres = genres,
                        date = date,
                        trackNumber = trackNumber,
                        trackTotal = trackTotal,
                        discNumber = discNumber,
                        discTotal = discTotal,
                        lyrics = lyrics,
                        artwork = artworkChange,
                    )
                    onDismissRequest()
                    context.symphony.groove.coroutineScope.launch {
                        val result = service.save(
                            songId = song.id,
                            draft = loadedDraft,
                            edit = requestedEdit,
                        )
                        if (result !is MediaMetadataEditingService.SaveResult.Success) {
                            io.github.zyrouge.symphony.utils.Logger.warn(
                                "SongMetadataEditor",
                                "silent save did not complete: $result",
                            )
                        }
                    }
                },
            ) {
                Text("Save")
            }
        },
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MetadataField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    coroutineScope.launch {
                        delay(220)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MetadataNumberField(
    modifier: Modifier,
    label: String,
    value: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        modifier = modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .onFocusChanged {
                if (it.isFocused) {
                    coroutineScope.launch {
                        delay(220)
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            },
        value = value,
        onValueChange = { input ->
            if (input.isEmpty() || input.all(Char::isDigit)) {
                onValueChange(input)
            }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
        ),
        enabled = enabled,
    )
}

private fun readArtwork(context: ViewContext, uri: Uri): Pair<ByteArray, String> {
    val resolver = context.activity.contentResolver
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= MAX_ARTWORK_BYTES) { "Image is larger than 20 MB" }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: error("The selected image could not be opened")
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "The selected file is not an image" }
    val mimeType = resolver.getType(uri)
        ?.takeIf { it.startsWith("image/", ignoreCase = true) }
        ?: options.outMimeType
        ?: error("The image type could not be determined")
    return bytes to mimeType
}

private const val MAX_ARTWORK_BYTES = 20 * 1024 * 1024
