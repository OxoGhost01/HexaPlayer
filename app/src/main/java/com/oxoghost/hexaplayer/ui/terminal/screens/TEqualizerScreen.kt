package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.state.EqPresetKind
import com.oxoghost.hexaplayer.ui.state.EqualizerActions
import com.oxoghost.hexaplayer.ui.state.EqualizerState
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCheckMark
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyAction
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TPopup
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TStepper
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import kotlin.math.roundToInt

private fun signed(db: Float): String = db.roundToInt().let { if (it > 0) "+$it" else "$it" }

@Composable
fun TEqualizerScreen(state: EqualizerState, actions: EqualizerActions, onBack: () -> Unit) {
    val c = TerminalTheme.colors
    var presets by remember { mutableStateOf(false) }
    TScreen(
        header = {
            THeader(
                "eq", onBack = onBack,
                action = if (state.enabled) "[x] enabled" else "[ ] enabled",
                actionColor = c.accent,
                actionDescription = if (state.enabled) "Turn the equalizer off" else "Turn the equalizer on",
                onAction = { actions.setEnabled(!state.enabled) },
            )
        },
        status = {
            Box(Modifier.clickable(role = Role.Button, onClickLabel = "Save or manage presets") { presets = true }) {
                TStatusLine(
                    TMode.EQ,
                    state.presetName ?: "custom preset unsaved",
                    position = if (state.presetName == null) "save as" else "presets",
                )
            }
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            if (!state.available) {
                TText("audio effects are not available on this device", Modifier.padding(vertical = 12.dp), color = c.warn, maxLines = 2)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TStepper(
                    state.presetName?.lowercase() ?: "custom",
                    { actions.stepPreset(-1) },
                    { actions.stepPreset(1) },
                    Modifier.weight(1f),
                    label = "preset",
                    previousDescription = "Previous preset",
                    nextDescription = "Next preset",
                )
                TButton("reset", actions::reset, large = true)
            }
            TSectionBox("${state.bands.size} bands · dB", Modifier.padding(top = 12.dp)) {
                BandChart(state, actions)
            }
            Column(Modifier.padding(top = 16.dp)) {
                EffectRow("bass boost", state.bassEnabled, actions::setBass, state.bass, actions::setBassStrength, "${(state.bass * 100).roundToInt()}%")
                TDashedDivider()
                EffectRow("virtualizer", state.virtualizerEnabled, actions::setVirtualizer, state.virtualizer, actions::setVirtualizerStrength,
                    "${(state.virtualizer * 100).roundToInt()}%")
                TDashedDivider()
                EffectRow(
                    "loudness", state.loudnessEnabled, actions::setLoudness,
                    state.loudnessDb / EqualizerState.MAX_LOUDNESS_DB, { actions.setLoudnessDb(it * EqualizerState.MAX_LOUDNESS_DB) },
                    "+%.1fdB".format(state.loudnessDb),
                )
                TDashedDivider()
                TStepper(
                    state.reverbName,
                    { actions.setReverb(state.reverb - 1) },
                    { actions.setReverb(state.reverb + 1) },
                    Modifier.padding(vertical = 8.dp),
                    label = "  reverb   ",
                    previousDescription = "Previous reverb",
                    nextDescription = "Next reverb",
                )
            }
        }
    }
    if (presets) PresetPopup(state, actions) { presets = false }
}

