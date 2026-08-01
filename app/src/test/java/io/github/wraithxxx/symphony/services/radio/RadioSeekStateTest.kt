package io.github.wraithxxx.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioSeekStateTest {
    @Test
    fun `clamps seek targets to the track duration`() {
        val state = RadioSeekState()

        assertEquals(RadioSeekState.Command(0L), state.request(-1_000L, 10_000L))
        assertNull(state.onSeekComplete())
        assertEquals(RadioSeekState.Command(10_000L), state.request(15_000L, 10_000L))
    }

    @Test
    fun `keeps only the latest request while a seek is active`() {
        val state = RadioSeekState()

        assertEquals(RadioSeekState.Command(60_000L), state.request(60_000L, 120_000L))
        assertNull(state.request(50_000L, 120_000L))
        assertNull(state.request(40_000L, 120_000L))
        assertEquals(40_000L, state.positionForReporting(actualPosition = 10_000L))

        assertEquals(RadioSeekState.Command(40_000L), state.onSeekComplete())
        assertTrue(state.isSeeking())
        assertNull(state.onSeekComplete())
        assertFalse(state.isSeeking())
    }

    @Test
    fun `a repeated target settles without an unnecessary second platform seek`() {
        val state = RadioSeekState()

        assertEquals(RadioSeekState.Command(30_000L), state.request(30_000L, 60_000L))
        assertNull(state.request(30_000L, 60_000L))

        assertNull(state.onSeekComplete())
        assertFalse(state.isSeeking())
    }

    @Test
    fun `backward targets can cross an earlier absolute seek`() {
        val state = RadioSeekState()
        val duration = 7L * 60L * 60L * 1_000L

        assertEquals(
            RadioSeekState.Command(4L * 60L * 60L * 1_000L),
            state.request(4L * 60L * 60L * 1_000L, duration),
        )
        assertNull(state.onSeekComplete())

        var position = 4L * 60L * 60L * 1_000L
        repeat(5) {
            position -= 60L * 60L * 1_000L
            val expected = position.coerceAtLeast(0L)
            assertEquals(
                RadioSeekState.Command(expected),
                state.request(position, duration),
            )
            assertNull(state.onSeekComplete())
        }

        assertEquals(0L, state.positionForReporting(actualPosition = 0L))
    }

    @Test
    fun `restored seek and immediate relative seek share one target sequence`() {
        val state = RadioSeekState()
        val duration = 6L * 60L * 60L * 1_000L
        val restored = 3L * 60L * 60L * 1_000L
        val backward = restored - 10_000L

        assertEquals(RadioSeekState.Command(restored), state.request(restored, duration))
        assertEquals(restored, state.positionForReporting(actualPosition = 0L))
        assertNull(state.request(backward, duration))
        assertEquals(backward, state.positionForReporting(actualPosition = 0L))
        assertEquals(RadioSeekState.Command(backward), state.onSeekComplete())
        assertNull(state.onSeekComplete())
        assertEquals(backward, state.positionForReporting(actualPosition = backward))
    }

    @Test
    fun `reset discards active and pending targets from a destroyed player`() {
        val state = RadioSeekState()

        state.request(80_000L, 100_000L)
        state.request(20_000L, 100_000L)
        state.reset()

        assertFalse(state.isSeeking())
        assertEquals(5_000L, state.positionForReporting(actualPosition = 5_000L))
        assertNull(state.onSeekComplete())
    }
}
