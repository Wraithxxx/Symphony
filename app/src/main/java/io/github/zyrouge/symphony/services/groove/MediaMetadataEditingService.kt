package io.github.zyrouge.symphony.services.groove

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import io.github.zyrouge.metaphony.AudioArtwork
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.DocumentFileX
import io.github.zyrouge.symphony.utils.ImagePreserver
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.time.LocalDate

class MediaMetadataEditingService(private val symphony: Symphony) {
    data class Draft(
        val artists: String,
        val album: String,
        val albumArtists: String,
        val composers: String,
        val genres: String,
        val date: String,
        val trackNumber: String,
        val trackTotal: String,
        val discNumber: String,
        val discTotal: String,
        val lyrics: String,
        val hasArtwork: Boolean,
        val tagEditingSupported: Boolean,
        internal val originalProperties: PropertyMap,
        internal val originalPictures: Array<Picture>,
    )

    sealed class ArtworkChange {
        object Keep : ArtworkChange()
        object Remove : ArtworkChange()
        data class Replace(val bytes: ByteArray, val mimeType: String) : ArtworkChange()
    }

    data class Edit(
        val filenameBase: String,
        val artists: String,
        val album: String,
        val albumArtists: String,
        val composers: String,
        val genres: String,
        val date: String,
        val trackNumber: String,
        val trackTotal: String,
        val discNumber: String,
        val discTotal: String,
        val lyrics: String,
        val artwork: ArtworkChange,
    )

    sealed class LoadResult {
        data class Success(val draft: Draft) : LoadResult()
        object NotFound : LoadResult()
        object PermissionDenied : LoadResult()
        object Unsupported : LoadResult()
        data class Failed(val reason: String) : LoadResult()
    }

    sealed class SaveResult {
        data class Success(val song: Song) : SaveResult()
        data class PartialSuccess(val song: Song, val reason: String) : SaveResult()
        object NotFound : SaveResult()
        object InvalidName : SaveResult()
        object Conflict : SaveResult()
        object PermissionDenied : SaveResult()
        object Unsupported : SaveResult()
        object RenameUnsupported : SaveResult()
        data class Failed(val reason: String) : SaveResult()
    }

    suspend fun load(songId: String): LoadResult {
        val song = symphony.groove.song.get(songId) ?: return LoadResult.NotFound
        return try {
            withContext(Dispatchers.IO) {
                val metadata = withReadDescriptor(song.uri) { fd ->
                    TagLib.getMetadata(fd, readPictures = true)
                } ?: return@withContext LoadResult.Success(fallbackDraft(song))
                val properties = metadata.propertyMap.deepCopy()
                LoadResult.Success(
                    Draft(
                        artists = properties.multiValue(ARTIST),
                        album = properties.singleValue(ALBUM),
                        albumArtists = properties.multiValue(ALBUM_ARTIST),
                        composers = properties.multiValue(COMPOSER),
                        genres = properties.multiValue(GENRE),
                        date = properties.singleValue(DATE),
                        trackNumber = properties.singleValue(TRACK_NUMBER),
                        trackTotal = properties.singleValue(TRACK_TOTAL),
                        discNumber = properties.singleValue(DISC_NUMBER),
                        discTotal = properties.singleValue(DISC_TOTAL),
                        lyrics = properties.singleValue(LYRICS),
                        hasArtwork = metadata.pictures.isNotEmpty(),
                        tagEditingSupported = true,
                        originalProperties = properties,
                        originalPictures = metadata.pictures,
                    )
                )
            }
        } catch (_: SecurityException) {
            LoadResult.PermissionDenied
        } catch (_: FileNotFoundException) {
            LoadResult.NotFound
        } catch (error: Exception) {
            Logger.error("MediaMetadataEditingService", "metadata load failed", error)
            LoadResult.Failed(error.localizedMessage ?: error.toString())
        }
    }

