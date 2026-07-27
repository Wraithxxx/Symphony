package io.github.zyrouge.symphony.services.groove

import android.provider.DocumentsContract
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.DocumentFileX
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class MediaDeletionService(private val symphony: Symphony) {
    sealed class Result {
        data class Success(val deletedCurrentSong: Boolean) : Result()
        object NotFound : Result()
        object PermissionDenied : Result()
        object Unsupported : Result()
        data class Failed(val reason: String) : Result()
    }

    data class BatchFailure(
        val songId: String,
        val title: String,
        val result: Result,
    )

    data class BatchResult(
        val requestedCount: Int,
        val deletedSongIds: Set<String>,
        val failures: List<BatchFailure>,
        val deletedCurrentSong: Boolean,
    )

    suspend fun delete(songId: String): Result {
        return symphony.groove.withLibraryTransaction {
            deleteInLibraryTransaction(songId)
        }
    }

    suspend fun deleteMany(songIds: Collection<String>): BatchResult {
        val requestedIds = songIds.distinct()
        return symphony.groove.withLibraryTransaction {
            val failures = mutableListOf<BatchFailure>()
            val deletedSongs = mutableListOf<Song>()

            requestedIds.forEach { songId ->
                val song = symphony.groove.song.get(songId)
                if (song == null) {
                    failures += BatchFailure(songId, songId, Result.NotFound)
                    return@forEach
                }
                when (val result = deleteDocument(song)) {
                    is Result.Success -> deletedSongs += song
                    else -> failures += BatchFailure(song.id, song.title, result)
                }
            }

            val deletedCurrentSong = reconcileDeletedSongs(deletedSongs)
            BatchResult(
                requestedCount = requestedIds.size,
                deletedSongIds = deletedSongs.mapTo(linkedSetOf(), Song::id),
                failures = failures,
                deletedCurrentSong = deletedCurrentSong,
            )
        }
    }

    private suspend fun deleteInLibraryTransaction(songId: String): Result {
        val song = symphony.groove.song.get(songId) ?: return Result.NotFound
        val result = deleteDocument(song)
        if (result !is Result.Success) {
            return result
        }
        return Result.Success(reconcileDeletedSongs(listOf(song)))
    }

    private suspend fun deleteDocument(song: Song): Result {
        return StorageMutationRetry.run(
            shouldRetry = { it is Result.Failed },
        ) { attempt ->
            val result = deleteDocumentOnce(song)
            if (result is Result.Failed) {
                Logger.warn(
                    "MediaDeletionService",
                    "deletion attempt $attempt failed for ${song.id}: ${result.reason}",
                )
            }
            result
        }
    }

    private suspend fun deleteDocumentOnce(song: Song): Result {
        val document = try {
            withContext(Dispatchers.IO) {
                DocumentFileX.fromSingleUri(symphony.applicationContext, song.uri)
            }
        } catch (_: SecurityException) {
            return Result.PermissionDenied
        } catch (_: FileNotFoundException) {
            return Result.Success(deletedCurrentSong = false)
        } catch (err: Exception) {
            return Result.Failed(err.localizedMessage ?: err.toString())
        } ?: return Result.Success(deletedCurrentSong = false)

        if (!document.supportsDelete) {
            return Result.Unsupported
        }

        val deleted = try {
            withContext(Dispatchers.IO) {
                DocumentsContract.deleteDocument(
                    symphony.applicationContext.contentResolver,
                    song.uri,
                )
            }
        } catch (_: SecurityException) {
            return Result.PermissionDenied
        } catch (_: FileNotFoundException) {
            return Result.Success(deletedCurrentSong = false)
        } catch (err: Exception) {
            Logger.error("MediaDeletionService", "document deletion failed", err)
            return Result.Failed(err.localizedMessage ?: err.toString())
        }
        if (!deleted || documentStillExists(song)) {
            return Result.Failed("The storage provider rejected the deletion")
        }

        return Result.Success(deletedCurrentSong = false)
    }

    private suspend fun documentStillExists(song: Song): Boolean = try {
        withContext(Dispatchers.IO) {
            DocumentFileX.fromSingleUri(symphony.applicationContext, song.uri) != null
        }
    } catch (_: FileNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (error: Exception) {
        Logger.warn("MediaDeletionService", "deletion verification failed", error)
        true
    }

    private suspend fun reconcileDeletedSongs(songs: List<Song>): Boolean {
        if (songs.isEmpty()) {
            return false
        }
        val songIds = songs.mapTo(linkedSetOf(), Song::id)
        val deletedCurrentSong = withContext(Dispatchers.Main) {
            symphony.radio.removeDeletedSongs(songIds)
        }
        runCatching {
            symphony.groove.playlist.removeSongPaths(songs.mapTo(linkedSetOf(), Song::path))
        }.onFailure {
            Logger.error("MediaDeletionService", "playlist cleanup failed", it)
        }
        runCatching {
            withContext(Dispatchers.IO) {
                songs.forEach { song ->
                    symphony.database.songCache.delete(song.id)
                    symphony.database.lyricsCache.delete(song.id)
                    song.coverFile?.let { symphony.database.artworkCache.get(it).delete() }
                }
            }
        }.onFailure {
            Logger.error("MediaDeletionService", "private cache cleanup failed", it)
        }
        symphony.groove.publishDeletedSongs(songs)
        return deletedCurrentSong
    }
}
