package com.oxoghost.hexaplayer.repository

import android.content.Context

class CoverRepository(context: Context) {

    private val prefs = context.getSharedPreferences("cover_overrides", Context.MODE_PRIVATE)

    fun getOverride(songId: Long): String? = prefs.getString(songId.toString(), null)

    fun setOverride(songId: Long, uri: String) {
        prefs.edit().putString(songId.toString(), uri).apply()
    }

    fun clearOverride(songId: Long) {
        prefs.edit().remove(songId.toString()).apply()
    }
}
