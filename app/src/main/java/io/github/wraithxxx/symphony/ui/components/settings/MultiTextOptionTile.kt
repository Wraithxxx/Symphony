package io.github.wraithxxx.symphony.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.wraithxxx.symphony.ui.components.ScaffoldDialog
import io.github.wraithxxx.symphony.ui.helpers.ViewContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsMultiTextOptionTile(
    context: ViewContext,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    values: List<String>,
    allowDuplicates: Boolean = false,
    onReset: (() -> Unit)? = null,
    onChange: (List<String>) -> Unit,
) {
    var isOpen by remember { mutableStateOf(false) }

    Card(
        colors = SettingsTileDefaults.cardColors(),
        onClick = { isOpen = !isOpen }
    ) {
        ListItem(
            colors = SettingsTileDefaults.listItemColors(),
            leadingContent = { icon() },
            headlineContent = { title() },
            supportingContent = {
                Text(context.symphony.t.XItems(values.size.toString()))
            },
        )
    }

    if (isOpen) {
        var showAddDialog by remember { mutableStateOf(false) }
        val nValues = remember {
            mutableStateListOf(*values.toTypedArray())
        }
        val modified by remember(values, nValues) {
            derivedStateOf { values != nValues }
        }

        when {
            !showAddDialog -> ScaffoldDialog(
                onDismissRequest = {
                    if (!modified) {
                        isOpen = false
                    }
                },
                title = title,
                content = {
                    Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                8.dp,
                                Alignment.CenterHorizontally,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            nValues.forEachIndexed { i, x ->
                                InputChip(
                                    selected = true,
                                    onClick = { nValues.removeAt(i) },
                                    label = { Text(x) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Filled.Close,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            showAddDialog = true
                        }
                    ) {
                        Text(context.symphony.t.AddItem)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    onReset?.let {
                        TextButton(
                            onClick = {
                                it()
                                isOpen = false
                            }
                        ) {
                            Text(context.symphony.t.Reset)
                        }
                    }
                    TextButton(
                        onClick = {
                            isOpen = false
                        }
                    ) {
                        Text(context.symphony.t.Cancel)
                    }
                    TextButton(
                        enabled = modified,
                        onClick = {
                            onChange(nValues)
                            isOpen = false
                        }
                    ) {
                        Text(context.symphony.t.Done)
                    }
                },
            )

            else -> {
                var input by remember { mutableStateOf("") }

                ScaffoldDialog(
                    onDismissRequest = {
                        showAddDialog = false
                    },
                    title = title,
                    content = {
                        Box(
                            modifier = Modifier
                                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    unfocusedIndicatorColor = DividerDefaults.color,
                                ),
                                value = input,
                                onValueChange = {
                                    input = it
                                }
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                showAddDialog = false
                            }
                        ) {
                            Text(context.symphony.t.Cancel)
                        }
                        TextButton(
                            enabled = input.isNotEmpty(),
                            onClick = {
                                if (
                                    input.isNotEmpty() &&
                                    (allowDuplicates || !nValues.contains(input))
                                ) {
                                    nValues.add(input)
                                }
                                showAddDialog = false
                            }
                        ) {
                            Text(context.symphony.t.Done)
                        }
                    },
                )
            }
        }
    }
}
