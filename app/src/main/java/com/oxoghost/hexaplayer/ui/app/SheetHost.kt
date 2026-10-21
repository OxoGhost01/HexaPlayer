package com.oxoghost.hexaplayer.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.fileDetails
import com.oxoghost.hexaplayer.ui.material.screens.MAddSongsSheet
import com.oxoghost.hexaplayer.ui.material.screens.MAddToSheet
import com.oxoghost.hexaplayer.ui.material.screens.MFileDetailsSheet
import com.oxoghost.hexaplayer.ui.material.screens.MPlaylistEditorSheet
import com.oxoghost.hexaplayer.ui.material.screens.MSleepSheet
import com.oxoghost.hexaplayer.ui.material.screens.MSongActionsSheet
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PickState
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.PlaylistChoice
import com.oxoghost.hexaplayer.ui.state.PlaylistDraft
import com.oxoghost.hexaplayer.ui.state.Sheet
import com.oxoghost.hexaplayer.ui.state.SleepPick
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.state.rememberAudioInfo
import com.oxoghost.hexaplayer.ui.terminal.screens.TAddSongsPopup
import com.oxoghost.hexaplayer.ui.terminal.screens.TAddToPopup
import com.oxoghost.hexaplayer.ui.terminal.screens.TFileDetailsPopup
import com.oxoghost.hexaplayer.ui.terminal.screens.TPlaylistEditorPopup
import com.oxoghost.hexaplayer.ui.terminal.screens.TSleepPopup
import com.oxoghost.hexaplayer.ui.terminal.screens.TSongActionsPopup
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/** Draws the open sheet (material) or popup (terminal) with its shared state. */
@Composable
fun SheetHost(terminal: Boolean, pixelCovers: Boolean, ui: UiModel, vm: MusicViewModel, playback: PlaybackState, actions: HexaActions) {
    val sheet = ui.sheet ?: return
    val songs by vm.songs.observeAsState(emptyList())
    val playlists by vm.playlists.observeAsState(emptyList())
    when (sheet) {
        is Sheet.SongActions -> {
            val info = rememberAudioInfo(vm, sheet.song)
            if (terminal) TSongActionsPopup(sheet, info, pixelCovers, actions) else MSongActionsSheet(sheet, info, actions)
        }
        is Sheet.AddTo -> {
            val pick = remember(sheet) { PickState() }
            val choices = remember(playlists, songs) {
                val byId = songs.associateBy { it.id }
                playlists.sortedByDescending { it.lastEdited }.map { p ->
                    val first = p.songIds.firstNotNullOfOrNull { byId[it] }
                    PlaylistChoice(p, p.songIds.count { it in byId }, p.coverUri ?: first?.artModel, p.coverUri ?: first?.albumKey)
                }
            }
            if (terminal) TAddToPopup(sheet, choices, pick, actions) else MAddToSheet(sheet, choices, pick, actions)
        }
        is Sheet.AddSongs -> {
            val pick = remember(sheet) { PickState() }
            val current = playlists.firstOrNull { it.id == sheet.playlist.id } ?: sheet.playlist
            val available = remember(songs, current) {
                val inside = current.songIds.toSet()
                songs.filter { it.id !in inside }.sortedBy { it.title.lowercase() }
            }
            if (terminal) TAddSongsPopup(current, available, pick, actions) else MAddSongsSheet(current, available, pick, actions)
        }
        is Sheet.FileDetails -> {
            val info = rememberAudioInfo(vm, sheet.song)
            val rows = fileDetails(sheet.song, info)
            if (terminal) TFileDetailsPopup(sheet.song, rows, actions) else MFileDetailsSheet(rows, actions)
        }
        Sheet.Sleep -> {
            val pick = remember { SleepPick(vm.sleepTimerTotalMs.takeIf { it > 0 }?.div(1000) ?: 30 * 60L) }
            if (terminal) TSleepPopup(pick, playback.sleepEndsAt, vm.sleepTimerTotalMs, actions)
            else MSleepSheet(pick, playback.sleepEndsAt, vm.sleepTimerTotalMs, actions)
        }
        is Sheet.PlaylistEditor -> {
            val draft = remember(sheet) { PlaylistDraft(sheet.playlist) }
            val fallback = remember(sheet, songs) {
                val first = sheet.playlist?.songIds?.firstNotNullOfOrNull { id -> songs.firstOrNull { it.id == id } }
                first?.artModel to first?.albumKey
            }
            if (terminal) TPlaylistEditorPopup(draft, fallback.first, fallback.second, pixelCovers, actions)
            else MPlaylistEditorSheet(draft, fallback.first, fallback.second, actions)
        }
    }
}