    suspend fun save(songId: String, draft: Draft, edit: Edit): SaveResult =
        symphony.groove.withLibraryTransaction {
            val song = symphony.groove.song.get(songId)
                ?: return@withLibraryTransaction SaveResult.NotFound
            val renameValidation = MediaFilenamePolicy.buildDisplayName(
                currentFilename = song.filename,
                requestedBaseName = edit.filenameBase,
            )
            val renameRequested = when (renameValidation) {
                is MediaFilenamePolicy.Result.Valid -> true
                MediaFilenamePolicy.Result.Unchanged -> false
                else -> return@withLibraryTransaction SaveResult.InvalidName
            }
            val document = try {
                withContext(Dispatchers.IO) {
                    DocumentFileX.fromSingleUri(symphony.applicationContext, song.uri)
                }
            } catch (_: SecurityException) {
                return@withLibraryTransaction SaveResult.PermissionDenied
            } catch (_: FileNotFoundException) {
                return@withLibraryTransaction SaveResult.NotFound
            } ?: return@withLibraryTransaction SaveResult.NotFound
            if (draft.tagEditingSupported && !document.supportsWrite) {
                return@withLibraryTransaction SaveResult.Unsupported
            }

            val playback = symphony.radio.releaseForFileMutation(song.id)
            var renamedSong: Song? = null
            try {
                val targetSong = if (renameRequested) {
                    when (
                        val result = symphony.groove.renaming.renameInLibraryTransaction(
                            songId = song.id,
                            requestedBaseName = edit.filenameBase,
                            reconcile = false,
                        )
                    ) {
                        is MediaRenameService.Result.Success -> result.song.also {
                            renamedSong = it
                        }

                        MediaRenameService.Result.NotFound ->
                            return@withLibraryTransaction SaveResult.NotFound
                        MediaRenameService.Result.InvalidName ->
                            return@withLibraryTransaction SaveResult.InvalidName
                        MediaRenameService.Result.Unchanged -> song
                        MediaRenameService.Result.Conflict ->
                            return@withLibraryTransaction SaveResult.Conflict
                        MediaRenameService.Result.PermissionDenied ->
                            return@withLibraryTransaction SaveResult.PermissionDenied
                        MediaRenameService.Result.Unsupported ->
                            return@withLibraryTransaction SaveResult.RenameUnsupported
                        is MediaRenameService.Result.Failed ->
                            return@withLibraryTransaction SaveResult.Failed(result.reason)
                    }
                } else {
                    song
                }
                if (!draft.tagEditingSupported) {
                    if (!renameRequested) {
                        return@withLibraryTransaction SaveResult.Unsupported
                    }
                    val filenameTitledSong = targetSong.copy(
                        title = SimplePath(targetSong.path).nameWithoutExtension,
                    )
                    reconcileEditedSong(song, filenameTitledSong)
                    return@withLibraryTransaction SaveResult.Success(filenameTitledSong)
                }
                val properties = draft.originalProperties.deepCopy().apply {
                    remove(TITLE)
                    replaceMulti(ARTIST, edit.artists)
                    replace(ALBUM, edit.album)
                    replaceMulti(ALBUM_ARTIST, edit.albumArtists)
                    replaceMulti(COMPOSER, edit.composers)
                    replaceMulti(GENRE, edit.genres)
                    replace(DATE, edit.date)
                    replace(TRACK_NUMBER, edit.trackNumber)
                    replace(TRACK_TOTAL, edit.trackTotal)
                    replace(DISC_NUMBER, edit.discNumber)
                    replace(DISC_TOTAL, edit.discTotal)
                    replace(LYRICS, edit.lyrics, preserveLineBreaks = true)
                }
                val pictures = when (val artwork = edit.artwork) {
                    ArtworkChange.Keep -> draft.originalPictures
                    ArtworkChange.Remove -> emptyArray()
                    is ArtworkChange.Replace -> arrayOf(
                        Picture(
                            data = artwork.bytes,
                            description = "",
                            pictureType = "Front Cover",
                            mimeType = artwork.mimeType,
                        )
                    )
                }
                val saved = try {
                    StorageMutationRetry.run(
                        shouldRetry = { successful -> !successful },
                    ) { attempt ->
                        val successful = try {
                            withContext(Dispatchers.IO) {
                                val propertiesSaved = withWriteDescriptor(targetSong.uri) { fd ->
                                    TagLib.savePropertyMap(fd, properties)
                                }
                                val picturesSaved = when (edit.artwork) {
                                    ArtworkChange.Keep -> true
                                    else -> withWriteDescriptor(targetSong.uri) { fd ->
                                        TagLib.savePictures(fd, pictures)
                                    }
                                }
                                propertiesSaved && picturesSaved &&
                                        verifyWrittenMetadata(
                                            targetSong.uri,
                                            properties,
                                            edit.artwork,
                                        )
                            }
                        } catch (error: SecurityException) {
                            throw error
                        } catch (error: FileNotFoundException) {
                            throw error
                        } catch (error: Exception) {
                            Logger.warn(
                                "MediaMetadataEditingService",
                                "transient metadata write failure on attempt $attempt",
                                error,
                            )
                            false
                        }
                        if (!successful) {
                            Logger.warn(
                                "MediaMetadataEditingService",
                                "metadata verification failed on attempt $attempt",
                            )
                        }
                        successful
                    }
                } catch (_: SecurityException) {
                    return@withLibraryTransaction reconcilePartialRename(
                        originalSong = song,
                        renamedSong = renamedSong,
                        failure = SaveResult.PermissionDenied,
                    )
                } catch (_: FileNotFoundException) {
                    return@withLibraryTransaction reconcilePartialRename(
                        originalSong = song,
                        renamedSong = renamedSong,
                        failure = SaveResult.NotFound,
                    )
                }
                if (!saved) {
                    return@withLibraryTransaction reconcilePartialRename(
                        originalSong = song,
                        renamedSong = renamedSong,
                        failure = SaveResult.Unsupported,
                    )
                }

                val updatedDocument = withContext(Dispatchers.IO) {
                    DocumentFileX.fromSingleUri(symphony.applicationContext, targetSong.uri)
                } ?: return@withLibraryTransaction reconcilePartialRename(
                    originalSong = song,
                    renamedSong = renamedSong,
                    failure = SaveResult.NotFound,
                )
                val updatedSong = projectUpdatedSong(targetSong, updatedDocument, edit)
                reconcileEditedSong(song, updatedSong)
                SaveResult.Success(updatedSong)
            } catch (error: Exception) {
                Logger.error("MediaMetadataEditingService", "metadata save failed", error)
                reconcilePartialRename(
                    originalSong = song,
                    renamedSong = renamedSong,
                    failure = SaveResult.Failed(error.localizedMessage ?: error.toString()),
                )
            } finally {
                symphony.radio.restoreAfterFileMutation(playback)
            }
        }

