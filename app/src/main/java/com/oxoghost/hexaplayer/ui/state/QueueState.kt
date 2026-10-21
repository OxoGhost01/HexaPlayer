package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/** A queued song and its index in the player timeline. */
data class QueueEntry(val song: Song, val exoIndex: Int, val isUser: Boolean)

@Immutable
data class QueueState(
    val current: QueueEntry?,
    val upNext: List<QueueEntry>,
    val thenFrom: List<QueueEntry>,
    val source: String?,
    val shuffle: Boolean,
    val positionMs: Long,
    val durationMs: Long,
) {
    val total: Int get() = (if (current != null) 1 else 0) + upNext.size + thenFrom.size
    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0) + (upNext + thenFrom).sumOf { it.song.duration }
    val empty: Boolean get() = current == null && upNext.isEmpty() && thenFrom.isEmpty()
}

/** Same model as the 1.x queue: now playing, songs added by the user, then the context. */
@Composable
fun rememberQueueState(vm: MusicViewModel, playback: PlaybackState): QueueState {
    val ctx by vm.currentQueue.observeAsState(emptyList())
    val ctxIdx by vm.currentQueueIndex.observeAsState(0)
    val uq by vm.userQueueLive.observeAsState(emptyList())
    val fromUq by vm.isPlayingFromUserQueue.observeAsState(false)
    val song = playback.song
    val current = song?.let { QueueEntry(it, if (fromUq) ctxIdx + 1 else ctxIdx, fromUq) }
    val upcomingUq = if (fromUq && uq.isNotEmpty()) uq.drop(1) else uq
    val upNext = upcomingUq.mapIndexed { i, s -> QueueEntry(s, ctxIdx + if (fromUq) i + 2 else i + 1, true) }
    val thenFrom = ctx.drop(ctxIdx + 1).mapIndexed { i, s -> QueueEntry(s, ctxIdx + 1 + uq.size + i, false) }
    return QueueState(current, upNext, thenFrom, playback.source, playback.shuffle, playback.positionMs, playback.durationMs)
}
