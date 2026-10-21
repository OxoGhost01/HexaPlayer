package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.oxoghost.hexaplayer.data.LyricsState
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

@Immutable
data class LyricsUiState(
    val loading: Boolean,
    val lines: List<String>,
    val timestamps: List<Long>?,
    val synced: Boolean,
    val currentLine: Int,
    val offsetMs: Long,
) {
    val found: Boolean get() = lines.isNotEmpty()
}

@Composable
fun rememberLyricsState(vm: MusicViewModel, playback: PlaybackState): LyricsUiState {
    val state by vm.lyricsState.observeAsState(LyricsState.NotFound)
    val offset by vm.lyricsOffsetMs.observeAsState(0L)
    return when (val s = state) {
        is LyricsState.Found -> {
            val ts = s.timestamps
            val line = if (s.isSynced && ts != null) ts.indexOfLast { it + offset <= playback.positionMs } else -1
            LyricsUiState(false, s.lines, ts, s.isSynced, line, offset)
        }
        LyricsState.Loading -> LyricsUiState(true, emptyList(), null, false, -1, offset)
        else -> LyricsUiState(false, emptyList(), null, false, -1, offset)
    }
}
