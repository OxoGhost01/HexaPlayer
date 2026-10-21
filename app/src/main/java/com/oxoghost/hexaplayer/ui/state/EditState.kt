package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.AudioInfo
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.repository.SongTags
import com.oxoghost.hexaplayer.repository.TagField
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Edit song info, for one song or (batch) the common fields of several. */
@Stable
class EditState(val songs: List<Song>, initial: SongTags) {
    val batch = songs.size > 1
    val fields: List<TagField> = if (batch) TagField.entries.filter { it.batch } else TagField.entries

    var initial by mutableStateOf(initial)
        private set
    var values by mutableStateOf(initial)
    var cover by mutableStateOf<CoverChange>(CoverChange.Keep)
    var saving by mutableStateOf(false)

    /** Fields whose value differs between the selected songs (batch only), shown as "keep". */
    var mixed by mutableStateOf<Set<TagField>>(emptySet())
        private set

    val changed: List<TagField> get() = fields.filter { values[it] != initial[it] }
    val dirty: Boolean get() = changed.isNotEmpty() || cover != CoverChange.Keep
    val changes: Map<TagField, String> get() = changed.associateWith { values[it] }

    /** What the cover preview shows: the picked image, nothing (removed) or the current art. */
    val coverModel: Any?
        get() = when (val c = cover) {
            is CoverChange.Set -> c.uri
            CoverChange.Remove -> null
            CoverChange.Keep -> songs.firstOrNull()?.artModel
        }

    fun set(field: TagField, value: String) {
        values = values.with(field, value)
    }

    internal fun loaded(tags: SongTags, mixed: Set<TagField> = emptySet()) {
        val untouched = !dirty
        initial = tags
        this.mixed = mixed
        if (untouched) values = tags
    }
}

@Composable
fun rememberEditState(vm: MusicViewModel, ids: List<Long>): EditState? {
    val all by vm.songs.observeAsState(emptyList())
    val songs = remember(all, ids) {
        val byId = all.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }
    if (songs.isEmpty()) return null
    val state = remember(ids) { EditState(songs, songs.first().let { vm.tags.fromLibrary(it) }) }
    LaunchedEffect(ids) {
        if (songs.size == 1) {
            state.loaded(withContext(Dispatchers.IO) { vm.tags.read(songs.first()) })
        } else {
            val each = songs.map { vm.tags.fromLibrary(it) }
            val mixed = state.fields.filter { f -> each.any { it[f] != each.first()[f] } }.toSet()
            val common = mixed.fold(each.first()) { tags, f -> tags.with(f, "") }
            state.loaded(common, mixed)
        }
    }
    return state
}

/** Format, bit depth and sample rate of a song, read in the background. */
@Composable
fun rememberAudioInfo(vm: MusicViewModel, song: Song?): AudioInfo? {
    val info by produceState<AudioInfo?>(null, song?.id) {
        value = song?.let { withContext(Dispatchers.IO) { runCatching { vm.tags.audioInfo(it) }.getOrNull() } }
    }
    return info
}
