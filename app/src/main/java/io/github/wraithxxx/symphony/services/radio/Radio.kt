package io.github.wraithxxx.symphony.services.radio

import android.os.SystemClock
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.groove.Song
import io.github.wraithxxx.symphony.utils.Eventer
import io.github.wraithxxx.symphony.utils.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Date
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap

class Radio(private val symphony: Symphony) : Symphony.Hooks {
    sealed class Events {
        sealed class Player : Events() {
            object Staged : Player()
            object Started : Player()
            object Stopped : Player()
            object Paused : Player()
            object Resumed : Player()
            object Seeked : Player()
            object StateChanged : Player()
            object Ended : Player()
        }

        sealed class Queue : Events() {
            object Modified : Queue()
            object IndexChanged : Queue()
            object Cleared : Queue()
        }

        sealed class QueueOption : Events() {
            object LoopModeChanged : QueueOption()
            object ShuffleModeChanged : QueueOption()
            object SleepTimerChanged : QueueOption()
            object SpeedChanged : QueueOption()
            object PitchChanged : QueueOption()
            object PauseOnCurrentSongEndChanged : QueueOption()
        }
    }

    data class SleepTimer(
        val duration: Long,
        val endsAt: Long,
        val timer: Timer,
        var quitOnEnd: Boolean,
    )

    val onUpdate = Eventer<Events>()
    val queue = RadioQueue(symphony)
    val shorty = RadioShorty(symphony)
    val session = RadioSession(symphony)
    val progress = RadioPlaybackProgress(symphony)
    var observatory = RadioObservatory(symphony)

    private val focus = RadioFocus(symphony)
    private val nativeReceiver = RadioNativeReceiver(symphony)
    private val playbackState = RadioPlaybackState()
    private var player: RadioPlayer? = null
    private var nextPlayer: RadioPlayer? = null
    private var playerGeneration = 0L
    private var stagedStartPosition: Long? = null
    private var stagedDuration = 0L
    private var restorationStartedAt: Long? = null
    private val restorationReady = CompletableDeferred<Unit>()
    private val restoredSongCache = ConcurrentHashMap<String, Song>()

    val hasPlayer get() = player?.usable == true
    internal val currentPlayerSongId get() = player?.id
    internal fun hasOpenPlayerForFile(songId: String) =
        player?.id == songId || nextPlayer?.id == songId
    val playbackGeneration get() = playerGeneration
    val canControlPlayback get() = player != null && playbackReadiness !in setOf(
        RadioPlaybackReadiness.Idle,
        RadioPlaybackReadiness.Error,
    )
    val isPlaying get() = player?.isPlaying == true
    val playbackReadiness get() = playbackState.snapshot().readiness
    val isPlayPending get() = playbackState.snapshot().playPending
    val currentPlaybackPosition: RadioPlayer.PlaybackPosition?
        get() {
            val actual = player?.playbackPosition
            val staged = stagedStartPosition
            return when {
                playbackReadiness in setOf(
                    RadioPlaybackReadiness.Restoring,
                    RadioPlaybackReadiness.Preparing,
                    RadioPlaybackReadiness.Seeking,
                ) -> RadioPlayer.PlaybackPosition(
                    played = (staged ?: 0L).coerceIn(0L, stagedDuration.coerceAtLeast(0L)),
                    total = (actual?.total ?: stagedDuration).coerceAtLeast(0L),
                )

                else -> actual
            }
        }
    val currentSpeed get() = player?.speed ?: RadioPlayer.DEFAULT_SPEED
    val currentPitch get() = player?.pitch ?: RadioPlayer.DEFAULT_PITCH
    val audioSessionId get() = player?.audioSessionId
    val onPlaybackPositionUpdate = Eventer<RadioPlayer.PlaybackPosition>()

    var persistedSpeed = RadioPlayer.DEFAULT_SPEED
    var persistedPitch = RadioPlayer.DEFAULT_PITCH
    var sleepTimer: SleepTimer? = null
    var pauseOnCurrentSongEnd = false

    init {
        nativeReceiver.start()
        onUpdate.subscribe(this::watchQueueUpdates)
    }

    fun ready() {
        observatory.start()
        session.start()
        progress.start()
        attachGrooveListener()
    }

