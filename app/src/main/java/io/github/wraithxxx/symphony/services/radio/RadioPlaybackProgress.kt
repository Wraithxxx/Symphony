package io.github.wraithxxx.symphony.services.radio

import android.os.SystemClock
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.groove.Song
import io.github.wraithxxx.symphony.utils.EventUnsubscribeFn

class RadioPlaybackProgress(private val symphony: Symphony) {
    private val store = PlaybackProgressStore(symphony.applicationContext)
    val entries = store.entries

    private var positionSubscriber: EventUnsubscribeFn? = null
    private var updateSubscriber: EventUnsubscribeFn? = null
    private var lastSavedSongId: String? = null
    private var lastSavedAt = 0L
    private var suppressedSongId: String? = null

    fun start() {
        positionSubscriber = symphony.radio.onPlaybackPositionUpdate.subscribe { position ->
            saveCurrent(position = position, force = false)
        }
        updateSubscriber = symphony.radio.onUpdate.subscribe { event ->
            if (event == Radio.Events.Player.Paused || event == Radio.Events.Player.Seeked) {
                saveCurrent(force = true)
            }
        }
    }

    fun destroy() {
        saveCurrent(force = true)
        positionSubscriber?.invoke()
        positionSubscriber = null
        updateSubscriber?.invoke()
        updateSubscriber = null
    }

    fun resolveStartPosition(
        song: Song,
        explicitPositionMs: Long?,
        useRememberedPosition: Boolean,
    ): Long? {
        explicitPositionMs?.let { return it.coerceIn(0L, song.duration.coerceAtLeast(0L)) }
        if (!useRememberedPosition) {
            return null
        }
        val entry = store.get(song) ?: return null
        return PlaybackProgressPolicy.restorablePosition(
            enabled = symphony.settings.rememberTrackPositions.value,
            minimumDurationMs = minimumDurationMs(),
            durationMs = song.duration,
            positionMs = entry.positionMs,
        )
    }

    fun hasRestorablePosition(song: Song): Boolean =
        store.entries.value[song.id]?.let { entry ->
            PlaybackProgressPolicy.matchesFile(
                storedPath = entry.path,
                storedDurationMs = entry.durationMs,
                storedDateModified = entry.dateModified,
                storedSize = entry.size,
                currentPath = song.path,
                currentDurationMs = song.duration,
                currentDateModified = song.dateModified,
                currentSize = song.size,
            ) &&
                    PlaybackProgressPolicy.restorablePosition(
                        enabled = symphony.settings.rememberTrackPositions.value,
                        minimumDurationMs = minimumDurationMs(),
                        durationMs = song.duration,
                        positionMs = entry.positionMs,
                    ) != null
        } == true

    fun saveCurrent(
        position: RadioPlayer.PlaybackPosition? = symphony.radio.currentPlaybackPosition,
        force: Boolean,
    ) {
        val songId = symphony.radio.currentPlayerSongId ?: return
        if (suppressedSongId != null && suppressedSongId != songId) {
            suppressedSongId = null
        }
        val song = symphony.groove.song.get(songId) ?: return
        val playbackPosition = position ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && songId == lastSavedSongId && now - lastSavedAt < SAVE_INTERVAL_MS) {
            return
        }
        val decision = when {
            suppressedSongId == songId -> PlaybackProgressPolicy.Decision.Ignore
            else -> PlaybackProgressPolicy.decide(
                enabled = symphony.settings.rememberTrackPositions.value,
                minimumDurationMs = minimumDurationMs(),
                durationMs = playbackPosition.total.takeIf { it > 0L } ?: song.duration,
                positionMs = playbackPosition.played,
            )
        }
        when (decision) {
            PlaybackProgressPolicy.Decision.Ignore -> {}
            PlaybackProgressPolicy.Decision.Clear -> store.delete(songId)
            is PlaybackProgressPolicy.Decision.Save -> store.put(song, decision.positionMs)
        }
        lastSavedSongId = songId
        lastSavedAt = now
        symphony.radio.persistCurrentQueueSnapshot()
    }

    fun delete(songId: String) {
        store.delete(songId)
        if (lastSavedSongId == songId) {
            lastSavedSongId = null
            lastSavedAt = 0L
        }
    }

    fun migrate(oldSong: Song, newSong: Song) {
        store.migrate(oldSong, newSong)
        if (lastSavedSongId == oldSong.id) {
            lastSavedSongId = newSong.id
        }
    }

    fun clear() {
        store.clear()
        suppressedSongId = symphony.radio.currentPlayerSongId
        lastSavedSongId = null
        lastSavedAt = 0L
    }

    fun reconcileLibrary(songIds: Set<String>) {
        store.retain(songIds)
    }

    private fun minimumDurationMs() =
        symphony.settings.minimumRememberedTrackDurationMinutes.value * 60_000L

    companion object {
        internal const val SAVE_INTERVAL_MS = 15_000L
    }
}
