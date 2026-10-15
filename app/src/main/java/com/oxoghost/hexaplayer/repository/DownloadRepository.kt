package com.oxoghost.hexaplayer.repository

import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.data.SyncEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Snapshot of a server-side job (`GET /jobs/{id}`). */
data class ServerJob(
    val status: String,      // running | done | error
    val done: Int,
    val total: Int,
    val message: String,
    val error: String?,
    val folders: List<String>,
    val files: List<String>
)

/** Result of `GET /health`. */
data class ServerHealth(
    val version: String,
    val serverVersion: Int,
    val root: String
)

/**
 * HTTP client for the HexaDL companion server. Uses HttpURLConnection to avoid
 * pulling in extra dependencies, matching the rest of the app's networking.
 */
class DownloadRepository {

    /** Returns the server's spotdl version and download root; throws on failure. */
    suspend fun health(server: String, token: String): ServerHealth = withContext(Dispatchers.IO) {
        val json = httpGet(url(server, "/health"), token)
        val obj = JSONObject(json)
        ServerHealth(
            version = obj.optString("spotdl", "?"),
            serverVersion = obj.optInt("server", 0),
            root = obj.optString("root", "")
        )
    }

    /** Starts a job on the server, returning its server-side id. */
    suspend fun startJob(
        server: String,
        token: String,
        link: String,
        kind: JobKind,
        folder: String?,
        root: String?
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("url", link)
            put("kind", if (kind == JobKind.SYNC) "sync" else "download")
            if (!folder.isNullOrBlank()) put("folder", folder)
            if (!root.isNullOrBlank()) put("root", root)
        }
        val json = httpPost(url(server, "/jobs"), token, body.toString())
        JSONObject(json).getString("id")
    }

    suspend fun pollJob(server: String, token: String, jobId: String): ServerJob =
        withContext(Dispatchers.IO) {
            val json = httpGet(url(server, "/jobs/$jobId"), token)
            val obj = JSONObject(json)
            val foldersArr = obj.optJSONArray("folders") ?: JSONArray()
            val folders = (0 until foldersArr.length()).map { foldersArr.getString(it) }
            val filesArr = obj.optJSONArray("files") ?: JSONArray()
            val files = (0 until filesArr.length()).map { filesArr.getString(it) }
            ServerJob(
                status = obj.optString("status", "running"),
                done = obj.optInt("done", 0),
                total = obj.optInt("total", 0),
                message = obj.optString("message", ""),
                error = obj.optString("error", "").ifBlank { null },
                folders = folders,
                files = files
            )
        }

    suspend fun stopJob(server: String, token: String, jobId: String) {
        withContext(Dispatchers.IO) { httpPost(url(server, "/jobs/$jobId/stop"), token, "{}") }
    }

    suspend fun listSyncs(server: String, token: String): List<SyncEntry> =
        withContext(Dispatchers.IO) {
            val json = httpGet(url(server, "/syncs"), token)
            val arr = JSONObject(json).optJSONArray("syncs") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val s = arr.getJSONObject(i)
                SyncEntry(
                    url = s.optString("url"),
                    folder = s.optString("folder"),
                    root = s.optString("root"),
                    name = s.optString("name").ifBlank { "Playlist" }
                )
            }
        }

    // --- HTTP helpers ------------------------------------------------------ //

    private fun url(server: String, path: String): String {
        val base = server.trim().trimEnd('/')
        val withScheme = if (base.startsWith("http://") || base.startsWith("https://")) base
        else "https://$base"
        return withScheme + path
    }

    private fun open(urlStr: String, token: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        if (token.isNotBlank()) conn.setRequestProperty("X-Token", token)
        return conn
    }

    private fun httpGet(urlStr: String, token: String): String {
        val conn = open(urlStr, token)
        return try {
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                throw IOException("HTTP ${conn.responseCode}: ${err ?: conn.responseMessage}")
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPost(urlStr: String, token: String, body: String): String {
        val conn = open(urlStr, token)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        return try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                throw IOException("HTTP ${conn.responseCode}: ${err ?: conn.responseMessage}")
            }
            conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }
}