    private fun fallbackDraft(song: Song) = Draft(
        artists = song.artists.joinToString("; "),
        album = song.album.orEmpty(),
        albumArtists = song.albumArtists.joinToString("; "),
        composers = song.composers.joinToString("; "),
        genres = song.genres.joinToString("; "),
        date = song.date?.toString() ?: song.year?.toString().orEmpty(),
        trackNumber = song.trackNumber?.toString().orEmpty(),
        trackTotal = song.trackTotal?.toString().orEmpty(),
        discNumber = song.discNumber?.toString().orEmpty(),
        discTotal = song.discTotal?.toString().orEmpty(),
        lyrics = "",
        hasArtwork = song.coverFile != null,
        tagEditingSupported = false,
        originalProperties = hashMapOf(),
        originalPictures = emptyArray(),
    )

    private suspend fun reconcilePartialRename(
        originalSong: Song,
        renamedSong: Song?,
        failure: SaveResult,
    ): SaveResult {
        val actualRenamedSong = renamedSong?.copy(
            title = SimplePath(renamedSong.path).nameWithoutExtension,
        ) ?: return failure
        reconcileEditedSong(originalSong, actualRenamedSong)
        val reason = when (failure) {
            SaveResult.NotFound -> "The file was renamed, but its metadata could not be reread."
            SaveResult.PermissionDenied ->
                "The file was renamed, but write permission prevented metadata changes."
            SaveResult.Unsupported ->
                "The file was renamed, but this format or provider rejected metadata changes."
            is SaveResult.Failed -> failure.reason
            else -> "The filename changed, but the remaining metadata changes failed."
        }
        return SaveResult.PartialSuccess(actualRenamedSong, reason)
    }

