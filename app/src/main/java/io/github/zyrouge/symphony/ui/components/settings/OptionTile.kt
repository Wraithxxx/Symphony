package io.github.zyrouge.symphony.ui.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.ScaffoldDialog
import io.github.zyrouge.symphony.ui.components.SymphonyChoiceRow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsOptionTile(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    value: T,
    values: Map<T, String>,
    captions: Map<T, String>? = null,
    enabled: Boolean = true,
    onChange: (T) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var isOpen by remember { mutableStateOf(false) }

    Card(
        enabled = enabled,
        colors = SettingsTileDefaults.cardColors(),
        onClick = {
            isOpen = !isOpen
        }
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(enabled = enabled),
            leadingContent = { icon() },
            headlineContent = { title() },
            supportingContent = { Text(values[value]!!) },
        )
    }

    if (isOpen) {
        ScaffoldDialog(
            onDismissRequest = {
                isOpen = false
            },
            title = title,
            content = {
                val scrollState = rememberScrollState()
                var initialScroll by remember {
                    mutableStateOf(false)
                }

                Column(
                    modifier = Modifier
                        .padding(0.dp, 8.dp)
                        .verticalScroll(scrollState)
                ) {
                    values.map { entry ->
                        val caption = captions?.get(entry.key)
                        val active = value == entry.key

                        SymphonyChoiceRow(
                            selected = active,
                            title = entry.value,
                            subtitle = caption,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    if (active && !initialScroll) {
                                        val offset = coordinates.positionInParent()
                                        coroutineScope.launch {
                                            scrollState.scrollTo(offset.y.toInt())
                                        }
                                        initialScroll = true
                                    }
                                },
                            onClick = {
                                onChange(entry.key)
                                isOpen = false
                            },
                        )
                    }
                }
            },
        )
    }
}
