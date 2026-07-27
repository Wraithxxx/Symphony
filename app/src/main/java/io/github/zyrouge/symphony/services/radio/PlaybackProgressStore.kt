package io.github.zyrouge.symphony.services.radio

import android.content.Context
import androidx.core.content.edit
import io.github.zyrouge.symphony.services.groove.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class PlaybackProgressStore(context: Context) {
    @Serializable
    data class Entry(
        val path: String,
        val positionMs: Long,
        val durationMs: Long,
        val dateModified: Long,
        val size: Long,
        val updatedAt: Long,
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val _entries = MutableStateFlow(loadEntries())
    val entries = _entries.asStateFlow()

    fun get(song: Song): Entry? = synchronized(lock) {
        val entry = _entries.value[song.id] ?: return@synchronized null
        if (!entry.matches(song)) {
            deleteLocked(song.id)
            return@synchronized null
        }
        entry
    }

    fun put(song: Song, positionMs: Long) = synchronized(lock) {
        val entry = Entry(
            path = song.path,
            positionMs = positionMs,
            durationMs = song.duration,
            dateModified = song.dateModified,
            size = song.size,
            updatedAt = System.currentTimeMillis(),
        )
        preferences.edit {
            putString(song.id, Json.encodeToString(Entry.serializer(), entry))
        }
        _entries.value = _entries.value + (song.id to entry)
    }

    fun delete(songId: String) = synchronized(lock) {
        deleteLocked(songId)
    }

    fun migrate(oldSong: Song, newSong: Song) = synchronized(lock) {
        val entry = _entries.value[oldSong.id] ?: return@synchronized
        if (!entry.matches(oldSong)) {
            deleteLocked(oldSong.id)
            return@synchronized
        }
        val migrated = entry.copy(
            path = newSong.path,
            durationMs = newSong.duration,
            dateModified = newSong.dateModified,
            size = newSong.size,
            updatedAt = System.currentTimeMillis(),
        )
        preferences.edit {
            remove(oldSong.id)
            putString(newSong.id, Json.encodeToString(Entry.serializer(), migrated))
        }
        _entries.value = (_entries.value - oldSong.id) + (newSong.id to migrated)
    }

    fun clear() = synchronized(lock) {
        preferences.edit { clear() }
        _entries.value = emptyMap()
    }

    fun retain(songIds: Set<String>) = synchronized(lock) {
        val removed = _entries.value.keys - songIds
        if (removed.isEmpty()) {
            return@synchronized
        }
        preferences.edit {
            removed.forEach(::remove)
        }
        _entries.value = _entries.value - removed
    }

    private fun deleteLocked(songId: String) {
        if (!_entries.value.containsKey(songId)) {
            return
        }
        preferences.edit { remove(songId) }
        _entries.value = _entries.value - songId
    }

    private fun loadEntries(): Map<String, Entry> {
        val entries = mutableMapOf<String, Entry>()
        preferences.all.forEach { (songId, rawValue) ->
            val serialized = rawValue as? String ?: return@forEach
            runCatching {
                Json.decodeFromString(Entry.serializer(), serialized)
            }.onSuccess {
                entries[songId] = it
            }
        }
        return entries
    }

    private fun Entry.matches(song: Song) =
        PlaybackProgressPolicy.matchesFile(
            storedPath = path,
            storedDurationMs = durationMs,
            storedDateModified = dateModified,
            storedSize = size,
            currentPath = song.path,
            currentDurationMs = song.duration,
            currentDateModified = song.dateModified,
            currentSize = song.size,
        )

    companion object {
        private const val PREFERENCES_NAME = "playback_progress"
    }
}
