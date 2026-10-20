package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/** Mini player line: title and a character progress bar, pause and next keys. */
@Composable
fun TMiniLine(
    title: String,
    artist: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TerminalTheme.colors
    Column(modifier.fillMaxWidth().background(c.panel)) {
        TDivider()
        Row(
            Modifier.fillMaxWidth().height(Metrics.miniPlayer.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(onClickLabel = "Open now playing", onClick = onOpen)
                    .padding(start = Metrics.screenPaddingH.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                TText(buildAnnotatedString {
                    withStyle(SpanStyle(color = c.accent)) { append("▶︎ ") }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(title) }
                    withStyle(SpanStyle(color = c.dim)) { append(" $artist") }
                })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                    TCharProgress(fraction, Modifier.weight(1f))
                    TText(
                        "${formatDuration(positionMs)}/${formatDuration(durationMs)}",
                        color = c.dim,
                        style = TerminalTheme.type.tiny,
                    )
                }
            }
            TKeyButton(if (isPlaying) "‖" else "▶︎", if (isPlaying) "Pause" else "Play", 44, onPlayPause)
            TKeyButton("▶︎|", "Next", 48, onNext)
        }
        TDivider()
    }
}

@Composable
internal fun TKeyButton(glyph: String, description: String, width: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width.dp, Metrics.touchMin.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TText(glyph)
    }
}
