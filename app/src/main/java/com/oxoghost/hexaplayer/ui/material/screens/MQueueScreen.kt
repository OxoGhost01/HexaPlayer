package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.ReorderState
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.rememberReorderState
import com.oxoghost.hexaplayer.ui.common.reorderHandle
import com.oxoghost.hexaplayer.ui.common.reorderItem
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.segmentShape
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.QueueEntry
import com.oxoghost.hexaplayer.ui.state.QueueState

private const val ROW_DP = 64

@Composable
fun MQueueScreen(queue: QueueState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val reorder = rememberReorderState()
    Column(Modifier.fillMaxSize().background(s.surface).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MCircleButton(Symbols.KeyboardArrowDown, "Close", actions::back, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text("Queue", style = MaterialTheme.typography.headlineMedium)
                Text("${queue.total} songs · ${queue.remainingMs / 60_000} min left", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (queue.upNext.isNotEmpty() || queue.thenFrom.isNotEmpty()) {
                MPillButton("Clear", actions::clear, style = MButtonStyle.Text, size = MButtonSize.S, contentColor = s.error)
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            if (queue.empty) item { Text("The queue is empty", color = s.onSurfaceVariant) }
            queue.current?.let { cur ->
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(HexaShapes.card)
                            .background(s.primaryContainer)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CoverImage(cur.song.artModel, cur.song.albumKey, Modifier.size(64.dp), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Now playing", color = s.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                            Text(cur.song.title, color = s.onPrimaryContainer, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(cur.song.artist, color = s.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        Symbol(Symbols.GraphicEq, null, tint = s.onPrimaryContainer)
                    }
                }
            }
            if (queue.upNext.isNotEmpty()) {
                item { MSectionLabel("Up next · added by you", Modifier.padding(top = 16.dp)) }
                group("user", queue.upNext, reorder, actions, numbered = false, draggable = true)
            }
            if (queue.thenFrom.isNotEmpty()) {
                val from = queue.source?.substringAfter(" · ", queue.source)
                item { MSectionLabel(if (from != null) "Then from $from" else "Up next", Modifier.padding(top = 16.dp)) }
                if (queue.shuffle) {
                    item { Text("Shuffle is on: this part keeps its order", Modifier.padding(start = 4.dp, bottom = 8.dp), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
                group("context", queue.thenFrom, reorder, actions, numbered = true, draggable = !queue.shuffle)
            }
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
        val s = MaterialTheme.colorScheme
        val rowPx = with(LocalDensity.current) { (ROW_DP + 2).dp.toPx() }
        val dragging = reorder.isDragging(name, i)
        Row(
            Modifier
                .reorderItem(reorder, name, i)
                .padding(bottom = 2.dp)
                .fillMaxWidth()
                .height(ROW_DP.dp)
                .clip(if (dragging) androidx.compose.foundation.shape.RoundedCornerShape(20.dp) else segmentShape(i, entries.size))
                .background(if (dragging) s.surfaceContainerHighest else s.surfaceContainer)
                .clickable { actions.playAt(entry.exoIndex) }
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (numbered) {
                Text("${i + 2}", Modifier.width(28.dp), color = s.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
                Text(entry.song.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDuration(entry.song.duration), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else {
                CoverImage(entry.song.artModel, entry.song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
                Column(Modifier.weight(1f)) {
                    Text(entry.song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.song.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
                MIconButton(Symbols.Close, "Remove ${entry.song.title}", { actions.remove(entry.exoIndex) })
            }
            if (draggable) {
                Box(
                    Modifier
                        .size(48.dp)
                        .reorderHandle(reorder, name, i, entries.size, rowPx) { from, to ->
                            actions.move(entries[from].exoIndex, entries[to].exoIndex)
                        }
                        .semantics { contentDescription = "Drag ${entry.song.title}" },
                    contentAlignment = Alignment.Center,
                ) { Symbol(Symbols.DragHandle, null, tint = s.onSurfaceVariant) }
            } else {
                Box(Modifier.size(16.dp))
            }
        }
    }
}
