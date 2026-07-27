package io.github.zyrouge.symphony.services.groove.repositories

import io.github.zyrouge.metaphony.utils.withCase
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Artist
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.ui.helpers.Assets
import io.github.zyrouge.symphony.ui.helpers.createHandyImageRequest
import io.github.zyrouge.symphony.utils.ConcurrentSet
import io.github.zyrouge.symphony.utils.FuzzySearchOption
import io.github.zyrouge.symphony.utils.FuzzySearcher
import io.github.zyrouge.symphony.utils.concurrentSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

class ArtistRepository(private val symphony: Symphony) {
    enum class SortBy {
        CUSTOM,
        ARTIST_NAME,
        TRACKS_COUNT,
        ALBUMS_COUNT,
    }

    @Volatile
    private var cache = ConcurrentHashMap<String, Artist>()
    @Volatile
    private var songIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    @Volatile
    private var albumIdsCache = ConcurrentHashMap<String, ConcurrentSet<String>>()
    private val searcher = FuzzySearcher<String>(
        options = listOf(FuzzySearchOption({ v -> get(v)?.name?.let { compareString(it) } }))
    )

    val isUpdating get() = symphony.groove.exposer.isUpdating
    private val _all = MutableStateFlow<List<String>>(emptyList())
    val all = _all.asStateFlow()
    private val _count = MutableStateFlow(0)
    val count = _count.asStateFlow()

    private fun emitCount() = _count.update {
        cache.size
    }

    internal fun onSong(song: Song) {
        song.artists.forEach { artist ->
            songIdsCache.compute(artist) { _, value ->
                value?.apply { add(song.id) } ?: concurrentSetOf(song.id)
            }
            val numberOfAlbums = symphony.groove.album.getIdFromSong(song)?.let { album ->
                albumIdsCache.compute(artist) { _, value ->
                    value?.apply { add(album) } ?: concurrentSetOf(album)
                }?.size ?: 0
            } ?: 0
            cache.compute(artist) { _, value ->
                value?.apply {
                    this.numberOfAlbums = numberOfAlbums
                    numberOfTracks++
                } ?: run {
                    _all.update {
                        it + artist
                    }
                    emitCount()
                    Artist(
                        name = artist,
                        numberOfAlbums = numberOfAlbums,
                        numberOfTracks = 1,
                    )
                }
            }
        }
    }

    internal fun replaceAll(songs: Collection<Song>) {
        val replacement = ArtistRepository(symphony)
        songs.forEach(replacement::onSong)
        cache = replacement.cache
        songIdsCache = replacement.songIdsCache
        albumIdsCache = replacement.albumIdsCache
        _all.value = replacement._all.value
        _count.value = replacement.cache.size
    }

    fun reset() {
        cache.clear()
        songIdsCache.clear()
        albumIdsCache.clear()
        _all.update {
            emptyList()
        }
        emitCount()
    }

    fun getArtworkUri(artistName: String) = songIdsCache[artistName]?.firstOrNull()
        ?.let { symphony.groove.song.getArtworkUri(it) }
        ?: symphony.groove.song.getDefaultArtworkUri()

    fun createArtworkImageRequest(artistName: String) = createHandyImageRequest(
        symphony.applicationContext,
        image = getArtworkUri(artistName),
        fallback = Assets.placeholderDarkId,
    )

    fun search(artistNames: List<String>, terms: String, limit: Int = 7) = searcher
        .search(terms, artistNames, maxLength = limit)

    fun sort(artistNames: List<String>, by: SortBy, reverse: Boolean): List<String> {
        val sensitive = symphony.settings.caseSensitiveSorting.value
        val sorted = when (by) {
            SortBy.CUSTOM -> artistNames
            SortBy.ARTIST_NAME -> artistNames.sortedBy { get(it)?.name?.withCase(sensitive) }
            SortBy.TRACKS_COUNT -> artistNames.sortedBy { get(it)?.numberOfTracks }
            SortBy.ALBUMS_COUNT -> artistNames.sortedBy { get(it)?.numberOfTracks }
        }
        return if (reverse) sorted.reversed() else sorted
    }

    fun count() = cache.size
    fun ids() = cache.keys.toList()
    fun values() = cache.values.toList()

    fun get(id: String) = cache[id]
    fun get(ids: List<String>) = ids.mapNotNull { get(it) }
    fun getAlbumIds(artistName: String) = albumIdsCache[artistName]?.toList() ?: emptyList()
    fun getSongIds(artistName: String) = songIdsCache[artistName]?.toList() ?: emptyList()
}
