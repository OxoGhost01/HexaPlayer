package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MDialog
import com.oxoghost.hexaplayer.ui.material.components.MEffectRow
import com.oxoghost.hexaplayer.ui.material.components.MEqSlider
import com.oxoghost.hexaplayer.ui.material.components.MFilterChip
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSwitch
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.EqPresetKind
import com.oxoghost.hexaplayer.ui.state.EqualizerActions
import com.oxoghost.hexaplayer.ui.state.EqualizerState
import kotlin.math.roundToInt

@Composable
fun MEqualizerScreen(state: EqualizerState, actions: EqualizerActions, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(s.surface).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MIconButton(Symbols.ArrowBack, "Back", onBack, tint = s.onSurface, size = 48.dp)
            Text("Equalizer", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineMedium)
            MSwitch(state.enabled, actions::setEnabled)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (!state.available) {
                Text("Audio effects are not available on this device", Modifier.padding(vertical = 12.dp), color = s.error)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.presets.forEach { preset ->
                    MFilterChip(preset.name, preset.name == state.presetName, { actions.applyPreset(preset) })
                }
                MFilterChip(if (state.presetName == null) "+ Save" else "Presets", false, { saving = true })
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(HexaShapes.card)
                    .background(s.surfaceContainer)
                    .padding(vertical = 16.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                state.bands.forEachIndexed { i, band ->
                    MEqSlider(band.db, { actions.setBand(i, it) }, band.label, range = -state.rangeDb..state.rangeDb)
                }
            }
            MSegmentedGroup(Modifier.padding(top = 8.dp, bottom = 24.dp)) {
                MSegment(0, 4) {
                    MEffectRow("Bass boost", "${(state.bass * 100).roundToInt()}%", state.bassEnabled, actions::setBass, state.bass, actions::setBassStrength)
                }
                MSegment(1, 4) {
                    MEffectRow("Virtualizer", "${(state.virtualizer * 100).roundToInt()}%", state.virtualizerEnabled, actions::setVirtualizer,
                        state.virtualizer, actions::setVirtualizerStrength)
                }
                MSegment(2, 4) {
                    MEffectRow(
                        "Loudness", "+%.1f dB".format(state.loudnessDb), state.loudnessEnabled, actions::setLoudness,
                        state.loudnessDb / EqualizerState.MAX_LOUDNESS_DB, { actions.setLoudnessDb(it * EqualizerState.MAX_LOUDNESS_DB) },
                    )
                }
                MSegment(3, 4) { ReverbRow(state, actions) }
            }
        }
    }
    if (saving) PresetDialog(state, actions) { saving = false }
}

@Composable
private fun RowScope.ReverbRow(state: EqualizerState, actions: EqualizerActions) {
    val s = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Text("Reverb", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
    Box {
        Row(
            Modifier.clip(HexaShapes.chip).clickable(role = Role.DropdownList) { open = true }.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.reverbName.replaceFirstChar { it.uppercase() }, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Symbol(Symbols.ArrowDropDown, null, tint = s.onSurfaceVariant)
        }
        DropdownMenu(open, { open = false }) {
            EqualizerState.reverbNames.forEachIndexed { i, name ->
                DropdownMenuItem(
                    text = { Text(name.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        actions.setReverb(i)
                        open = false
                    },
                )
            }
        }
    }
}

/** Save the current bands under a name; your saved presets can be deleted here. */
@Composable
private fun PresetDialog(state: EqualizerState, actions: EqualizerActions, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    val users = state.presets.filter { it.kind == EqPresetKind.User }
    MDialog(
        title = "Save preset",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            actions.savePreset(name)
            onDismiss()
        },
        icon = Symbols.Equalizer,
        dismissText = "Close",
        confirmEnabled = name.isNotBlank(),
    ) {
        Column {
            MTextField(name, { name = it }, "Name", Modifier.fillMaxWidth())
            users.forEach { preset ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(preset.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    MIconButton(Symbols.Delete, "Delete ${preset.name}", { actions.deletePreset(preset) }, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