    fun destroy() {
        progress.destroy()
        stop()
        observatory.destroy()
        session.destroy()
        nativeReceiver.destroy()
    }

    data class PlayOptions(
        val index: Int = 0,
        val autostart: Boolean = true,
        val startPosition: Long? = null,
        val useRememberedPosition: Boolean = true,
    )

    internal data class FileMutationPlayback(
        val index: Int,
        val positionMs: Long,
        val autostart: Boolean,
    )

    internal suspend fun releaseForFileMutation(songId: String): FileMutationPlayback? =
        withContext(Dispatchers.Main.immediate) {
            nextPlayer?.takeIf { it.id == songId }?.let {
                nextPlayer = null
                it.destroy()
            }
            val current = player?.takeIf { it.id == songId } ?: return@withContext null
            progress.saveCurrent(force = true)
            val snapshot = FileMutationPlayback(
                index = queue.currentSongIndex,
                positionMs = currentPlaybackPosition?.played ?: 0L,
                autostart = isPlaying || isPlayPending,
            )
            playerGeneration++
            player = null
            stagedStartPosition = null
            stagedDuration = 0L
            restorationStartedAt = null
            playbackState.onStopped()
            current.destroy()
            emitPlaybackState()
            onUpdate.dispatch(Events.Player.Stopped)
            snapshot
        }

    internal suspend fun restoreAfterFileMutation(snapshot: FileMutationPlayback?) {
        snapshot ?: return
        withContext(Dispatchers.Main.immediate) {
            if (snapshot.index !in queue.currentQueue.indices) {
                return@withContext
            }
            play(
                PlayOptions(
                    index = snapshot.index,
                    autostart = snapshot.autostart,
                    startPosition = snapshot.positionMs,
                    useRememberedPosition = false,
                )
            )
        }
    }

    fun play(options: PlayOptions) {
        progress.saveCurrent(force = true)
        stopCurrentSong(publishState = false)
        val song = queue.getSongIdAt(options.index)?.let(::resolveSong)
        if (song == null) {
            onSongFinish(SongFinishSource.Exception)
            return
        }
        try {
            val generation = ++playerGeneration
            stagedDuration = song.duration.coerceAtLeast(0L)
            stagedStartPosition = progress.resolveStartPosition(
                song = song,
                explicitPositionMs = options.startPosition,
                useRememberedPosition = options.useRememberedPosition,
            )
            if (stagedStartPosition?.let { it > 0L } == true) {
                restorationStartedAt = restorationStartedAt ?: SystemClock.elapsedRealtime()
                logRestoration("player staged")
            }
            playbackState.stage(
                restoring = stagedStartPosition?.let { it > 0L } == true,
                autostart = options.autostart,
            )
            queue.currentSongIndex = options.index
            emitPlaybackState()
            player = nextPlayer?.takeIf {
                when {
                    it.id == song.id -> true
                    else -> {
                        it.destroy()
                        false
                    }
                }
            } ?: RadioPlayer(symphony, song.id, song.uri)
            nextPlayer = null
            val stagedPlayer = player!!
            stagedPlayer.setOnPreparedListener {
                if (!isCurrentPlayer(stagedPlayer, generation)) {
                    return@setOnPreparedListener
                }
                setSpeed(persistedSpeed, true)
                setPitch(persistedPitch, true)

                val currentPosition = stagedPlayer.playbackPosition
                val restoredTarget = stagedStartPosition
                    ?.coerceIn(0L, currentPosition?.total ?: 0L)
                    ?: 0L
                val requiresSeek = restoredTarget > 0L &&
                        restoredTarget != currentPosition?.played
                val shouldStart = playbackState.onPrepared(requiresSeek)
                logRestoration("player prepared")
                emitPlaybackState()

                if (requiresSeek) {
                    val awaitingSeek = stagedPlayer.seek(restoredTarget)
                    if (!awaitingSeek) {
                        finishSeek(stagedPlayer, generation)
                    }
                } else if (shouldStart) {
                    stagedStartPosition = null
                    startPrepared(requestAudioFocus = true)
                } else {
                    stagedStartPosition = null
                }
            }
            stagedPlayer.setOnPlaybackPositionListener {
                if (isCurrentPlayer(stagedPlayer, generation)) {
                    onPlaybackPositionUpdate.dispatch(it)
                }
            }
            stagedPlayer.setOnSeekCompleteListener {
                finishSeek(stagedPlayer, generation)
            }
            stagedPlayer.setOnIsPlayingChangedListener { playing ->
                if (!isCurrentPlayer(stagedPlayer, generation)) {
                    return@setOnIsPlayingChangedListener
                }
                playbackState.onPlayingChanged(playing)
                emitPlaybackState()
            }
            stagedPlayer.setOnFinishListener {
                onSongFinish(SongFinishSource.Finish)
            }
            stagedPlayer.setOnErrorListener { what, extra ->
                if (!isCurrentPlayer(stagedPlayer, generation)) {
                    return@setOnErrorListener
                }
                playbackState.onError()
                emitPlaybackState()
                Logger.warn(
                    "Radio",
                    "skipping song ${queue.currentSongId} (${queue.currentSongIndex}) due to $what + $extra"
                )
                when {
                    // happens when change playback params fail, we skip it since its non-critical
                    what == 1 && extra == -22 -> onSongFinish(SongFinishSource.Finish)
                    else -> {
                        queue.remove(queue.currentSongIndex, playReplacement = false)
                        onSongFinish(SongFinishSource.Exception)
                    }
                }
            }
            playbackState.onPreparing()
            emitPlaybackState()
            stagedPlayer.prepare()
            prepareNextPlayer()
            onUpdate.dispatch(Events.Player.Staged)
        } catch (err: Exception) {
            Logger.warn(
                "Radio",
                "skipping song ${queue.currentSongId} (${queue.currentSongIndex})",
                err,
            )
            queue.remove(queue.currentSongIndex)
        }
    }

