package io.github.zyrouge.symphony.services.groove

import android.net.Uri
import android.provider.DocumentsContract
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.DocumentFileX
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SimplePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class MediaRenameService(private val symphony: Symphony) {
    sealed class Result {
        data class Success(val song: Song) : Result()
        object NotFound : Result()
        object InvalidName : Result()
        object Unchanged : Result()
        object Conflict : Result()
        object PermissionDenied : Result()
        object Unsupported : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun rename(songId: String, requestedBaseName: String): Result =
        symphony.groove.withLibraryTransaction {
            renameInLibraryTransaction(songId, requestedBaseName, reconcile = true)
        }

    internal suspend fun renameInLibraryTransaction(
        songId: String,
        requestedBaseName: String,
        reconcile: Boolean,
    ): Result {
        return try {
            val song = symphony.groove.song.get(songId) ?: return Result.NotFound
            val displayName = when (
                val validation = MediaFilenamePolicy.buildDisplayName(
                    currentFilename = song.filename,
                    requestedBaseName = requestedBaseName,
                )
            ) {
                is MediaFilenamePolicy.Result.Valid -> validation.displayName
                MediaFilenamePolicy.Result.Unchanged -> return Result.Unchanged
                else -> return Result.InvalidName
            }
            val oldPath = SimplePath(song.path)
            val newPath = oldPath.parent?.join(displayName) ?: SimplePath(displayName)
            if (symphony.groove.exposer.uris.keys.any {
                    it != song.path && it.equals(newPath.pathString, ignoreCase = true)
                }
            ) {
                return Result.Conflict
            }

            val document = queryDocument(song.uri) ?: return Result.NotFound
            if (!document.supportsRename) {
                return Result.Unsupported
            }
            val renamedUri = renameDocument(song.uri, displayName)
                ?: return Result.Failed(
                    "The storage provider rejected the filename",
                )
            val renamedDocument = queryDocument(renamedUri)
                ?: return Result.Failed(
                    "The file was renamed, but its updated storage record could not be read",
                )
            val actualPath = oldPath.parent?.join(renamedDocument.name)
                ?: SimplePath(renamedDocument.name)
            val updatedSong = withContext(Dispatchers.IO) {
                runCatching {
                    Song.parse(
                        symphony = symphony,
                        path = actualPath,
                        file = renamedDocument,
                        stableId = song.id,
                    )
                }.getOrElse { error ->
                    Logger.warn("MediaRenameService", "metadata refresh after rename failed", error)
                    song.copy(
                        uri = renamedUri,
                        path = actualPath.pathString,
                        dateModified = renamedDocument.lastModified,
                        size = renamedDocument.size,
                    )
                }
            }

            if (reconcile) {
                reconcile(song, updatedSong)
            }
            Result.Success(updatedSong)
        } catch (_: RenamePermissionException) {
            Result.PermissionDenied
        } catch (error: RenameFailureException) {
            Result.Failed(error.message ?: "The storage provider rejected the rename")
        } catch (error: Exception) {
            Logger.error("MediaRenameService", "rename failed", error)
            Result.Failed(error.localizedMessage ?: error.toString())
        }
    }

    private suspend fun queryDocument(uri: Uri): DocumentFileX? = try {
        withContext(Dispatchers.IO) {
            DocumentFileX.fromSingleUri(symphony.applicationContext, uri)
        }
    } catch (_: SecurityException) {
        throw RenamePermissionException()
    } catch (_: FileNotFoundException) {
        null
    }

    private suspend fun renameDocument(uri: Uri, displayName: String): Uri? = try {
        withContext(Dispatchers.IO) {
            DocumentsContract.renameDocument(
                symphony.applicationContext.contentResolver,
                uri,
                displayName,
            )
        }
    } catch (_: SecurityException) {
        throw RenamePermissionException()
    } catch (_: FileNotFoundException) {
        null
    } catch (error: Exception) {
        Logger.error("MediaRenameService", "document rename failed", error)
        throw RenameFailureException(error.localizedMessage ?: error.toString())
    }

    private suspend fun reconcile(oldSong: Song, newSong: Song) {
        runCatching {
            symphony.groove.playlist.replaceSongPath(oldSong.path, newSong.path)
            symphony.radio.progress.migrate(oldSong, newSong)
            withContext(Dispatchers.IO) {
                val updated = symphony.database.songCache.update(newSong)
                if (updated == 0) {
                    symphony.database.songCache.insert(newSong)
                }
            }
            symphony.groove.refreshInLibraryTransaction(Groove.FetchOptions(force = true))
        }.onFailure {
            Logger.error("MediaRenameService", "library reconciliation failed", it)
        }
    }

    private class RenamePermissionException : RuntimeException()
    private class RenameFailureException(message: String) : RuntimeException(message)
}
