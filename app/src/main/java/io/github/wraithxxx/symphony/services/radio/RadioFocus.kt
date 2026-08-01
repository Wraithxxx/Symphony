package io.github.wraithxxx.symphony.services.radio

import android.media.AudioManager
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import io.github.wraithxxx.symphony.Symphony

// Credits: https://github.com/RetroMusicPlayer/RetroMusicPlayer/blob/7b1593009319c8d8e04660470ba37f814e8203eb/app/src/main/java/code/name/monkey/retromusic/service/LocalPlayback.kt
class RadioFocus(val symphony: Symphony) {
    var hasFocus = false
        private set
    private val state = RadioFocusState()

    private val audioManager: AudioManager =
        symphony.applicationContext.getSystemService(AudioManager::class.java)

    private val audioFocusRequest: AudioFocusRequestCompat =
        AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributesCompat.Builder()
                    .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { event ->
                when (event) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        hasFocus = true
                        when (state.onGain()) {
                            RadioFocusState.GainAction.None -> {}
                            RadioFocusState.GainAction.Resume -> {
                                symphony.radio.resumeAfterAudioFocusGain()
                            }
                            RadioFocusState.GainAction.RestoreVolume -> {
                                symphony.radio.restoreVolume()
                            }
                        }
                    }

                    AudioManager.AUDIOFOCUS_LOSS -> {
                        hasFocus = false
                        when (
                            state.onPermanentLoss(
                                isPlaying = symphony.radio.isPlaying,
                                ignoreLoss = shouldIgnoreLoss(),
                            )
                        ) {
                            RadioFocusState.LossAction.Pause -> {
                                symphony.radio.pauseForAudioFocusLoss()
                            }
                            else -> {}
                        }
                        abandonFocus()
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        hasFocus = false
                        when (
                            state.onTransientLoss(
                                isPlaying = symphony.radio.isPlaying,
                                ignoreLoss = shouldIgnoreLoss(),
                            )
                        ) {
                            RadioFocusState.LossAction.Pause -> {
                                symphony.radio.pauseForAudioFocusLoss()
                            }
                            else -> {}
                        }
                    }

                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                        hasFocus = false
                        when (
                            state.onDuckLoss(
                                isPlaying = symphony.radio.isPlaying,
                                ignoreLoss = shouldIgnoreLoss(),
                            )
                        ) {
                            RadioFocusState.LossAction.Duck -> symphony.radio.duck()
                            else -> {}
                        }
                    }
                }
            }
            .build()

    private fun shouldIgnoreLoss() =
        symphony.settings.audioInterruptionBehavior.value ==
                AudioInterruptionBehavior.KeepPlaying

    fun requestFocus(): Boolean {
        state.cancelPendingRecovery()
        hasFocus = AudioManagerCompat.requestAudioFocus(
            audioManager,
            audioFocusRequest
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return hasFocus
    }

    fun abandonFocus(): Boolean {
        hasFocus = false
        return AudioManagerCompat.abandonAudioFocusRequest(
            audioManager,
            audioFocusRequest
        ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun cancelPendingRecovery() {
        state.cancelPendingRecovery()
    }
}
