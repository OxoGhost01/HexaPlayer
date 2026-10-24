package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.settings.flavorRoles
import com.oxoghost.hexaplayer.ui.settings.roles
import com.oxoghost.hexaplayer.ui.settings.toPalette
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TColorPicker
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.components.hex
import com.oxoghost.hexaplayer.ui.terminal.theme.LocalTerminalColors
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalColors
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalPalettes
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/**
 * Edits a custom flavor: name, the 11 color roles with a picker, and a live preview drawn with
 * the draft. [editing] is the flavor being changed, or null to make a new one from [base].
 */
@Composable
fun TFlavorEditorScreen(
    editing: String?,
    base: TerminalColors,
    existing: Set<String>,
    onSave: (name: String, colors: TerminalColors, previousName: String?) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
) {
    val c = TerminalTheme.colors
    val colors = remember { mutableStateListOf(*base.roles().toTypedArray()) }
    var name by remember { mutableStateOf(editing ?: "my flavor") }
    var role by remember { mutableIntStateOf(5) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clean = name.trim()
    val taken = clean != editing && (clean in existing || clean in TerminalPalettes.names)
    val draft = colors.toList().toPalette()

    TScreen(
        header = { THeader("flavors/", current = if (editing == null) "new" else editing, onBack = onBack) },
        status = { TStatusLine(TMode.THEME, if (taken) "name already used" else "editing ${flavorRoles[role]}", position = colors[role].hex()) },
        modifier = Modifier.imePadding(),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TInput(name, { name = it.take(24) }, prompt = ">", placeholder = "flavor name")
            CompositionLocalProvider(LocalTerminalColors provides draft) { DraftPreview() }
            Column {
                flavorRoles.forEachIndexed { i, label ->
                    val on = i == role
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(if (on) c.selection else c.background)
                            .selectable(on, role = Role.RadioButton) { role = i }
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(22.dp).background(colors[i]).border(1.dp, c.border))
                        TText(label, Modifier.weight(1f), color = if (on) c.accent else c.foreground)
                        TText(colors[i].hex(), color = c.dim)
                    }
                }
            }
            TDashedDivider()
            TColorPicker(colors[role], { colors[role] = it })
            if (editing != null) {
                TButton(
                    if (confirmDelete) "tap again to delete" else "delete flavor",
                    { if (confirmDelete) onDelete(editing) else confirmDelete = true },
                    Modifier.fillMaxWidth(),
                    color = c.error,
                )
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TButton(":q! discard", onBack, Modifier.weight(1f), large = true)
            TButton(":w save", { onSave(clean, draft, editing) }, Modifier.weight(1f), filled = true, large = true,
                enabled = clean.isNotEmpty() && !taken)
        }
    }
}

/** The appearance preview, drawn with whatever colors are provided around it. */
@Composable
private fun DraftPreview() {
    val c = TerminalTheme.colors
    Column(Modifier.fillMaxWidth().background(c.background).border(1.dp, c.border)) {
        Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 10.dp, vertical = 6.dp)) {
            TText("hexa ", color = c.accent)
            TText("~/lib/songs", color = c.dim)
        }
        listOf(Triple("▶ now playing", c.accent, c.background), Triple("cursor row", c.background, c.accent), Triple("an album/", c.accent2, c.background))
            .forEach { (title, fg, bg) ->
                Row(Modifier.fillMaxWidth().height(32.dp).background(bg).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TText(title, Modifier.weight(1f), color = fg)
                    TText("3:41", color = if (bg == c.accent) c.background else c.dim)
                }
            }
        Row(Modifier.fillMaxWidth().background(c.selection).padding(horizontal = 10.dp, vertical = 6.dp)) {
            TText("selected · ", color = c.foreground)
            TText("ok ", color = c.ok)
            TText("warn ", color = c.warn)
            TText("err", color = c.error)
        }
    }
}
