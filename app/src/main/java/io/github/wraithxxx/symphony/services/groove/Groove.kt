package io.github.wraithxxx.symphony.services.groove

import android.os.SystemClock
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.groove.repositories.AlbumArtistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.AlbumRepository
import io.github.wraithxxx.symphony.services.groove.repositories.ArtistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.GenreRepository
import io.github.wraithxxx.symphony.services.groove.repositories.PlaylistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.SongRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Groove(private val symphony: Symphony) : Symphony.Hooks {
    enum class Kind {
        SONG,
        ALBUM,
        ARTIST,
        ALBUM_ARTIST,
        GENRE,
        PLAYLIST,
    }

    val coroutineScope = CoroutineScope(Dispatchers.Default)
    var readyDeferred = CompletableDeferred<Boolean>()

    val exposer = MediaExposer(symphony)
    val song = SongRepository(symphony)
    val album = AlbumRepository(symphony)
    val artist = ArtistRepository(symphony)
    val albumArtist = AlbumArtistRepository(symphony)
    val genre = GenreRepository(symphony)
    val playlist = PlaylistRepository(symphony)
    val deletion = MediaDeletionService(symphony)
    val renaming = MediaRenameService(symphony)
    val metadataEditing = MediaMetadataEditingService(symphony)
    private val refreshMutex = Mutex()
    private val refreshGate = GrooveRefreshGate(AUTO_REFRESH_INTERVAL_MS)

    data class FetchOptions(
        val force: Boolean = false,
        val refreshMetadata: Boolean = false,
    )

    private suspend fun performRefresh(options: FetchOptions): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!refreshGate.shouldRefresh(now = now, force = options.force)) {
            return true
        }
        val committed = exposer.fetch(refreshMetadata = options.refreshMetadata)
        if (committed) {
            metadataEditing.applyPendingArtworkOverlays()
            playlist.fetch()
            refreshGate.onSuccess(SystemClock.elapsedRealtime())
        }
        return committed
    }

    private suspend fun refresh(options: FetchOptions): Boolean = refreshMutex.withLock {
        performRefresh(options)
    }

    internal suspend fun <T> withLibraryTransaction(block: suspend () -> T): T =
        refreshMutex.withLock { block() }

    internal suspend fun refreshInLibraryTransaction(options: FetchOptions): Boolean =
        performRefresh(options)

    internal fun publishEditedSong(oldSong: Song, newSong: Song) {
        song.replace(oldSong, newSong)
        val librarySongs = song.all.value.mapNotNull(song::get)
        album.replaceAll(librarySongs)
        artist.replaceAll(librarySongs)
        albumArtist.replaceAll(librarySongs)
        genre.replaceAll(librarySongs)
        exposer.replaceDocument(oldSong, newSong)
        playlist.onScanFinish()
        symphony.radio.session.invalidateArtwork(newSong.id)
        symphony.radio.onUpdate.dispatch(
            io.github.wraithxxx.symphony.services.radio.Radio.Events.Player.StateChanged
        )
    }

    internal fun publishArtworkOverlay(oldSong: Song, newSong: Song) {
        song.replace(oldSong, newSong)
        exposer.replaceDocument(oldSong, newSong)
        symphony.radio.session.invalidateArtwork(newSong.id)
        symphony.radio.onUpdate.dispatch(
            io.github.wraithxxx.symphony.services.radio.Radio.Events.Player.StateChanged
        )
    }

    internal fun publishDeletedSongs(deletedSongs: Collection<Song>) {
        val deletedIds = deletedSongs.mapTo(hashSetOf(), Song::id)
        song.removeAll(deletedIds)
        val librarySongs = song.all.value.mapNotNull(song::get)
        album.replaceAll(librarySongs)
        artist.replaceAll(librarySongs)
        albumArtist.replaceAll(librarySongs)
        genre.replaceAll(librarySongs)
        exposer.removeDocuments(deletedSongs)
        playlist.onScanFinish()
    }

    fun fetch(options: FetchOptions = FetchOptions()): Job = coroutineScope.launch {
        refresh(options)
    }

    override fun onSymphonyReady() {
        coroutineScope.launch {
            metadataEditing.start()
            val ready = refresh(FetchOptions(force = true))
            if (!readyDeferred.isCompleted) {
                readyDeferred.complete(ready)
            }
            metadataEditing.onLibraryReady()
        }
    }

    override fun onSymphonyDestroy() {
        metadataEditing.stop()
    }

    override fun onSymphonyActivityResume() {
        fetch()
    }

    companion object {
        internal const val AUTO_REFRESH_INTERVAL_MS = 2_000L
    }
}
