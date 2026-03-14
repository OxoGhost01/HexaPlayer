package com.oxoghost.hexaplayer.data

sealed class LyricsState {
    object Loading : LyricsState()
    object NotFound : LyricsState()
    /** [lines] = text of each lyric line; [timestamps] = ms offsets, null when not synced */
    data class Found(
        val lines: List<String>,
        val timestamps: List<Long>?,
        val isSynced: Boolean
    ) : LyricsState()
}
