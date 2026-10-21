package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.horizontalSwipe
import com.oxoghost.hexaplayer.ui.common.rememberCountdown
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MPlayerControls
import com.oxoghost.hexaplayer.ui.material.components.MPlayerToolbar
import com.oxoghost.hexaplayer.ui.material.components.MRepeat
import com.oxoghost.hexaplayer.ui.material.components.MWavySeek
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.RepeatState

@Composable
fun MPlayerScreen(playback: PlaybackState, actions: HexaActions, onAlbum: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val song = playback.song
    val sleepLeft = rememberCountdown(playback.sleepEndsAt)
    Column(
        Modifier
            .fillMaxSize()
            .background(s.surface)
            .systemBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MCircleButton(Symbols.KeyboardArrowDown, "Close", actions::back, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val source = playback.source
                if (source != null) {
                    Text("Playing from ${source.substringBefore(" · ", "")}".trim(), color = s.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(source.substringAfter(" · ", source), style = MaterialTheme.typography.titleSmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
            MCircleButton(Symbols.MoreVert, "Song actions", { song?.let(actions::songMenu) },
                container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
        }
        if (song == null) {
            Text("Nothing is playing", Modifier.padding(24.dp), color = s.onSurfaceVariant)
            return@Column
        }
        Box(
            Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .horizontalSwipe(onLeft = actions::next, onRight = actions::previous)
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 120.dp.toPx()) actions.back() }) { _, d -> total += d }
                }
        ) {
            CoverImage(song.artModel, song.albumKey, Modifier.fillMaxSize(), HexaShapes.coverPlayer, "Cover of ${song.album}")
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    song.artist,
                    Modifier.clickable(onClickLabel = "Open artist") { actions.openArtist(song.artist) },
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    song.album,
                    Modifier.clickable(onClickLabel = "Open album", onClick = onAlbum),
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (song.format.isNotEmpty()) {
                Text(
                    song.format.uppercase(),
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(s.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        MWavySeek(playback.fraction, { actions.seekTo((it * playback.durationMs).toLong()) }, playback.isPlaying, Modifier.padding(top = 12.dp))
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration(playback.positionMs), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            Text(formatDuration(playback.durationMs), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        MPlayerControls(
            isPlaying = playback.isPlaying,
            shuffle = playback.shuffle,
            repeat = when (playback.repeat) {
                RepeatState.Off -> MRepeat.Off
                RepeatState.All -> MRepeat.All
                RepeatState.One -> MRepeat.One
            },
            onShuffle = actions::toggleShuffle,
            onPrevious = actions::previous,
            onPlayPause = actions::togglePlayPause,
            onNext = actions::next,
            onRepeat = actions::cycleRepeat,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.weight(1f))
        MPlayerToolbar(
            onLyrics = actions::openLyrics,
            onSleep = actions::sleepTimer,
            onQueue = actions::openQueue,
            onEqualizer = actions::openEqualizer,
            sleepLabel = sleepLeft?.let { "${(it / 60_000).coerceAtLeast(1)}m" },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}
