package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.rememberCountdown
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MPlayerControls
import com.oxoghost.hexaplayer.ui.material.components.MPlayerToolbar
import com.oxoghost.hexaplayer.ui.material.components.MRepeat
import com.oxoghost.hexaplayer.ui.material.components.MWavySeek
import com.oxoghost.hexaplayer.ui.material.motion.Motion
import com.oxoghost.hexaplayer.ui.material.motion.rememberSwipeState
import com.oxoghost.hexaplayer.ui.material.motion.swipeGestures
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.RepeatState

@Composable
fun MPlayerScreen(playback: PlaybackState, actions: HexaActions, onAlbum: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val song = playback.song
    val sleepLeft = rememberCountdown(playback.sleepEndsAt)
    val swipe = rememberSwipeState()
    // A swipe already moved the cover out and back in, so the song change itself only swaps it.
    var bySwipe by remember { mutableStateOf(false) }
    LaunchedEffect(song?.id) { bySwipe = false }
    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Dragging down to close: the screen follows the finger and shrinks a little.
                val y = swipe.y.value.coerceAtLeast(0f)
                val f = (y / size.height).coerceIn(0f, 1f)
                translationY = y
                scaleX = 1f - f * 0.1f
                scaleY = 1f - f * 0.1f
                shape = RoundedCornerShape((f * 160).dp)
                clip = f > 0f
            }
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
        val current = song to playback.queueIndex
        Box(
            Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .swipeGestures(
                    swipe,
                    onLeft = { bySwipe = true; actions.next() },
                    onRight = { bySwipe = true; actions.previous() },
                    onDown = actions::back,
                )
                .graphicsLayer {
                    translationX = swipe.x.value
                    rotationZ = swipe.x.value / size.width * 6f
                }
        ) {
            AnimatedContent(
                current,
                contentKey = { it.first.id },
                transitionSpec = {
                    if (bySwipe) EnterTransition.None togetherWith ExitTransition.None
                    else {
                        val forward = targetState.second >= initialState.second
                        val dir = if (forward) 1 else -1
                        (slideInHorizontally(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it * dir } + fadeIn(tween(Motion.SHORT)))
                            .togetherWith(slideOutHorizontally(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { -it * dir / 2 } +
                                fadeOut(tween(Motion.SHORT)) + scaleOut(tween(Motion.MEDIUM), targetScale = 0.9f))
                    }
                },
                label = "cover",
            ) { (shown, _) ->
                CoverImage(shown.artModel, shown.albumKey, Modifier.fillMaxSize(), HexaShapes.coverPlayer, "Cover of ${shown.album}")
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                song,
                Modifier.weight(1f),
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(tween(Motion.MEDIUM, delayMillis = 60)) + slideInVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it / 4 })
                        .togetherWith(fadeOut(tween(Motion.SHORT / 2)))
                },
                label = "title",
            ) { shown ->
                Column {
                    Text(shown.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        shown.artist,
                        Modifier.clickable(onClickLabel = "Open artist") { actions.openArtist(shown.artist) },
                        color = s.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        shown.album,
                        Modifier.clickable(onClickLabel = "Open album", onClick = onAlbum),
                        color = s.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
