package com.oxoghost.hexaplayer.ui.state

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray

enum class LibrarySection { Songs, Albums, Artists, Playlists, Folders }

enum class SearchScope { All, Songs, Albums, Artists, Lyrics }

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
    Edited("edited ↓", "Recently edited"),
}

val LibrarySection.sortKeys: List<SortKey>
    get() = when (this) {
        LibrarySection.Songs -> listOf(SortKey.TitleAsc, SortKey.TitleDesc, SortKey.Artist, SortKey.Album, SortKey.Duration, SortKey.DateAdded)
        LibrarySection.Albums -> listOf(SortKey.NameAsc, SortKey.NameDesc, SortKey.Artist, SortKey.SongCount)
        LibrarySection.Artists -> listOf(SortKey.NameAsc, SortKey.NameDesc, SortKey.SongCount)
        LibrarySection.Playlists -> listOf(SortKey.Edited, SortKey.NameAsc, SortKey.NameDesc)
        LibrarySection.Folders -> listOf(SortKey.NameAsc, SortKey.NameDesc)
    }

/**
 * UI-only state shared by both skins (section, sorts, folder path, cursors, search). The library
 * view (section, sorts, album layout, grid size) is kept across launches.
 */
class UiModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("hexa_ui", Context.MODE_PRIVATE)

    private var savedSection by mutableStateOf(
        prefs.getString("section", null)?.let { name -> LibrarySection.entries.firstOrNull { it.name == name } } ?: LibrarySection.Songs
    )
    var section: LibrarySection
        get() = savedSection
        set(value) {
            savedSection = value
            prefs.edit().putString("section", value.name).apply()
        }

    val sorts = mutableStateMapOf<LibrarySection, SortKey>().apply {
        LibrarySection.entries.forEach { s ->
            prefs.getString("sort_${s.name}", null)?.let { name -> s.sortKeys.firstOrNull { it.name == name } }?.let { put(s, it) }
        }
    }

    /** Albums as a list instead of a grid. */
    var albumsAsList by mutableStateOf(prefs.getBoolean("albums_as_list", false))
        private set

    /** Albums and playlists per row in the material library (artists get one more). */
    var gridColumns by mutableIntStateOf(prefs.getInt("grid_columns", 2).coerceIn(MIN_COLUMNS, MAX_COLUMNS))
        private set
    val cursors = mutableStateMapOf<LibrarySection, Int>()

    /** Folder stack below the library root: (relative path, display name). */
    val folderStack = mutableStateListOf<Pair<String, String>>()

    var searchQuery by mutableStateOf("")
    var searchScope by mutableStateOf(SearchScope.All)
    val searchHistory = mutableStateListOf<String>().apply { addAll(loadHistory()) }

    var sheet by mutableStateOf<Sheet?>(null)
    val selection = Selection()

    /** First launch done (or the app was set up before 2.0). */
    var onboarded by mutableStateOf(prefs.getBoolean("onboarded", false))
        private set

    fun finishOnboarding() {
        onboarded = true
        prefs.edit().putBoolean("onboarded", true).apply()
    }

    fun sortOf(s: LibrarySection): SortKey = sorts[s] ?: s.sortKeys.first()

    fun cycleSort(s: LibrarySection) {
        val keys = s.sortKeys
        setSort(s, keys[(keys.indexOf(sortOf(s)) + 1) % keys.size])
    }

    fun setSort(s: LibrarySection, key: SortKey) {
        sorts[s] = key
        prefs.edit().putString("sort_${s.name}", key.name).apply()
    }

    fun toggleAlbumsAsList() {
        albumsAsList = !albumsAsList
        prefs.edit().putBoolean("albums_as_list", albumsAsList).apply()
    }

    fun changeGridColumns(columns: Int) {
        gridColumns = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
        prefs.edit().putInt("grid_columns", gridColumns).apply()
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
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 4
    }
}
