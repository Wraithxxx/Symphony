package io.github.zyrouge.symphony.services

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SettingsTest {
    @Test
    fun `forward and backward seek durations use independent preference keys`() {
        assertNotEquals(
            Settings.SEEK_BACK_DURATION_KEY,
            Settings.SEEK_FORWARD_DURATION_KEY,
        )
    }
}
