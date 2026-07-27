package io.github.zyrouge.symphony.services.radio

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RadioMediaButtonCommandResolverTest {
    @Test
    fun `previous and skip-backward keys resolve to previous`() {
        assertEquals(
            RadioMediaButtonCommandResolver.Command.Previous,
            resolve(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
        )
        assertEquals(
            RadioMediaButtonCommandResolver.Command.Previous,
            resolve(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD),
        )
    }

    @Test
    fun `next and skip-forward keys resolve to next`() {
        assertEquals(
            RadioMediaButtonCommandResolver.Command.Next,
            resolve(KeyEvent.KEYCODE_MEDIA_NEXT),
        )
        assertEquals(
            RadioMediaButtonCommandResolver.Command.Next,
            resolve(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD),
        )
    }

    @Test
    fun `key-up and repeated key-down do not fire a second command`() {
        assertNull(
            RadioMediaButtonCommandResolver.resolve(
                action = KeyEvent.ACTION_UP,
                keyCode = KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                repeatCount = 0,
            )
        )
        assertNull(
            RadioMediaButtonCommandResolver.resolve(
                action = KeyEvent.ACTION_DOWN,
                keyCode = KeyEvent.KEYCODE_MEDIA_NEXT,
                repeatCount = 1,
            )
        )
    }

    @Test
    fun `rewind and fast-forward remain seek commands`() {
        assertNull(resolve(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertNull(resolve(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertTrue(
            !RadioMediaButtonCommandResolver.isQueueSkipKey(KeyEvent.KEYCODE_MEDIA_REWIND)
        )
        assertTrue(
            !RadioMediaButtonCommandResolver.isQueueSkipKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        )
    }

    private fun resolve(keyCode: Int) = RadioMediaButtonCommandResolver.resolve(
        action = KeyEvent.ACTION_DOWN,
        keyCode = keyCode,
        repeatCount = 0,
    )
}
