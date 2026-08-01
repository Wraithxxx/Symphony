package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioPlaybackCommandPolicyTest {
    @Test
    fun `play is idempotent while already playing or pending`() {
        assertFalse(RadioPlaybackCommandPolicy.shouldPlay(true, true, false))
        assertFalse(RadioPlaybackCommandPolicy.shouldPlay(true, false, true))
        assertTrue(RadioPlaybackCommandPolicy.shouldPlay(true, false, false))
    }

    @Test
    fun `pause cancels both active and pending playback`() {
        assertTrue(RadioPlaybackCommandPolicy.shouldPause(true, true, false))
        assertTrue(RadioPlaybackCommandPolicy.shouldPause(true, false, true))
        assertFalse(RadioPlaybackCommandPolicy.shouldPause(true, false, false))
    }

    @Test
    fun `commands are ignored without a controllable player`() {
        assertFalse(RadioPlaybackCommandPolicy.shouldPlay(false, false, false))
        assertFalse(RadioPlaybackCommandPolicy.shouldPause(false, true, false))
    }
}
