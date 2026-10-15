package com.oxoghost.hexaplayer.util

import android.content.Context
import android.util.TypedValue
import android.widget.Toast

fun Long.toTimeString(): String {
    val totalSeconds = this / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/** Resolves a theme color attribute (e.g. R.attr.colorPrimary) to an ARGB int. */
fun Context.themeColor(attrResId: Int): Int {
    val tv = TypedValue()
    theme.resolveAttribute(attrResId, tv, true)
    return tv.data
}

/** Converts dp to pixels. */
fun Int.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density + 0.5f).toInt()

fun Context.toast(text: CharSequence, long: Boolean = false) {
    Toast.makeText(applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
