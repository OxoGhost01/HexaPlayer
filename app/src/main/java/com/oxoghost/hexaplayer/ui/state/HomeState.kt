package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.data.db.SongCount
import com.oxoghost.hexaplayer.repository.Period
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.repository.SavedQueue
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The remembered queue as the Resume card shows it. */
@Immutable
data class ResumeInfo(
    val song: Song?,
    val title: String,
    val artist: String,
    val source: String?,
    val position: Int,
    val size: Int,
    val positionMs: Long,
    val durationMs: Long,
    val savedAt: Long,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** A song with its play count, for most played lists. */
@Immutable
data class PlayedSong(val song: Song?, val title: String, val artist: String, val plays: Int)

/** Totals of the current year for the Wrapped card. */
@Immutable
data class WrappedTeaser(val year: Int, val listenedMs: Long, val songs: Int, val artists: Int) {
    val hours: Long get() = listenedMs / 3_600_000

    /** "12 h" past an hour, "37 min" below. */
    val time: String get() = if (hours >= 1) "$hours h" else "${listenedMs / 60_000} min"
}

@Immutable
data class HomeState(
    val allSongs: List<Song>,
    val recentSongs: List<Song>,
    val recentAlbums: List<Album>,
    val songCount: Int,
    val albumCount: Int,
    val artistCount: Int,
    val resume: ResumeInfo? = null,
    val recentlyPlayed: List<Song> = emptyList(),
    val mostPlayed: List<PlayedSong> = emptyList(),
    val monthLabel: String = "",
    val wrapped: WrappedTeaser? = null,
)

private data class HomeHistory(
    val recentlyPlayed: List<Long> = emptyList(),
    val mostPlayed: List<SongCount> = emptyList(),
    val wrapped: WrappedTeaser? = null,
)

@Composable
fun rememberHomeState(vm: MusicViewModel): HomeState {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val artists by vm.artists.observeAsState(emptyList())
    val restored by vm.restored.observeAsState()
    val version by vm.historyVersion.observeAsState(0)
    val history by produceState(HomeHistory(), version) {
        value = withContext(Dispatchers.IO) {
            val month = Period(PeriodKind.Month)
            val year = Period(PeriodKind.Year)
            val totals = vm.history.wrapped(year, topSongs = 0)
            HomeHistory(
                recentlyPlayed = vm.history.recent(10).map { it.songId },
                mostPlayed = vm.history.mostPlayed(month, 5),
                wrapped = WrappedTeaser(year.start.year, totals.listenedMs, totals.songs, totals.artists),
            )
        }
    }
    return remember(songs, albums, artists, restored, history) {
        val byId = songs.associateBy { it.id }
        val recent = songs.sortedByDescending { it.dateAdded }
        val albumById = albums.associateBy { it.id }
        HomeState(
            allSongs = songs,
            recentSongs = recent.take(8),
            recentAlbums = recent.asSequence().map { it.albumId }.distinct().mapNotNull { albumById[it] }.filter { it.songs.size > 1 }.take(10).toList(),
            songCount = songs.size,
            albumCount = albums.count { it.songs.size > 1 },
            artistCount = artists.size,
            resume = restored?.toResume(byId),
            recentlyPlayed = history.recentlyPlayed.mapNotNull { byId[it] },
            mostPlayed = history.mostPlayed.map { PlayedSong(byId[it.songId], it.title, it.artist, it.plays) },
            monthLabel = Period(PeriodKind.Month).start.month.name.lowercase(),
            wrapped = history.wrapped,
        )
    }
}

private fun SavedQueue.toResume(byId: Map<Long, Song>): ResumeInfo? {
    val item = current ?: return null
    val song = byId[item.songId]
    return ResumeInfo(
        song = song,
        title = item.title,
        artist = item.artist,
        source = source,
        position = index + 1,
        size = items.size,
        positionMs = positionMs,
        durationMs = song?.duration ?: 0L,
        savedAt = savedAt,
    )
}
