package io.github.wraithxxx.symphony.services.radio

internal object RadioSessionPlaybackState {
    enum class Published {
        Playing,
        Buffering,
        Paused,
    }

    fun resolve(
        isPlaying: Boolean,
        readiness: RadioPlaybackReadiness,
        isPlayPending: Boolean,
    ): Published = when {
        isPlaying -> Published.Playing
        isPlayPending && readiness in setOf(
            RadioPlaybackReadiness.Restoring,
            RadioPlaybackReadiness.Preparing,
            RadioPlaybackReadiness.Seeking,
            RadioPlaybackReadiness.Ready,
        ) -> Published.Buffering
        else -> Published.Paused
    }
}
