package io.github.wraithxxx.symphony.services.radio

import android.app.Service
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Intent
import android.os.IBinder
import androidx.media.session.MediaButtonReceiver
import io.github.wraithxxx.symphony.SymphonyApplication
import io.github.wraithxxx.symphony.utils.Eventer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RadioNotificationService : Service() {
    enum class Event {
        START,
        STOP,
    }

    private val symphony
        get() = (application as SymphonyApplication).symphony
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        symphony.emitReady()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        events.dispatch(Event.START)
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val mediaButtonIntent = Intent(intent)
            commandScope.launch {
                symphony.radio.awaitRestoration()
                MediaButtonReceiver.handleIntent(
                    symphony.radio.session.mediaSession,
                    mediaButtonIntent,
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        commandScope.cancel()
        super.onDestroy()
        destroy(false)
    }

    companion object {
        val events = Eventer<Event>()
        var instance: RadioNotificationService? = null

        fun destroy(stop: Boolean = true) {
            instance?.let {
                instance = null
                if (stop) {
                    it.stopForeground(STOP_FOREGROUND_REMOVE)
                    it.stopSelf()
                }
                events.dispatch(Event.STOP)
            }
        }
    }
}
