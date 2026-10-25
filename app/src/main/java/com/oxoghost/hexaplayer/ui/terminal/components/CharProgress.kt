package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * `━━━━━━━━━━` progress: elapsed in [color], rest in [track]. The split snaps to whole
 * character cells so it reads like terminal output.
 */
@Composable
fun TCharProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    style: TextStyle = TerminalTheme.type.tiny,
    color: Color = TerminalTheme.colors.accent,
    track: Color = TerminalTheme.colors.border,
    height: Dp = 16.dp,
) {
    val cell = rememberCellWidth(style)
    Canvas(modifier.fillMaxWidth().height(height).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
    }) {
        val cellPx = cell.toPx()
        val cells = floor(size.width / cellPx).toInt().coerceAtLeast(1)
        val split = (fraction.coerceIn(0f, 1f) * cells).roundToInt() * cellPx
        val y = size.height / 2
        val stroke = 2.dp.toPx()
        drawLine(track, Offset(split, y), Offset(cells * cellPx, y), stroke)
        if (split > 0) drawLine(color, Offset(0f, y), Offset(split, y), stroke)
    }
}

/** Seek bar: `━━━━━●──────`, draggable and tappable. Reports the new fraction. */
@Composable
fun TSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = TerminalTheme.colors.accent,
    track: Color = TerminalTheme.colors.border,
) {
    val seek by rememberUpdatedState(onSeek)
    val cell = rememberCellWidth()
    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) { detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    seek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                setProgress { seek(it.coerceIn(0f, 1f)); true }
            }
    ) {
        val cellPx = cell.toPx()
        val cells = floor(size.width / cellPx).toInt().coerceAtLeast(1)
        val split = (fraction.coerceIn(0f, 1f) * (cells - 1)).roundToInt() * cellPx
        val y = size.height / 2
        drawLine(track, Offset(split, y), Offset(cells * cellPx, y), 1.dp.toPx())
        if (split > 0) drawLine(color, Offset(0f, y), Offset(split, y), 2.dp.toPx())
        drawCircle(color, cellPx * 0.42f, Offset(split + cellPx / 2, y))
    }
}

/** `█████░░░░░` bar for downloads, updates and the sleep timer. Fills the available width. */
@Composable
fun TBlockBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = TerminalTheme.colors.accent,
    track: Color = TerminalTheme.colors.border,
    style: TextStyle = TerminalTheme.type.body,
) {
    val cell = rememberCellWidth(style)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cells = (maxWidth / cell).toInt().coerceAtLeast(1)
        val full = (fraction.coerceIn(0f, 1f) * cells).roundToInt()
        TText(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                append("█".repeat(full))
                pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = track))
                append("░".repeat(cells - full))
                pop()
            },
            style = style,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    }
}

/** `░░███░░░░░` with the block bouncing back and forth, for work with no known progress. */
@Composable
fun TBlockBarIndeterminate(
    modifier: Modifier = Modifier,
    color: Color = TerminalTheme.colors.accent,
    track: Color = TerminalTheme.colors.border,
    style: TextStyle = TerminalTheme.type.body,
) {
    val cell = rememberCellWidth(style)
    val t by rememberInfiniteTransition(label = "bounce").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "block",
    )
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cells = (maxWidth / cell).toInt().coerceAtLeast(1)
        val block = (cells / 5).coerceIn(1, cells)
        val start = ((cells - block) * t).roundToInt()
        TText(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = track))
                append("░".repeat(start))
                pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = color))
                append("█".repeat(block))
                pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = track))
                append("░".repeat(cells - start - block))
                pop()
            },
            style = style,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
        )
    }
}

/** `|/-\` spinner, one character wide. */
@Composable
fun TSpinner(modifier: Modifier = Modifier, color: Color = TerminalTheme.colors.accent) {
    val frame by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing)),
        label = "frame",
    )
    TText("|/-\\"[frame.toInt().coerceIn(0, 3)].toString(), modifier, color = color)
}
