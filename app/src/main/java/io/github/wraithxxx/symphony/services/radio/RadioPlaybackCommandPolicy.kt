package io.github.wraithxxx.symphony.services.radio

internal object RadioPlaybackCommandPolicy {
    fun shouldPlay(
        canControlPlayback: Boolean,
        isPlaying: Boolean,
        isPlayPending: Boolean,
    ) = canControlPlayback && !isPlaying && !isPlayPending

    fun shouldPause(
        canControlPlayback: Boolean,
        isPlaying: Boolean,
        isPlayPending: Boolean,
    ) = canControlPlayback && (isPlaying || isPlayPending)
}
