package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.formatSize
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.common.rememberCountdown
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.RepeatState
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSeekBar
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** "album · Showbiz" -> "Showbiz". */
internal fun sourceName(source: String?): String? = source?.substringAfter(" · ", source)

@Composable
fun TPlayerScreen(playback: PlaybackState, actions: HexaActions, pixelCovers: Boolean, onAlbum: () -> Unit) {
    val c = TerminalTheme.colors
    val song = playback.song
    val sleepLeft = rememberCountdown(playback.sleepEndsAt)
    TScreen(
        header = {
            THeader(
                path = "from",
                current = sourceName(playback.source)?.let { "$it/" },
                onBack = actions::back,
                action = if (playback.queueSize > 0) "[${playback.queueIndex + 1}/${playback.queueSize}]" else null,
                actionDescription = "Open queue",
                onAction = actions::openQueue,
            )
        },
        status = {
            val parts = buildList {
                if (playback.gapless) add("gapless")
                if (playback.shuffle) add("shuffle")
            }
            TStatusLine(
                TMode.PLAY,
                parts.joinToString(" · ").ifEmpty { "playing" },
                position = sleepLeft?.let { "sleep ${formatDuration(it)}" },
            )
        },
    ) {
        if (song == null) {
            TText("nothing is playing", Modifier.padding(12.dp), color = c.dim)
            return@TScreen
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                PixelCover(
                    song.artModel, song.albumKey, 26,
                    Modifier
                        .fillMaxWidth(0.74f)
                        .aspectRatio(1f)
                        .horizontalSwipe(onLeft = actions::next, onRight = actions::previous)
                        .pointerInput(Unit) {
                            var total = 0f
                            detectVerticalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > 120.dp.toPx()) actions.back() },
                            ) { _, d -> total += d }
                        },
                    pixelCovers,
                    contentDescription = "Cover of ${song.album}",
                )
            }
            Spacer(Modifier.height(16.dp))
            MetaRow("title") {
                TText(song.title, Modifier.clickable(onClickLabel = "Song actions") { actions.songMenu(song) },
                    style = TerminalTheme.type.title)
            }
            MetaRow("artist") {
                TText(song.artist, Modifier.clickable(onClickLabel = "Open artist") { actions.openArtist(song.artist) }, color = c.accent2)
            }
            MetaRow("album") {
                TText(song.album, Modifier.clickable(onClickLabel = "Open album", onClick = onAlbum))
            }
            MetaRow("file") {
                val file = listOf(song.format, formatSize(song.size)).filter { it.isNotEmpty() }.joinToString(" · ")
                TText(file.ifEmpty { "?" }, color = c.dim)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TText(formatDuration(playback.positionMs))
                TSeekBar(playback.fraction, { actions.seekTo((it * playback.durationMs).toLong()) }, Modifier.weight(1f))
                TText(formatDuration(playback.durationMs), color = c.dim)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Key("⇄", Modifier.weight(0.7f), color = if (playback.shuffle) c.accent else c.dim,
                    border = false, description = if (playback.shuffle) "Shuffle on" else "Shuffle off", onClick = actions::toggleShuffle)
                Key("|◀︎", Modifier.weight(1f), description = "Previous", onClick = actions::previous)
                Box(
                    Modifier
                        .weight(2f)
                        .height(60.dp)
                        .background(c.accent)
                        .clickable(role = Role.Button, onClick = actions::togglePlayPause)
                        .semantics { contentDescription = if (playback.isPlaying) "Pause" else "Play" },
                    contentAlignment = Alignment.Center,
                ) {
                    TText(
                        if (playback.isPlaying) "‖ pause" else "▶︎ play",
                        color = c.background,
                        style = TerminalTheme.type.title,
                    )
                }
                Key("▶︎|", Modifier.weight(1f), description = "Next", onClick = actions::next)
                val repeatLabel = when (playback.repeat) {
                    RepeatState.Off -> "↻ off"
                    RepeatState.All -> "↻ all"
                    RepeatState.One -> "↻ one"
                }
                Key(repeatLabel, Modifier.weight(1.1f), color = if (playback.repeat == RepeatState.Off) c.dim else c.accent,
                    border = false, description = "Repeat ${playback.repeat.name.lowercase()}", onClick = actions::cycleRepeat)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolKey("l", "yrics", Modifier.weight(1f), onClick = actions::openLyrics)
                ToolKey("q", "ueue", Modifier.weight(1f), onClick = actions::openQueue)
                if (sleepLeft != null) {
                    ToolKey("", formatDuration(sleepLeft), Modifier.weight(1f), highlight = c.warn, onClick = actions::sleepTimer)
                } else {
                    ToolKey("s", "leep", Modifier.weight(1f), onClick = actions::sleepTimer)
                }
                ToolKey("e", "q", Modifier.weight(1f), onClick = actions::openEqualizer)
            }
        }
    }
}

@Composable
private fun MetaRow(key: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
        TText(key, Modifier.width(92.dp), color = TerminalTheme.colors.dim)
        value()
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier,
    description: String,
    color: Color = TerminalTheme.colors.foreground,
    border: Boolean = true,
    onClick: () -> Unit,
) {
    val c = TerminalTheme.colors
    Box(
        modifier
            .height(60.dp)
            .then(if (border) Modifier.border(1.dp, c.border) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TText(label, color = color)
    }
}

/** Tool key: the shortcut letter in accent then the rest of the label (`l`yrics). */
@Composable
private fun ToolKey(key: String, rest: String, modifier: Modifier, highlight: Color? = null, onClick: () -> Unit) {
    val c = TerminalTheme.colors
    Box(
        modifier
            .height(48.dp)
            .border(1.dp, highlight ?: c.border)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TText(buildAnnotatedString {
            withStyle(SpanStyle(color = highlight ?: c.accent, fontWeight = FontWeight.Bold)) { append(key) }
            withStyle(SpanStyle(color = highlight ?: c.foreground)) { append(rest) }
        })
    }
}
