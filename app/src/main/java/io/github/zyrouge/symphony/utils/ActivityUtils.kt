package io.github.zyrouge.symphony.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.zyrouge.symphony.Symphony

object ActivityUtils {
    fun startBrowserActivity(activity: Context, uri: Uri) {
        activity.startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
    }

    fun copyToClipboardAndNotify(symphony: Symphony, text: String) {
        val clipboardManager =
            symphony.applicationContext.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
        symphony.uiMessages.show(symphony.t.CopiedXToClipboard(text))
    }

    fun makePersistableReadableUri(context: Context, uri: Uri) {
        val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, read or write)
        } catch (_: SecurityException) {
            context.contentResolver.takePersistableUriPermission(uri, read)
        }
    }

}
