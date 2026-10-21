package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LyricsUiState
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.terminal.components.TDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSeekBar
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TLyricsScreen(lyrics: LyricsUiState, playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    val song = playback.song
    val listState = rememberLazyListState()
    LaunchedEffect(lyrics.currentLine) {
        if (lyrics.currentLine >= 0) listState.animateScrollToItem((lyrics.currentLine - 2).coerceAtLeast(0))
    }
    TScreen(
        header = {
            THeader(
                "lyrics", onBack = actions::back,
                action = when {
                    lyrics.loading -> "lrclib · ..."
                    !lyrics.found -> "lrclib · none"
                    lyrics.synced -> "lrclib · synced"
                    else -> "lrclib · plain"
                },
                actionColor = if (lyrics.synced) c.ok else c.dim,
                onAction = {},
            )
        },
        status = {
            TStatusLine(
                TMode.LRC,
                if (lyrics.synced) "tap a line to seek" else "plain lyrics",
                position = if (lyrics.found && lyrics.currentLine >= 0) "${lyrics.currentLine + 1}/${lyrics.lines.size}" else null,
            )
        },
    ) {
        if (song != null) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PixelCover(song.artModel, song.albumKey, 8, Modifier.size(44.dp), pixelCovers)
                Column(Modifier.weight(1f)) {
                    TText(song.title, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
                    TText(song.artist, color = c.dim)
                }
                if (lyrics.synced) {
                    OffsetKey("-", "Lyrics earlier") { actions.shiftOffset(-500) }
                    TText("offset %+.1fs".format(lyrics.offsetMs / 1000f), color = c.dim)
                    OffsetKey("+", "Lyrics later") { actions.shiftOffset(500) }
                }
            }
        }
        TDivider()
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(12.dp)) {
            if (!lyrics.found) {
                item { TText(if (lyrics.loading) "searching lrclib..." else "no lyrics found", color = c.dim) }
            }
            itemsIndexed(lyrics.lines) { i, line ->
                val current = i == lyrics.currentLine
                val past = lyrics.synced && i < lyrics.currentLine
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (current) c.selection else Color.Transparent)
                        .then(if (lyrics.synced) Modifier.clickable { actions.seekToLine(i) } else Modifier)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val ts = lyrics.timestamps?.getOrNull(i)
                    if (lyrics.synced && ts != null) {
                        val s = ts / 1000
                        TText("[%02d:%02d]".format(s / 60, s % 60), color = if (current) c.accent else c.dim)
                    }
                    TText(
                        line.ifBlank { "♪" },
                        Modifier.weight(1f),
                        color = if (past) c.dim else c.foreground,
                        style = if (current) TerminalTheme.type.title else TerminalTheme.type.body,
                        maxLines = 4,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
        TDivider()
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TText(formatDuration(playback.positionMs))
                TSeekBar(playback.fraction, { actions.seekTo((it * playback.durationMs).toLong()) }, Modifier.weight(1f))
                TText(formatDuration(playback.durationMs), color = c.dim)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ControlKey("|◀︎", "Previous", Modifier.weight(1f), onClick = actions::previous)
                ControlKey(if (playback.isPlaying) "‖ pause" else "▶︎ play", if (playback.isPlaying) "Pause" else "Play",
                    Modifier.weight(2f), filled = true, onClick = actions::togglePlayPause)
                ControlKey("▶︎|", "Next", Modifier.weight(1f), onClick = actions::next)
            }
        }
    }
}

@Composable
private fun OffsetKey(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp, 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { TText(label, color = TerminalTheme.colors.accent) }
}

@Composable
private fun ControlKey(label: String, description: String, modifier: Modifier, filled: Boolean = false, onClick: () -> Unit) {
    val c = TerminalTheme.colors
    Box(
        modifier
            .height(52.dp)
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(1.dp, c.border))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TText(label, color = if (filled) c.background else c.foreground,
            style = TerminalTheme.type.body.copy(fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal))
    }
}

