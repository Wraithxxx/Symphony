package io.github.wraithxxx.symphony

import android.app.Application

class SymphonyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LauncherIconManager.synchronize(this)
    }

    val symphony: Symphony by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Symphony(this)
    }
}
