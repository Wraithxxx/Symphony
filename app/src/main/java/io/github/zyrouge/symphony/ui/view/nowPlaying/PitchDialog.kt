package io.github.zyrouge.symphony.ui.view.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.ScaffoldDialog
import io.github.zyrouge.symphony.ui.components.Slider
import io.github.zyrouge.symphony.ui.components.SymphonyChoiceChip
import io.github.zyrouge.symphony.ui.components.SymphonyToggleRow
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import kotlin.math.roundToInt

@Composable
fun NowPlayingPitchDialog(
    context: ViewContext,
    currentPitch: Float,
    persistedPitch: Float,
    onDismissRequest: () -> Unit,
) {
    val allowedPitches = listOf(0.5f, 1f, 1.5f, 2f, 3f)
    val allowedPitchRange = allowedPitches.first()..allowedPitches.last()
    var persistent by remember {
        mutableStateOf(currentPitch == persistedPitch)
    }

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(context.symphony.t.Pitch)
        },
        content = {
            Column(modifier = Modifier.padding(0.dp, 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        4.dp,
                        Alignment.CenterHorizontally
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    allowedPitches.forEach { pitch ->
                        val onClick = {
                            context.symphony.radio.setPitch(pitch, persistent)
                        }
                        SymphonyChoiceChip(
                            text = "x$pitch",
                            selected = currentPitch == pitch,
                            onClick = onClick,
                        )
                    }
                }
                Slider(
                    value = currentPitch,
                    onChange = { value ->
                        val pitch = (value * 10).roundToInt().toFloat() / 10
                        context.symphony.radio.setPitch(pitch, persistent)
                    },
                    range = allowedPitchRange,
                    label = { value ->
                        Text("x$value")
                    },
                    modifier = Modifier
                        .padding(top = 24.dp, bottom = 8.dp)
                )

                SymphonyToggleRow(
                    checked = persistent,
                    title = context.symphony.t.PersistUntilQueueEnd,
                    onCheckedChange = {
                        persistent = it
                        context.symphony.radio.setPitch(currentPitch, persistent)
                    },
                )
            }
        },
        actions = {
            TextButton(
                onClick = {
                    context.symphony.radio.setPitch(1f, persistent)
                    onDismissRequest()
                }
            ) {
                Text(context.symphony.t.Reset)
            }
            TextButton(
                onClick = onDismissRequest
            ) {
                Text(context.symphony.t.Done)
            }
        },
    )
}
