package io.github.zyrouge.symphony.services.radio

import android.view.KeyEvent

internal object RadioMediaButtonCommandResolver {
    enum class Command {
        Previous,
        Next,
    }

    fun isQueueSkipKey(keyCode: Int) = keyCode in setOf(
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
    )

    fun resolve(
        action: Int,
        keyCode: Int,
        repeatCount: Int,
    ): Command? {
        if (action != KeyEvent.ACTION_DOWN || repeatCount != 0) {
            return null
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> Command.Previous

            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> Command.Next

            else -> null
        }
    }
}
