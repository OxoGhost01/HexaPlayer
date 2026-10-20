package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalColors
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/** Mode shown in the status line block (TERMINAL.md > Mode blocks). */
enum class TMode {
    NOR, VIS, INS, FIND, REC, HIT, PLAY, LRC, QUE, EQ, DL, CFG, THEME, MOV, EMPTY, SETUP;

    fun color(c: TerminalColors): Color = when (this) {
        VIS, INS, FIND, REC -> c.accent2
        HIT -> c.ok
        MOV, EMPTY, SETUP -> c.warn
        else -> c.accent
    }
}

/** `NOR  Paper Satellites.flac                2/248` */
@Composable
fun TStatusLine(
    mode: TMode,
    context: String,
    modifier: Modifier = Modifier,
    position: String? = null,
) {
    val c = TerminalTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.statusLine.dp)
            .background(c.panel),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TText(
            mode.name,
            modifier = Modifier
                .fillMaxHeight()
                .background(mode.color(c))
                .padding(horizontal = 8.dp)
                .wrapContentHeight(),
            color = c.background,
            style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold),
        )
        TText(context, Modifier.weight(1f).padding(horizontal = 8.dp), color = c.dim)
        if (position != null) TText(position, Modifier.padding(horizontal = 8.dp), color = c.dim)
    }
}
