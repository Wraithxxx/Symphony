package io.github.wraithxxx.symphony.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.utils.ActivityUtils

@Composable
fun LongPressCopyableText(context: ViewContext, text: String) {
    Text(
        text,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = {
                ActivityUtils.copyToClipboardAndNotify(context.symphony, text)
            })
        }
    )
}
