package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp

/** Thick slider: 12 dp track, 4 x 20 dp thumb bar, gap, rest in surfaceContainerHighest. */
@Composable
fun MThickSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String = "",
) {
    val s = MaterialTheme.colorScheme
    val change by rememberUpdatedState(onValueChange)
    val active = if (enabled) s.primary else s.outline
    Canvas(
        modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(enabled) { if (enabled) detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(enabled) {
                if (enabled) detectHorizontalDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                if (enabled) setProgress { change(it.coerceIn(0f, 1f)); true }
            }
    ) {
        val track = 12.dp.toPx()
        val cy = size.height / 2
        val thumbW = 4.dp.toPx()
        val gap = 6.dp.toPx()
        val r = CornerRadius(track / 2)
        val x = (size.width * value.coerceIn(0f, 1f)).coerceIn(thumbW / 2 + track, size.width - thumbW / 2)
        val activeEnd = x - thumbW / 2 - gap
        drawRoundRect(active, Offset(0f, cy - track / 2), Size(activeEnd.coerceAtLeast(track), track), r)
        val restStart = x + thumbW / 2 + gap
        if (size.width > restStart) {
            drawRoundRect(s.surfaceContainerHighest, Offset(restStart, cy - track / 2), Size(size.width - restStart, track), r)
        }
        drawRoundRect(active, Offset(x - thumbW / 2, cy - 10.dp.toPx()), Size(thumbW, 20.dp.toPx()), CornerRadius(thumbW / 2))
    }
}

/** Effect row: label, value, switch, then a thick slider (bass boost, virtualizer, loudness). */
@Composable
fun MEffectRow(
    label: String,
    valueText: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text(valueText, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            MSwitch(enabled, onEnabledChange)
        }
        MThickSlider(value, onValueChange, enabled = enabled, description = label)
    }
}