    private fun prepareNextPlayer() {
        if (!symphony.settings.gaplessPlayback.value) {
            return
        }
        val (nextSongIndex) = getNextSong(SongFinishSource.Finish)
        val song = queue.getSongIdAt(nextSongIndex)?.let(::resolveSong) ?: return
        if (song.id == nextPlayer?.id) {
            return
        }
        try {
            nextPlayer?.destroy()
            nextPlayer = RadioPlayer(symphony, song.id, song.uri).also {
                it.prepare()
            }
        } catch (err: Exception) {
            Logger.warn(
                "Radio",
                "unable to prepare next player ${song.id} (${nextSongIndex})",
                err,
            )
        }
    }

    fun resume() {
        val shouldStart = playbackState.requestPlay()
        emitPlaybackState()
        if (shouldStart) {
            startPrepared(requestAudioFocus = true)
        }
    }

    internal fun resumeAfterAudioFocusGain() {
        val shouldStart = playbackState.requestPlay()
        emitPlaybackState()
        if (shouldStart) {
            startPrepared(requestAudioFocus = false)
        }
    }

    private fun startPrepared(requestAudioFocus: Boolean) {
        player?.let {
            if (!it.usable || playbackReadiness != RadioPlaybackReadiness.Ready) {
                return@let
            }
            if (requestAudioFocus) {
                val hasFocus = focus.requestFocus()
                if (!hasFocus) {
                    playbackState.cancelPlay()
                    emitPlaybackState()
                    return
                }
            }
            if (it.fadePlayback) {
                it.changeVolumeInstant(RadioPlayer.MIN_VOLUME)
            }
            it.changeVolume(RadioPlayer.MAX_VOLUME) {}
            val hasPlayedOnce = it.hasPlayedOnce
            it.start()
            onUpdate.dispatch(
                when {
                    !hasPlayedOnce -> Events.Player.Started
                    else -> Events.Player.Resumed
                }
            )
        }
    }

    fun cancelPendingPlay() {
        playbackState.cancelPlay()
        emitPlaybackState()
    }

    fun pause() {
        focus.cancelPendingRecovery()
        cancelPendingPlay()
        if (!isPlaying) {
            focus.abandonFocus()
            return
        }
        pause(abandonAudioFocus = true) {}
    }

    private fun pause(
        forceFade: Boolean = false,
        abandonAudioFocus: Boolean = true,
        onFinish: () -> Unit,
    ) {
        player?.let {
            if (!it.isPlaying) {
                return@let
            }
            it.changeVolume(
                to = RadioPlayer.MIN_VOLUME,
                forceFade = forceFade,
            ) { _ ->
                it.pause()
                if (abandonAudioFocus) {
                    focus.abandonFocus()
                }
                onFinish()
                onUpdate.dispatch(Events.Player.Paused)
            }
        }
    }

