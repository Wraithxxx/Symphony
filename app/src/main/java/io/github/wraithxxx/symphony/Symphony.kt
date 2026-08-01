package io.github.wraithxxx.symphony

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.wraithxxx.symphony.services.AppMeta
import io.github.wraithxxx.symphony.services.Permissions
import io.github.wraithxxx.symphony.services.Settings
import io.github.wraithxxx.symphony.services.database.Database
import io.github.wraithxxx.symphony.services.groove.Groove
import io.github.wraithxxx.symphony.services.i18n.Translator
import io.github.wraithxxx.symphony.services.radio.Radio
import io.github.wraithxxx.symphony.ui.UiMessageBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class Symphony(application: Application) : AndroidViewModel(application), Symphony.Hooks {
    interface Hooks {
        fun onSymphonyReady() {}
        fun onSymphonyDestroy() {}
        fun onSymphonyActivityReady() {}
        fun onSymphonyActivityResume() {}
        fun onSymphonyActivityPause() {}
        fun onSymphonyActivityDestroy() {}
    }

    val permission = Permissions(this)
    val settings = Settings(this)
    val database = Database(this)
    val groove = Groove(this)
    val radio = Radio(this)
    val translator = Translator(this)
    val uiMessages = UiMessageBus()

    var t by mutableStateOf(translator.getCurrentTranslation())

    val applicationContext get() = getApplication<Application>().applicationContext
    var closeApp: (() -> Unit)? = null
    private var isReady = false
    private var hooks = listOf(this, radio, groove)

    internal fun emitReady() {
        if (isReady) {
            return
        }
        isReady = true
        notifyHooks { onSymphonyReady() }
    }

    internal fun emitDestroy() {
        notifyHooks { onSymphonyDestroy() }
    }

    internal fun emitActivityReady() {
        emitReady()
        notifyHooks { onSymphonyActivityReady() }
    }

    internal fun emitActivityPause() {
        notifyHooks { onSymphonyActivityPause() }
    }

    internal fun emitActivityResume() {
        notifyHooks { onSymphonyActivityResume() }
    }

    internal fun emitActivityDestroy() {
        notifyHooks { onSymphonyActivityDestroy() }
    }

    override fun onSymphonyReady() {
        checkVersion()
        viewModelScope.launch {
            translator.onChange { nTranslation ->
                t = nTranslation
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        emitDestroy()
    }

    private fun notifyHooks(fn: Hooks.() -> Unit) {
        hooks.forEach { fn.invoke(it) }
    }

    private fun checkVersion() {
        if (!settings.checkForUpdates.value) {
            return
        }
        viewModelScope.launch {
            val latestVersion = withContext(Dispatchers.IO) {
                AppMeta.fetchLatestVersion()
            }
            if (latestVersion == null) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (settings.showUpdateToast.value && AppMeta.version != latestVersion) {
                    uiMessages.show(t.NewVersionAvailableX(latestVersion))
                }
            }
        }
    }
}
