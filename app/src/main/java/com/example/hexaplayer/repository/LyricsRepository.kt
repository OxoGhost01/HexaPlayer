package com.example.hexaplayer.repository

import com.example.hexaplayer.data.LyricsState
import com.example.hexaplayer.data.Song
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository {

    private val gson = Gson()

    private data class LrcLibResponse(
        @SerializedName("plainLyrics") val plainLyrics: String?,
        @SerializedName("syncedLyrics") val syncedLyrics: String?,
        @SerializedName("instrumental") val instrumental: Boolean = false
    )

    suspend fun fetchLyrics(song: Song): LyricsState = withContext(Dispatchers.IO) {
        try {
            val artist = URLEncoder.encode(song.artist, "UTF-8")
            val title  = URLEncoder.encode(song.title,  "UTF-8")
            val album  = URLEncoder.encode(song.album,  "UTF-8")
            val dur    = song.duration / 1000

            val url = "https://lrclib.net/api/get?" +
                "artist_name=$artist&track_name=$title&album_name=$album&duration=$dur"

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("Lrclib-Client", "HexaPlayer/1.0 (github.com/example/hexaplayer)")
            conn.connectTimeout = 8_000
            conn.readTimeout    = 8_000

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                parseResponse(json)
            } else {
                conn.disconnect()
                LyricsState.NotFound
            }
        } catch (_: Exception) {
            LyricsState.NotFound
        }
    }

    private fun parseResponse(json: String): LyricsState {
        val r = gson.fromJson(json, LrcLibResponse::class.java) ?: return LyricsState.NotFound
        if (r.instrumental) return LyricsState.NotFound

        val synced = r.syncedLyrics
        if (!synced.isNullOrBlank()) {
            val (lines, timestamps) = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return LyricsState.Found(lines, timestamps, isSynced = true)
            }
        }

        val plain = r.plainLyrics
        if (!plain.isNullOrBlank()) {
            val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
            return LyricsState.Found(lines, null, isSynced = false)
        }

        return LyricsState.NotFound
    }

    /** Returns (textLines, timestampsMs). Both lists are parallel. */
    private fun parseLrc(lrc: String): Pair<List<String>, List<Long>> {
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})](.*)""")
        val lines = mutableListOf<String>()
        val timestamps = mutableListOf<Long>()

        lrc.lines().forEach { raw ->
            val m = regex.find(raw.trim()) ?: return@forEach
            val (min, sec, ms, text) = m.destructured
            val msMultiplier = if (ms.length == 2) 10L else 1L
            val ts = min.toLong() * 60_000L + sec.toLong() * 1_000L + ms.toLong() * msMultiplier
            val t = text.trim()
            if (t.isNotEmpty()) {
                timestamps.add(ts)
                lines.add(t)
            }
        }

        // Sort by timestamp
        val sorted = timestamps.zip(lines).sortedBy { it.first }
        return sorted.map { it.second } to sorted.map { it.first }
    }
}