@Composable
private fun BandChart(state: EqualizerState, actions: EqualizerActions) {
    val c = TerminalTheme.colors
    val bands = state.bands
    if (bands.isEmpty()) {
        TText("no bands", color = c.dim)
        return
    }
    val range = state.rangeDb
    val set by rememberUpdatedState(actions::setBand)
    Column {
        Row(Modifier.fillMaxWidth()) {
            bands.forEach { TText(signed(it.db), Modifier.weight(1f), color = c.foreground, style = TerminalTheme.type.small.copy(textAlign = TextAlign.Center)) }
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(vertical = 8.dp)
                .semantics { contentDescription = "Equalizer bands, drag a column to change it" }
                .pointerInput(bands.size, range) {
                    detectTapGestures { set.invoke(bandAt(it, size.width, bands.size), dbAt(it.y, size.height, range)) }
                }
                .pointerInput(bands.size, range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        set.invoke(bandAt(change.position, size.width, bands.size), dbAt(change.position.y, size.height, range))
                    }
                }
        ) {
            val col = size.width / bands.size
            val barW = col * 0.72f
            val mid = size.height / 2
            bands.forEachIndexed { i, band ->
                val left = i * col + (col - barW) / 2
                val h = (band.db / range) * mid
                val top = if (h >= 0) mid - h else mid
                drawRect(c.accent, Offset(left, top), Size(barW, kotlin.math.abs(h)))
                drawRect(c.accent, Offset(left, mid - 1f), Size(barW, 2f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            bands.forEach { TText(it.label, Modifier.weight(1f), color = c.dim, style = TerminalTheme.type.small.copy(textAlign = TextAlign.Center)) }
        }
        TText("drag a column · ±${range.roundToInt()} dB", Modifier.fillMaxWidth().padding(top = 8.dp), color = c.dim,
            style = TerminalTheme.type.small.copy(textAlign = TextAlign.Center))
    }
}

private fun bandAt(p: Offset, width: Int, count: Int): Int =
    (p.x / (width.toFloat() / count)).toInt().coerceIn(0, count - 1)

private fun dbAt(y: Float, height: Int, range: Float): Float =
    ((height / 2f - y) / (height / 2f) * range).coerceIn(-range, range).roundToInt().toFloat()

@Composable
private fun EffectRow(label: String, on: Boolean, onToggle: (Boolean) -> Unit, value: Float, onValue: (Float) -> Unit, valueText: String) {
    val c = TerminalTheme.colors
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.width(150.dp).clickable(role = Role.Checkbox, onClickLabel = label) { onToggle(!on) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TCheckMark(on)
            TText(label)
        }
        TSlider(value, onValue, Modifier.weight(1f), label)
        TText(valueText, Modifier.width(72.dp), color = c.dim, style = TerminalTheme.type.body.copy(textAlign = TextAlign.End))
    }
}

/** Thin line slider with a dot, 0..1. */
@Composable
fun TSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, description: String = "") {
    val c = TerminalTheme.colors
    val change by rememberUpdatedState(onValueChange)
    Canvas(
        modifier
            .height(32.dp)
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { ch, _ ->
                    ch.consume()
                    change((ch.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val y = size.height / 2
        val x = size.width * value.coerceIn(0f, 1f)
        drawLine(c.border, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
        drawLine(c.accent, Offset(0f, y), Offset(x, y), 2.dp.toPx())
        drawCircle(c.accent, 5.dp.toPx(), Offset(x.coerceAtLeast(5.dp.toPx()), y))
    }
}

@Composable
private fun PresetPopup(state: EqualizerState, actions: EqualizerActions, onClose: () -> Unit) {
    val c = TerminalTheme.colors
    var name by remember { mutableStateOf("") }
    TPopup(true, title = "presets", onDismiss = onClose) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TText("save the current bands as", color = c.dim)
            TInput(name, { name = it }, prompt = "+", placeholder = "preset name")
            TButton("save", {
                actions.savePreset(name)
                onClose()
            }, Modifier.fillMaxWidth(), filled = true, large = true, enabled = name.isNotBlank())
            val users = state.presets.filter { it.kind == EqPresetKind.User }
            if (users.isNotEmpty()) {
                TDashedDivider()
                TText("your presets", color = c.dim)
            }
        }
        state.presets.filter { it.kind == EqPresetKind.User }.forEach { preset ->
            TKeyAction("x", "delete ${preset.name}", { actions.deletePreset(preset) }, labelColor = c.error)
        }
    }
}
