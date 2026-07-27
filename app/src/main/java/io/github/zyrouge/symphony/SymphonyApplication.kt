package io.github.zyrouge.symphony

import android.app.Application

class SymphonyApplication : Application() {
    val symphony: Symphony by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Symphony(this)
    }
}
