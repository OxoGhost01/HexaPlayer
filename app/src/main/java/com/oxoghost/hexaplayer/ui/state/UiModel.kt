package com.oxoghost.hexaplayer.ui.state

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray

enum class LibrarySection { Songs, Albums, Artists, Playlists, Folders }

enum class SearchScope { All, Songs, Albums, Artists }

/**
 * Sort orders. [terminal] and [material] are the labels each skin shows; the sort row cycles
 * through the orders of the current section (as the 1.x sort button did).
 */
enum class SortKey(val terminal: String, val material: String) {
    TitleAsc("title ↑", "Title A to Z"),
    TitleDesc("title ↓", "Title Z to A"),
    Artist("artist ↑", "Artist"),
    Album("album ↑", "Album"),
    Duration("length ↓", "Longest first"),
    DateAdded("added ↓", "Recently added"),
    NameAsc("name ↑", "Name A to Z"),
    NameDesc("name ↓", "Name Z to A"),
    SongCount("songs ↓", "Most songs"),
    Created("created ↓", "Recently created"),
}

val LibrarySection.sortKeys: List<SortKey>
    get() = when (this) {
        LibrarySection.Songs -> listOf(SortKey.TitleAsc, SortKey.TitleDesc, SortKey.Artist, SortKey.Album, SortKey.Duration, SortKey.DateAdded)
        LibrarySection.Albums -> listOf(SortKey.NameAsc, SortKey.NameDesc, SortKey.Artist, SortKey.SongCount)
        LibrarySection.Artists -> listOf(SortKey.NameAsc, SortKey.NameDesc, SortKey.SongCount)
        LibrarySection.Playlists -> listOf(SortKey.Created, SortKey.NameAsc, SortKey.NameDesc)
        LibrarySection.Folders -> listOf(SortKey.NameAsc, SortKey.NameDesc)
    }

/** UI-only state shared by both skins (section, sorts, folder path, cursors, search). */
class UiModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hexa_ui", Context.MODE_PRIVATE)

    var section by mutableStateOf(LibrarySection.Songs)
    val sorts = mutableStateMapOf<LibrarySection, SortKey>()
    val cursors = mutableStateMapOf<LibrarySection, Int>()

    /** Folder stack below the library root: (relative path, display name). */
    val folderStack = mutableStateListOf<Pair<String, String>>()

    var searchQuery by mutableStateOf("")
    var searchScope by mutableStateOf(SearchScope.All)
    val searchHistory = mutableStateListOf<String>().apply { addAll(loadHistory()) }

    fun sortOf(s: LibrarySection): SortKey = sorts[s] ?: s.sortKeys.first()

    fun cycleSort(s: LibrarySection) {
        val keys = s.sortKeys
        sorts[s] = keys[(keys.indexOf(sortOf(s)) + 1) % keys.size]
    }

    fun rememberSearch(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        searchHistory.remove(q)
        searchHistory.add(0, q)
        while (searchHistory.size > MAX_HISTORY) searchHistory.removeAt(searchHistory.lastIndex)
        saveHistory()
    }

    fun clearSearchHistory() {
        searchHistory.clear()
        saveHistory()
    }

    private fun loadHistory(): List<String> = try {
        val arr = JSONArray(prefs.getString("search_history", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveHistory() {
        prefs.edit().putString("search_history", JSONArray(searchHistory.toList()).toString()).apply()
    }

    companion object {
        private const val MAX_HISTORY = 10
    }
}
