package com.oxoghost.hexaplayer.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Horizontal fling: left = [onLeft] (next), right = [onRight] (previous). */
fun Modifier.horizontalSwipe(onLeft: () -> Unit, onRight: () -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 80.dp.toPx()
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (total < -threshold) onLeft() else if (total > threshold) onRight()
        },
    ) { _, delta -> total += delta }
}

/**
 * Swipe a row to the right to queue it (1.x behaviour): the row follows the finger at 40 %,
 * a [color] band grows behind it, and releasing past the threshold calls [onSwipe].
 */
@Composable
fun SwipeToQueue(onSwipe: () -> Unit, color: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        modifier
            .drawBehind {
                if (offset.value > 0) {
                    val alpha = (offset.value / threshold).coerceIn(0f, 1f) * 0.6f
                    drawRect(color.copy(alpha = alpha), size = size.copy(width = offset.value / 0.4f))
                }
            }
            .pointerInput(Unit) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total > threshold) onSwipe()
                        scope.launch { offset.animateTo(0f) }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f) } },
                ) { _, delta ->
                    total = (total + delta).coerceAtLeast(0f)
                    scope.launch { offset.snapTo(total * 0.4f) }
                }
            }
            .offset { IntOffset(offset.value.roundToInt(), 0) }
    ) { content() }
}
