package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

/** M3 2024 linear progress: active part, 4 dp gap, track, and a stop dot at the end. */
@Composable
fun MLinearProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Canvas(modifier.fillMaxWidth().height(thickness).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
    }) {
        val h = size.height
        val r = CornerRadius(h / 2)
        val gap = 4.dp.toPx()
        val active = (size.width - h - gap) * fraction.coerceIn(0f, 1f)
        if (active > 0) drawRoundRect(color, Offset.Zero, Size(active, h), r)
        val trackStart = if (active > 0) active + gap else 0f
        val trackEnd = size.width - h - gap
        if (trackEnd > trackStart) drawRoundRect(track, Offset(trackStart, 0f), Size(trackEnd - trackStart, h), r)
        drawCircle(color, h / 2, Offset(size.width - h / 2, h / 2))
    }
}

/** Home Resume card: cover, label, title, source, progress, Resume and Not now. */
@Composable
fun MResumeCard(
    title: String,
    subtitle: String,
    progress: Float,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Pick up where you left off",
    cover: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(HexaShapes.card)
            .background(s.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp)) { cover() }
            Column(Modifier.weight(1f)) {
                Text(label, color = s.primary, style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        MLinearProgress(progress)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MPillButton("Resume", onResume, icon = Symbols.PlayArrow, size = MButtonSize.M)
            MPillButton("Not now", onDismiss, style = MButtonStyle.Text, size = MButtonSize.M)
        }
    }
}
