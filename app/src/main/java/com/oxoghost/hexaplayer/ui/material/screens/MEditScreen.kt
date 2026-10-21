package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.repository.CoverChange
import com.oxoghost.hexaplayer.repository.TagField
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.EditState
import com.oxoghost.hexaplayer.ui.state.HexaActions

private val TagField.label: String
    get() = when (this) {
        TagField.AlbumArtist -> "Album artist"
        else -> name
    }

private val wideFields = listOf(TagField.Title, TagField.Artist, TagField.Album, TagField.AlbumArtist, TagField.Genre)
private val shortFields = listOf(TagField.Year, TagField.Track, TagField.Disc)

/** Full-screen dialog: close, title, Save; cover with Change / Remove; outlined fields. */
@Composable
fun MEditScreen(edit: EditState?, actions: HexaActions) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(s.surface).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MIconButton(Symbols.Close, "Discard changes", actions::back, tint = s.onSurface, size = 48.dp)
            Text(
                when {
                    edit == null -> "Edit song info"
                    edit.batch -> "Edit ${edit.songs.size} songs"
                    else -> "Edit song info"
                },
                Modifier.weight(1f).padding(start = 12.dp),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (edit != null) {
                MPillButton("Save", { actions.saveTags(edit) }, size = MButtonSize.M, enabled = edit.dirty && !edit.saving)
            }
        }
        if (edit == null) {
            Text("This song is no longer in your library", Modifier.padding(24.dp), color = s.onSurfaceVariant)
            return@Column
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!edit.batch) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverImage(edit.coverModel, edit.songs.first().albumKey, Modifier.size(112.dp), HexaShapes.coverAlbumHeader, "Cover")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MPillButton("Change cover", { actions.pickImage { edit.cover = CoverChange.Set(it) } },
                            style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Image)
                        if (edit.cover != CoverChange.Remove) {
                            MPillButton("Remove", { edit.cover = CoverChange.Remove }, style = MButtonStyle.Text, size = MButtonSize.M,
                                icon = Symbols.HideImage, contentColor = s.error)
                        }
                    }
                }
            }
            wideFields.filter { it in edit.fields }.forEach { field -> Field(edit, field, Modifier.fillMaxWidth()) }
            val shorts = shortFields.filter { it in edit.fields }
            if (shorts.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    shorts.forEach { field -> Field(edit, field, Modifier.weight(1f)) }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(s.surfaceContainer, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Symbol(Symbols.Info, null, tint = s.onSurfaceVariant)
                Text(
                    "Android asks you to confirm the change for ${if (edit.batch) "these files" else "this file"}. The tags are written into the file itself.",
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun Field(edit: EditState, field: TagField, modifier: Modifier) {
    MTextField(
        value = edit.values[field],
        onValueChange = { edit.set(field, it) },
        label = field.label,
        modifier = modifier,
        supportingText = if (field in edit.mixed && edit.values[field].isEmpty()) "Different on each song, kept as is" else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (field == TagField.Year) KeyboardType.Number else KeyboardType.Text),
    )
}
