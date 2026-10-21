package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.ui.common.Symbols

/** The sheet (material) or popup (terminal) open over the current screen. */
@Immutable
sealed interface Sheet {
    /** [playlist] set when opened from a playlist, which adds "remove from playlist". */
    data class SongActions(val song: Song, val playlist: Playlist? = null) : Sheet
    data class AddTo(val songs: List<Song>, val label: String? = null) : Sheet
    data class AddSongs(val playlist: Playlist) : Sheet
    data class FileDetails(val song: Song) : Sheet
    data object Sleep : Sheet

    /** [playlist] null creates a new one. */
    data class PlaylistEditor(val playlist: Playlist?) : Sheet
}

/** Song actions, in display order. [key] is the terminal shortcut letter. */
enum class SongAction(val key: String, val terminal: String, val material: String, val icon: String, val opens: Boolean) {
    PlayNext("n", "play next", "Play next", Symbols.PlaylistPlay, false),
    Queue("q", "add to queue", "Add to queue", Symbols.QueueMusic, false),
    Playlist("p", "add to playlist", "Add to playlist", Symbols.PlaylistAdd, true),
    Album("a", "go to album", "Go to album", Symbols.Album, true),
    Artist("r", "go to artist", "Go to artist", Symbols.Person, true),
    Edit("e", "edit song info", "Edit info", Symbols.Edit, true),
    Remove("x", "remove from playlist", "Remove from playlist", Symbols.Remove, false),
    Details("i", "file details", "File details", Symbols.Info, true);

    companion object {
        fun forSheet(sheet: Sheet.SongActions): List<SongAction> =
            entries.filter { it != Remove || sheet.playlist != null }
    }
}

/** A playlist row of the Add to playlist sheet. */
@Immutable
data class PlaylistChoice(val playlist: Playlist, val count: Int, val cover: Any?, val coverKey: String?)

/** Checked items (playlist ids or song ids), filter text and the inline new playlist name. */
@Stable
class PickState {
    var filter by mutableStateOf("")
    var checked by mutableStateOf<Set<String>>(emptySet())
        private set
    var newName by mutableStateOf("")

    /** Targets the confirm button counts: checked items plus the new playlist, if named. */
    val count: Int get() = checked.size + if (newName.isNotBlank()) 1 else 0

    fun toggle(id: String) {
        checked = if (id in checked) checked - id else checked + id
    }

    fun matches(vararg text: String): Boolean = filter.isBlank() || text.any { it.contains(filter.trim(), ignoreCase = true) }
}

val sleepPresetsMinutes = listOf(15, 30, 45, 60)

/** Sleep timer length being chosen: a preset or a custom text. */
@Stable
class SleepPick(initialSeconds: Long) {
    var seconds by mutableLongStateOf(initialSeconds)
        private set
    var custom by mutableStateOf("")
        private set

    val presetIndex: Int get() = if (custom.isNotEmpty()) -1 else sleepPresetsMinutes.indexOf((seconds / 60).toInt()).takeIf { seconds % 60 == 0L } ?: -1
    val customValid: Boolean get() = custom.isEmpty() || parseSleepDuration(custom) != null

    fun preset(index: Int) {
        custom = ""
        seconds = sleepPresetsMinutes[index] * 60L
    }

    fun typeCustom(text: String) {
        custom = text
        parseSleepDuration(text)?.let { seconds = it }
    }
}

/** Name and cover being edited for a new or existing playlist. */
@Stable
class PlaylistDraft(val playlist: Playlist?) {
    var name by mutableStateOf(playlist?.name.orEmpty())
    var cover by mutableStateOf<CoverChange>(CoverChange.Keep)
    var confirmDelete by mutableStateOf(false)

    fun coverModel(fallback: Any?): Any? = when (val c = cover) {
        is CoverChange.Set -> c.uri
        CoverChange.Remove -> null
        CoverChange.Keep -> playlist?.coverUri ?: fallback
    }

    val hasCover: Boolean get() = cover is CoverChange.Set || (cover == CoverChange.Keep && playlist?.coverUri != null)
}

/** "1h20m", "45m", "90s", "1:20:00" or a plain number of minutes, clamped to 10 s .. 10 h. */
fun parseSleepDuration(text: String): Long? {
    val t = text.trim().lowercase().replace(" ", "")
    if (t.isEmpty()) return null
    val seconds = when {
        ':' in t -> t.split(':').map { it.toLongOrNull() ?: return null }.fold(0L) { acc, part -> acc * 60 + part }
        t.all { it.isDigit() } -> t.toLong() * 60
        else -> {
            val parts = Regex("(\\d+)([hms])").findAll(t).toList()
            if (parts.isEmpty() || parts.sumOf { it.value.length } != t.length) return null
            parts.sumOf { m ->
                val n = m.groupValues[1].toLong()
                when (m.groupValues[2]) {
                    "h" -> n * 3600
                    "m" -> n * 60
                    else -> n
                }
            }
        }
    }
    return seconds.takeIf { it > 0 }?.coerceIn(10L, 36_000L)
}

/** 30m, 1h20m, 45s. */
fun formatSleepShort(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return buildString {
        if (h > 0) append("${h}h")
        if (m > 0) append("${m}m")
        if (s > 0 || isEmpty()) append("${s}s")
    }
}

/** 30 min, 1 h 20 min, 45 s. */
fun formatSleepLong(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return listOfNotNull(
        h.takeIf { it > 0 }?.let { "$it h" },
        m.takeIf { it > 0 }?.let { "$it min" },
        s.takeIf { it > 0 && h == 0L }?.let { "$it s" },
    ).joinToString(" ").ifEmpty { "0 s" }
}
