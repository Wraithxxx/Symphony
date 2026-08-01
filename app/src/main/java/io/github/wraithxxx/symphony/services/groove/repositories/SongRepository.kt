package io.github.wraithxxx.symphony.services.groove.repositories

import android.net.Uri
import androidx.core.net.toUri
import io.github.zyrouge.metaphony.utils.withCase
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.groove.Song
import io.github.wraithxxx.symphony.ui.helpers.Assets
import io.github.wraithxxx.symphony.ui.helpers.createHandyImageRequest
import io.github.wraithxxx.symphony.utils.FuzzySearchOption
import io.github.wraithxxx.symphony.utils.FuzzySearcher
import io.github.wraithxxx.symphony.utils.KeyGenerator
import io.github.wraithxxx.symphony.utils.Logger
import io.github.wraithxxx.symphony.utils.SimpleFileSystem
import io.github.wraithxxx.symphony.utils.SimplePath
import io.github.wraithxxx.symphony.utils.joinToStringIfNotEmpty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class SongRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        TITLE,
        ARTIST,
        ALBUM,
        DURATION,
        DATE_MODIFIED,
        COMPOSER,
        ALBUM_ARTIST,
        YEAR,
        FILENAME,
        TRACK_NUMBER,
    }

    @Volatile
    private var cache = ConcurrentHashMap<String, Song>()
    @Volatile
    internal var pathCache = ConcurrentHashMap<String, String>()
    internal val idGenerator = KeyGenerator.TimeIncremental()
    private val searcher = FuzzySearcher<String>(
        options = listOf(
            FuzzySearchOption({ v -> get(v)?.title?.let { compareString(it) } }, 3),
            FuzzySearchOption({ v -> get(v)?.filename?.let { compareString(it) } }, 2),
            FuzzySearchOption({ v -> get(v)?.artists?.let { compareCollection(it) } }),
            FuzzySearchOption({ v -> get(v)?.album?.let { compareString(it) } })
        )
    )

    val isUpdating get() = symphony.groove.exposer.isUpdating
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()
    private val _id = MutableStateFlow(System.currentTimeMillis())
    val id = _id.asStateFlow()
    @Volatile
    var explorer = SimpleFileSystem.Folder()

    private fun emitCount() = _count.update { cache.size }

    private fun emitIds() = _id.update {
        System.currentTimeMillis()
    }

    internal fun onSong(song: Song) {
        val normalizedSong = normalizeTitle(song)
        val isNew = cache.put(normalizedSong.id, normalizedSong) == null
        pathCache[normalizedSong.path] = normalizedSong.id
        emitIds()
        if (isNew) {
            explorer.addChildFile(SimplePath(normalizedSong.path)).data = normalizedSong.id
            _all.update {
                it + normalizedSong.id
            }
        }
        emitCount()
    }

    internal fun replace(oldSong: Song, newSong: Song) {
        val normalizedSong = normalizeTitle(newSong)
        cache[normalizedSong.id] = normalizedSong
        if (oldSong.path != normalizedSong.path) {
            pathCache.remove(oldSong.path)
        }
        pathCache[normalizedSong.path] = normalizedSong.id
        if (normalizedSong.id in _all.value) {
            explorer = SimpleFileSystem.Folder().also { next ->
                _all.value.mapNotNull(cache::get).forEach {
                    next.addChildFile(SimplePath(it.path)).data = it.id
                }
            }
        }
        emitIds()
    }

    internal fun removeAll(songIds: Set<String>) {
        if (songIds.isEmpty()) {
            return
        }
        songIds.forEach { id ->
            cache.remove(id)?.let { pathCache.remove(it.path) }
        }
        _all.update { ids -> ids.filterNot(songIds::contains) }
        explorer = SimpleFileSystem.Folder().also { next ->
            _all.value.mapNotNull(cache::get).forEach {
                next.addChildFile(SimplePath(it.path)).data = it.id
            }
        }
        emitCount()
        emitIds()
    }

    internal fun replaceAll(songs: Collection<Song>, retainedSongs: Collection<Song>) {
        val librarySongs = songs.map(::normalizeTitle).distinctBy { it.id }
        val nextCache = ConcurrentHashMap<String, Song>()
        retainedSongs.map(::normalizeTitle).forEach { nextCache[it.id] = it }
        librarySongs.forEach { nextCache[it.id] = it }
        val nextPathCache = ConcurrentHashMap<String, String>()
        nextCache.values.forEach { nextPathCache[it.path] = it.id }
        val nextExplorer = SimpleFileSystem.Folder()
        librarySongs.forEach {
            nextExplorer.addChildFile(SimplePath(it.path)).data = it.id
        }

        cache = nextCache
        pathCache = nextPathCache
        explorer = nextExplorer
        _all.value = librarySongs.map { it.id }
        _count.value = librarySongs.size
        emitIds()
    }

    private fun normalizeTitle(song: Song): Song {
        val filenameTitle = SimplePath(song.path).nameWithoutExtension
        return if (song.title == filenameTitle) song else song.copy(title = filenameTitle)
    }

    fun reset() {
        cache.clear()
        pathCache.clear()
        explorer = SimpleFileSystem.Folder()
        emitIds()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun search(songIds: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, songIds, maxLength = limit)

    fun sort(songIds: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> songIds
            SortBy.TITLE -> songIds.sortedBy { get(it)?.title?.withCase(sensitive) }
            SortBy.ARTIST -> songIds.sortedBy { get(it)?.artists?.joinToStringIfNotEmpty(sensitive) }
            SortBy.ALBUM -> songIds.sortedBy { get(it)?.album?.withCase(sensitive) }
            SortBy.DURATION -> songIds.sortedBy { get(it)?.duration }
            SortBy.DATE_MODIFIED -> songIds.sortedBy { get(it)?.dateModified }
            SortBy.COMPOSER -> songIds.sortedBy {
                get(it)?.composers?.joinToStringIfNotEmpty(sensitive)
            }

            SortBy.ALBUM_ARTIST -> songIds.sortedBy {
                get(it)?.albumArtists?.joinToStringIfNotEmpty(sensitive)
            }

            SortBy.YEAR -> songIds.sortedBy { get(it)?.year }
            SortBy.FILENAME -> songIds.sortedBy { get(it)?.filename?.withCase(sensitive) }
            SortBy.TRACK_NUMBER -> songIds.sortedWith(
                compareBy({ get(it)?.discNumber }, { get(it)?.trackNumber }),
            )
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(id: String) = cache[id]
    fun get(ids: List<String>) = ids.mapNotNull { get(it) }

    fun getArtworkUri(songId: String): Uri = get(songId)?.coverFile
        ?.let { symphony.database.artworkCache.get(it) }?.toUri()
        ?: getDefaultArtworkUri()

    fun getDefaultArtworkUri() = Assets.getPlaceholderUri(symphony)

    fun createArtworkImageRequest(songId: String) = createHandyImageRequest(
        symphony.applicationContext,
        image = getArtworkUri(songId),
        fallback = Assets.getPlaceholderId(symphony),
    )

    suspend fun getLyrics(song: Song): String? {
        try {
            val lrcPath = SimplePath(song.path).let {
                it.parent?.join(it.nameWithoutExtension + ".lrc")?.pathString
            }
            symphony.groove.exposer.uris[lrcPath]?.let { uri ->
                symphony.applicationContext.contentResolver.openInputStream(uri)?.use {
                    return String(it.readBytes())
                }
            }
            return symphony.database.lyricsCache.get(song.id)
        } catch (err: Exception) {
            Logger.error("LyricsRepository", "fetch lyrics failed", err)
        }
        return null
    }
}
