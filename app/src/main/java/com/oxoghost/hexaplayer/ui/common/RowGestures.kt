package com.oxoghost.hexaplayer.ui.common

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Song row gestures: tap, long press (released without moving: [onLongClick], the song actions)
 * and long press then drag ([onRangeStart], then [onRange] with how many rows the finger moved),
 * which starts multi-select. Replaces `combinedClickable` on rows that support selection.
 */
fun Modifier.rowGestures(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRangeStart: (() -> Unit)? = null,
    onRange: ((Int) -> Unit)? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val haptics = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val rangeStart by rememberUpdatedState(onRangeStart)
    val range by rememberUpdatedState(onRange)

    semantics {
        onClick { click(); true }
        onLongClick { longClick(); true }
    }
        .indication(interaction, LocalIndication.current)
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val press = PressInteraction.Press(down.position)
                interaction.tryEmit(press)

                // 0: long press, 1: tap, 2: cancelled (scroll, swipe, another finger)
                var outcome = 0
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            outcome = 2
                            break
                        }
                        if (change.changedToUp()) {
                            outcome = if (change.isConsumed) 2 else 1
                            change.consume()
                            break
                        }
                        if (change.isConsumed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            outcome = 2
                            break
                        }
                    }
                }
                when (outcome) {
                    1 -> {
                        interaction.tryEmit(PressInteraction.Release(press))
                        click()
                        return@awaitEachGesture
                    }
                    2 -> {
                        interaction.tryEmit(PressInteraction.Cancel(press))
                        return@awaitEachGesture
                    }
                }

                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val rowHeight = size.height.toFloat().coerceAtLeast(1f)
                var dragging = false
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) {
                        change.consume()
                        break
                    }
                    val dy = change.position.y - down.position.y
                    if (!dragging && range != null && abs(dy) > rowHeight / 2) {
                        dragging = true
                        rangeStart?.invoke()
                    }
                    if (dragging) range?.invoke((dy / rowHeight).roundToInt())
                    change.consume()
                }
                interaction.tryEmit(PressInteraction.Release(press))
                if (!dragging) longClick()
            }
        }
}
