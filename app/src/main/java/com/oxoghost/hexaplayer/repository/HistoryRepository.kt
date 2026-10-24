package com.oxoghost.hexaplayer.repository

import android.content.Context
import com.oxoghost.hexaplayer.data.db.AlbumTotal
import com.oxoghost.hexaplayer.data.db.HexaDatabase
import com.oxoghost.hexaplayer.data.db.NameTotal
import com.oxoghost.hexaplayer.data.db.SongCount
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodKind { Week, Month, Year }

/** A calendar week, month or year; [offset] 0 is the current one, -1 the one before. */
data class Period(val kind: PeriodKind, val offset: Int = 0) {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    val start: LocalDate
        get() {
            val today = LocalDate.now(zone)
            return when (kind) {
                PeriodKind.Week -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
                PeriodKind.Month -> today.withDayOfMonth(1).plusMonths(offset.toLong())
                PeriodKind.Year -> today.withDayOfYear(1).plusYears(offset.toLong())
            }
        }

    val end: LocalDate
        get() = when (kind) {
            PeriodKind.Week -> start.plusWeeks(1)
            PeriodKind.Month -> start.plusMonths(1)
            PeriodKind.Year -> start.plusYears(1)
        }

    val fromMs: Long get() = start.atStartOfDay(zone).toInstant().toEpochMilli()
    val toMs: Long get() = end.atStartOfDay(zone).toInstant().toEpochMilli()

    /** "week of 20 oct", "october 2026", "2026", in lower case. */
    val label: String
        get() = when (kind) {
            PeriodKind.Week -> "week of ${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).lowercase()}"
            PeriodKind.Month -> "${start.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase()} ${start.year}"
            PeriodKind.Year -> "${start.year}"
        }

    /** Labels of the chart buckets: 12 months, the days of the month, or 7 weekdays. */
    val bucketLabels: List<String>
        get() = when (kind) {
            PeriodKind.Week -> DayOfWeek.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) }
            PeriodKind.Month -> (1..start.lengthOfMonth()).map { it.toString() }
            PeriodKind.Year -> java.time.Month.entries.map { it.getDisplayName(TextStyle.NARROW, Locale.ENGLISH) }
        }

    fun bucketOf(epochMs: Long): Int {
        val day = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        return when (kind) {
            PeriodKind.Week -> day.dayOfWeek.value - 1
            PeriodKind.Month -> day.dayOfMonth - 1
            PeriodKind.Year -> day.monthValue - 1
        }
    }

    val isCurrent: Boolean get() = offset == 0
}

data class WrappedStats(
    val period: Period,
    val plays: Int,
    val listenedMs: Long,
    val songs: Int,
    val artists: Int,
    val topSongs: List<SongCount>,
    val topArtists: List<NameTotal>,
    val topAlbums: List<AlbumTotal>,
    /** Hour of day with the most listening, null without plays. */
    val peakHour: Int?,
    /** Most consecutive days with at least one play. */
    val longestStreak: Int,
    /** Listening time per bucket (see [Period.bucketLabels]), in ms. */
    val buckets: List<Long>,
) {
    val empty: Boolean get() = plays == 0
}

/** Play history queries for Home, the detail pages and Wrapped. */
class HistoryRepository(context: Context) {

    private val dao = HexaDatabase.get(context).plays()

    suspend fun recent(limit: Int = 8): List<SongCount> = dao.recent(limit)

    suspend fun mostPlayed(period: Period, limit: Int = 5): List<SongCount> = dao.topSongs(period.fromMs, period.toMs, limit)

    /** Plays per song id, all time. */
    suspend fun playCounts(): Map<Long, Int> = dao.allCounts().associate { it.songId to it.plays }

    suspend fun firstPlay(): Long? = dao.firstPlay()

    suspend fun wrapped(period: Period, topSongs: Int = 5): WrappedStats {
        val from = period.fromMs
        val to = period.toMs
        val totals = dao.totals(from, to)
        val points = dao.points(from, to)
        val buckets = LongArray(period.bucketLabels.size)
        val hours = LongArray(24)
        val zone = ZoneId.systemDefault()
        val days = sortedSetOf<LocalDate>()
        points.forEach { p ->
            buckets[period.bucketOf(p.startedAt).coerceIn(0, buckets.lastIndex)] += p.msPlayed
            val time = Instant.ofEpochMilli(p.startedAt).atZone(zone)
            hours[time.hour] += p.msPlayed
            days += time.toLocalDate()
        }
        var streak = 0
        var best = 0
        var previous: LocalDate? = null
        days.forEach { d ->
            streak = if (previous?.plusDays(1) == d) streak + 1 else 1
            best = maxOf(best, streak)
            previous = d
        }
        return WrappedStats(
            period = period,
            plays = totals.plays,
            listenedMs = totals.ms,
            songs = totals.songs,
            artists = totals.artists,
            topSongs = dao.topSongs(from, to, topSongs),
            topArtists = dao.topArtists(from, to, 3),
            topAlbums = dao.topAlbums(from, to, 4),
            peakHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 },
            longestStreak = best,
            buckets = buckets.toList(),
        )
    }
}
