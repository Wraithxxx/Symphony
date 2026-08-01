package io.github.wraithxxx.symphony

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.wraithxxx.symphony.services.Settings

object LauncherIconManager {
    fun synchronize(context: Context) {
        setComponents(context, usesOriginalIcon(context))
    }

    fun switchIcon(activity: Activity, useOriginal: Boolean) {
        setComponents(activity, useOriginal)
        activity.startActivity(
            Intent(activity, launcherClass(useOriginal)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        activity.finishAffinity()
    }

    fun createLaunchIntent(context: Context) =
        Intent(context, launcherClass(usesOriginalIcon(context))).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

    private fun usesOriginalIcon(context: Context) = context
        .getSharedPreferences(Settings.PREFERENCES_NAME, Context.MODE_PRIVATE)
        .getBoolean(Settings.USE_ORIGINAL_APP_ICON_KEY, false)

    private fun launcherClass(useOriginal: Boolean) = when {
        useOriginal -> OriginalLauncherActivity::class.java
        else -> WraithLauncherActivity::class.java
    }

    private fun setComponents(context: Context, useOriginal: Boolean) {
        val packageManager = context.packageManager
        setComponentEnabled(
            packageManager,
            ComponentName(context, launcherClass(useOriginal)),
            true,
        )
        setComponentEnabled(
            packageManager,
            ComponentName(context, launcherClass(!useOriginal)),
            false,
        )
    }

    private fun setComponentEnabled(
        packageManager: PackageManager,
        componentName: ComponentName,
        enabled: Boolean,
    ) {
        val desiredState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (packageManager.getComponentEnabledSetting(componentName) == desiredState) return
        packageManager.setComponentEnabledSetting(
            componentName,
            desiredState,
            PackageManager.DONT_KILL_APP,
        )
    }
}
