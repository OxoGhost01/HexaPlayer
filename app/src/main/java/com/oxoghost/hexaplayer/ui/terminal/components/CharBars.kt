package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import kotlin.math.roundToInt

/** Stats row: label, a `█` bar proportional to [value] / [max], count right aligned. */
@Composable
fun TCharBarRow(
    label: String,
    value: Int,
    max: Int,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 136.dp,
    color: Color = TerminalTheme.colors.accent2,
) {
    val c = TerminalTheme.colors
    val cell = rememberCellWidth()
    Row(
        modifier.fillMaxWidth().height(26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TText(label, Modifier.width(labelWidth))
        BoxWithConstraints(Modifier.weight(1f)) {
            val cells = (maxWidth / cell).toInt()
            val n = if (max > 0) (value.toFloat() / max * cells).roundToInt().coerceIn(1, cells) else 0
            TText("█".repeat(n), color = color, overflow = TextOverflow.Clip)
        }
        TText(
            value.toString(),
            Modifier.width(cell * maxOf(3, max.toString().length)),
            color = c.dim,
            style = TerminalTheme.type.body.copy(textAlign = TextAlign.End),
        )
    }
}

private const val EIGHTHS = " ▁▂▃▄▅▆▇█"

/**
 * Vertical block chart (months, waveform, spectrum): one column per value (0..1), [rows] lines
 * tall, using `▁▂▃▄▅▆▇█` for the top of each column.
 */
@Composable
fun TBlockChart(
    values: List<Float>,
    rows: Int,
    modifier: Modifier = Modifier,
    color: Color = TerminalTheme.colors.accent2,
    gap: Boolean = true,
) {
    val lines = (rows - 1 downTo 0).joinToString("\n") { row ->
        values.joinToString(if (gap) " " else "") { v ->
            val eighths = (v.coerceIn(0f, 1f) * rows * 8).roundToInt() - row * 8
            EIGHTHS[eighths.coerceIn(0, 8)].toString()
        }
    }
    TText(lines, modifier, color = color, maxLines = rows, overflow = TextOverflow.Clip)
}

/** One-line spark: `▁▃▅█▆▃` (identify level meter, spectrum line). */
@Composable
fun TSpark(values: List<Float>, modifier: Modifier = Modifier, color: Color = TerminalTheme.colors.accent) {
    val text = values.joinToString("") { v -> EIGHTHS[(v.coerceIn(0f, 1f) * 7).roundToInt() + 1].toString() }
    TText(text, modifier, color = color, overflow = TextOverflow.Clip)
}
