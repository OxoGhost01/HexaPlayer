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

/** Terminal length: 34:12 under an hour, 1h52 above. */
fun formatLengthShort(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "%dh%02d".format(minutes / 60, minutes % 60) else formatDuration(ms)
}

/** Material length: 34 min, 1 h 31 min. */
fun formatLengthLong(ms: Long): String {
    val minutes = (ms + 30_000) / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** 12 Oct (lower case for the terminal skin). */
fun formatDay(epochMs: Long, lower: Boolean = false): String {
    val text = java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH).format(java.util.Date(epochMs)).trimEnd('.')
    return if (lower) text.lowercase(java.util.Locale.ROOT) else text
}

/** 00:12, the wall-clock time [epochMs] falls on. */
fun formatClock(epochMs: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.ROOT).format(java.util.Date(epochMs))

/** "1 play", "3 plays". [many] defaults to the word plus s. */
fun plural(count: Number, one: String, many: String = one + "s"): String = "$count ${if (count.toLong() == 1L) one else many}"
