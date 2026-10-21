package com.oxoghost.hexaplayer.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Drag-to-reorder for fixed-height rows: drag the handle, release to move. */
@Stable
class ReorderState {
    var group by mutableStateOf<String?>(null)
    var index by mutableIntStateOf(-1)
    var offset by mutableFloatStateOf(0f)

    fun isDragging(group: String, index: Int) = this.group == group && this.index == index

    /** Index the dragged row would land on. */
    fun target(rowHeightPx: Float, count: Int) = (index + (offset / rowHeightPx).roundToInt()).coerceIn(0, count - 1)
}

@Composable
fun rememberReorderState() = remember { ReorderState() }

fun Modifier.reorderHandle(
    state: ReorderState,
    group: String,
    index: Int,
    count: Int,
    rowHeightPx: Float,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier = pointerInput(group, index, count) {
    fun reset() {
        state.group = null
        state.index = -1
        state.offset = 0f
    }
    detectDragGestures(
        onDragStart = {
            state.group = group
            state.index = index
            state.offset = 0f
        },
        onDragEnd = {
            val to = state.target(rowHeightPx, count)
            if (to != index) onMove(index, to)
            reset()
        },
        onDragCancel = { reset() },
    ) { change, amount ->
        change.consume()
        state.offset += amount.y
    }
}

/** Moves the row being dragged with the finger, above its neighbours. */
fun Modifier.reorderItem(state: ReorderState, group: String, index: Int): Modifier =
    if (state.isDragging(group, index)) zIndex(1f).graphicsLayer { translationY = state.offset } else this
