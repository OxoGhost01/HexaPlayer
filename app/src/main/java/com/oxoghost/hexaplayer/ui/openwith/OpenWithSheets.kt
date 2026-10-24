package com.oxoghost.hexaplayer.ui.openwith

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.formatSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MWavySeek
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyAction
import com.oxoghost.hexaplayer.ui.terminal.components.TSeekBar
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Swallows taps so they don't reach the scrim behind the sheet. */
@Composable
private fun Modifier.blockTaps(): Modifier = clickable(remember { MutableInteractionSource() }, indication = null) {}

private fun OpenWithFile.detail(): String =
    listOf(format, bitrateKbps.takeIf { it > 0 }?.let { "$it kbps" }, formatSize(sizeBytes).ifEmpty { null })
        .filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
fun TOpenWithSheet(state: OpenWithState, actions: OpenWithActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    val file = state.file
    Column(
        Modifier
            .fillMaxWidth()
            .blockTaps()
            .background(c.background)
            .drawBehind { drawLine(c.accent, Offset(0f, 0f), Offset(size.width, 0f), 2.dp.toPx()) }
            .navigationBarsPadding()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText("hexa", color = c.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
            TText("open", Modifier.weight(1f), color = c.dim)
            if (file != null && !file.inLibrary) {
                TText("not in library", Modifier.border(1.dp, c.warn).padding(horizontal = 8.dp, vertical = 2.dp), color = c.warn)
            }
            Box(Modifier.clickable(onClickLabel = "Close", onClick = actions::close).padding(8.dp)) { TText("[x]", color = c.dim) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PixelCover(null, file?.name, 8, Modifier.size(96.dp), pixelCovers)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TText(file?.title ?: file?.name ?: "...", style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                if (file?.title != null) TText(file.artist ?: file.name, color = c.dim)
                TText(file?.folder.orEmpty(), color = c.dim, maxLines = 2)
                TText(file?.detail().orEmpty(), color = c.dim)
                if (file != null && file.title == null) TText("no tags found", color = c.dim)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(formatDuration(state.positionMs))
            TSeekBar(state.fraction, actions::seekTo, Modifier.weight(1f))
            TText(formatDuration(state.durationMs), color = c.dim)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TButton("-10s", { actions.seekBy(-10_000) }, Modifier.weight(1f), large = true)
            TButton(if (state.isPlaying) "‖ pause" else "▶︎ play", actions::togglePlay, Modifier.weight(2f), filled = true, large = true)
            TButton("+10s", { actions.seekBy(10_000) }, Modifier.weight(1f), large = true)
        }
        TText("your queue is untouched. it resumes when you close this.", color = c.dim, maxLines = 2)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (file?.inLibrary != true || state.copied) {
                TKeyAction(
                    "c",
                    when {
                        state.copied -> "copied into the library folder"
                        state.copying -> "copying..."
                        else -> "copy into library folder"
                    },
                    actions::copyToLibrary,
                    Modifier.border(1.dp, c.border),
                    labelColor = if (state.copied) c.ok else c.foreground,
                )
            }
            TKeyAction("q", "add to current queue", actions::addToQueue, Modifier.border(1.dp, c.border))
            TKeyAction("o", "open in full player", actions::openFullPlayer, Modifier.border(1.dp, c.border))
        }
    }
}

@Composable
fun MOpenWithSheet(state: OpenWithState, actions: OpenWithActions) {
    val s = MaterialTheme.colorScheme
    val file = state.file
    Column(
        Modifier
            .navigationBarsPadding()
            .padding(16.dp)
            .fillMaxWidth()
            .blockTaps()
            .clip(RoundedCornerShape(32.dp))
            .background(s.surfaceContainerHigh)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Symbol(Symbols.Hexagon, null, size = 28.dp, filled = true, tint = s.primary)
            Text("HexaPlayer", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            MIconButton(Symbols.Close, "Close", actions::close, tint = s.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(s.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Symbol(Symbols.Album, null, size = 48.dp, tint = s.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(file?.title ?: file?.name ?: "", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(file?.artist, file?.detail()?.ifEmpty { null }, if (file != null && file.title == null) "no tags" else null).joinToString(" · "),
                    color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                )
                if (file != null && !file.inLibrary) {
                    Text("Not in your library", Modifier.border(1.dp, s.outline, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        MWavySeek(state.fraction, actions::seekTo, state.isPlaying)
        Row {
            Text(formatDuration(state.positionMs), Modifier.weight(1f), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(formatDuration(state.durationMs), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            MCircleButton(Symbols.Replay10, "Back 10 seconds", { actions.seekBy(-10_000) }, container = s.surfaceContainerHighest, content = s.onSurface)
            MCircleButton(
                if (state.isPlaying) Symbols.Pause else Symbols.PlayArrow, if (state.isPlaying) "Pause" else "Play", actions::togglePlay,
                container = s.primaryContainer, content = s.onPrimaryContainer, size = 88.dp, iconSize = 36.dp,
                shape = RoundedCornerShape(28.dp), filled = true,
            )
            MCircleButton(Symbols.Forward10, "Forward 10 seconds", { actions.seekBy(10_000) }, container = s.surfaceContainerHighest, content = s.onSurface)
        }
        Text("Your queue stays untouched and resumes after this.", Modifier.fillMaxWidth(), color = s.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (file?.inLibrary != true || state.copied) {
                MPillButton(
                    when {
                        state.copied -> "Added"
                        state.copying -> "Adding..."
                        else -> "Add to library"
                    },
                    actions::copyToLibrary, Modifier.weight(1f), style = MButtonStyle.Tonal, icon = Symbols.LibraryAdd,
                )
            } else {
                MPillButton("Add to queue", actions::addToQueue, Modifier.weight(1f), style = MButtonStyle.Tonal, icon = Symbols.QueueMusic)
            }
            MPillButton("Open app", actions::openFullPlayer, Modifier.weight(1f), style = MButtonStyle.Outlined, icon = Symbols.OpenInFull)
        }
        if (file?.inLibrary != true || state.copied) {
            MPillButton("Add to current queue", actions::addToQueue, Modifier.fillMaxWidth(), style = MButtonStyle.Text, icon = Symbols.QueueMusic)
        }
    }
}
