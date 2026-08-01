package io.github.wraithxxx.symphony.services.radio

enum class RadioPlaybackReadiness {
    Idle,
    Restoring,
    Preparing,
    Seeking,
    Ready,
    Playing,
    Error,
}

internal class RadioPlaybackState {
    data class Snapshot(
        val readiness: RadioPlaybackReadiness,
        val playPending: Boolean,
    )

    private var readiness = RadioPlaybackReadiness.Idle
    private var playPending = false

    @Synchronized
    fun snapshot() = Snapshot(readiness, playPending)

    @Synchronized
    fun stage(restoring: Boolean, autostart: Boolean): Snapshot {
        readiness = when {
            restoring -> RadioPlaybackReadiness.Restoring
            else -> RadioPlaybackReadiness.Preparing
        }
        playPending = autostart
        return snapshot()
    }

    @Synchronized
    fun onPreparing(): Snapshot {
        if (readiness != RadioPlaybackReadiness.Restoring) {
            readiness = RadioPlaybackReadiness.Preparing
        }
        return snapshot()
    }

    @Synchronized
    fun requestPlay(): Boolean {
        playPending = true
        return readiness == RadioPlaybackReadiness.Ready
    }

    @Synchronized
    fun cancelPlay(): Snapshot {
        playPending = false
        return snapshot()
    }

    @Synchronized
    fun onPrepared(requiresSeek: Boolean): Boolean {
        readiness = when {
            requiresSeek -> RadioPlaybackReadiness.Seeking
            else -> RadioPlaybackReadiness.Ready
        }
        return playPending && !requiresSeek
    }

    @Synchronized
    fun onSeekStarted(): Snapshot {
        readiness = RadioPlaybackReadiness.Seeking
        return snapshot()
    }

    @Synchronized
    fun onSeekComplete(isPlaying: Boolean): Boolean {
        readiness = when {
            isPlaying -> RadioPlaybackReadiness.Playing
            else -> RadioPlaybackReadiness.Ready
        }
        return playPending && !isPlaying
    }

    @Synchronized
    fun onPlayingChanged(isPlaying: Boolean): Snapshot {
        when {
            isPlaying -> {
                readiness = RadioPlaybackReadiness.Playing
                playPending = false
            }

            readiness == RadioPlaybackReadiness.Playing -> {
                readiness = RadioPlaybackReadiness.Ready
            }
        }
        return snapshot()
    }

    @Synchronized
    fun onError(): Snapshot {
        readiness = RadioPlaybackReadiness.Error
        playPending = false
        return snapshot()
    }

    @Synchronized
    fun onStopped(): Snapshot {
        readiness = RadioPlaybackReadiness.Idle
        playPending = false
        return snapshot()
    }
}