    fun pauseInstant() {
        focus.cancelPendingRecovery()
        focus.abandonFocus()
        cancelPendingPlay()
        player?.let {
            it.pause()
            onUpdate.dispatch(Events.Player.Paused)
        }
    }

    internal fun pauseForAudioFocusLoss() {
        player?.let {
            if (!it.isPlaying) {
                return@let
            }
            it.pause()
            onUpdate.dispatch(Events.Player.Paused)
        }
    }

    fun stop(ended: Boolean = true) {
        focus.cancelPendingRecovery()
        focus.abandonFocus()
        progress.saveCurrent(force = true)
        stopCurrentSong()
        queue.reset()
        clearSleepTimer()
        persistedSpeed = RadioPlayer.DEFAULT_SPEED
        persistedPitch = RadioPlayer.DEFAULT_PITCH
        if (ended) onUpdate.dispatch(Events.Player.Ended)
    }

    fun jumpTo(index: Int) = play(PlayOptions(index = index))
    fun jumpToPrevious() {
        previousSongIndex()?.let(::jumpTo)
    }
    fun jumpToNext() {
        nextSongIndex()?.let(::jumpTo)
    }
    fun canJumpToPrevious() = previousSongIndex() != null
    fun canJumpToNext() = nextSongIndex() != null

    private fun previousSongIndex() = RadioQueueNavigation.previousIndex(
        currentIndex = queue.currentSongIndex,
        queueSize = queue.currentQueue.size,
    )

    private fun nextSongIndex() = RadioQueueNavigation.nextIndex(
        currentIndex = queue.currentSongIndex,
        queueSize = queue.currentQueue.size,
    )

    internal fun reconcileLibrary(librarySongIds: Set<String>) {
        queue.reconcileLibrary(librarySongIds)
        progress.reconcileLibrary(librarySongIds)
    }

    internal fun removeDeletedSong(songId: String): Boolean {
        return removeDeletedSongs(setOf(songId))
    }

    internal fun removeDeletedSongs(songIds: Set<String>): Boolean {
        if (songIds.isEmpty()) {
            return false
        }
        songIds.forEach(progress::delete)
        val continuePlaying = isPlaying || isPlayPending
        val result = queue.removeDeletedSongs(songIds)
        if (!result.removedCurrentSong) {
            return false
        }
        when {
            result.replacementIndex >= 0 -> play(
                PlayOptions(
                    index = result.replacementIndex,
                    autostart = continuePlaying,
                )
            )

            else -> stop()
        }
        songIds.forEach(progress::delete)
        return true
    }

    fun seek(position: Long) {
        player?.let {
            if (!it.usable) {
                stagedStartPosition = position.coerceIn(0L, stagedDuration.coerceAtLeast(0L))
                onPlaybackPositionUpdate.dispatch(
                    RadioPlayer.PlaybackPosition(
                        played = stagedStartPosition ?: 0L,
                        total = stagedDuration,
                    )
                )
                onUpdate.dispatch(Events.Player.Seeked)
                return@let
            }
            playbackState.onSeekStarted()
            emitPlaybackState()
            val awaitingSeek = it.seek(position)
            onUpdate.dispatch(Events.Player.Seeked)
            if (!awaitingSeek) {
                finishSeek(it, playerGeneration)
            }
        }
    }

    fun playFromBeginning(songId: String) {
        progress.delete(songId)
        val currentQueueIndex = when {
            queue.currentSongId == songId -> queue.currentSongIndex
            else -> queue.currentQueue.indexOf(songId)
        }
        if (currentQueueIndex >= 0) {
            play(
                PlayOptions(
                    index = currentQueueIndex,
                    startPosition = 0L,
                    useRememberedPosition = false,
                )
            )
            progress.delete(songId)
            return
        }
        shorty.playQueue(
            songId,
            options = PlayOptions(
                startPosition = 0L,
                useRememberedPosition = false,
            ),
        )
        progress.delete(songId)
    }

    fun duck() {
        player?.let {
            it.changeVolume(RadioPlayer.DUCK_VOLUME) {}
        }
    }

