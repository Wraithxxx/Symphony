package io.github.zyrouge.symphony.services.groove

import android.net.Uri
import android.os.SystemClock
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.ActivityUtils
import io.github.zyrouge.symphony.utils.ConcurrentSet
import io.github.zyrouge.symphony.utils.DocumentFileX
import io.github.zyrouge.symphony.utils.Logger
import io.github.zyrouge.symphony.utils.SimpleFileSystem
import io.github.zyrouge.symphony.utils.SimplePath
import io.github.zyrouge.symphony.utils.concurrentSetOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MediaExposer(private val symphony: Symphony) {
    @Volatile
    internal var uris: Map<String, Uri> = emptyMap()
    @Volatile
    var explorer = SimpleFileSystem.Folder()
    private val _isUpdating = MutableStateFlow(false)
    val isUpdating = _isUpdating.asStateFlow()

    private fun emitUpdate(value: Boolean) = _isUpdating.update {
        value
    }

    private data class ScanCycle(
        val songCache: ConcurrentHashMap<String, Song>,
        val songCacheUnused: ConcurrentSet<String>,
        val artworkCacheUnused: ConcurrentSet<String>,
        val lyricsCacheUnused: ConcurrentSet<String>,
        val filter: MediaFilter,
        val refreshMetadata: Boolean,
        val songs: ConcurrentHashMap<String, Song> = ConcurrentHashMap(),
        val uris: ConcurrentHashMap<String, Uri> = ConcurrentHashMap(),
        val explorer: SimpleFileSystem.Folder = SimpleFileSystem.Folder(),
        val valid: AtomicBoolean = AtomicBoolean(true),
    ) {
        companion object {
            suspend fun create(symphony: Symphony, refreshMetadata: Boolean): ScanCycle {
                val songCache = ConcurrentHashMap(symphony.database.songCache.entriesPathMapped())
                val songCacheUnused = concurrentSetOf(songCache.map { it.value.id })
                val artworkCacheUnused = concurrentSetOf(symphony.database.artworkCache.all())
                val lyricsCacheUnused = concurrentSetOf(symphony.database.lyricsCache.keys())
                val filter = MediaFilter(
                    symphony.settings.songsFilterPattern.value,
                    symphony.settings.blacklistFolders.value.toSortedSet(),
                    symphony.settings.whitelistFolders.value.toSortedSet()
                )
                return ScanCycle(
                    songCache = songCache,
                    songCacheUnused = songCacheUnused,
                    artworkCacheUnused = artworkCacheUnused,
                    lyricsCacheUnused = lyricsCacheUnused,
                    filter = filter,
                    refreshMetadata = refreshMetadata,
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun fetch(refreshMetadata: Boolean = false): Boolean {
        emitUpdate(true)
        val startedAt = SystemClock.elapsedRealtime()
        val previousSongs = symphony.groove.song.values().associateBy { it.id }
        val playbackSongBefore = symphony.radio.queue.currentSongId
        val playbackGenerationBefore = symphony.radio.playbackGeneration
        var committed = false
        try {
            val context = symphony.applicationContext
            val folderUris = symphony.settings.mediaFolders.value
            val cycle = ScanCycle.create(symphony, refreshMetadata)
            folderUris.map { x ->
                ActivityUtils.makePersistableReadableUri(context, x)
                val root = DocumentFileX.fromTreeUri(context, x)
                if (root == null) {
                    cycle.valid.set(false)
                    return@map
                }
                root.let {
                    val path = SimplePath(DocumentFileX.getParentPathOfTreeUri(x) ?: it.name)
                    with(Dispatchers.IO) {
                        scanMediaTree(cycle, path, it)
                    }
                }
            }
            if (cycle.valid.get()) {
                commit(cycle)
                trimCache(cycle)
                committed = true
                val nextSongs = cycle.songs.toMap()
                val added = nextSongs.keys - previousSongs.keys
                val removed = previousSongs.keys - nextSongs.keys
                val changed = (nextSongs.keys intersect previousSongs.keys).count {
                    nextSongs[it] != previousSongs[it]
                }
                Logger.debug(
                    "MediaExposer",
                    "refresh committed in ${SystemClock.elapsedRealtime() - startedAt}ms " +
                            "(added=${added.size}, changed=$changed, removed=${removed.size}, " +
                            "total=${nextSongs.size}, playbackSong=$playbackSongBefore, " +
                            "playbackGenerationPreserved=" +
                            "${playbackGenerationBefore == symphony.radio.playbackGeneration})"
                )
            } else {
                Logger.warn("MediaExposer", "scan snapshot discarded because a media tree failed")
            }
        } catch (err: Exception) {
            Logger.error("MediaExposer", "fetch failed", err)
        }
        emitUpdate(false)
        if (committed) {
            emitFinish()
        }
        return committed
    }

    private suspend fun scanMediaTree(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
        try {
            if (!cycle.filter.isWhitelisted(path.pathString)) {
                return
            }
            coroutineScope {
                file.list().map {
                    val childPath = path.join(it.name)
                    async {
                        when {
                            it.isDirectory -> scanMediaTree(cycle, childPath, it)
                            else -> scanMediaFile(cycle, childPath, it)
                        }
                    }
                }.awaitAll()
            }
        } catch (err: Exception) {
            cycle.valid.set(false)
            Logger.error("MediaExposer", "scan media tree failed", err)
        }
    }

    private suspend fun scanMediaFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
        try {
            when {
                path.extension == "lrc" -> scanLrcFile(cycle, path, file)
                file.mimeType == MIMETYPE_M3U -> scanM3UFile(cycle, path, file)
                file.mimeType.startsWith("audio/") -> scanAudioFile(cycle, path, file)
            }
        } catch (err: Exception) {
            retainCachedSong(cycle, path, file)
            Logger.error("MediaExposer", "scan media file failed", err)
        }
    }

    private suspend fun scanAudioFile(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
        val pathString = path.pathString
        cycle.uris[pathString] = file.uri
        val lastModified = file.lastModified
        val cached = cycle.songCache[pathString]
        val cacheHit = !cycle.refreshMetadata && cached != null
                && cached.dateModified == lastModified
                && (cached.coverFile?.let { cycle.artworkCacheUnused.contains(it) } != false)
        val song = when {
            cacheHit -> cached
            else -> Song.parse(symphony, path, file, stableId = cached?.id)
        }
        if (song.duration.milliseconds < symphony.settings.minSongDuration.value.seconds) {
            return
        }
        if (!cacheHit) {
            when (cached) {
                null -> symphony.database.songCache.insert(song)
                else -> symphony.database.songCache.update(song)
            }
            cached?.coverFile?.let {
                if (symphony.database.artworkCache.get(it).delete()) {
                    cycle.artworkCacheUnused.remove(it)
                }
            }
        }
        cycle.songCacheUnused.remove(song.id)
        song.coverFile?.let {
            cycle.artworkCacheUnused.remove(it)
        }
        cycle.lyricsCacheUnused.remove(song.id)
        stageSong(cycle, song, path)
    }

    private fun scanLrcFile(
        @Suppress("Unused") cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        cycle.uris[path.pathString] = file.uri
        synchronized(cycle.explorer) {
            cycle.explorer.addChildFile(path)
        }
    }

    private fun scanM3UFile(
        @Suppress("Unused") cycle: ScanCycle,
        path: SimplePath,
        file: DocumentFileX,
    ) {
        cycle.uris[path.pathString] = file.uri
        synchronized(cycle.explorer) {
            cycle.explorer.addChildFile(path)
        }
    }

    private fun stageSong(cycle: ScanCycle, song: Song, path: SimplePath) {
        cycle.songs[song.id] = song
        synchronized(cycle.explorer) {
            cycle.explorer.addChildFile(path)
        }
    }

    private fun retainCachedSong(cycle: ScanCycle, path: SimplePath, file: DocumentFileX) {
        val cached = cycle.songCache[path.pathString] ?: return
        cycle.uris[path.pathString] = file.uri
        cycle.songCacheUnused.remove(cached.id)
        cached.coverFile?.let(cycle.artworkCacheUnused::remove)
        cycle.lyricsCacheUnused.remove(cached.id)
        runCatching { stageSong(cycle, cached, path) }
    }

    private suspend fun commit(cycle: ScanCycle) {
        val songs = cycle.songs.values.toList()
        val songIds = songs.mapTo(mutableSetOf()) { it.id }
        val retainedQueueSongs = symphony.radio.queue.currentQueue
            .toList()
            .filter { it !in songIds }
            .mapNotNull(symphony.groove.song::get)

        symphony.groove.song.replaceAll(songs, retainedQueueSongs)
        symphony.groove.album.replaceAll(songs)
        symphony.groove.artist.replaceAll(songs)
        symphony.groove.albumArtist.replaceAll(songs)
        symphony.groove.genre.replaceAll(songs)
        uris = cycle.uris.toMap()
        explorer = cycle.explorer
        withContext(Dispatchers.Main) {
            symphony.radio.reconcileLibrary(songIds)
        }
    }

    private suspend fun trimCache(cycle: ScanCycle) {
        try {
            symphony.database.songCache.delete(cycle.songCacheUnused)
        } catch (err: Exception) {
            Logger.warn("MediaExposer", "trim song cache failed", err)
        }
        for (x in cycle.artworkCacheUnused) {
            try {
                symphony.database.artworkCache.get(x).delete()
            } catch (err: Exception) {
                Logger.warn("MediaExposer", "delete artwork cache file failed", err)
            }
        }
        try {
            symphony.database.lyricsCache.delete(cycle.lyricsCacheUnused)
        } catch (err: Exception) {
            Logger.warn("MediaExposer", "trim lyrics cache failed", err)
        }
    }

    suspend fun reset() {
        emitUpdate(true)
        uris = emptyMap()
        explorer = SimpleFileSystem.Folder()
        symphony.database.songCache.clear()
        emitUpdate(false)
    }

    internal fun replaceDocument(oldSong: Song, newSong: Song) {
        uris = uris.toMutableMap().apply {
            if (oldSong.path != newSong.path) {
                remove(oldSong.path)
            }
            put(newSong.path, newSong.uri)
        }
        explorer = SimpleFileSystem.Folder().also { next ->
            uris.keys.forEach { next.addChildFile(SimplePath(it)) }
        }
    }

    internal fun removeDocuments(songs: Collection<Song>) {
        val removedPaths = songs.mapTo(hashSetOf(), Song::path)
        uris = uris.filterKeys { it !in removedPaths }
        explorer = SimpleFileSystem.Folder().also { next ->
            uris.keys.forEach { next.addChildFile(SimplePath(it)) }
        }
    }

    private fun emitFinish() {
        symphony.groove.playlist.onScanFinish()
    }

    private class MediaFilter(
        pattern: String?,
        private val blacklisted: Set<String>,
        private val whitelisted: Set<String>,
    ) {
        private val regex = pattern?.let { Regex(it, RegexOption.IGNORE_CASE) }

        fun isWhitelisted(path: String): Boolean {
            regex?.let {
                if (!it.containsMatchIn(path)) {
                    return false
                }
            }
            val bFilter = blacklisted.findLast {
                path.startsWith(it)
            }
            if (bFilter == null) {
                return true
            }
            val wFilter = whitelisted.findLast {
                it.startsWith(bFilter) && path.startsWith(it)
            }
            return wFilter != null
        }
    }

    companion object {
        const val MIMETYPE_M3U = "audio/x-mpegurl"
    }
}
