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

/** HTTP client for the HexaDL server, which always runs in Termux on this phone. */
class DownloadRepository {

    /** Returns the server's spotdl version and download root; throws on failure. */
    suspend fun health(): ServerHealth = withContext(Dispatchers.IO) {
        val json = httpGet("/health")
        val obj = JSONObject(json)
        ServerHealth(
            version = obj.optString("spotdl", "?"),
            serverVersion = obj.optInt("server", 0),
            root = obj.optString("root", "")
        )
    }

    /** Starts a job on the server, returning its server-side id. */
    suspend fun startJob(
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
        val json = httpPost("/jobs", body.toString())
        JSONObject(json).getString("id")
    }

    suspend fun pollJob(jobId: String): ServerJob =
        withContext(Dispatchers.IO) {
            val json = httpGet("/jobs/$jobId")
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

    suspend fun stopJob(jobId: String) {
        withContext(Dispatchers.IO) { httpPost("/jobs/$jobId/stop", "{}") }
    }

    suspend fun listSyncs(): List<SyncEntry> =
        withContext(Dispatchers.IO) {
            val json = httpGet("/syncs")
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

    private fun open(path: String): HttpURLConnection {
        val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        return conn
    }

    private fun httpGet(path: String): String {
        val conn = open(path)
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

    private fun httpPost(path: String, body: String): String {
        val conn = open(path)
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

    companion object {
        /** Must match HEXADL_PORT in termux/setup.sh */
        private const val BASE_URL = "http://127.0.0.1:47823"
    }
}
