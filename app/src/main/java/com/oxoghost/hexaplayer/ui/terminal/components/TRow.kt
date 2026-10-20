package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/**
 * List row. [cursor] inverts it in accent; [playing] adds `▶︎` and colors the title;
 * [selected] non-null shows the VIS markers `[+]` / `[ ]`; [directory] draws the title as a
 * directory (`accent2`, trailing `/`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    cursor: Boolean = false,
    playing: Boolean = false,
    selected: Boolean? = null,
    directory: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    val c = TerminalTheme.colors
    val onCursor = c.background
    val titleColor = when {
        cursor -> onCursor
        playing -> c.accent
        directory -> c.accent2
        else -> c.foreground
    }
    val dimColor = if (cursor) onCursor else c.dim
    Row(
        modifier
            .fillMaxWidth()
            .height(if (subtitle != null) Metrics.listRow2Line.dp else Metrics.listRow1Line.dp)
            .background(if (cursor) c.accent else if (selected == true) c.selection else c.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (selected) {
            null -> TText(if (playing) "▶︎" else "", Modifier.width(10.dp), color = if (cursor) onCursor else c.accent)
            true -> TText("[+]", color = c.accent2)
            false -> TText("[ ]", color = c.border)
        }
        Column(Modifier.weight(1f)) {
            TText(if (directory) "$title/" else title, color = titleColor)
            if (subtitle != null) TText(subtitle, color = dimColor, style = TerminalTheme.type.small)
        }
        if (trailing != null) TText(trailing, color = dimColor)
    }
}

/** Library section switcher: 104 dp column, current item `▸ albums` on the selection color. */
@Composable
fun TParentColumn(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    Column(
        modifier
            .width(Metrics.parentColumnWidth.dp)
            .fillMaxHeight()
            .drawBehind {
                drawLine(c.border, Offset(size.width - 0.5f, 0f), Offset(size.width - 0.5f, size.height), 1.dp.toPx())
            }
            .padding(top = 4.dp),
    ) {
        items.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(Metrics.listRow1Line.dp)
                    .background(if (on) c.selection else c.background)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                TText(if (on) "▸ $label" else "  $label", color = if (on) c.accent else c.dim)
            }
        }
    }
}

/**
 * Preview of the item under the cursor: pixel cover, a few metadata lines, `open →`.
 * [cover] is usually a 96 dp [com.oxoghost.hexaplayer.ui.common.PixelCover].
 */
@Composable
fun TPreviewPane(
    lines: List<AnnotatedString>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    cover: @Composable () -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = TerminalTheme.colors
    Column(modifier.fillMaxWidth()) {
        TDivider()
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp)) { cover() }
            Column(Modifier.weight(1f)) {
                lines.forEach { TText(it, style = TerminalTheme.type.small, color = c.dim) }
                extra?.invoke(this)
                Box(
                    Modifier
                        .height(Metrics.touchMin.dp)
                        .clickable(role = Role.Button, onClick = onOpen),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    TText("open →", color = c.accent)
                }
            }
        }
    }
}
