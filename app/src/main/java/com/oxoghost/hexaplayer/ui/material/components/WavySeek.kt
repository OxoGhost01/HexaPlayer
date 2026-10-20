package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Now playing seek bar: wavy active track (amplitude 4 dp, wavelength 30 dp, 4 dp stroke),
 * a 5 x 24 dp thumb, flat inactive track and a stop dot. The wave moves only while [playing].
 */
@Composable
fun MWavySeek(
    fraction: Float,
    onSeek: (Float) -> Unit,
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    val seek by rememberUpdatedState(onSeek)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase = (phase + (now - last) / 1e9f * 2f * PI.toFloat()) % (2f * PI.toFloat())
                last = now
            }
        }
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) { detectTapGestures { seek((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ -> seek((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .semantics {
                contentDescription = "Seek"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
                setProgress { seek(it.coerceIn(0f, 1f)); true }
            }
    ) {
        val cy = size.height / 2
        val stroke = 4.dp.toPx()
        val amp = 4.dp.toPx()
        val wavelength = 30.dp.toPx()
        val gap = 6.dp.toPx()
        val thumbW = 5.dp.toPx()
        val x = (size.width * fraction.coerceIn(0f, 1f)).coerceIn(thumbW / 2, size.width - thumbW / 2)
        val activeEnd = x - thumbW / 2 - gap
        if (activeEnd > stroke) {
            val wave = Path()
            var px = stroke / 2
            wave.moveTo(px, cy + amp * sin(px / wavelength * 2 * PI.toFloat() - phase))
            while (px < activeEnd) {
                px = minOf(px + 2f, activeEnd)
                wave.lineTo(px, cy + amp * sin(px / wavelength * 2 * PI.toFloat() - phase))
            }
            drawPath(wave, s.primary, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val trackStart = x + thumbW / 2 + gap
        val trackEnd = size.width - stroke
        if (trackEnd > trackStart) {
            drawLine(s.secondaryContainer, Offset(trackStart, cy), Offset(trackEnd, cy), stroke, StrokeCap.Round)
        }
        drawCircle(s.primary, 2.dp.toPx(), Offset(size.width - 2.dp.toPx(), cy))
        drawRoundRect(
            s.primary,
            Offset(x - thumbW / 2, cy - 12.dp.toPx()),
            Size(thumbW, 24.dp.toPx()),
            CornerRadius(thumbW / 2),
        )
    }
}