    private suspend fun reconcileEditedSong(originalSong: Song, updatedSong: Song) {
        if (originalSong.path != updatedSong.path) {
            symphony.groove.playlist.replaceSongPath(originalSong.path, updatedSong.path)
        }
        symphony.radio.progress.migrate(originalSong, updatedSong)
        withContext(Dispatchers.IO) {
            val updated = symphony.database.songCache.update(updatedSong)
            if (updated == 0) {
                symphony.database.songCache.insert(updatedSong)
            }
        }
        symphony.groove.publishEditedSong(originalSong, updatedSong)
    }

    private suspend fun projectUpdatedSong(
        song: Song,
        document: DocumentFileX,
        edit: Edit,
    ): Song {
        val rawDate = edit.date.trim()
        val parsedDate = runCatching { LocalDate.parse(rawDate) }.getOrNull()
        val parsedYear = parsedDate?.year ?: rawDate.toIntOrNull()
        val coverFile = updateArtworkCache(song, edit.artwork)
        withContext(Dispatchers.IO) {
            val lyrics = edit.lyrics.trim()
            if (lyrics.isEmpty()) {
                symphony.database.lyricsCache.delete(setOf(song.id))
            } else {
                symphony.database.lyricsCache.put(song.id, lyrics)
            }
        }
        return song.copy(
            title = SimplePath(song.path).nameWithoutExtension,
            album = edit.album.trim().ifEmpty { null },
            artists = MediaMetadataEditPolicy.splitMultiValue(edit.artists).toSet(),
            composers = MediaMetadataEditPolicy.splitMultiValue(edit.composers).toSet(),
            albumArtists = MediaMetadataEditPolicy.splitMultiValue(edit.albumArtists).toSet(),
            genres = MediaMetadataEditPolicy.splitMultiValue(edit.genres).toSet(),
            trackNumber = edit.trackNumber.trim().toIntOrNull(),
            trackTotal = edit.trackTotal.trim().toIntOrNull(),
            discNumber = edit.discNumber.trim().toIntOrNull(),
            discTotal = edit.discTotal.trim().toIntOrNull(),
            date = parsedDate,
            year = parsedYear,
            dateModified = document.lastModified,
            size = document.size,
            coverFile = coverFile,
        )
    }

    private suspend fun updateArtworkCache(song: Song, change: ArtworkChange): String? =
        withContext(Dispatchers.IO) {
            when (change) {
                ArtworkChange.Keep -> song.coverFile
                ArtworkChange.Remove -> {
                    song.coverFile?.let { symphony.database.artworkCache.get(it).delete() }
                    null
                }

                is ArtworkChange.Replace -> runCatching {
                    val bitmap = BitmapFactory.decodeByteArray(
                        change.bytes,
                        0,
                        change.bytes.size,
                    ) ?: error("The selected artwork could not be decoded")
                    val name = "${song.id}-edit-${System.currentTimeMillis()}." +
                            AudioArtwork.Format.Jpeg.extension
                    FileOutputStream(symphony.database.artworkCache.get(name)).use { writer ->
                        ImagePreserver
                            .resize(bitmap, symphony.settings.artworkQuality.value)
                            .compress(Bitmap.CompressFormat.JPEG, 100, writer)
                    }
                    song.coverFile
                        ?.takeIf { it != name }
                        ?.let { symphony.database.artworkCache.get(it).delete() }
                    name
                }.onFailure {
                    Logger.warn("MediaMetadataEditingService", "artwork cache update failed", it)
                }.getOrDefault(song.coverFile)
            }
        }

