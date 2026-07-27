package io.github.zyrouge.symphony.services.radio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RadioForegroundLifecycleTest {
    @Test
    fun `first notification starts service then foreground`() {
        val lifecycle = RadioForegroundLifecycle()

        assertEquals(
            RadioForegroundLifecycle.Action.StartService,
            lifecycle.onNotification(serviceAvailable = false),
        )
        assertEquals(
            RadioForegroundLifecycle.Action.StartForeground,
            lifecycle.onServiceStarted(),
        )
    }

    @Test
    fun `notification during preparation does not start duplicate service`() {
        val lifecycle = RadioForegroundLifecycle()
        lifecycle.onNotification(serviceAvailable = false)

        assertEquals(
            RadioForegroundLifecycle.Action.None,
            lifecycle.onNotification(serviceAvailable = false),
        )
        assertEquals(
            RadioForegroundLifecycle.Action.StartForeground,
            lifecycle.onServiceStarted(),
        )
    }

    @Test
    fun `system restarted service promotes its next notification`() {
        val lifecycle = RadioForegroundLifecycle()

        assertEquals(
            RadioForegroundLifecycle.Action.None,
            lifecycle.onServiceStarted(),
        )
        assertEquals(
            RadioForegroundLifecycle.Action.StartForeground,
            lifecycle.onNotification(serviceAvailable = true),
        )
    }

    @Test
    fun `foreground service updates later notifications`() {
        val lifecycle = RadioForegroundLifecycle()
        lifecycle.onServiceStarted()
        lifecycle.onNotification(serviceAvailable = true)

        assertEquals(
            RadioForegroundLifecycle.Action.UpdateNotification,
            lifecycle.onNotification(serviceAvailable = true),
        )
    }

    @Test
    fun `reset requires a fresh service start`() {
        val lifecycle = RadioForegroundLifecycle()
        lifecycle.onServiceStarted()
        lifecycle.onNotification(serviceAvailable = true)
        lifecycle.reset()

        assertEquals(
            RadioForegroundLifecycle.Action.StartService,
            lifecycle.onNotification(serviceAvailable = false),
        )
    }
}
