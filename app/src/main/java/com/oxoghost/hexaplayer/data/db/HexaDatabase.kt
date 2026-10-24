package com.oxoghost.hexaplayer.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** One listen: counted after 30 s or half the track (see PlayTracker). */
@Entity(tableName = "play_events", indices = [Index("startedAt"), Index("songId")])
data class PlayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val msPlayed: Long,
    val completed: Boolean,
    // Kept so stats survive songs being removed or retagged.
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
)

/** Lyrics fetched from LRCLIB, kept for offline use and lyrics search. */
@Entity(tableName = "lyrics_cache")
data class CachedLyrics(
    @PrimaryKey val songId: Long,
    val title: String,
    val artist: String,
    /** Plain text, one line per lyric line. */
    val text: String,
    /** LRC timestamps in ms, comma separated, empty when not synced. */
    val timestamps: String,
    val fetchedAt: Long,
)

data class SongCount(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val plays: Int,
    @ColumnInfo(name = "ms") val ms: Long,
)

data class NameTotal(val name: String, val plays: Int, val ms: Long)

data class AlbumTotal(val album: String, val artist: String, val albumId: Long, val plays: Int, val ms: Long)

data class Totals(val plays: Int, val ms: Long, val songs: Int, val artists: Int)

data class TimePoint(val startedAt: Long, val msPlayed: Long)

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEvent)

    @Query("SELECT COUNT(*) AS plays, COALESCE(SUM(msPlayed), 0) AS ms, COUNT(DISTINCT songId) AS songs, COUNT(DISTINCT artist) AS artists FROM play_events WHERE startedAt >= :from AND startedAt < :to")
    suspend fun totals(from: Long, to: Long): Totals

    @Query("SELECT songId, title, artist, album, albumId, COUNT(*) AS plays, SUM(msPlayed) AS ms FROM play_events WHERE startedAt >= :from AND startedAt < :to GROUP BY songId ORDER BY plays DESC, ms DESC LIMIT :limit")
    suspend fun topSongs(from: Long, to: Long, limit: Int): List<SongCount>

    @Query("SELECT artist AS name, COUNT(*) AS plays, SUM(msPlayed) AS ms FROM play_events WHERE startedAt >= :from AND startedAt < :to GROUP BY artist ORDER BY ms DESC LIMIT :limit")
    suspend fun topArtists(from: Long, to: Long, limit: Int): List<NameTotal>

    @Query("SELECT album, artist, albumId, COUNT(*) AS plays, SUM(msPlayed) AS ms FROM play_events WHERE startedAt >= :from AND startedAt < :to GROUP BY albumId ORDER BY plays DESC LIMIT :limit")
    suspend fun topAlbums(from: Long, to: Long, limit: Int): List<AlbumTotal>

    @Query("SELECT startedAt, msPlayed FROM play_events WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    suspend fun points(from: Long, to: Long): List<TimePoint>

    /** Latest play of each song, newest first. */
    @Query("SELECT songId, title, artist, album, albumId, COUNT(*) AS plays, SUM(msPlayed) AS ms FROM play_events GROUP BY songId ORDER BY MAX(startedAt) DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SongCount>

    @Query("SELECT songId, title, artist, album, albumId, COUNT(*) AS plays, SUM(msPlayed) AS ms FROM play_events GROUP BY songId")
    suspend fun allCounts(): List<SongCount>

    @Query("SELECT MIN(startedAt) FROM play_events")
    suspend fun firstPlay(): Long?
}

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(lyrics: CachedLyrics)

    @Query("SELECT * FROM lyrics_cache WHERE songId = :songId")
    suspend fun get(songId: Long): CachedLyrics?

    @Query("SELECT * FROM lyrics_cache WHERE text LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun search(query: String, limit: Int): List<CachedLyrics>
}

@Database(entities = [PlayEvent::class, CachedLyrics::class], version = 1, exportSchema = false)
abstract class HexaDatabase : RoomDatabase() {
    abstract fun plays(): PlayEventDao
    abstract fun lyrics(): LyricsDao

    companion object {
        @Volatile
        private var instance: HexaDatabase? = null

        fun get(context: Context): HexaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, HexaDatabase::class.java, "hexa.db")
                .build()
                .also { instance = it }
        }
    }
}
