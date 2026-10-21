package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.AudioInfo
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatClock
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.qualityLine
import com.oxoghost.hexaplayer.ui.common.rememberCountdown
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PickState
import com.oxoghost.hexaplayer.ui.state.PlaylistChoice
import com.oxoghost.hexaplayer.ui.state.PlaylistDraft
import com.oxoghost.hexaplayer.ui.state.Sheet
import com.oxoghost.hexaplayer.ui.state.SleepPick
import com.oxoghost.hexaplayer.ui.state.SongAction
import com.oxoghost.hexaplayer.ui.state.formatSleepShort
import com.oxoghost.hexaplayer.ui.state.sleepPresetsMinutes
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockBar
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCheckMark
import com.oxoghost.hexaplayer.ui.terminal.components.TChoiceRow
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyAction
import com.oxoghost.hexaplayer.ui.terminal.components.TPopup
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TSongActionsPopup(sheet: Sheet.SongActions, info: AudioInfo?, pixelCovers: Boolean, actions: HexaActions) {
    val c = TerminalTheme.colors
    val song = sheet.song
    TPopup(true, title = song.title, onDismiss = actions::closeSheet) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PixelCover(song.artModel, song.albumKey, 8, Modifier.size(56.dp), pixelCovers)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TText(song.artist)
                TText(listOfNotNull(song.album, song.year.takeIf { it > 0 }?.toString(), formatDuration(song.duration)).joinToString(" · "), color = c.dim)
                TText(info?.qualityLine() ?: song.format, color = c.dim)
            }
        }
        TDashedDivider(Modifier.padding(top = 8.dp))
        SongAction.forSheet(sheet).forEachIndexed { i, action ->
            TKeyAction(
                action.key,
                action.terminal,
                { actions.songAction(song, action, sheet.playlist) },
                trailing = if (action.opens) "›" else null,
                highlighted = i == 0,
                labelColor = if (action == SongAction.Remove) c.error else c.foreground,
            )
        }
    }
}

@Composable
fun TAddToPopup(sheet: Sheet.AddTo, choices: List<PlaylistChoice>, pick: PickState, actions: HexaActions) {
    val c = TerminalTheme.colors
    val what = sheet.label ?: if (sheet.songs.size == 1) "1 song" else "${sheet.songs.size} songs"
    TPopup(true, title = "add $what to", onDismiss = actions::closeSheet) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TInput(pick.filter, { pick.filter = it }, prompt = ">", placeholder = "filter")
            val shown = choices.filter { pick.matches(it.playlist.name) }
            LazyColumn(Modifier.heightIn(max = 280.dp)) {
                items(shown, key = { it.playlist.id }) { choice ->
                    CheckRow(choice.playlist.id in pick.checked, choice.playlist.name, "${choice.count}") { pick.toggle(choice.playlist.id) }
                }
            }
            if (choices.isEmpty()) TText("no playlists yet", color = c.dim)
            TDashedDivider()
            TText("or create a new playlist", color = c.dim)
            TInput(pick.newName, { pick.newName = it }, prompt = "+", placeholder = "playlist name")
            Buttons(
                confirm = "add to ${pick.count}",
                enabled = pick.count > 0,
                onCancel = actions::closeSheet,
                onConfirm = { actions.addToPlaylists(sheet.songs, pick.checked.toList(), pick.newName) },
            )
        }
    }
}

@Composable
fun TAddSongsPopup(playlist: Playlist, available: List<Song>, pick: PickState, actions: HexaActions) {
    val c = TerminalTheme.colors
    TPopup(true, title = "add songs to ${playlist.name}", onDismiss = actions::closeSheet) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TInput(pick.filter, { pick.filter = it }, prompt = ">", placeholder = "filter")
            val shown = available.filter { pick.matches(it.title, it.artist, it.album) }
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(shown, key = { it.id }) { song ->
                    CheckRow(song.id.toString() in pick.checked, song.title, song.artist) { pick.toggle(song.id.toString()) }
                }
            }
            if (available.isEmpty()) TText("every song is already in this playlist", color = c.dim)
            Buttons(
                confirm = "add ${pick.checked.size}",
                enabled = pick.checked.isNotEmpty(),
                onCancel = actions::closeSheet,
                onConfirm = {
                    val ids = pick.checked.mapNotNull { it.toLongOrNull() }.toSet()
                    actions.addToPlaylists(available.filter { it.id in ids }, listOf(playlist.id), null)
                },
            )
        }
    }
}

