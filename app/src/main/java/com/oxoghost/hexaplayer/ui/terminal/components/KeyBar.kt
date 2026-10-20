package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/** One key: the [key] prefix (digit or letter) and its label. */
data class TKey(val key: String, val label: String)

/** Tab key bar `1 home  2 lib  3 find  4 dl`; the active key is inverted in accent. */
@Composable
fun TKeyBar(keys: List<TKey>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    TKeyRow(modifier) {
        keys.forEachIndexed { i, k ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) c.accent else c.panel)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                TText(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = if (on) c.background else c.dim)) { append(k.key) }
                        append(" ")
                        append(k.label)
                    },
                    color = if (on) c.background else c.foreground,
                    style = TerminalTheme.type.body.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                )
            }
        }
    }
}

/** Selection action bar (VIS mode): the letter in accent, then the label. */
@Composable
fun TActionKeyBar(keys: List<TKey>, onClick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    TKeyRow(modifier) {
        keys.forEachIndexed { i, k ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(role = Role.Button, onClick = { onClick(i) }),
                contentAlignment = Alignment.Center,
            ) {
                TText(buildAnnotatedString {
                    withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)) { append(k.key) }
                    append(" ")
                    append(k.label)
                })
            }
        }
    }
}

@Composable
private fun TKeyRow(modifier: Modifier, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.keybar.dp)
            .background(TerminalTheme.colors.panel)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}
