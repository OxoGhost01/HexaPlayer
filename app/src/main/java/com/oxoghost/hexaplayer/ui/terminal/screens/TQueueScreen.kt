package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.ReorderState
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.rememberReorderState
import com.oxoghost.hexaplayer.ui.common.reorderHandle
import com.oxoghost.hexaplayer.ui.common.reorderItem
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.QueueEntry
import com.oxoghost.hexaplayer.ui.state.QueueState
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

private const val ROW_DP = 48

@Composable
fun TQueueScreen(queue: QueueState, actions: HexaActions) {
    val c = TerminalTheme.colors
    val reorder = rememberReorderState()
    TScreen(
        header = {
            THeader(
                "queue", onBack = actions::back,
                suffix = "${queue.total} songs · ${formatDuration(queue.remainingMs)} left",
                action = if (queue.upNext.isNotEmpty() || queue.thenFrom.isNotEmpty()) "clear" else null,
                actionColor = c.error,
                actionDescription = "Clear the queue",
                onAction = actions::clear,
            )
        },
        status = {
            TStatusLine(
                TMode.QUE,
                if (queue.shuffle) "shuffle on: context order fixed" else "tap: play · ≡ drag to reorder",
            )
        },
    ) {
        if (queue.empty) {
            TText("the queue is empty", Modifier.padding(12.dp), color = c.dim)
            return@TScreen
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            queue.current?.let { cur ->
                label("playing", c.accent, null)
                item {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).background(c.selection).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TText("▶︎", color = c.accent)
                        Column(Modifier.weight(1f)) {
                            TText(cur.song.title, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
                            TText(cur.song.artist, color = c.dim, style = TerminalTheme.type.small)
                        }
                        TText("${formatDuration(queue.positionMs)}/${formatDuration(queue.durationMs)}", color = c.dim)
                    }
                }
            }
            if (queue.upNext.isNotEmpty()) {
                label("up next · added by you", c.accent2, queue.upNext.size)
                group("user", queue.upNext, reorder, actions, numbered = false, draggable = true)
            }
            if (queue.thenFrom.isNotEmpty()) {
                val from = sourceName(queue.source)?.let { "then · from $it/" } ?: "then"
                label(from, c.accent, queue.thenFrom.size)
                group("context", queue.thenFrom, reorder, actions, numbered = true, draggable = !queue.shuffle)
            }
        }
    }
}

private fun LazyListScope.label(text: String, color: Color, count: Int?) {
    item {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp)) {
            TText(text, Modifier.weight(1f), color = color, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
            if (count != null) TText("$count", color = TerminalTheme.colors.dim)
        }
    }
}

private fun LazyListScope.group(
    name: String,
    entries: List<QueueEntry>,
    reorder: ReorderState,
    actions: HexaActions,
    numbered: Boolean,
    draggable: Boolean,
) {
    itemsIndexed(entries, key = { _, e -> "$name${e.exoIndex}${e.song.id}" }) { i, entry ->
        val c = TerminalTheme.colors
        val rowPx = with(LocalDensity.current) { ROW_DP.dp.toPx() }
        val dragging = reorder.isDragging(name, i)
        Row(
            Modifier
                .reorderItem(reorder, name, i)
                .fillMaxWidth()
                .height(ROW_DP.dp)
                .background(if (dragging) c.selection else c.background)
                .clickable { actions.playAt(entry.exoIndex) }
                .padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (numbered) {
                TText("%02d".format(i + 1), color = c.dim)
                TText(entry.song.title, Modifier.weight(1f))
                TText(formatDuration(entry.song.duration), color = c.dim)
            } else {
                Column(Modifier.weight(1f)) {
                    TText(entry.song.title)
                    TText(entry.song.artist, color = c.dim, style = TerminalTheme.type.small)
                }
                RowKey("x", "Remove ${entry.song.title}") { actions.remove(entry.exoIndex) }
            }
            if (draggable) {
                Box(
                    Modifier
                        .size(44.dp)
                        .reorderHandle(reorder, name, i, entries.size, rowPx) { from, to ->
                            actions.move(entries[from].exoIndex, entries[to].exoIndex)
                        }
                        .semantics { contentDescription = "Drag ${entry.song.title}" },
                    contentAlignment = Alignment.Center,
                ) { TText("≡", color = if (dragging) c.warn else c.dim) }
            } else {
                Box(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun RowKey(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { TText(label, color = TerminalTheme.colors.dim) }
}

