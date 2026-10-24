package com.oxoghost.hexaplayer.util

import android.content.Context
import android.widget.Toast

fun Context.toast(text: CharSequence, long: Boolean = false) {
    Toast.makeText(applicationContext, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}
