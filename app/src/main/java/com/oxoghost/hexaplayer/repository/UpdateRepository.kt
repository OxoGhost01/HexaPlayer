package com.oxoghost.hexaplayer.repository

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val latestVersion: String, val downloadUrl: String, val changelog: String = "")

class UpdateRepository {

    companion object {
        private const val API_URL =
            "https://api.github.com/repos/OxoGhost01/HexaPlayer/releases/latest"

        /**
         * Returns true if [latest] is strictly newer than [current].
         * Compares dot-separated numeric version components (e.g. "1.3" vs "1.3.1").
         */
        fun isNewerVersion(current: String, latest: String): Boolean {
            val c = current.trim().split(".").mapNotNull { it.toIntOrNull() }
            val l = latest.trim().split(".").mapNotNull { it.toIntOrNull() }
            for (i in 0 until maxOf(c.size, l.size)) {
                val cv = c.getOrNull(i) ?: 0
                val lv = l.getOrNull(i) ?: 0
                if (lv > cv) return true
                if (lv < cv) return false
            }
            return false
        }
    }

    /**
     * Queries the GitHub releases API and returns update info if a release exists.
     * Returns null on network/parse error. Must be called off the main thread.
     */
    fun checkForUpdate(): UpdateInfo? {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connect()

            if (conn.responseCode != 200) return null

            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val obj = JSONObject(json)

            val tagName = obj.getString("tag_name").removePrefix("v")
            val htmlUrl = obj.getString("html_url")
            val changelog = obj.optString("body", "")

            // Prefer a direct APK asset download URL; fall back to the release page
            val assets = obj.optJSONArray("assets")
            val apkUrl = if (assets != null) {
                (0 until assets.length()).mapNotNull { i ->
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        asset.getString("browser_download_url")
                    } else null
                }.firstOrNull()
            } else null

            UpdateInfo(tagName, apkUrl ?: htmlUrl, changelog)
        } catch (_: Exception) {
            null
        }
    }
}
