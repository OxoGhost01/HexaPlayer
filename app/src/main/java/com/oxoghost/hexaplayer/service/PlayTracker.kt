package com.oxoghost.hexaplayer.service

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.oxoghost.hexaplayer.data.db.PlayEvent
import com.oxoghost.hexaplayer.repository.QueueStore
import com.oxoghost.hexaplayer.repository.SavedItem

/**
 * Measures how long the current track is actually heard and reports a play once the track ends
 * or changes, if it reached 30 seconds or half its length (ARCHITECTURE.md > Play history).
 */
class PlayTracker(private val onPlay: (PlayEvent) -> Unit) {

    private var item: SavedItem? = null
    private var startedAt = 0L
    private var durationMs = 0L
    private var heardMs = 0L
    private var resumedAt = -1L

    fun onIsPlayingChanged(player: Player, playing: Boolean) {
        if (item == null) begin(player.currentMediaItem)
        durationMs = player.duration.coerceAtLeast(durationMs)
        if (playing) resumedAt = SystemClock.elapsedRealtime() else pause()
    }

    /** [auto]: the previous track played to its end. */
    fun onTransition(player: Player, next: MediaItem?, auto: Boolean) {
        finish(completed = auto)
        begin(next)
        if (player.isPlaying) resumedAt = SystemClock.elapsedRealtime()
    }

    /** The current track is loaded, so its length is known. */
    fun onReady(player: Player) {
        if (player.duration > 0) durationMs = player.duration
    }

    fun onEnded() = finish(completed = true)

    /** The service stops: count what was heard. */
    fun flush() = finish(completed = false)

    private fun begin(next: MediaItem?) {
        item = next?.let(SavedItem::of)?.takeUnless { it.mediaId.startsWith(QueueStore.EXTERNAL_PREFIX) || it.songId < 0 }
        startedAt = System.currentTimeMillis()
        durationMs = 0L
        heardMs = 0L
        resumedAt = -1L
    }

    private fun pause() {
        if (resumedAt >= 0) heardMs += SystemClock.elapsedRealtime() - resumedAt
        resumedAt = -1L
    }

    private fun finish(completed: Boolean) {
        pause()
        val current = item ?: return
        val counts = heardMs >= 30_000 || (durationMs > 0 && heardMs * 2 >= durationMs)
        if (counts) {
            onPlay(
                PlayEvent(
                    songId = current.songId,
                    startedAt = startedAt,
                    msPlayed = heardMs,
                    completed = completed,
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    albumId = current.albumId,
                )
            )
        }
        item = null
        heardMs = 0L
    }
}
