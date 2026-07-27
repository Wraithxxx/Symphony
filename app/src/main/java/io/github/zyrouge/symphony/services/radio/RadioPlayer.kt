package io.github.zyrouge.symphony.services.radio

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.utils.Logger

typealias RadioPlayerOnPreparedListener = () -> Unit
typealias RadioPlayerOnPlaybackPositionListener = (RadioPlayer.PlaybackPosition) -> Unit
typealias RadioPlayerOnSeekCompleteListener = (RadioPlayer.PlaybackPosition) -> Unit
typealias RadioPlayerOnIsPlayingChangedListener = (Boolean) -> Unit
typealias RadioPlayerOnFinishListener = () -> Unit
typealias RadioPlayerOnErrorListener = (Int, Int) -> Unit

@androidx.annotation.OptIn(UnstableApi::class)
class RadioPlayer(val symphony: Symphony, val id: String, val uri: Uri) {
    data class PlaybackPosition(val played: Long, val total: Long) {
        val ratio: Float
            get() = (played.toFloat() / total).takeIf { it.isFinite() } ?: 0f

        companion object {
            val zero = PlaybackPosition(0L, 0L)
        }
    }

    enum class State {
        Unprepared,
        Preparing,
        Prepared,
        Finished,
        Destroyed,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val seekState = RadioSeekState()
    private var exoPlayer: ExoPlayer? = null
    private var prepareRequested = false
    private var positionUpdaterRunning = false
    private var onPrepared: RadioPlayerOnPreparedListener? = null
    private var onPlaybackPosition: RadioPlayerOnPlaybackPositionListener? = null
    private var onSeekComplete: RadioPlayerOnSeekCompleteListener? = null
    private var onIsPlayingChanged: RadioPlayerOnIsPlayingChangedListener? = null
    private var onFinish: RadioPlayerOnFinishListener? = null
    private var onError: RadioPlayerOnErrorListener? = null
    private var fader: RadioEffects.Fader? = null

    @Volatile
    private var cachedPlaybackPosition: PlaybackPosition? = null

    @Volatile
    private var cachedIsPlaying = false

    @Volatile
    private var cachedAudioSessionId: Int? = null

    @Volatile
    var state = State.Unprepared
        private set

    @Volatile
    var hasPlayedOnce = false
        private set

    @Volatile
    var volume = MAX_VOLUME
        private set

    @Volatile
    var speed = DEFAULT_SPEED
        private set

    @Volatile
    var pitch = DEFAULT_PITCH
        private set

    val usable get() = state == State.Prepared
    val fadePlayback get() = symphony.settings.fadePlayback.value
    val audioSessionId get() = cachedAudioSessionId
    val isPlaying get() = cachedIsPlaying
    val isSeeking get() = seekState.isSeeking()
    val playbackPosition get() = cachedPlaybackPosition

