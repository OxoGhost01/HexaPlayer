package com.oxoghost.hexaplayer.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Two-finger pinch on a scrolling grid, reported in steps: +1 when the fingers spread (bigger
 * tiles), -1 when they close. Taken before the grid sees it, so one-finger scrolling is untouched
 * and a pinch never scrolls.
 */
fun Modifier.pinchSteps(onStep: (Int) -> Unit): Modifier = composed {
    val step by rememberUpdatedState(onStep)
    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var scale = 1f
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    scale *= event.calculateZoom()
                    when {
                        scale > STEP -> { step(+1); scale = 1f }
                        scale < 1 / STEP -> { step(-1); scale = 1f }
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}

private const val STEP = 1.3f
