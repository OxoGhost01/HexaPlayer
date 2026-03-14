package com.oxoghost.hexaplayer.repository

import android.content.Context
import com.oxoghost.hexaplayer.data.Playlist
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getAll(): List<Playlist> {
        val json = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        val type = object : TypeToken<List<Playlist>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun save(playlist: Playlist) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) list[idx] = playlist else list.add(playlist)
        persist(list)
    }

    fun delete(playlistId: String) {
        persist(getAll().filter { it.id != playlistId })
    }

    fun addSong(playlistId: String, songId: Long) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        val pl = list[idx]
        if (songId !in pl.songIds) {
            list[idx] = pl.copy(songIds = pl.songIds + songId)
        }
        persist(list)
    }

    fun removeSong(playlistId: String, songId: Long) {
        val list = getAll().toMutableList()
        val idx = list.indexOfFirst { it.id == playlistId }
        if (idx < 0) return
        list[idx] = list[idx].copy(songIds = list[idx].songIds - songId)
        persist(list)
    }

    private fun persist(list: List<Playlist>) {
        prefs.edit().putString(KEY_PLAYLISTS, gson.toJson(list)).apply()
    }

    companion object {
        private const val PREFS_NAME = "hexa_playlists"
        private const val KEY_PLAYLISTS = "playlists"
    }
}
