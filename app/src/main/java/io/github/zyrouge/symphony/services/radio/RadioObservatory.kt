package io.github.zyrouge.symphony.services.radio

import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.EventUnsubscribeFn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class RadioObservatory(private val symphony: Symphony) {
    private var updateSubscriber: EventUnsubscribeFn? = null
    private var playbackPositionUpdateSubscriber: EventUnsubscribeFn? = null
    private val playbackSnapshotState = RadioPlaybackSnapshotState()

    private val _playbackSnapshot = MutableStateFlow(RadioPlaybackSnapshot.empty)
    val playbackSnapshot = _playbackSnapshot.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()
    private val _playbackReadiness = MutableStateFlow(RadioPlaybackReadiness.Idle)
    val playbackReadiness = _playbackReadiness.asStateFlow()
    private val _isPlayPending = MutableStateFlow(false)
    val isPlayPending = _isPlayPending.asStateFlow()
    private val _playbackPosition = MutableStateFlow(RadioPlayer.PlaybackPosition.zero)
    val playbackPosition = _playbackPosition.asStateFlow()
    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex = _queueIndex.asStateFlow()
    private val _queue = MutableStateFlow(emptyList<String>())
    val queue = _queue.asStateFlow()
    private val _loopMode = MutableStateFlow(RadioQueue.LoopMode.None)
    val loopMode = _loopMode.asStateFlow()
    private val _shuffleMode = MutableStateFlow(false)
    val shuffleMode = _shuffleMode.asStateFlow()
    private val _sleepTimer = MutableStateFlow<Radio.SleepTimer?>(null)
    val sleepTimer = _sleepTimer.asStateFlow()
    private val _pauseOnCurrentSongEnd = MutableStateFlow(false)
    val pauseOnCurrentSongEnd = _pauseOnCurrentSongEnd.asStateFlow()
    private val _speed = MutableStateFlow(RadioPlayer.DEFAULT_SPEED)
    val speed = _speed.asStateFlow()
    private val _persistedSpeed = MutableStateFlow(RadioPlayer.DEFAULT_SPEED)
    val persistedSpeed = _persistedSpeed.asStateFlow()
    private val _pitch = MutableStateFlow(RadioPlayer.DEFAULT_PITCH)
    val pitch = _pitch.asStateFlow()
    private val _persistedPitch = MutableStateFlow(RadioPlayer.DEFAULT_PITCH)
    val persistedPitch = _persistedPitch.asStateFlow()

    fun start() {
        updateSubscriber = symphony.radio.onUpdate.subscribe { event ->
            when (event) {
                Radio.Events.Player.Seeked -> {
                    emitPlaybackSnapshot()
                    emitPlaybackPosition()
                }
                is Radio.Events.Player -> {
                    emitPlaybackSnapshot()
                    emitIsPlaying()
                    emitPlaybackState()
                }
                is Radio.Events.Queue.IndexChanged -> {
                    emitQueueIndex()
                    emitPlaybackSnapshot()
                }
                is Radio.Events.Queue -> emitQueue()
                Radio.Events.QueueOption.LoopModeChanged -> emitLoopMode()
                Radio.Events.QueueOption.ShuffleModeChanged -> emitShuffleMode()
                Radio.Events.QueueOption.SleepTimerChanged -> emitSleepTimer()
                Radio.Events.QueueOption.SpeedChanged -> emitSpeed()
                Radio.Events.QueueOption.PitchChanged -> emitPitch()
                Radio.Events.QueueOption.PauseOnCurrentSongEndChanged -> emitPauseOnCurrentSongEnd()
            }
        }
        playbackPositionUpdateSubscriber = symphony.radio.onPlaybackPositionUpdate.subscribe {
            emitPlaybackSnapshot()
            emitPlaybackPosition()
        }
        emitLoopMode()
        emitPlaybackSnapshot()
    }

    fun destroy() {
        updateSubscriber?.invoke()
        playbackPositionUpdateSubscriber?.invoke()
    }

    private fun emitIsPlaying() = _isPlaying.update {
        symphony.radio.isPlaying
    }

    private fun emitPlaybackState() {
        _playbackReadiness.update { symphony.radio.playbackReadiness }
        _isPlayPending.update { symphony.radio.isPlayPending }
    }

    private fun emitPlaybackSnapshot() {
        val candidate = RadioPlaybackSnapshot(
            generation = symphony.radio.playbackGeneration,
            songId = symphony.radio.queue.currentSongId,
            queueIndex = symphony.radio.queue.currentSongIndex,
            position = symphony.radio.currentPlaybackPosition
                ?: RadioPlayer.PlaybackPosition.zero,
            readiness = symphony.radio.playbackReadiness,
            isPlaying = symphony.radio.isPlaying,
            isPlayPending = symphony.radio.isPlayPending,
        )
        if (playbackSnapshotState.publish(candidate)) {
            _playbackSnapshot.value = candidate
        }
    }

    private fun emitPlaybackPosition() = _playbackPosition.update {
        symphony.radio.currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
    }

    private fun emitQueueIndex() = _queueIndex.update {
        symphony.radio.queue.currentSongIndex
    }

    private fun emitLoopMode() = _loopMode.update {
        symphony.radio.queue.currentLoopMode
    }

    private fun emitShuffleMode() = _shuffleMode.update {
        symphony.radio.queue.currentShuffleMode
    }

    private fun emitSleepTimer() = _sleepTimer.update {
        symphony.radio.sleepTimer
    }

    private fun emitSpeed() {
        _speed.update { symphony.radio.currentSpeed }
        _persistedSpeed.update { symphony.radio.persistedSpeed }
    }

    fun emitPitch() {
        _pitch.update { symphony.radio.currentPitch }
        _persistedPitch.update { symphony.radio.persistedPitch }
    }


    private fun emitPauseOnCurrentSongEnd() = _pauseOnCurrentSongEnd.update {
        symphony.radio.pauseOnCurrentSongEnd
    }

    private fun emitQueue() {
        _queue.update {
            symphony.radio.queue.currentQueue.toList()
        }
    }
}
