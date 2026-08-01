package io.github.wraithxxx.symphony.services.radio

import io.github.wraithxxx.symphony.Symphony
import kotlin.random.Random

class RadioShorty(private val symphony: Symphony) {
    fun play() {
        if (RadioPlaybackCommandPolicy.shouldPlay(
                canControlPlayback = symphony.radio.canControlPlayback,
                isPlaying = symphony.radio.isPlaying,
                isPlayPending = symphony.radio.isPlayPending,
            )
        ) {
            symphony.radio.resume()
        }
    }

    fun pause() {
        if (RadioPlaybackCommandPolicy.shouldPause(
                canControlPlayback = symphony.radio.canControlPlayback,
                isPlaying = symphony.radio.isPlaying,
                isPlayPending = symphony.radio.isPlayPending,
            )
        ) {
            symphony.radio.pause()
        }
    }

    fun playPause() {
        if (!symphony.radio.canControlPlayback) {
            return
        }
        when {
            symphony.radio.isPlaying -> pause()
            symphony.radio.isPlayPending -> symphony.radio.cancelPendingPlay()
            else -> play()
        }
    }

    fun seekFromCurrent(offsetSecs: Int) {
        if (!symphony.radio.hasPlayer) {
            return
        }
        symphony.radio.currentPlaybackPosition?.run {
            val to = (played + (offsetSecs * 1000)).coerceIn(0..total)
            symphony.radio.seek(to)
        }
    }

    fun previous(): Boolean {
        return when {
            !symphony.radio.hasPlayer -> false
            symphony.radio.currentPlaybackPosition!!.played <= 3000 && symphony.radio.canJumpToPrevious() -> {
                symphony.radio.jumpToPrevious()
                true
            }

            else -> {
                symphony.radio.seek(0)
                false
            }
        }
    }

    fun skip(): Boolean {
        return when {
            !symphony.radio.hasPlayer -> false
            symphony.radio.canJumpToNext() -> {
                symphony.radio.jumpToNext()
                true
            }

            else -> {
                symphony.radio.seek(0)
                false
            }
        }
    }

    fun playQueue(
        songIds: List<String>,
        options: Radio.PlayOptions = Radio.PlayOptions(),
        shuffle: Boolean = false,
    ) {
        symphony.radio.stop(ended = false)
        if (songIds.isEmpty()) {
            return
        }
        symphony.radio.queue.add(
            songIds,
            options = options.run {
                copy(index = if (shuffle) Random.nextInt(songIds.size) else options.index)
            }
        )
        symphony.radio.queue.setShuffleMode(shuffle)
    }

    fun playQueue(
        songId: String,
        options: Radio.PlayOptions = Radio.PlayOptions(),
        shuffle: Boolean = false,
    ) = playQueue(listOf(songId), options = options, shuffle = shuffle)
}
