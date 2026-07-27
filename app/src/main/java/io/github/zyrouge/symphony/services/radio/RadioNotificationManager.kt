package io.github.zyrouge.symphony.services.radio

import android.app.Notification
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.github.zyrouge.symphony.R
import io.github.zyrouge.symphony.Symphony

class RadioNotificationManager(val symphony: Symphony) {
    private var manager = NotificationManagerCompat.from(symphony.applicationContext)
    private var lastNotification: Notification? = null
    private val foregroundLifecycle = RadioForegroundLifecycle()
    private val service: RadioNotificationService?
        get() = RadioNotificationService.instance

    fun prepare() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(
                RadioNotification.CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_LOW,
            ).run {
                setName(symphony.applicationContext.getString(R.string.app_name))
                setLightsEnabled(false)
                setVibrationEnabled(false)
                setShowBadge(false)
                build()
            }
        )
        RadioNotificationService.events.subscribe {
            when (it) {
                RadioNotificationService.Event.START -> onServiceStart()
                RadioNotificationService.Event.STOP -> onServiceStop()
            }
        }
    }

    fun cancel() {
        destroyNotification()
        RadioNotificationService.destroy()
    }

    fun notify(notification: Notification) {
        when (foregroundLifecycle.onNotification(service != null)) {
            RadioForegroundLifecycle.Action.None -> {
                lastNotification = notification
            }

            RadioForegroundLifecycle.Action.StartService -> {
                lastNotification = notification
                createService()
            }

            RadioForegroundLifecycle.Action.StartForeground -> {
                lastNotification = null
                startForeground(notification)
            }

            RadioForegroundLifecycle.Action.UpdateNotification -> {
                try {
                    manager.notify(RadioNotification.NOTIFICATION_ID, notification)
                } catch (_: SecurityException) {
                    // NOTE: the notification updates even without permission...
                }
            }
        }
    }

    private fun destroyNotification() {
        foregroundLifecycle.reset()
        lastNotification = null
        manager.cancel(RadioNotification.CHANNEL_ID, RadioNotification.NOTIFICATION_ID)
    }

    private fun createService() {
        val intent = Intent(symphony.applicationContext, RadioNotificationService::class.java)
        symphony.applicationContext.startForegroundService(intent)
    }

    private fun onServiceStart() {
        if (foregroundLifecycle.onServiceStarted() ==
            RadioForegroundLifecycle.Action.StartForeground
        ) {
            lastNotification?.let { notification ->
                lastNotification = null
                startForeground(notification)
            }
        }
    }

    private fun onServiceStop() {
        destroyNotification()
    }

    private fun startForeground(notification: Notification) {
        ServiceCompat.startForeground(
            service!!,
            RadioNotification.NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )
    }
}
