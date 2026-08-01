package io.github.wraithxxx.symphony.services.groove

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GrooveRefreshGateTest {
    @Test
    fun `first automatic refresh is allowed`() {
        assertTrue(GrooveRefreshGate(2_000L).shouldRefresh(now = 0L, force = false))
    }

    @Test
    fun `successful refresh suppresses immediate repeat`() {
        val gate = GrooveRefreshGate(2_000L)
        gate.onSuccess(10_000L)

        assertFalse(gate.shouldRefresh(now = 11_999L, force = false))
        assertTrue(gate.shouldRefresh(now = 12_000L, force = false))
    }

    @Test
    fun `manual refresh bypasses interval`() {
        val gate = GrooveRefreshGate(2_000L)
        gate.onSuccess(10_000L)

        assertTrue(gate.shouldRefresh(now = 10_001L, force = true))
    }
}
