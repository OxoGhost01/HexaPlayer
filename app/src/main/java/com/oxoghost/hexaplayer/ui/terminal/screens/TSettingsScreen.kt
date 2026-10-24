package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.state.SettingsActions
import com.oxoghost.hexaplayer.ui.state.SettingsState
import com.oxoghost.hexaplayer.ui.state.librarySorts
import com.oxoghost.hexaplayer.ui.state.licenses
import com.oxoghost.hexaplayer.ui.state.permissionNotes
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TChoiceRow
import com.oxoghost.hexaplayer.ui.terminal.components.TConfigHeader
import com.oxoghost.hexaplayer.ui.terminal.components.TConfigRow
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyAction
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TPopup
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.components.TValueKind
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

private enum class Picker { Folder, Sort, Crossfade, Licenses, Permissions }

private val crossfadeSteps = listOf(0, 5, 10, 20, 30, 50)

private fun bool(on: Boolean) = if (on) "true" else "false"
private fun mark(on: Boolean) = if (on) "[x]" else "[ ]"

/** Settings as one scrolling config.toml; booleans toggle in place, `›` rows open a picker. */
@Composable
fun TSettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    equalizerOn: Boolean,
    onBack: () -> Unit,
    onAppearance: () -> Unit,
    onEqualizer: () -> Unit,
    onSetup: () -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    TScreen(
        header = { THeader("~/.config/hexa/", current = "config.toml", onBack = onBack) },
        status = { TStatusLine(TMode.CFG, "v${state.version} · ${state.updateStatus}", position = "saved") },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TConfigHeader("appearance")
            TConfigRow("interface", "\"${state.ui.skin.name.lowercase()}\"", TValueKind.Text, onAppearance, control = "›")
            TConfigRow("theme", "\"${state.ui.terminalPalette.lowercase()}\"", TValueKind.Text, onAppearance, control = "›")
            TConfigRow("amoled_black", bool(state.ui.pureBlack), TValueKind.Bool, { actions.setPureBlack(!state.ui.pureBlack) }, control = mark(state.ui.pureBlack))
            TConfigRow("pixel_covers", bool(state.ui.pixelCovers), TValueKind.Bool, { actions.setPixelCovers(!state.ui.pixelCovers) }, control = mark(state.ui.pixelCovers))

            TConfigHeader("library")
            TConfigRow("folder", "\"${state.folder.ifEmpty { "<all audio>" }}\"", TValueKind.Text, { picker = Picker.Folder }, control = "›")
            TConfigRow("sort", "\"${librarySorts.getOrNull(state.sortOrder)?.first ?: "title_asc"}\"", TValueKind.Text, { picker = Picker.Sort }, control = "›")
            TConfigRow("show_unknown", bool(state.showUnknown), TValueKind.Bool, { actions.setShowUnknown(!state.showUnknown) }, control = mark(state.showUnknown))
            TConfigRow("rescan", if (state.scanning) "scanning..." else "${state.songCount} songs", TValueKind.Number, actions::rescan, control = "run")

            TConfigHeader("playback")
            TConfigRow("gapless", bool(state.gapless), TValueKind.Bool, { actions.setGapless(!state.gapless) }, control = mark(state.gapless))
            TConfigRow("resume_on_skip", bool(state.resumeOnSkip), TValueKind.Bool, { actions.setResumeOnSkip(!state.resumeOnSkip) }, control = mark(state.resumeOnSkip))
            TConfigRow("crossfade_ms", "${state.crossfadeTenths * 100}", TValueKind.Number, { picker = Picker.Crossfade }, control = "›")
            TConfigRow("skip_silence", bool(state.skipSilence), TValueKind.Bool, { actions.setSkipSilence(!state.skipSilence) }, control = mark(state.skipSilence))

            TConfigHeader("audio")
            TConfigRow("equalizer", if (equalizerOn) "\"on\"" else "\"off\"", TValueKind.Text, onEqualizer, control = "›")

            TConfigHeader("download")
            TConfigRow("downloader", "\"termux\"", TValueKind.Text, onSetup, control = "›")

            TConfigHeader("updates")
            TConfigRow("updates", if (state.autoUpdate) "\"auto\"" else "\"manual\"", TValueKind.Text, { actions.setAutoUpdate(!state.autoUpdate) }, control = mark(state.autoUpdate))
            TConfigRow(
                "check", "\"${state.updateStatus}\"", TValueKind.Text,
                { if (state.latestVersion != null) actions.openUpdate() else actions.checkUpdates() },
                control = if (state.latestVersion != null) "›" else "run",
            )

            TConfigHeader("about")
            TConfigRow("version", "\"${state.version}\"", TValueKind.Text, actions::openAppInfo, control = "›")
            TConfigRow("licenses", "${licenses.size}", TValueKind.Number, { picker = Picker.Licenses }, control = "›")
            TConfigRow("permissions", "${permissionNotes.size}", TValueKind.Number, { picker = Picker.Permissions }, control = "›")
            TConfigRow("donate", "\"github sponsors\"", TValueKind.Text, actions::openDonate, control = "›")
            TConfigRow("source", "\"github\"", TValueKind.Text, actions::openGitHub, control = "›")
            Column(Modifier.padding(bottom = 16.dp)) {}
        }
    }
    when (picker) {
        Picker.Folder -> FolderPopup(state, actions) { picker = null }
        Picker.Sort -> TPopup(true, "sort", { picker = null }) {
            librarySorts.forEachIndexed { i, (key, _) ->
                TKeyAction("${i + 1}", key, {
                    actions.setSort(i)
                    picker = null
                }, highlighted = i == state.sortOrder)
            }
        }
        Picker.Crossfade -> TPopup(true, "crossfade", { picker = null }) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TChoiceRow(
                    crossfadeSteps.map { if (it == 0) "off" else "%.1fs".format(it / 10f) },
                    crossfadeSteps.indexOf(state.crossfadeTenths),
                    { actions.setCrossfade(crossfadeSteps[it]) },
                    height = 44,
                )
                TButton("done", { picker = null }, Modifier.fillMaxWidth(), filled = true)
            }
        }
        Picker.Licenses -> InfoPopup("licenses", licenses.map { it.first to it.second }) { picker = null }
        Picker.Permissions -> InfoPopup("permissions", permissionNotes.map { it.first to "${it.second} · ${it.third.lowercase()}" }) { picker = null }
        null -> {}
    }
}

@Composable
private fun FolderPopup(state: SettingsState, actions: SettingsActions, onClose: () -> Unit) {
    val c = TerminalTheme.colors
    var text by remember { mutableStateOf(state.folder) }
    TPopup(true, "library folder", onClose) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TText("path from the storage root, empty scans all audio", color = c.dim, maxLines = 2)
            TInput(text, { text = it }, prompt = "/sdcard/", placeholder = "Music")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("pick", {
                    actions.chooseFolder()
                    onClose()
                }, Modifier.weight(1f))
                TButton("all audio", {
                    actions.setFolder("")
                    onClose()
                }, Modifier.weight(1f))
                TButton(":w save", {
                    actions.setFolder(text)
                    onClose()
                }, Modifier.weight(1f), filled = true)
            }
        }
    }
}

@Composable
private fun InfoPopup(title: String, rows: List<Pair<String, String>>, onClose: () -> Unit) {
    val c = TerminalTheme.colors
    TPopup(true, title, onClose) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (name, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TText(name, Modifier.width(170.dp), maxLines = 2)
                    TText(value, Modifier.weight(1f), color = c.dim, maxLines = 3)
                }
            }
            TButton("close", onClose, Modifier.fillMaxWidth())
        }
    }
}
