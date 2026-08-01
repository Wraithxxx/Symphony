package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioFocusStateTest {
    @Test
    fun `temporary loss resumes only when playback was active`() {
        val state = RadioFocusState()

        assertEquals(
            RadioFocusState.LossAction.Pause,
            state.onTransientLoss(isPlaying = true, ignoreLoss = false),
        )
        assertEquals(RadioFocusState.GainAction.Resume, state.onGain())
        assertEquals(RadioFocusState.GainAction.None, state.onGain())

        assertEquals(
            RadioFocusState.LossAction.None,
            state.onTransientLoss(isPlaying = false, ignoreLoss = false),
        )
        assertEquals(RadioFocusState.GainAction.None, state.onGain())
    }

    @Test
    fun `repeated temporary loss retains pending resume`() {
        val state = RadioFocusState()

        state.onTransientLoss(isPlaying = true, ignoreLoss = false)
        state.onTransientLoss(isPlaying = false, ignoreLoss = false)

        assertEquals(RadioFocusState.GainAction.Resume, state.onGain())
    }

    @Test
    fun `ducking restores volume without resuming`() {
        val state = RadioFocusState()

        assertEquals(
            RadioFocusState.LossAction.Duck,
            state.onDuckLoss(isPlaying = true, ignoreLoss = false),
        )
        assertEquals(RadioFocusState.GainAction.RestoreVolume, state.onGain())
    }

    @Test
    fun `permanent loss clears temporary recovery`() {
        val state = RadioFocusState()

        state.onTransientLoss(isPlaying = true, ignoreLoss = false)
        assertEquals(
            RadioFocusState.LossAction.None,
            state.onPermanentLoss(isPlaying = false, ignoreLoss = false),
        )
        assertEquals(RadioFocusState.GainAction.None, state.onGain())
    }

    @Test
    fun `manual cancellation prevents automatic recovery`() {
        val state = RadioFocusState()

        state.onTransientLoss(isPlaying = true, ignoreLoss = false)
        state.cancelPendingRecovery()

        assertEquals(RadioFocusState.GainAction.None, state.onGain())
    }

    @Test
    fun `ignore loss does not schedule recovery or change playback`() {
        val state = RadioFocusState()

        assertEquals(
            RadioFocusState.LossAction.None,
            state.onTransientLoss(isPlaying = true, ignoreLoss = true),
        )
        assertEquals(
            RadioFocusState.LossAction.None,
            state.onDuckLoss(isPlaying = true, ignoreLoss = true),
        )
        assertEquals(RadioFocusState.GainAction.None, state.onGain())
    }
}
