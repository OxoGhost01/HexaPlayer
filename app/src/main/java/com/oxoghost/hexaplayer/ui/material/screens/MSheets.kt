package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.AudioInfo
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.format
import com.oxoghost.hexaplayer.ui.common.formatClock
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.common.qualityLine
import com.oxoghost.hexaplayer.ui.common.rememberCountdown
import com.oxoghost.hexaplayer.ui.material.components.MActionGrid
import com.oxoghost.hexaplayer.ui.material.components.MBottomSheet
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCheckbox
import com.oxoghost.hexaplayer.ui.material.components.MConnectedButtonGroup
import com.oxoghost.hexaplayer.ui.material.components.MGroupOption
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.PickState
import com.oxoghost.hexaplayer.ui.state.PlaylistChoice
import com.oxoghost.hexaplayer.ui.state.PlaylistDraft
import com.oxoghost.hexaplayer.ui.state.Sheet
import com.oxoghost.hexaplayer.ui.state.SleepPick
import com.oxoghost.hexaplayer.ui.state.SongAction
import com.oxoghost.hexaplayer.ui.state.formatSleepLong
import com.oxoghost.hexaplayer.ui.state.sleepPresetsMinutes

@Composable
private fun SheetTitle(text: String) {
    Text(text, Modifier.padding(start = 8.dp, bottom = 16.dp), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
}

@Composable
fun MSongActionsSheet(sheet: Sheet.SongActions, info: AudioInfo?, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val song = sheet.song
    MBottomSheet(onDismiss = actions::closeSheet) {
        Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverImage(song.artModel, song.albumKey, Modifier.size(72.dp), RoundedCornerShape(20.dp))
            Column {
                Text(song.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} · ${formatDuration(song.duration)}", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(info?.qualityLine(upper = true) ?: song.format.uppercase(), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        val all = SongAction.forSheet(sheet)
        val tile = { a: SongAction -> Triple(a.icon, a.material) { actions.songAction(song, a, sheet.playlist) } }
        MActionGrid(
            tiles = all.filter { it != SongAction.Details }.map(tile),
            wide = all.firstOrNull { it == SongAction.Details }?.let(tile),
        )
    }
}

@Composable
fun MAddToSheet(sheet: Sheet.AddTo, choices: List<PlaylistChoice>, pick: PickState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(choices.isEmpty()) }
    val what = sheet.label ?: if (sheet.songs.size == 1) "1 song" else "${sheet.songs.size} songs"
    MBottomSheet(onDismiss = actions::closeSheet) {
        SheetTitle("Add $what to")
        if (creating) {
            MTextField(pick.newName, { pick.newName = it }, "New playlist name", Modifier.fillMaxWidth().padding(bottom = 12.dp))
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(s.primaryContainer)
                    .clickable(role = Role.Button) { creating = true }
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Symbol(Symbols.Add, null, tint = s.onPrimaryContainer)
                Text("New playlist", color = s.onPrimaryContainer, style = MaterialTheme.typography.titleMedium)
            }
        }
        LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 12.dp)) {
            itemsIndexed(choices, key = { _, c -> c.playlist.id }) { i, choice ->
                val checked = choice.playlist.id in pick.checked
                MSegment(i, choices.size, Modifier.padding(bottom = 2.dp), onClick = { pick.toggle(choice.playlist.id) }) {
                    CoverImage(choice.cover, choice.coverKey ?: choice.playlist.name, Modifier.size(44.dp), HexaShapes.coverList)
                    Column(Modifier.weight(1f)) {
                        Text(choice.playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${choice.count} songs", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    MCheckbox(checked, null)
                }
            }
        }
        SheetButtons("Add to ${pick.count}", pick.count > 0, actions::closeSheet) {
            actions.addToPlaylists(sheet.songs, pick.checked.toList(), pick.newName)
        }
    }
}

@Composable
fun MAddSongsSheet(playlist: Playlist, available: List<Song>, pick: PickState, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    MBottomSheet(onDismiss = actions::closeSheet) {
        SheetTitle("Add songs to ${playlist.name}")
        MTextField(pick.filter, { pick.filter = it }, "Filter", Modifier.fillMaxWidth(), leading = { Symbol(Symbols.Search, null) })
        val shown = available.filter { pick.matches(it.title, it.artist, it.album) }
        if (available.isEmpty()) {
            Text("Every song is already in this playlist", Modifier.padding(vertical = 16.dp), color = s.onSurfaceVariant)
        }
        LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 12.dp)) {
            itemsIndexed(shown, key = { _, song -> song.id }) { i, song ->
                MSegment(i, shown.size, Modifier.padding(bottom = 2.dp), onClick = { pick.toggle(song.id.toString()) }) {
                    CoverImage(song.artModel, song.albumKey, Modifier.size(44.dp), HexaShapes.coverList)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    MCheckbox(song.id.toString() in pick.checked, null)
                }
            }
        }
        SheetButtons("Add ${pick.checked.size}", pick.checked.isNotEmpty(), actions::closeSheet) {
            val ids = pick.checked.mapNotNull { it.toLongOrNull() }.toSet()
            actions.addToPlaylists(available.filter { it.id in ids }, listOf(playlist.id), null)
        }
    }
}

