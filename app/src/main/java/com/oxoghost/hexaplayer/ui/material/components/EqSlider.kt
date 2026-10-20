package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * EQ band: 16 x 180 dp track, active part from 0 dB to [value] in primary, 4 dp handle bar,
 * value above, [frequency] below. Values in dB within [range].
 */
@Composable
fun MEqSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    frequency: String,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = -12f..12f,
) {
    val s = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onValueChange)
    val span = range.endInclusive - range.start
    fun toValue(y: Float, h: Float) = (range.endInclusive - (y / h).coerceIn(0f, 1f) * span).roundToInt().toFloat()
    val label = value.roundToInt().let { if (it > 0) "+$it" else "$it" }
    Column(modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = s.primary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Canvas(
            Modifier
                .width(36.dp)
                .height(180.dp)
                .pointerInput(range) { detectTapGestures { change(toValue(it.y, size.height.toFloat())) } }
                .pointerInput(range) { detectVerticalDragGestures { c, _ -> change(toValue(c.position.y, size.height.toFloat())) } }
                .semantics {
                    contentDescription = "$frequency Hz band, $label dB"
                    progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                    setProgress { change(it.coerceIn(range.start, range.endInclusive)); true }
                }
        ) {
            val trackW = 16.dp.toPx()
            val left = (size.width - trackW) / 2
            val r = CornerRadius(trackW / 2)
            drawRoundRect(s.secondaryContainer, Offset(left, 0f), Size(trackW, size.height), r)
            val center = size.height * (range.endInclusive / span)
            val y = size.height * ((range.endInclusive - value.coerceIn(range.start, range.endInclusive)) / span)
            val top = minOf(center, y)
            val h = abs(center - y).coerceAtLeast(trackW)
            drawRoundRect(s.primary, Offset(left, if (abs(center - y) < trackW) center - trackW / 2 else top), Size(trackW, h), r)
            val handleW = 28.dp.toPx()
            drawRoundRect(
                s.onSurface,
                Offset((size.width - handleW) / 2, y - 2.dp.toPx()),
                Size(handleW, 4.dp.toPx()),
                CornerRadius(2.dp.toPx()),
            )
        }
        Text(frequency, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