    private val positionUpdater = object : Runnable {
        override fun run() {
            if (!positionUpdaterRunning || state == State.Destroyed) {
                return
            }
            exoPlayer?.let(::updatePlaybackSnapshot)
            emitPlaybackPosition()
            mainHandler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val wasPrepared = state == State.Prepared
                    state = State.Prepared
                    updatePlaybackSnapshot(player)
                    createDurationTimer()
                    if (!wasPrepared) {
                        onPrepared?.invoke()
                    }
                }

                Player.STATE_ENDED -> {
                    state = State.Finished
                    cachedIsPlaying = false
                    updatePlaybackSnapshot(player)
                    destroyDurationTimer()
                    onFinish?.invoke()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            cachedIsPlaying = isPlaying
            if (isPlaying) {
                createDurationTimer()
            }
            exoPlayer?.let(::updatePlaybackSnapshot)
            onIsPlayingChanged?.invoke(isPlaying)
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            cachedAudioSessionId = audioSessionId.takeUnless { it == C.AUDIO_SESSION_ID_UNSET }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val player = exoPlayer ?: return
            updatePlaybackSnapshot(player)
            if (reason != Player.DISCONTINUITY_REASON_SEEK || !seekState.isSeeking()) {
                emitPlaybackPosition()
                return
            }
            val nextCommand = seekState.onSeekComplete()
            if (nextCommand != null) {
                performSeek(nextCommand)
                emitPlaybackPosition()
            } else {
                updatePlaybackSnapshot(player)
                emitPlaybackPosition()
                playbackPosition?.let { onSeekComplete?.invoke(it) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val failedPlayer = exoPlayer
            state = State.Destroyed
            cachedIsPlaying = false
            destroyDurationTimer()
            failedPlayer?.removeListener(this)
            failedPlayer?.release()
            if (exoPlayer === failedPlayer) {
                exoPlayer = null
            }
            onError?.invoke(error.errorCode, 0)
        }
    }

    init {
        runOnPlayerThread(::createPlayer)
    }

    private fun createPlayer() {
        if (state == State.Destroyed || exoPlayer != null) {
            return
        }
        try {
            val extractorsFactory = DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
            val mediaSource = ProgressiveMediaSource.Factory(
                DefaultDataSource.Factory(symphony.applicationContext),
                extractorsFactory,
            ).createMediaSource(MediaItem.fromUri(uri))
            exoPlayer = ExoPlayer.Builder(symphony.applicationContext)
                .setAudioAttributes(AudioAttributes.DEFAULT, false)
                .build()
                .also { player ->
                    player.addListener(playerListener)
                    player.volume = volume
                    player.playbackParameters = PlaybackParameters(speed, pitch)
                    player.setMediaSource(mediaSource)
                    updatePlaybackSnapshot(player)
                    if (prepareRequested) {
                        preparePlayer(player)
                    }
                }
        } catch (err: Exception) {
            state = State.Destroyed
            Logger.error("RadioPlayer", "player creation failed for $id", err)
            onError?.invoke(PlaybackException.ERROR_CODE_UNSPECIFIED, 0)
        }
    }

    fun prepare() {
        prepareRequested = true
        runOnPlayerThread {
            exoPlayer?.let(::preparePlayer)
        }
    }

    private fun preparePlayer(player: ExoPlayer) {
        when (state) {
            State.Unprepared -> {
                state = State.Preparing
                player.prepare()
            }

            State.Prepared -> onPrepared?.invoke()
            else -> {}
        }
    }

    fun stop() = destroy()

    fun destroy() {
        if (state == State.Destroyed) {
            return
        }
        state = State.Destroyed
        seekState.reset()
        cachedIsPlaying = false
        destroyDurationTimer()
        runOnPlayerThread {
            exoPlayer?.let { player ->
                player.removeListener(playerListener)
                player.release()
            }
            exoPlayer = null
            cachedAudioSessionId = null
        }
    }

    fun start() {
        if (!usable) {
            return
        }
        runOnPlayerThread {
            exoPlayer?.let { player ->
                if (!hasPlayedOnce) {
                    hasPlayedOnce = true
                }
                player.playbackParameters = PlaybackParameters(speed, pitch)
                player.play()
                updatePlaybackSnapshot(player)
                createDurationTimer()
            }
        }
    }

    fun pause() {
        runOnPlayerThread {
            exoPlayer?.let { player ->
                player.pause()
                updatePlaybackSnapshot(player)
            }
            destroyDurationTimer()
        }
    }

    fun seek(to: Long): Boolean {
        val current = playbackPosition ?: return false
        val duration = current.total
        val target = to.coerceIn(0L, duration.coerceAtLeast(0L))
        if (!seekState.isSeeking() && target == current.played) {
            return false
        }
        val command = seekState.request(to, duration)
        cachedPlaybackPosition = PlaybackPosition(
            played = seekState.positionForReporting(playbackPosition?.played ?: 0L),
            total = duration,
        )
        emitPlaybackPosition()
        command?.let(::performSeek)
        return seekState.isSeeking()
    }

    private fun performSeek(command: RadioSeekState.Command) {
        runOnPlayerThread {
            if (!usable) {
                seekState.reset()
                return@runOnPlayerThread
            }
            try {
                exoPlayer?.seekTo(command.position)
            } catch (err: IllegalStateException) {
                seekState.reset()
                Logger.warn("RadioPlayer", "seek failed for $id", err)
            }
        }
    }

    fun changeVolume(
        to: Float,
        forceFade: Boolean = false,
        onFinish: (Boolean) -> Unit,
    ) {
        fader?.stop()
        when {
            to == volume -> onFinish(true)
            forceFade || fadePlayback -> {
                val duration = (symphony.settings.fadePlaybackDuration.value * 1000).toInt()
                fader = RadioEffects.Fader(
                    RadioEffects.Fader.Options(volume, to, duration),
                    onUpdate = { changeVolumeInstant(it) },
                    onFinish = {
                        onFinish(it)
                        fader = null
                    },
                )
                fader?.start()
            }

            else -> {
                changeVolumeInstant(to)
                onFinish(true)
            }
        }
    }

    fun changeVolumeInstant(to: Float) {
        volume = to.coerceIn(MIN_VOLUME, MAX_VOLUME)
        runOnPlayerThread {
            exoPlayer?.volume = volume
        }
    }

    fun changeSpeed(to: Float) {
        speed = to
        applyPlaybackParameters()
    }

    fun changePitch(to: Float) {
        pitch = to
        applyPlaybackParameters()
    }

    private fun applyPlaybackParameters() {
        runOnPlayerThread {
            try {
                exoPlayer?.playbackParameters = PlaybackParameters(speed, pitch)
            } catch (err: Exception) {
                Logger.error("RadioPlayer", "changing playback parameters failed", err)
            }
        }
    }

    fun setOnPreparedListener(listener: RadioPlayerOnPreparedListener?) {
        onPrepared = listener
    }

    fun setOnPlaybackPositionListener(listener: RadioPlayerOnPlaybackPositionListener?) {
        onPlaybackPosition = listener
    }

    fun setOnSeekCompleteListener(listener: RadioPlayerOnSeekCompleteListener?) {
        onSeekComplete = listener
    }

    fun setOnIsPlayingChangedListener(listener: RadioPlayerOnIsPlayingChangedListener?) {
        onIsPlayingChanged = listener
    }

    fun setOnFinishListener(listener: RadioPlayerOnFinishListener?) {
        onFinish = listener
    }

    fun setOnErrorListener(listener: RadioPlayerOnErrorListener?) {
        onError = listener
    }

    private fun createDurationTimer() {
        if (positionUpdaterRunning || state == State.Destroyed) {
            return
        }
        positionUpdaterRunning = true
        mainHandler.removeCallbacks(positionUpdater)
        mainHandler.post(positionUpdater)
    }

    private fun emitPlaybackPosition() {
        playbackPosition?.let { onPlaybackPosition?.invoke(it) }
    }

    private fun destroyDurationTimer() {
        positionUpdaterRunning = false
        mainHandler.removeCallbacks(positionUpdater)
    }

    private fun updatePlaybackSnapshot(player: ExoPlayer) {
        val duration = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val actualPosition = player.currentPosition.coerceAtLeast(0L)
        cachedPlaybackPosition = PlaybackPosition(
            played = seekState.positionForReporting(actualPosition).coerceIn(0L, duration),
            total = duration,
        )
        cachedIsPlaying = player.isPlaying
    }

    private fun runOnPlayerThread(action: () -> Unit) {
        if (Looper.myLooper() == mainHandler.looper) {
            action()
        } else {
            mainHandler.post {
                if (state != State.Destroyed || exoPlayer != null) {
                    action()
                }
            }
        }
    }

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 1f
        const val DUCK_VOLUME = 0.2f
        const val DEFAULT_SPEED = 1f
        const val DEFAULT_PITCH = 1f
        private const val POSITION_UPDATE_INTERVAL_MS = 100L
    }
}
