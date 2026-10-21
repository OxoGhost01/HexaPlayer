package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.repository.AudioInfo
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.repository.TagField
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.state.EditState
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

private val TagField.key: String
    get() = when (this) {
        TagField.AlbumArtist -> "album_artist"
        else -> name.lowercase()
    }

/** Vim-like tag editor: numbered `key = value` lines, changed values in warn. */
@Composable
fun TEditScreen(edit: EditState?, info: AudioInfo?, pixelCovers: Boolean, actions: HexaActions) {
    val c = TerminalTheme.colors
    if (edit == null) {
        TScreen(header = { THeader("tags: ", onBack = actions::back) }, status = { TStatusLine(TMode.NOR, "not found") }) {
            TText("this song is no longer in the library", Modifier.padding(12.dp), color = c.dim)
        }
        return
    }
    val song = edit.songs.first()
    val changes = edit.changed.size + if (edit.cover != CoverChange.Keep) 1 else 0
    TScreen(
        header = { THeader("tags: ", current = if (edit.batch) "${edit.songs.size} files" else song.displayName, onBack = actions::back) },
        status = {},
        modifier = Modifier.imePadding(),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (!edit.batch) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    PixelCover(edit.coverModel, song.albumKey, 16, Modifier.size(88.dp), pixelCovers, "Cover")
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TText("cover · " + coverLabel(edit.cover, info), color = c.dim)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TButton("pick image", { actions.pickImage { edit.cover = CoverChange.Set(it) } }, Modifier.weight(1f))
                            TButton("clear", { edit.cover = CoverChange.Remove }, color = c.error)
                        }
                    }
                }
                TDivider()
            }
            edit.fields.forEachIndexed { i, field ->
                TagLine(i + 1, field, edit)
                TDivider()
            }
            TText(
                "android asks you to confirm the change for ${if (edit.batch) "these files" else "this file"}. the tags are written into the file itself.",
                Modifier.padding(12.dp),
                color = c.dim,
                maxLines = 4,
            )
        }
        TStatusLine(
            TMode.INS,
            if (edit.saving) "writing..." else "$changes ${if (changes == 1) "field" else "fields"} changed",
            position = if (edit.batch) "${edit.songs.size} files" else formatDuration(song.duration),
        )
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TButton(":q! discard", actions::back, Modifier.weight(1f), large = true)
            TButton(":w save", { actions.saveTags(edit) }, Modifier.weight(1f), filled = true, large = true, enabled = edit.dirty && !edit.saving)
        }
    }
}

private fun coverLabel(cover: CoverChange, info: AudioInfo?): String = when (cover) {
    is CoverChange.Set -> "new image"
    CoverChange.Remove -> "removed"
    CoverChange.Keep -> if (info != null && info.coverWidth > 0) "embedded ${info.coverWidth}x${info.coverHeight}" else "none embedded"
}

@Composable
private fun TagLine(number: Int, field: TagField, edit: EditState) {
    val c = TerminalTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val value = edit.values[field]
    val changed = value != edit.initial[field]
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(if (focused) c.selection else c.background)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TText("$number", Modifier.width(18.dp), color = c.dim)
        TText(field.key, Modifier.width(112.dp), color = c.accent)
        TText("=", color = c.dim)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) TText(if (field in edit.mixed) "(keep each)" else "", color = c.border)
            BasicTextField(
                value = value,
                onValueChange = { edit.set(field, it) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = field.key },
                singleLine = true,
                textStyle = TerminalTheme.type.body.copy(color = if (changed) c.warn else c.foreground),
                cursorBrush = SolidColor(c.foreground),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(keyboardType = if (field == TagField.Year) KeyboardType.Number else KeyboardType.Text),
            )
        }
    }
}
