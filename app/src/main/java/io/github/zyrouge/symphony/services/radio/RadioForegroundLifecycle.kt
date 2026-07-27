package io.github.zyrouge.symphony.services.radio

internal class RadioForegroundLifecycle {
    enum class Action {
        None,
        StartService,
        StartForeground,
        UpdateNotification,
    }

    private enum class Phase {
        Destroyed,
        Preparing,
        Ready,
    }

    private var phase = Phase.Destroyed
    private var foreground = false
    private var notificationPending = false

    fun onNotification(serviceAvailable: Boolean): Action {
        if (phase == Phase.Ready && serviceAvailable) {
            return if (foreground) {
                Action.UpdateNotification
            } else {
                foreground = true
                Action.StartForeground
            }
        }

        notificationPending = true
        return when (phase) {
            Phase.Preparing -> Action.None
            Phase.Destroyed,
            Phase.Ready,
            -> {
                phase = Phase.Preparing
                foreground = false
                Action.StartService
            }
        }
    }

    fun onServiceStarted(): Action {
        phase = Phase.Ready
        return if (notificationPending) {
            notificationPending = false
            foreground = true
            Action.StartForeground
        } else {
            Action.None
        }
    }

    fun reset() {
        phase = Phase.Destroyed
        foreground = false
        notificationPending = false
    }
}
