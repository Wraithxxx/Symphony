package io.github.wraithxxx.symphony.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.wraithxxx.symphony.ui.helpers.ViewContext

@Composable
fun IntroductoryDialog(
    context: ViewContext,
    onDismissRequest: () -> Unit,
) {
    val checkForUpdates by context.symphony.settings.checkForUpdates.flow.collectAsState()
    val showUpdateToast by context.symphony.settings.showUpdateToast.flow.collectAsState()

    ScaffoldDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text("\uD83D\uDC4B " + context.symphony.t.HelloThere)
        },
        content = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    context.symphony.t.IntroductoryMessage.trim(),
                    modifier = Modifier.padding(16.dp, 12.dp),
                )
                Box(modifier = Modifier.height(8.dp))
                SymphonyToggleRow(
                    title = context.symphony.t.CheckForUpdates,
                    value = checkForUpdates,
                    onChange = { value ->
                        context.symphony.settings.checkForUpdates.setValue(value)
                    }
                )
                Box(modifier = Modifier.height(8.dp))
                SymphonyToggleRow(
                    title = context.symphony.t.ShowUpdateToast,
                    value = showUpdateToast,
                    onChange = { value ->
                        context.symphony.settings.showUpdateToast.setValue(value)
                    },
                    enabled = checkForUpdates,
                )
                Box(modifier = Modifier.height(8.dp))
            }
        }
    )
}

@Composable
private fun SymphonyToggleRow(
    title: String,
    value: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Box(modifier = Modifier.padding(horizontal = 4.dp)) {
        io.github.wraithxxx.symphony.ui.components.SymphonyToggleRow(
            checked = value,
            title = title,
            onCheckedChange = onChange,
            enabled = enabled,
        )
    }
}
