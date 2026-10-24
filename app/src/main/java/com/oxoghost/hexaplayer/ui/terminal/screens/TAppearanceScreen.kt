package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.state.SettingsActions
import com.oxoghost.hexaplayer.ui.state.SettingsState
import com.oxoghost.hexaplayer.ui.terminal.components.TChoiceRow
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.components.TToggle
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalColors
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalPalettes
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Interface switch, live preview, flavors with swatch strips, pure black and pixel covers. */
@Composable
fun TAppearanceScreen(state: SettingsState, actions: SettingsActions, onEditFlavor: (String?) -> Unit, onBack: () -> Unit) {
    val c = TerminalTheme.colors
    val names = TerminalPalettes.names
    val custom = state.ui.customFlavors
    TScreen(
        header = { THeader("flavors ", current = "${names.size + custom.size} installed", onBack = onBack) },
        status = { TStatusLine(TMode.THEME, state.ui.terminalPalette.lowercase(), position = "tap to apply") },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TText("interface", color = c.dim)
                TChoiceRow(listOf("terminal", "material"), state.ui.skin.ordinal, { actions.setSkin(Skin.entries[it]) }, height = 44)
                Preview()
            }
            names.forEach { name -> FlavorRow(name, TerminalPalettes.get(name, false), name == state.ui.terminalPalette) { actions.setTerminalPalette(name) } }
            custom.forEach { (name, palette) ->
                FlavorRow(name, palette, name == state.ui.terminalPalette, onEdit = { onEditFlavor(name) }) { actions.setTerminalPalette(name) }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clickable(role = Role.Button, onClickLabel = "Make a new flavor from the current one") { onEditFlavor(null) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TText("+", color = c.accent)
                TText("new flavor from ${state.ui.terminalPalette.lowercase()}", color = c.accent)
            }
            TDashedDivider()
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                TToggle(state.ui.pureBlack, actions::setPureBlack, "pure black background", sublabel = "for amoled screens")
                TToggle(state.ui.pixelCovers, actions::setPixelCovers, "pixel covers", sublabel = "off: real covers, square")
            }
        }
    }
}

/** A tiny screen drawn with the current flavor: header, playing row, cursor row, directory, status. */
@Composable
private fun Preview() {
    val c = TerminalTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).border(1.dp, c.border, RoundedCornerShape(6.dp))) {
        Row(Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 10.dp, vertical = 8.dp)) {
            TText(buildAnnotatedString {
                withStyle(SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)) { append("hexa ") }
                withStyle(SpanStyle(color = c.dim)) { append("~/lib/songs") }
            })
        }
        PreviewRow("▶ now playing", "4:02", c.accent, c.background, c.dim)
        PreviewRow("cursor row", "3:41", c.background, c.accent, c.background)
        PreviewRow("an album/", "9", c.accent2, c.background, c.dim)
        Row(Modifier.fillMaxWidth().background(c.panel), verticalAlignment = Alignment.CenterVertically) {
            TText("NOR", Modifier.background(c.accent).padding(horizontal = 8.dp, vertical = 2.dp), color = c.background,
                style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
            TText(buildAnnotatedString {
                withStyle(SpanStyle(color = c.ok)) { append(" ok") }
                withStyle(SpanStyle(color = c.warn)) { append(" warn") }
                withStyle(SpanStyle(color = c.error)) { append(" err") }
            })
        }
    }
}

@Composable
private fun PreviewRow(title: String, trailing: String, fg: Color, bg: Color, dim: Color) {
    Row(Modifier.fillMaxWidth().height(36.dp).background(bg).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TText(title, Modifier.weight(1f), color = fg)
        TText(trailing, color = dim)
    }
}

@Composable
private fun FlavorRow(name: String, p: TerminalColors, selected: Boolean, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    val c = TerminalTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(if (selected) c.selection else c.background)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TText(if (selected) "●" else " ", color = c.accent)
        TText(name.lowercase(), Modifier.weight(1f), color = if (selected) c.accent else c.foreground)
        if (onEdit != null) {
            Box(Modifier.clickable(role = Role.Button, onClickLabel = "Edit $name", onClick = onEdit).padding(8.dp)) { TText("edit", color = c.dim) }
        }
        Row(Modifier.border(1.dp, c.border)) {
            listOf(p.background, p.foreground, p.accent, p.accent2, p.ok, p.error).forEach {
                Box(Modifier.size(16.dp, 22.dp).background(it))
            }
        }
    }
}
