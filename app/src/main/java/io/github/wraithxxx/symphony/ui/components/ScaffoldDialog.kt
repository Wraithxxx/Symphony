package io.github.wraithxxx.symphony.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Suppress("ConstPropertyName")
object ScaffoldDialogDefaults {
    const val PreferredMaxHeight = 0.8f
}

@Composable
fun ScaffoldDialog(
    title: @Composable () -> Unit,
    titleLeading: (@Composable () -> Unit)? = null,
    titleTrailing: (@Composable () -> Unit)? = null,
    topBar: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    contentHeight: Float? = null,
    removeActionsVerticalPadding: Boolean = false,
    onDismissRequest: () -> Unit,
) {
    val configuration = LocalConfiguration.current

    SymphonyModalSheet(
        expanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .run {
                    val maxHeight = (configuration.screenHeightDp * 0.9f).dp
                    when {
                        contentHeight != null -> height(maxHeight.times(contentHeight))
                        else -> requiredHeightIn(max = maxHeight)
                    }
                }
                .imePadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                titleLeading?.invoke()
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .weight(1f),
                ) {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        title()
                    }
                }
                titleTrailing?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            topBar?.invoke()
            Box(
                modifier = Modifier.run {
                    contentHeight?.let { weight(it) } ?: weight(1f, fill = false)
                }
            ) {
                content()
            }
            actions?.let {
                if (!removeActionsVerticalPadding) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    actions()
                }
                if (!removeActionsVerticalPadding) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
