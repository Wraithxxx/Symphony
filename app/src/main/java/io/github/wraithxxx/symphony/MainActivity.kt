package io.github.wraithxxx.symphony

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.github.wraithxxx.symphony.ui.view.BaseView
import io.github.wraithxxx.symphony.utils.Logger

open class MainActivity : ComponentActivity() {
    private var gSymphony: Symphony? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val ignition: ActivityIgnition by viewModels()
        if (savedInstanceState == null) {
            splashScreen.setKeepOnScreenCondition { !ignition.ready.value }
        }

        Thread.setDefaultUncaughtExceptionHandler { _, err ->
            Logger.error("MainActivity", "uncaught exception", err)
            ErrorActivity.start(this, err)
            finish()
        }

        val symphony = (application as SymphonyApplication).symphony
        symphony.permission.handle(this)
        gSymphony = symphony
        symphony.emitActivityReady()
        attachHandlers()

        enableEdgeToEdge()
        setContent {
            LaunchedEffect(LocalContext.current) {
                ignition.emitReady()
            }
            BaseView(symphony = symphony, activity = this)
        }
    }

    override fun onPause() {
        super.onPause()
        gSymphony?.emitActivityPause()
    }

    override fun onResume() {
        super.onResume()
        gSymphony?.emitActivityResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        gSymphony?.emitActivityDestroy()
    }

    private fun attachHandlers() {
        gSymphony?.closeApp = {
            finish()
        }
    }
}

class WraithLauncherActivity : MainActivity()

class OriginalLauncherActivity : MainActivity()