@Composable
private fun CheckRow(checked: Boolean, title: String, trailing: String, onToggle: () -> Unit) {
    val c = TerminalTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(role = Role.Checkbox, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TCheckMark(checked)
        TText(title, Modifier.weight(1f))
        TText(trailing, color = c.dim)
    }
}

@Composable
private fun Buttons(confirm: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TButton("cancel", onCancel, Modifier.weight(1f), large = true)
        TButton(confirm, onConfirm, Modifier.weight(1f), filled = true, large = true, enabled = enabled)
    }
}

@Composable
fun TFileDetailsPopup(song: Song, rows: List<Pair<String, String>>, actions: HexaActions) {
    val c = TerminalTheme.colors
    TPopup(true, title = song.displayName.ifBlank { song.title }, onDismiss = actions::closeSheet) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { (label, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TText(label, Modifier.width(96.dp), color = c.dim)
                    TText(value, Modifier.weight(1f), maxLines = 3)
                }
            }
            TButton("close", actions::closeSheet, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), large = true)
        }
    }
}

@Composable
fun TSleepPopup(pick: SleepPick, endsAt: Long?, totalMs: Long, actions: HexaActions) {
    val c = TerminalTheme.colors
    val left = rememberCountdown(endsAt)
    TPopup(true, title = "sleep timer", onDismiss = actions::closeSheet, borderColor = c.warn) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TText(formatDuration(left ?: (pick.seconds * 1000)), color = if (left != null) c.warn else c.dim, style = TerminalTheme.type.hero)
                TText(
                    if (left != null && endsAt != null) "left · stops at ${formatClock(endsAt)}"
                    else "stops at ${formatClock(System.currentTimeMillis() + pick.seconds * 1000)}",
                    Modifier.padding(bottom = 6.dp),
                    color = c.dim,
                )
            }
            TBlockBar(if (left != null && totalMs > 0) left.toFloat() / totalMs else 0f, color = c.warn)
            TChoiceRow(sleepPresetsMinutes.map { "${it}m" }, pick.presetIndex, pick::preset, accent = c.warn)
            TInput(
                pick.custom,
                pick::typeCustom,
                prompt = "custom",
                placeholder = "1h20m",
                trailing = { TText(if (pick.customValid) "10s .. 10h" else "? 1h20m, 45m", color = if (pick.customValid) c.dim else c.error) },
            )
            Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (left != null) TButton("cancel timer", actions::cancelSleepTimer, Modifier.weight(1f), color = c.error, large = true)
                else TButton("cancel", actions::closeSheet, Modifier.weight(1f), large = true)
                TButton("set ${formatSleepShort(pick.seconds)}", { actions.setSleepTimer(pick.seconds) }, Modifier.weight(1f),
                    filled = true, color = c.warn, large = true, enabled = pick.customValid)
            }
        }
    }
}

@Composable
fun TPlaylistEditorPopup(draft: PlaylistDraft, fallbackCover: Any?, fallbackKey: String?, pixelCovers: Boolean, actions: HexaActions) {
    val c = TerminalTheme.colors
    val playlist = draft.playlist
    TPopup(true, title = if (playlist == null) "new playlist" else "edit playlist", onDismiss = actions::closeSheet) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TInput(draft.name, { draft.name = it }, prompt = "+", placeholder = "playlist name")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelCover(draft.coverModel(fallbackCover), playlist?.coverUri ?: fallbackKey ?: draft.name, 8, Modifier.size(48.dp), pixelCovers)
                TButton("pick image", { actions.pickImage { draft.cover = CoverChange.Set(it) } }, Modifier.weight(1f))
                if (draft.hasCover) TButton("clear", { draft.cover = CoverChange.Remove }, color = c.error)
            }
            if (playlist != null) {
                TButton(
                    if (draft.confirmDelete) "tap again to delete" else "delete playlist",
                    { if (draft.confirmDelete) actions.deletePlaylist(playlist) else draft.confirmDelete = true },
                    Modifier.fillMaxWidth(),
                    color = c.error,
                )
            }
            Buttons(
                confirm = if (playlist == null) "create" else ":w save",
                enabled = draft.name.isNotBlank(),
                onCancel = actions::closeSheet,
                onConfirm = { actions.savePlaylist(playlist, draft.name, draft.cover) },
            )
        }
    }
}
