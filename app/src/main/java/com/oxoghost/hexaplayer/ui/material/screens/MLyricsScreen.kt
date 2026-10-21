package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MLinearProgress
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.LyricsUiState
import com.oxoghost.hexaplayer.ui.state.PlaybackState

@Composable
fun MLyricsScreen(lyrics: LyricsUiState, playback: PlaybackState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val song = playback.song
    val listState = rememberLazyListState()
    LaunchedEffect(lyrics.currentLine) {
        if (lyrics.currentLine >= 0) listState.animateScrollToItem((lyrics.currentLine - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().background(s.surfaceContainerLow).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MCircleButton(Symbols.KeyboardArrowDown, "Close", actions::back, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
            if (song != null) {
                CoverImage(song.artModel, song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            if (lyrics.found) {
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).background(s.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Symbol(if (lyrics.synced) Symbols.Sync else Symbols.Lyrics, null, size = 18.dp, tint = s.onSecondaryContainer)
                    Text(if (lyrics.synced) "Synced" else "Plain", color = s.onSecondaryContainer, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!lyrics.found) {
                item {
                    Text(if (lyrics.loading) "Looking for lyrics..." else "No lyrics found for this song",
                        color = s.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(lyrics.lines) { i, line ->
                val current = i == lyrics.currentLine
                Text(
                    line.ifBlank { "♪" },
                    Modifier
                        .fillMaxWidth()
                        .then(if (lyrics.synced) Modifier.clickable { actions.seekToLine(i) } else Modifier),
                    color = if (current || !lyrics.synced) s.onSurface else s.onSurfaceVariant,
                    style = if (current) MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold)
                    else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (lyrics.synced) "Lyrics from LRCLIB · tap a line to jump" else "Lyrics from LRCLIB",
                    Modifier.weight(1f),
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (lyrics.synced) {
                    MIconButton(Symbols.Remove, "Lyrics earlier", { actions.shiftOffset(-500) }, size = 40.dp, iconSize = 20.dp)
                    Text("%+.1fs".format(lyrics.offsetMs / 1000f), color = s.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    MIconButton(Symbols.Add, "Lyrics later", { actions.shiftOffset(500) }, size = 40.dp, iconSize = 20.dp)
                }
            }
            Row(
                Modifier
                    .padding(vertical = 12.dp)
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(HexaShapes.pill)
                    .background(s.surfaceContainerHigh)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MIconButton(Symbols.SkipPrevious, "Previous", actions::previous, tint = s.onSurface, size = 48.dp, filled = true)
                MLinearProgress(playback.fraction, Modifier.weight(1f))
                MCircleButton(if (playback.isPlaying) Symbols.Pause else Symbols.PlayArrow, if (playback.isPlaying) "Pause" else "Play",
                    actions::togglePlayPause, container = s.primaryContainer, content = s.onPrimaryContainer, size = 56.dp,
                    shape = RoundedCornerShape(20.dp), filled = true)
                MIconButton(Symbols.SkipNext, "Next", actions::next, tint = s.onSurface, size = 48.dp, filled = true)
            }
        }
    }
}
