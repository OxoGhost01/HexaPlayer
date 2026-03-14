package com.oxoghost.hexaplayer.repository

import com.oxoghost.hexaplayer.data.DownloadItem
import com.oxoghost.hexaplayer.data.DownloadStatus
import com.oxoghost.hexaplayer.data.JamendoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class DownloadRepository {

    suspend fun search(query: String, clientId: String): List<JamendoTrack> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.jamendo.com/v3.0/tracks/" +
                "?client_id=$clientId" +
                "&format=json" +
                "&search=$encoded" +
                "&fuzzytags=$encoded" +
                "&audioformat=mp32" +
                "&imagesize=200" +
                "&limit=20"
            val json = httpGet(url)
            val root = JSONObject(json)
            val headers = root.optJSONObject("headers")
            val code = headers?.optInt("code", 0) ?: 0
            if (code != 0) {
                val msg = headers?.optString("error_message") ?: "Unknown error"
                throw IOException("Jamendo API error $code: $msg")
            }
            val results = root.getJSONArray("results")
            (0 until results.length()).mapNotNull { i ->
                val t = results.getJSONObject(i)
                val audioUrl = t.optString("audiodownload").ifEmpty {
                    t.optString("audio")
                }
                if (audioUrl.isEmpty()) return@mapNotNull null
                JamendoTrack(
                    id = t.optString("id"),
                    name = t.optString("name"),
                    artistName = t.optString("artist_name"),
                    albumName = t.optString("album_name"),
                    albumImage = t.optString("album_image"),
                    audioUrl = audioUrl
                )
            }
        }

    suspend fun download(
        item: DownloadItem,
        cancelFlag: () -> Boolean,
        onProgress: (Int) -> Unit
    ): DownloadStatus = withContext(Dispatchers.IO) {
        val dir = File(item.outputFolder).also { it.mkdirs() }

        val safeName = buildSafeFilename(item.artist, item.title)
        val outFile = File(dir, safeName)
        if (outFile.exists()) return@withContext DownloadStatus.SKIPPED

        val conn = openConnection(item.audioUrl)
        val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
        var downloaded = 0L

        try {
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8_192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        if (cancelFlag()) {
                            outFile.delete()
                            return@withContext DownloadStatus.CANCELLED
                        }
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            onProgress((downloaded * 100 / totalBytes).toInt())
                        }
                    }
                }
            }
            DownloadStatus.DONE
        } catch (e: Exception) {
            outFile.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun buildSafeFilename(artist: String, title: String): String {
        val illegal = Regex("""[/\\:*?"<>|]""")
        val a = artist.replace(illegal, "_").take(60)
        val t = title.replace(illegal, "_").take(80)
        return if (a.isNotEmpty()) "$a - $t.mp3" else "$t.mp3"
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        var location = urlStr
        repeat(5) {
            val conn = URL(location).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            val code = conn.responseCode
            if (code in 300..399) {
                location = conn.getHeaderField("Location") ?: return conn
                conn.disconnect()
            } else {
                return conn
            }
        }
        return URL(location).openConnection() as HttpURLConnection
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        return try {
            conn.inputStream.bufferedReader().readText()
        } catch (e: IOException) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            throw IOException("HTTP ${conn.responseCode}: ${err ?: e.message}")
        } finally {
            conn.disconnect()
        }
    }
}
