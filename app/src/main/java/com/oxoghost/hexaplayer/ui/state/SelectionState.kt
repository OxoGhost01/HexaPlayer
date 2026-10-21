package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oxoghost.hexaplayer.data.Song

/**
 * Multi-select (terminal VIS mode). Selection happens inside one list, the [pool]; long press and
 * drag selects the range between the pressed row and the finger, taps then toggle single rows.
 */
@Stable
class Selection {
    var pool by mutableStateOf<List<Song>>(emptyList())
        private set
    var ids by mutableStateOf<Set<Long>>(emptySet())
        private set

    private var anchor = -1
    private var before: Set<Long> = emptySet()

    val active: Boolean get() = ids.isNotEmpty()
    val songs: List<Song> by derivedStateOf { pool.filter { it.id in ids } }
    val durationMs: Long by derivedStateOf { songs.sumOf { it.duration } }

    fun isSelected(song: Song) = song.id in ids

    /** Long press + drag started on [index] of [list]. */
    fun startRange(list: List<Song>, index: Int) {
        if (pool != list) {
            pool = list
            ids = emptySet()
        }
        anchor = index
        before = ids
        extendRange(index)
    }

    fun extendRange(index: Int) {
        if (anchor < 0) return
        val to = index.coerceIn(0, pool.lastIndex)
        val range = if (to >= anchor) anchor..to else to..anchor
        ids = before + range.map { pool[it].id }
    }

    fun toggle(list: List<Song>, song: Song) {
        if (pool != list) {
            pool = list
            ids = emptySet()
        }
        ids = if (song.id in ids) ids - song.id else ids + song.id
    }

    fun selectAll() {
        ids = pool.map { it.id }.toSet()
    }

    fun invert() {
        ids = pool.map { it.id }.filterNot { it in ids }.toSet()
    }

    fun clear() {
        ids = emptySet()
        anchor = -1
    }
}

val LocalSelection = compositionLocalOf { Selection() }