@Composable
private fun SheetButtons(confirm: String, enabled: Boolean, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MPillButton("Cancel", onCancel, Modifier.weight(1f), style = MButtonStyle.Text)
        MPillButton(confirm, onConfirm, Modifier.weight(1f), enabled = enabled)
    }
}

@Composable
fun MFileDetailsSheet(rows: List<Pair<String, String>>, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    MBottomSheet(onDismiss = actions::closeSheet) {
        SheetTitle("File details")
        MSegmentedGroup {
            rows.forEachIndexed { i, (label, value) ->
                MSegment(i, rows.size, minHeight = 56) {
                    Column {
                        Text(label.replaceFirstChar { it.uppercase() }, color = s.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                        Text(value, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun MSleepSheet(pick: SleepPick, endsAt: Long?, totalMs: Long, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val left = rememberCountdown(endsAt)
    var editing by remember { mutableStateOf(false) }
    MBottomSheet(onDismiss = actions::closeSheet) {
        SheetTitle("Sleep timer")
        Row(Modifier.padding(bottom = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            val fraction = if (left != null && totalMs > 0) left.toFloat() / totalMs else 0f
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 10.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(s.tertiaryContainer, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
                    drawArc(s.tertiary, -90f, 360f * fraction, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Symbol(Symbols.Bedtime, null, size = 40.dp, filled = true, tint = s.tertiary)
            }
            Column {
                Text(
                    formatDuration(left ?: (pick.seconds * 1000)),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
                    color = if (left != null) s.onSurface else s.onSurfaceVariant,
                )
                Text(
                    "Music stops at ${formatClock(endsAt ?: (System.currentTimeMillis() + pick.seconds * 1000))}",
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        MConnectedButtonGroup(sleepPresetsMinutes.map { MGroupOption("$it min") }, pick.presetIndex, pick::preset)
        MSegment(0, 1, Modifier.padding(top = 12.dp), container = s.surfaceContainerHigh, onClick = { editing = !editing }) {
            Symbol(Symbols.Schedule, null, tint = s.primary)
            Column(Modifier.weight(1f)) {
                Text("Custom", style = MaterialTheme.typography.titleSmall)
                Text("10 seconds to 10 hours", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (pick.presetIndex < 0) Text(formatSleepLong(pick.seconds), style = MaterialTheme.typography.titleSmall, color = s.primary)
        }
        if (editing) {
            MTextField(
                pick.custom,
                pick::typeCustom,
                "Length",
                Modifier.fillMaxWidth().padding(top = 12.dp),
                supportingText = if (pick.customValid) "For example 1h20m, 45m or 90s" else "Use a length like 1h20m, 45m or 90s",
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MPillButton("Turn off", actions::cancelSleepTimer, Modifier.weight(1f), style = MButtonStyle.Outlined,
                enabled = left != null, contentColor = s.error)
            MPillButton("Set ${formatSleepLong(pick.seconds)}", { actions.setSleepTimer(pick.seconds) }, Modifier.weight(1f), enabled = pick.customValid)
        }
    }
}

@Composable
fun MPlaylistEditorSheet(draft: PlaylistDraft, fallbackCover: Any?, fallbackKey: String?, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    val playlist = draft.playlist
    MBottomSheet(onDismiss = actions::closeSheet) {
        SheetTitle(if (playlist == null) "New playlist" else "Edit playlist")
        Row(Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoverImage(draft.coverModel(fallbackCover), playlist?.coverUri ?: fallbackKey ?: draft.name, Modifier.size(96.dp), RoundedCornerShape(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MPillButton("Change cover", { actions.pickImage { draft.cover = CoverChange.Set(it) } },
                    style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Image)
                if (draft.hasCover) {
                    MPillButton("Remove", { draft.cover = CoverChange.Remove }, style = MButtonStyle.Text, size = MButtonSize.M,
                        icon = Symbols.HideImage, contentColor = s.error)
                }
            }
        }
        MTextField(draft.name, { draft.name = it }, "Name", Modifier.fillMaxWidth())
        if (playlist != null) {
            MPillButton(
                if (draft.confirmDelete) "Tap again to delete" else "Delete playlist",
                { if (draft.confirmDelete) actions.deletePlaylist(playlist) else draft.confirmDelete = true },
                Modifier.padding(top = 8.dp),
                style = MButtonStyle.Text,
                icon = Symbols.Delete,
                contentColor = s.error,
            )
        }
        SheetButtons(if (playlist == null) "Create" else "Save", draft.name.isNotBlank(), actions::closeSheet) {
            actions.savePlaylist(playlist, draft.name, draft.cover)
        }
    }
}
