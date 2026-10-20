package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Code block on the panel color, 11 sp, selectable, wraps anywhere (setup command). */
@Composable
fun TCodeBlock(code: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier.fillMaxWidth().background(TerminalTheme.colors.panel).padding(8.dp)) {
        TText(code, style = TerminalTheme.type.tiny, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
    }
}
