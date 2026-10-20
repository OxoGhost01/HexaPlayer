package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalColors
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Prefix of a log line: `[ ok ]`, `[ .. ]`, `✓`, `✗`, `↓ 62%`, `·`. */
sealed class TLog {
    data object Ok : TLog()
    data object Waiting : TLog()
    data object Done : TLog()
    data class Failed(val error: Boolean = true) : TLog()
    data class Progress(val percent: Int) : TLog()
    data object Pending : TLog()

    val prefix: String
        get() = when (this) {
            Ok -> "[ ok ]"
            Waiting -> "[ .. ]"
            Done -> "✓"
            is Failed -> "✗"
            is Progress -> "↓ $percent%"
            Pending -> "·"
        }

    fun color(c: TerminalColors): Color = when (this) {
        Ok, Done -> c.ok
        Waiting -> c.warn
        is Failed -> if (error) c.error else c.warn
        is Progress -> c.accent
        Pending -> c.dim
    }
}

@Composable
fun TLogLine(kind: TLog, text: String, modifier: Modifier = Modifier, textColor: Color = TerminalTheme.colors.foreground) {
    val c = TerminalTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TText(kind.prefix, color = kind.color(c))
        TText(text, Modifier.weight(1f), color = textColor)
    }
}