    private fun verifyWrittenMetadata(
        uri: Uri,
        expected: PropertyMap,
        artwork: ArtworkChange,
    ): Boolean {
        val actual = withReadDescriptor(uri) { fd ->
            TagLib.getMetadata(fd, readPictures = artwork !is ArtworkChange.Keep)
        } ?: return false
        val keys = listOf(
            TITLE,
            ARTIST,
            ALBUM,
            ALBUM_ARTIST,
            COMPOSER,
            GENRE,
            DATE,
            TRACK_NUMBER,
            TRACK_TOTAL,
            DISC_NUMBER,
            DISC_TOTAL,
            LYRICS,
        )
        val propertiesMatch = keys.all { key ->
            actual.propertyMap[key]?.toList().orEmpty() ==
                    expected[key]?.toList().orEmpty()
        }
        val artworkMatches = when (artwork) {
            ArtworkChange.Keep -> true
            ArtworkChange.Remove -> actual.pictures.isEmpty()
            is ArtworkChange.Replace -> actual.pictures.isNotEmpty()
        }
        return propertiesMatch && artworkMatches
    }

    private fun <T> withReadDescriptor(uri: Uri, block: (Int) -> T): T {
        val descriptor = symphony.applicationContext.contentResolver
            .openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException(uri.toString())
        descriptor.use {
            val detached = it.dup().detachFd()
            return block(detached)
        }
    }

    private fun <T> withWriteDescriptor(uri: Uri, block: (Int) -> T): T {
        val descriptor = symphony.applicationContext.contentResolver
            .openFileDescriptor(uri, "rw")
            ?: throw FileNotFoundException(uri.toString())
        descriptor.use {
            val detached = it.dup().detachFd()
            return block(detached)
        }
    }

    private fun PropertyMap.deepCopy(): PropertyMap =
        HashMap(mapValues { (_, values) -> values.copyOf() })

    private fun PropertyMap.singleValue(key: String) = get(key)?.firstOrNull().orEmpty()

    private fun PropertyMap.multiValue(key: String) = get(key)?.joinToString("; ").orEmpty()

    private fun PropertyMap.replace(
        key: String,
        rawValue: String,
        preserveLineBreaks: Boolean = false,
    ) {
        val value = if (preserveLineBreaks) rawValue.trim() else rawValue.trim().replace('\n', ' ')
        if (value.isEmpty()) remove(key) else put(key, arrayOf(value))
    }

    private fun PropertyMap.replaceMulti(key: String, rawValue: String) {
        val values = MediaMetadataEditPolicy.splitMultiValue(rawValue)
        if (values.isEmpty()) remove(key) else put(key, values)
    }

    companion object {
        private const val TITLE = "TITLE"
        private const val ARTIST = "ARTIST"
        private const val ALBUM = "ALBUM"
        private const val ALBUM_ARTIST = "ALBUMARTIST"
        private const val COMPOSER = "COMPOSER"
        private const val GENRE = "GENRE"
        private const val DATE = "DATE"
        private const val TRACK_NUMBER = "TRACKNUMBER"
        private const val TRACK_TOTAL = "TRACKTOTAL"
        private const val DISC_NUMBER = "DISCNUMBER"
        private const val DISC_TOTAL = "DISCTOTAL"
        private const val LYRICS = "LYRICS"
    }
}
