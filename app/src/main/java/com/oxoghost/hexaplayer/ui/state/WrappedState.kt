package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.Period
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.repository.WrappedStats
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class RankedSong(val rank: Int, val song: Song?, val title: String, val artist: String, val plays: Int, val ms: Long)

@Immutable
data class RankedAlbum(val title: String, val artist: String, val albumId: Long, val cover: Song?, val plays: Int)

@Immutable
data class WrappedState(
    val period: Period,
    val loading: Boolean,
    val stats: WrappedStats?,
    val songs: List<RankedSong>,
    val albums: List<RankedAlbum>,
    /** Whether an earlier period with plays exists, for the "previous" button. */
    val hasEarlier: Boolean,
) {
    val hours: Long get() = (stats?.listenedMs ?: 0L) / 3_600_000
    val minutes: Long get() = ((stats?.listenedMs ?: 0L) / 60_000) % 60
    val days: Long get() = (stats?.listenedMs ?: 0L) / 86_400_000

    /** "this year", "this month", "this week" or the period label. */
    val title: String
        get() = if (period.isCurrent) "this ${period.kind.name.lowercase()}" else period.label

    val peak: String
        get() = stats?.peakHour?.let { "%02d:00".format(it) } ?: "-"
}

interface WrappedActions {
    fun setKind(kind: PeriodKind)
    fun shift(delta: Int)
    fun saveTop50()
}

@Composable
fun rememberWrappedPeriod(): Pair<Period, (Period) -> Unit> {
    var kind by rememberSaveable { mutableIntStateOf(PeriodKind.Year.ordinal) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    return Period(PeriodKind.entries[kind], offset) to { p: Period ->
        kind = p.kind.ordinal
        offset = p.offset.coerceAtMost(0)
    }
}

@Composable
fun rememberWrappedState(vm: MusicViewModel, period: Period): WrappedState {
    val songs by vm.songs.observeAsState(emptyList())
    val albums by vm.albums.observeAsState(emptyList())
    val version by vm.historyVersion.observeAsState(0)
    val loaded by produceState<Pair<WrappedStats, Long?>?>(null, period, version) {
        value = withContext(Dispatchers.IO) { vm.history.wrapped(period) to vm.history.firstPlay() }
    }
    val (stats, first) = loaded ?: (null to null)
    val byId = songs.associateBy { it.id }
    val albumById = albums.associateBy { it.id }
    return WrappedState(
        period = period,
        loading = loaded == null,
        stats = stats,
        songs = stats?.topSongs.orEmpty().mapIndexed { i, s -> RankedSong(i + 1, byId[s.songId], s.title, s.artist, s.plays, s.ms) },
        albums = stats?.topAlbums.orEmpty().map { a ->
            RankedAlbum(a.album, a.artist, a.albumId, albumById[a.albumId]?.songs?.firstOrNull(), a.plays)
        },
        hasEarlier = first != null && first < period.fromMs,
    )
}
