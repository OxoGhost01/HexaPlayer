package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MDialog
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar
import com.oxoghost.hexaplayer.ui.material.components.MSwitch
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.material.components.MThickSlider
import com.oxoghost.hexaplayer.ui.state.DownloadActions
import com.oxoghost.hexaplayer.ui.state.DownloadState
import com.oxoghost.hexaplayer.ui.state.SettingsActions
import com.oxoghost.hexaplayer.ui.state.SettingsPage
import com.oxoghost.hexaplayer.ui.state.SettingsState
import com.oxoghost.hexaplayer.ui.state.librarySorts
import com.oxoghost.hexaplayer.ui.state.licenses
import com.oxoghost.hexaplayer.ui.state.permissionNotes
import kotlin.math.roundToInt

/** "Settings" 34 bold, the app card, then one row per category. */
@Composable
fun MSettingsScreen(
    state: SettingsState,
    actions: SettingsActions,
    onBack: () -> Unit,
    onPage: (SettingsPage) -> Unit,
    onAppearance: () -> Unit,
    onEqualizer: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(s.surface).statusBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).navigationBarsPadding(),
    ) {
        MCircleButton(Symbols.ArrowBack, "Back", onBack, Modifier.padding(vertical = 8.dp), container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
        Text("Settings", Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
        AppCard(state, actions)
        val rows = listOf(
            Triple(Symbols.Palette, "Appearance", "Theme, colors, pure black") to onAppearance,
            Triple(Symbols.LibraryMusic, "Library", "Folder, sorting, unknown songs") to { onPage(SettingsPage.Library) },
            Triple(Symbols.PlayCircle, "Playback", "Gapless, resume, crossfade") to { onPage(SettingsPage.Playback) },
            Triple(Symbols.Equalizer, "Audio", "Equalizer and effects") to onEqualizer,
            Triple(Symbols.Download, "Download & Sync", "Termux, default folder") to { onPage(SettingsPage.Download) },
            Triple(Symbols.SystemUpdate, "Updates", if (state.autoUpdate) "Check automatically" else "Check by hand") to { onPage(SettingsPage.Updates) },
            Triple(Symbols.Info, "About", "Version, licenses, permissions") to { onPage(SettingsPage.About) },
        )
        val containers = listOf(s.primaryContainer to s.onPrimaryContainer, s.secondaryContainer to s.onSecondaryContainer, s.tertiaryContainer to s.onTertiaryContainer)
        MSegmentedGroup(Modifier.padding(top = 16.dp, bottom = 24.dp)) {
            rows.forEachIndexed { i, (row, onClick) ->
                val (bg, fg) = containers[i % containers.size]
                MSegment(i, rows.size, onClick = onClick, minHeight = 76) {
                    Box(Modifier.size(48.dp).background(bg, CircleShape), contentAlignment = Alignment.Center) {
                        Symbol(row.first, null, filled = true, tint = fg)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(row.second, style = MaterialTheme.typography.titleMedium)
                        Text(row.third, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppCard(state: SettingsState, actions: SettingsActions) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.surfaceContainer).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(64.dp).background(s.primaryContainer, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Symbol(Symbols.Hexagon, null, size = 32.dp, filled = true, tint = s.onPrimaryContainer)
            }
            Column {
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("HexaPlayer ") }
                    withStyle(SpanStyle(fontSize = 15.sp)) { append(state.version) }
                }, style = MaterialTheme.typography.titleLarge)
                Text("by OxoGhost01 · ${state.updateStatus}", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MPillButton("Donate", actions::openDonate, Modifier.weight(1f), style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Coffee)
            MPillButton("GitHub", actions::openGitHub, Modifier.weight(1f), size = MButtonSize.M, icon = Symbols.Code)
        }
    }
}

/** One settings category, built from segmented groups and switches. */
@Composable
fun MSettingsPageScreen(
    page: SettingsPage,
    state: SettingsState,
    actions: SettingsActions,
    download: DownloadState,
    downloadActions: DownloadActions,
    onBack: () -> Unit,
    onPage: (SettingsPage) -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(s.surface).statusBarsPadding()) {
        MSubTopBar(page.title, onBack, large = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                SettingsPage.Library -> LibraryPage(state, actions)
                SettingsPage.Playback -> PlaybackPage(state, actions)
                SettingsPage.Download -> DownloadPage(download, downloadActions)
                SettingsPage.Updates -> UpdatesPage(state, actions)
                SettingsPage.About -> AboutPage(state, actions, onPage)
                SettingsPage.Licenses -> Rows(licenses.map { it.first to it.second })
                SettingsPage.Permissions -> Rows(permissionNotes.map { it.first to "${it.second}. ${it.third}." })
            }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun RowScope.Texts(title: String, subtitle: String?) {
    Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun SwitchSegment(index: Int, count: Int, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    MSegment(index, count, onClick = { onChange(!checked) }) {
        Texts(title, subtitle)
        MSwitch(checked, onChange)
    }
}

@Composable
private fun ColumnScope.LibraryPage(state: SettingsState, actions: SettingsActions) {
    var typing by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    MSegmentedGroup {
        MSegment(0, 4, onClick = actions::chooseFolder) {
            Texts("Library folder", state.folder.ifEmpty { "All audio on this device" })
            MPillButton("Type", { typing = true }, style = MButtonStyle.Text, size = MButtonSize.S)
        }
        MSegment(1, 4, onClick = actions::rescan) {
            Texts("Rescan library", if (state.scanning) "Scanning..." else "${state.songCount} songs")
            Symbol(Symbols.Refresh, null, tint = MaterialTheme.colorScheme.primary)
        }
        MSegment(2, 4, onClick = { sorting = true }) {
            Texts("Sort songs by", librarySorts.getOrNull(state.sortOrder)?.second)
            Symbol(Symbols.ArrowDropDown, null)
        }
        SwitchSegment(3, 4, "Show unknown songs", "Tracks with no artist or title", state.showUnknown, actions::setShowUnknown)
    }
    if (state.folder.isNotEmpty()) {
        MPillButton("Scan all audio instead", { actions.setFolder("") }, style = MButtonStyle.Text, size = MButtonSize.M)
    }
    if (typing) FolderDialog(state.folder, { actions.setFolder(it) }) { typing = false }
    if (sorting) {
        MDialog("Sort songs by", { sorting = false }, "Close", { sorting = false }) {
            Column {
                librarySorts.forEachIndexed { i, (_, label) ->
                    MSegment(i, librarySorts.size, Modifier.padding(bottom = 2.dp), container = if (i == state.sortOrder) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, onClick = {
                        actions.setSort(i)
                        sorting = false
                    }, minHeight = 48) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun FolderDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    MDialog(
        title = "Library folder",
        onDismiss = onDismiss,
        confirmText = "Save",
        onConfirm = {
            onSave(text)
            onDismiss()
        },
        icon = Symbols.FolderOpen,
        dismissText = "Cancel",
    ) {
        MTextField(text, { text = it }, "Path from the storage root", Modifier.fillMaxWidth(), supportingText = "For example Music or Music/Albums")
    }
}

@Composable
private fun ColumnScope.PlaybackPage(state: SettingsState, actions: SettingsActions) {
    val s = MaterialTheme.colorScheme
    MSegmentedGroup {
        SwitchSegment(0, 4, "Gapless playback", "No gap between consecutive tracks", state.gapless, actions::setGapless)
        SwitchSegment(1, 4, "Resume on track change", "Keep playing when you skip", state.resumeOnSkip, actions::setResumeOnSkip)
        MSegment(2, 4) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text("Crossfade", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text(if (state.crossfadeTenths == 0) "Off" else "%.1f s".format(state.crossfadeTenths / 10f), color = s.onSurfaceVariant)
                }
                MThickSlider(state.crossfadeTenths / 50f, { actions.setCrossfade((it * 50).roundToInt()) }, description = "Crossfade length")
            }
        }
        SwitchSegment(3, 4, "Skip silence", "Skip silent parts (coming soon)", state.skipSilence, actions::setSkipSilence)
    }
}

@Composable
private fun ColumnScope.DownloadPage(state: DownloadState, actions: DownloadActions) {
    MSegmentedGroup {
        MSegment(0, 2, onClick = actions::openSetup) {
            Texts("Downloader setup", "Termux guide, start and test")
            Symbol(Symbols.ChevronRight, null)
        }
        SwitchSegment(1, 2, "Custom folder name", "Default: ${state.destination}/<name>", state.useCustomFolder, actions::setUseCustomFolder)
    }
    if (state.useCustomFolder) {
        MTextField(state.customFolder, actions::setCustomFolder, "Folder name", Modifier.fillMaxWidth())
    }
}

@Composable
private fun ColumnScope.UpdatesPage(state: SettingsState, actions: SettingsActions) {
    MSegmentedGroup {
        SwitchSegment(0, 2, "Check automatically", "Look for a new version each time the app opens", state.autoUpdate, actions::setAutoUpdate)
        MSegment(1, 2, onClick = { if (state.latestVersion != null) actions.openUpdate() else actions.checkUpdates() }) {
            Texts(if (state.latestVersion != null) "Update to v${state.latestVersion}" else "Check now", state.updateStatus.replaceFirstChar { it.uppercase() })
            Symbol(Symbols.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ColumnScope.AboutPage(state: SettingsState, actions: SettingsActions, onPage: (SettingsPage) -> Unit) {
    MSegmentedGroup {
        MSegment(0, 5, onClick = actions::openAppInfo) {
            Texts("Version", state.version)
            Symbol(Symbols.OpenInNew, null)
        }
        MSegment(1, 5, onClick = { onPage(SettingsPage.Licenses) }) {
            Texts("Licenses", "Open source libraries in the app")
            Symbol(Symbols.ChevronRight, null)
        }
        MSegment(2, 5, onClick = { onPage(SettingsPage.Permissions) }) {
            Texts("Permissions", "What each permission is for")
            Symbol(Symbols.ChevronRight, null)
        }
        MSegment(3, 5, onClick = actions::openDonate) {
            Texts("Donate", "GitHub Sponsors")
            Symbol(Symbols.Coffee, null)
        }
        MSegment(4, 5, onClick = actions::openGitHub) {
            Texts("Source code", "github.com/OxoGhost01/HexaPlayer")
            Symbol(Symbols.Code, null)
        }
    }
    MSectionLabel("No account, no ads, no tracking.", Modifier.padding(top = 4.dp))
}

@Composable
private fun Rows(rows: List<Pair<String, String>>) {
    MSegmentedGroup {
        rows.forEachIndexed { i, (title, value) ->
            MSegment(i, rows.size) { Texts(title, value) }
        }
    }
}
