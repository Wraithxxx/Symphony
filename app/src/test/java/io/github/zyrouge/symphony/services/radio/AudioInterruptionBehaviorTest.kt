package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioInterruptionBehaviorTest {
    @Test
    fun `legacy default maps to automatic interruption handling`() {
        assertEquals(
            AudioInterruptionBehavior.PauseAndResume,
            AudioInterruptionBehavior.fromLegacyIgnoreLoss(false),
        )
    }

    @Test
    fun `legacy ignore loss maps to keep playing`() {
        assertEquals(
            AudioInterruptionBehavior.KeepPlaying,
            AudioInterruptionBehavior.fromLegacyIgnoreLoss(true),
        )
    }
}
