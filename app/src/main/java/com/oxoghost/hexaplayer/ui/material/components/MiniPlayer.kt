package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.motion.Motion
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

/** What the mini player shows for one song; the old one keeps its own cover while it slides out. */
private class MiniContent(val key: Any?, val title: String, val artist: String, val cover: @Composable () -> Unit)

/**
 * Floating mini player: 52 dp cover, title and artist, play/pause in a circular progress ring,
 * next. Tapping the left part opens Now playing.
 */
@Composable
fun MMiniPlayer(
    title: String,
    artist: String,
    progress: Float,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    /** Changes with the song, so the cover and text slide to the new one. */
    key: Any? = title,
    cover: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.miniPlayer.dp)
            .clip(HexaShapes.miniPlayer)
            .background(s.surfaceContainerHigh)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClickLabel = "Open now playing", onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedContent(
                MiniContent(key, title, artist, cover),
                Modifier.weight(1f),
                contentKey = { it.key },
                transitionSpec = {
                    (slideInVertically(tween(Motion.MEDIUM, easing = Motion.Emphasized)) { it / 2 } + fadeIn(tween(Motion.SHORT)))
                        .togetherWith(slideOutVertically(tween(Motion.SHORT, easing = Motion.EmphasizedAccelerate)) { -it / 2 } + fadeOut(tween(Motion.SHORT / 2)))
                },
                label = "mini",
            ) { shown ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))) { shown.cover() }
                    Column(Modifier.weight(1f)) {
                        Text(shown.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(shown.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(52.dp)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(s.outlineVariant, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    s.primary, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            MCircleButton(
                if (isPlaying) Symbols.Pause else Symbols.PlayArrow,
                if (isPlaying) "Pause" else "Play",
                onPlayPause,
                container = s.primaryContainer,
                content = s.onPrimaryContainer,
                size = 40.dp,
                filled = true,
            )
        }
        MIconButton(Symbols.SkipNext, "Next", onNext, tint = s.onSurface, filled = true)
    }
}