    fun restoreVolume() {
        player?.let {
            it.changeVolume(RadioPlayer.MAX_VOLUME) {}
        }
    }

    fun setSpeed(speed: Float, persist: Boolean) {
        player?.let {
            it.changeSpeed(speed)
            if (persist) {
                persistedSpeed = speed
            }
            onUpdate.dispatch(Events.QueueOption.SpeedChanged)
        }
    }

    fun setPitch(pitch: Float, persist: Boolean) {
        player?.let {
            it.changePitch(pitch)
            if (persist) {
                persistedPitch = pitch
            }
            onUpdate.dispatch(Events.QueueOption.PitchChanged)
        }
    }

    fun setSleepTimer(
        duration: Long,
        quitOnEnd: Boolean,
    ) {
        val endsAt = System.currentTimeMillis() + duration
        val timer = Timer()
        timer.schedule(
            kotlin.concurrent.timerTask {
                val shouldQuit = sleepTimer?.quitOnEnd ?: quitOnEnd
                clearSleepTimer()
                focus.cancelPendingRecovery()
                pause(forceFade = true, abandonAudioFocus = true) {
                    if (shouldQuit) {
                        symphony.closeApp?.invoke()
                    }
                }
            },
            Date.from(Instant.ofEpochMilli(endsAt)),
        )
        clearSleepTimer()
        sleepTimer = SleepTimer(
            duration = duration,
            endsAt = endsAt,
            timer = timer,
            quitOnEnd = quitOnEnd,
        )
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    fun clearSleepTimer() {
        sleepTimer?.timer?.cancel()
        sleepTimer = null
        onUpdate.dispatch(Events.QueueOption.SleepTimerChanged)
    }

    @JvmName("setPauseOnCurrentSongEndTo")
    fun setPauseOnCurrentSongEnd(value: Boolean) {
        pauseOnCurrentSongEnd = value
        onUpdate.dispatch(Events.QueueOption.PauseOnCurrentSongEndChanged)
    }

    private fun stopCurrentSong(publishState: Boolean = true) {
        playerGeneration++
        stagedStartPosition = null
        stagedDuration = 0L
        restorationStartedAt = null
        playbackState.onStopped()
        if (publishState) {
            emitPlaybackState()
        }
        player?.let {
            player = null
            it.setOnPlaybackPositionListener {}
            it.setOnSeekCompleteListener {}
            it.setOnIsPlayingChangedListener {}
            it.setOnFinishListener {}
            it.setOnErrorListener { _, _ -> }
            it.changeVolume(RadioPlayer.MIN_VOLUME) { _ ->
                it.stop()
                onUpdate.dispatch(Events.Player.Stopped)
            }
        }
    }

    private enum class SongFinishSource {
        Finish,
        Exception,
    }

    private fun onSongFinish(source: SongFinishSource) {
        if (queue.isEmpty()) {
            stopCurrentSong()
            queue.currentSongIndex = -1
            return
        }
        var (nextSongIndex, autostart) = getNextSong(source)
        if (pauseOnCurrentSongEnd) {
            autostart = false
            setPauseOnCurrentSongEnd(false)
        }
        play(PlayOptions(nextSongIndex, autostart = autostart))
    }

    private fun getNextSong(source: SongFinishSource): Pair<Int, Boolean> {
        if (queue.isEmpty()) {
            return -1 to false
        }
        var autostart: Boolean
        var nextSongIndex: Int
        when (queue.currentLoopMode) {
            RadioQueue.LoopMode.Song -> {
                nextSongIndex = queue.currentSongIndex
                autostart = source == SongFinishSource.Finish
                if (!queue.hasSongAt(nextSongIndex)) {
                    nextSongIndex = 0
                    autostart = false
                }
            }

            else -> {
                nextSongIndex = when (source) {
                    SongFinishSource.Finish -> queue.currentSongIndex + 1
                    SongFinishSource.Exception -> queue.currentSongIndex
                }
                autostart = true
                if (!queue.hasSongAt(nextSongIndex)) {
                    nextSongIndex = 0
                    autostart = queue.currentLoopMode == RadioQueue.LoopMode.Queue
                }
            }
        }
        return nextSongIndex to autostart
    }

    private fun attachGrooveListener() {
        symphony.groove.coroutineScope.launch {
            try {
                val previous = symphony.settings.previousSongQueue.value ?: return@launch
                restorationStartedAt = SystemClock.elapsedRealtime()
                logRestoration("cached queue lookup started")
                val restoredFromCache = restorePreviousQueueFromCache(previous)
                if (!restoredFromCache) {
                    logRestoration("cached queue unavailable; awaiting library scan")
                    symphony.groove.readyDeferred.await()
                    restorePreviousQueue(
                        previous = previous,
                        availableSongIds = symphony.groove.song.ids().toSet(),
                    )
                }
            } finally {
                restorationReady.complete(Unit)
            }
        }
    }

    internal suspend fun awaitRestoration() {
        restorationReady.await()
    }

    private suspend fun restorePreviousQueueFromCache(
        previous: RadioQueue.Serialized,
    ): Boolean {
        val requestedIds = (previous.originalQueue + previous.currentQueue).distinct()
        if (requestedIds.isEmpty()) {
            return false
        }
        val cachedSongs = symphony.database.songCache.entriesByIds(requestedIds)
        logRestoration("cached queue lookup completed")
        val cachedById = cachedSongs.associateBy { it.id }
        restoredSongCache.putAll(cachedById)
        val restored = RadioQueueRestorer.filter(previous, cachedById.keys) ?: return false
        val currentSongId = restored.currentQueue.getOrNull(restored.currentSongIndex)
            ?: return false
        val currentSong = cachedById[currentSongId] ?: return false

        return withContext(Dispatchers.Main) {
            if (!queue.isEmpty() || player != null) {
                return@withContext false
            }
            symphony.groove.song.onSong(currentSong)
            queue.restore(restored)
            true
        }
    }

    private suspend fun restorePreviousQueue(
        previous: RadioQueue.Serialized,
        availableSongIds: Set<String>,
    ): Boolean {
        val restored = RadioQueueRestorer.filter(previous, availableSongIds) ?: return false
        return withContext(Dispatchers.Main) {
            if (!queue.isEmpty() || player != null) {
                return@withContext false
            }
            queue.restore(restored)
            true
        }
    }

    private fun finishSeek(stagedPlayer: RadioPlayer, generation: Long) {
        if (!isCurrentPlayer(stagedPlayer, generation) ||
            playbackReadiness != RadioPlaybackReadiness.Seeking
        ) {
            return
        }
        val shouldStart = playbackState.onSeekComplete(stagedPlayer.isPlaying)
        stagedStartPosition = null
        logRestoration("restored seek completed")
        emitPlaybackState()
        if (shouldStart) {
            startPrepared(requestAudioFocus = true)
        }
    }

    private fun isCurrentPlayer(candidate: RadioPlayer, generation: Long) =
        player === candidate && playerGeneration == generation

    private fun resolveSong(songId: String) = symphony.groove.song.get(songId)
        ?: restoredSongCache[songId]?.also(symphony.groove.song::onSong)

    private fun emitPlaybackState() {
        onUpdate.dispatch(Events.Player.StateChanged)
    }

    private fun logRestoration(milestone: String) {
        restorationStartedAt?.let { startedAt ->
            Logger.debug(
                "RadioRestore",
                "$milestone after ${SystemClock.elapsedRealtime() - startedAt} ms",
            )
        }
    }

    internal fun watchQueueUpdates(event: Events) {
        if (event !is Events.Queue) {
            return
        }
        prepareNextPlayer()
    }

    override fun onSymphonyReady() {
        ready()
    }

    override fun onSymphonyDestroy() {
        saveCurrentQueue()
        destroy()
    }

    override fun onSymphonyActivityPause() {
        saveCurrentQueue()
    }

    override fun onSymphonyActivityDestroy() {
        saveCurrentQueue()
    }

    private fun saveCurrentQueue() {
        progress.saveCurrent(force = true)
        persistCurrentQueueSnapshot()
    }

    internal fun persistCurrentQueueSnapshot() {
        if (queue.isEmpty()) {
            return
        }
        symphony.settings.previousSongQueue.setValue(
            RadioQueue.Serialized.create(
                queue = queue,
                playbackPosition = currentPlaybackPosition ?: RadioPlayer.PlaybackPosition.zero
            )
        )
    }
}
