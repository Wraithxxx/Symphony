package io.github.wraithxxx.symphony.ui.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.wraithxxx.symphony.ui.components.KeepScreenAwake
import io.github.wraithxxx.symphony.ui.components.LyricsText
import io.github.wraithxxx.symphony.ui.components.SymphonyModalSheet
import io.github.wraithxxx.symphony.ui.components.SymphonySheetHeader
import io.github.wraithxxx.symphony.ui.components.TimedContentTextStyle
import io.github.wraithxxx.symphony.ui.helpers.ViewContext
import io.github.wraithxxx.symphony.ui.view.nowPlaying.NowPlayingSeekBar
import io.github.wraithxxx.symphony.ui.view.nowPlaying.NowPlayingTraditionalControls
import io.github.wraithxxx.symphony.ui.view.nowPlaying.defaultHorizontalPadding

@Composable
fun LyricsSheet(
    context: ViewContext,
    data: NowPlayingData,
    onDismissRequest: () -> Unit,
) {
    val keepScreenAwake by context.symphony.settings.lyricsKeepScreenAwake.flow.collectAsState()
    val configuration = LocalConfiguration.current

    if (keepScreenAwake) {
        KeepScreenAwake()
    }

    SymphonyModalSheet(
        expanded = true,
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height((configuration.screenHeightDp * 0.9f).dp)
                .navigationBarsPadding(),
        ) {
            SymphonySheetHeader(
                title = context.symphony.t.Lyrics,
                subtitle = data.song.title,
                leadingContent = {
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, "Close lyrics")
                    }
                },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Box(modifier = Modifier.weight(1f)) {
                LyricsText(
                    context,
                    style = TimedContentTextStyle(
                        highlighted = MaterialTheme.typography.titleMedium.copy(
                            color = LocalContentColor.current,
                        ),
                        active = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        ),
                        inactive = MaterialTheme.typography.titleMedium.copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f),
                        ),
                        spacing = 8.dp,
                    ),
                    padding = PaddingValues(
                        horizontal = defaultHorizontalPadding,
                        vertical = 12.dp,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(defaultHorizontalPadding))
            NowPlayingSeekBar(context)
            Spacer(modifier = Modifier.height(defaultHorizontalPadding))
            NowPlayingTraditionalControls(context, data = data)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
