package com.oxoghost.hexaplayer.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** 4:02, or 1:04:02 past an hour. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Milliseconds left until [endsAt], updated every second; null when there is no deadline. */
@Composable
fun rememberCountdown(endsAt: Long?): Long? {
    val left by produceState(endsAt?.let { it - System.currentTimeMillis() }, endsAt) {
        while (endsAt != null) {
            value = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
            delay(1000)
        }
        value = null
    }
    return left
}
