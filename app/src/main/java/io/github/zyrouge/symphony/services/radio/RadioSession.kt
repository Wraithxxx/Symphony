package io.github.zyrouge.symphony.services.radio

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.activity.result.contract.ActivityResultContract
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.groove.Song
import io.github.zyrouge.symphony.utils.EventUnsubscribeFn
import io.github.zyrouge.symphony.utils.Logger
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class RadioSession(val symphony: Symphony) {
    data class UpdateRequest(
        val song: Song,
        val artworkUri: Uri,
        val artworkBitmap: Bitmap,
        val playbackPosition: RadioPlayer.PlaybackPosition,
        val isPlaying: Boolean,
        val playbackReadiness: RadioPlaybackReadiness,
        val isPlayPending: Boolean,
    )

    internal val mediaSession = MediaSessionCompat(symphony.applicationContext, MEDIA_SESSION_ID)
    private val artworkCacher = RadioArtworkCacher(symphony)
    private val notification = RadioNotification(symphony)
    private val publicationState = RadioSessionPublicationState()

    private val updateGeneration = AtomicLong(0L)
    private var updateSubscriber: EventUnsubscribeFn? = null

    internal fun invalidateArtwork(songId: String) {
        artworkCacher.invalidate(songId)
    }
    private var receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.action?.let { action ->
                handleAction(action)
            }
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            symphony.applicationContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(ACTION_PLAY_PAUSE)
                    addAction(ACTION_PREVIOUS)
                    addAction(ACTION_NEXT)
                    addAction(ACTION_STOP)
                },
                Context.RECEIVER_EXPORTED,
                // https://developer.android.com/reference/android/content/Context#RECEIVER_EXPORTED
                // really, RECEIVER_EXPORTED and RECEIVER_NOT_EXPORTED makes no difference.
                // the notification appears perfectly, Pano Scrobbler sees it,
                // Wear OS can send signals to play/pause the app, other media apps can pause it,
                // no clue what the difference here is... but here we are.
            )
        } else {
            @SuppressLint("UnspecifiedRegisterReceiverFlag")
            symphony.applicationContext.registerReceiver(
                receiver,
                IntentFilter().apply {
                    addAction(ACTION_PLAY_PAUSE)
                    addAction(ACTION_PREVIOUS)
                    addAction(ACTION_NEXT)
                    addAction(ACTION_STOP)
                },
            )
        }
        mediaSession.setCallback(
            object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onPause() {
                    super.onPause()
                    handleAction(ACTION_PLAY_PAUSE)
                }

                override fun onSkipToPrevious() {
                    super.onSkipToPrevious()
                    handleAction(ACTION_PREVIOUS)
                }

                override fun onSkipToNext() {
                    super.onSkipToNext()
                    handleAction(ACTION_NEXT)
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                    @Suppress("DEPRECATION")
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent.getParcelableExtra(
                            Intent.EXTRA_KEY_EVENT,
                            KeyEvent::class.java,
                        )
                    } else {
                        mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    } ?: return super.onMediaButtonEvent(mediaButtonEvent)

                    if (!RadioMediaButtonCommandResolver.isQueueSkipKey(keyEvent.keyCode)) {
                        return super.onMediaButtonEvent(mediaButtonEvent)
                    }

                    when (
                        RadioMediaButtonCommandResolver.resolve(
                            action = keyEvent.action,
                            keyCode = keyEvent.keyCode,
                            repeatCount = keyEvent.repeatCount,
                        )
                    ) {
                        RadioMediaButtonCommandResolver.Command.Previous -> {
                            handleAction(ACTION_PREVIOUS)
                        }

                        RadioMediaButtonCommandResolver.Command.Next -> {
                            handleAction(ACTION_NEXT)
                        }

                        null -> {}
                    }
                    return true
                }

                override fun onStop() {
                    super.onStop()
                    handleAction(ACTION_STOP)
                }

                override fun onSeekTo(pos: Long) {
                    super.onSeekTo(pos)
                    symphony.radio.seek(pos)
                }

                override fun onRewind() {
                    super.onRewind()
                    val duration = symphony.settings.seekBackDuration.value
                    symphony.radio.shorty.seekFromCurrent(-duration)
                }

                override fun onFastForward() {
                    super.onFastForward()
                    val duration = symphony.settings.seekForwardDuration.value
                    symphony.radio.shorty.seekFromCurrent(duration)
                }

            }
        )
        notification.start()
        updateSubscriber = symphony.radio.onUpdate.subscribe {
            when (it) {
                Radio.Events.Player.Ended -> cancel()
                is Radio.Events.Player -> update()
                else -> {}
            }
        }
        update()
    }

    fun handleAction(action: String) {
        when (action) {
            ACTION_PLAY_PAUSE -> symphony.radio.shorty.playPause()
            ACTION_PREVIOUS -> symphony.radio.shorty.previous()
            ACTION_NEXT -> symphony.radio.shorty.skip()
            ACTION_STOP -> symphony.radio.stop()
        }
    }

    fun cancel() {
        updateGeneration.incrementAndGet()
        publicationState.clear()
        notification.cancel()
        mediaSession.isActive = false
    }

    fun destroy() {
        updateSubscriber?.invoke()
        updateSubscriber = null
        cancel()
        symphony.applicationContext.unregisterReceiver(receiver)
        mediaSession.release()
    }

    fun createEqualizerActivityContract() = object : ActivityResultContract<Unit, Unit>() {
        override fun createIntent(
            context: Context,
            input: Unit,
        ) = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, symphony.applicationContext.packageName)
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, symphony.radio.audioSessionId ?: 0)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }

        override fun parseResult(
            resultCode: Int,
            intent: Intent?,
        ) {
        }
    }

    private fun update() {
        val generation = updateGeneration.incrementAndGet()
        val snapshot = symphony.radio.observatory.playbackSnapshot.value
        if (snapshot.songId == null) {
            return
        }
        publishCoreSession(snapshot)
        symphony.groove.coroutineScope.launch {
            updateAsync(generation, snapshot)
        }
    }

    private suspend fun updateAsync(
        generation: Long,
        snapshot: RadioPlaybackSnapshot,
    ) {
        val song = snapshot.songId?.let { symphony.groove.song.get(it) } ?: return
        val artworkUri = symphony.groove.song.getArtworkUri(song.id)
        val artworkBitmap = artworkCacher.getArtwork(song)
        val latestSnapshot = symphony.radio.observatory.playbackSnapshot.value
        if (
            updateGeneration.get() != generation ||
            latestSnapshot.generation != snapshot.generation ||
            latestSnapshot.songId != snapshot.songId
        ) {
            return
        }
        val req = UpdateRequest(
            song = song,
            artworkUri = artworkUri,
            artworkBitmap = artworkBitmap,
            playbackPosition = latestSnapshot.position,
            isPlaying = latestSnapshot.isPlaying,
            playbackReadiness = latestSnapshot.readiness,
            isPlayPending = latestSnapshot.isPlayPending,
        )
        updateSession(req)
        notification.update(req)
    }

    private fun updateSession(req: UpdateRequest) {
        ensureEnabled()
        publicationState.shouldPublishBasicMetadata(req.song.id)
        mediaSession.run {
            setMetadata(
                MediaMetadataCompat.Builder().run {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, req.song.title)
                    if (req.song.artists.isNotEmpty()) {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_ARTIST,
                            req.song.artists.joinToString()
                        )
                    }
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, req.song.album)
                    req.artworkBitmap.let {
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, it)
                    }
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        req.playbackPosition.total.toLong()
                    )
                    build()
                }
            )
            setPlaybackState(
                createPlaybackState(
                    playbackPosition = req.playbackPosition,
                    isPlaying = req.isPlaying,
                    playbackReadiness = req.playbackReadiness,
                    isPlayPending = req.isPlayPending,
                )
            )
        }
    }

    private fun publishCoreSession(snapshot: RadioPlaybackSnapshot) {
        ensureEnabled()
        val song = snapshot.songId?.let { symphony.groove.song.get(it) }
        if (song != null && publicationState.shouldPublishBasicMetadata(song.id)) {
            mediaSession.setMetadata(
                MediaMetadataCompat.Builder().run {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    if (song.artists.isNotEmpty()) {
                        putString(
                            MediaMetadataCompat.METADATA_KEY_ARTIST,
                            song.artists.joinToString(),
                        )
                    }
                    putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_DURATION,
                        snapshot.position.total,
                    )
                    build()
                }
            )
        }
        mediaSession.setPlaybackState(
            createPlaybackState(
                playbackPosition = snapshot.position,
                isPlaying = snapshot.isPlaying,
                playbackReadiness = snapshot.readiness,
                isPlayPending = snapshot.isPlayPending,
            )
        )
        Logger.debug(
            "RadioSession",
            "core state published for ${snapshot.songId} (${snapshot.readiness})",
        )
    }

    private fun createPlaybackState(
        playbackPosition: RadioPlayer.PlaybackPosition,
        isPlaying: Boolean,
        playbackReadiness: RadioPlaybackReadiness,
        isPlayPending: Boolean,
    ) = PlaybackStateCompat.Builder().run {
        setState(
            when {
                isPlaying -> PlaybackStateCompat.STATE_PLAYING
                isPlayPending || playbackReadiness in setOf(
                    RadioPlaybackReadiness.Restoring,
                    RadioPlaybackReadiness.Preparing,
                    RadioPlaybackReadiness.Seeking,
                ) -> PlaybackStateCompat.STATE_BUFFERING
                else -> PlaybackStateCompat.STATE_PAUSED
            },
            playbackPosition.played,
            1f,
        )
        setActions(
            PlaybackStateCompat.ACTION_PLAY
                    or PlaybackStateCompat.ACTION_PAUSE
                    or PlaybackStateCompat.ACTION_PLAY_PAUSE
                    or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    or PlaybackStateCompat.ACTION_STOP
                    or PlaybackStateCompat.ACTION_REWIND
                    or PlaybackStateCompat.ACTION_FAST_FORWARD
                    or PlaybackStateCompat.ACTION_SEEK_TO
        )
        build()
    }

    private fun ensureEnabled() {
        if (!mediaSession.isActive) {
            mediaSession.isActive = true
        }
    }

    companion object {
        val MEDIA_SESSION_ID = "${R.string.app_name}_media_session"

        val ACTION_PLAY_PAUSE = "${R.string.app_name}_play_pause"
        val ACTION_PREVIOUS = "${R.string.app_name}_previous"
        val ACTION_NEXT = "${R.string.app_name}_next"
        val ACTION_STOP = "${R.string.app_name}_stop"
    }
}
