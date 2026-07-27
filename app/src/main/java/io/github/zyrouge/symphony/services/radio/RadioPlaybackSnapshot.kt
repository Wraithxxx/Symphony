package io.github.zyrouge.symphony.services.radio

data class RadioPlaybackSnapshot(
    val generation: Long,
    val songId: String?,
    val queueIndex: Int,
    val position: RadioPlayer.PlaybackPosition,
    val readiness: RadioPlaybackReadiness,
    val isPlaying: Boolean,
    val isPlayPending: Boolean,
) {
    companion object {
        val empty = RadioPlaybackSnapshot(
            generation = 0L,
            songId = null,
            queueIndex = -1,
            position = RadioPlayer.PlaybackPosition.zero,
            readiness = RadioPlaybackReadiness.Idle,
            isPlaying = false,
            isPlayPending = false,
        )
    }
}

internal class RadioPlaybackSnapshotState {
    private var current = RadioPlaybackSnapshot.empty

    @Synchronized
    fun current() = current

    @Synchronized
    fun publish(candidate: RadioPlaybackSnapshot): Boolean {
        if (candidate.generation < current.generation) {
            return false
        }
        current = candidate
        return true
    }
}
