package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.Tab
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Library content when the scan found no audio: the scan as a command, then the 4 ways out. */
@Composable
fun TEmptyLibrary(folder: String, actions: HexaActions, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    val path = "/sdcard/" + folder.trim('/')
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        TText(buildAnnotatedString {
            withStyle(SpanStyle(color = c.accent)) { append("$ ") }
            append("hexa scan ${path.trimEnd('/')}")
        })
        TText(if (folder.isBlank()) "walking the whole device..." else "walking ${folder.trim('/')}...", Modifier.padding(top = 6.dp), color = c.dim)
        TText("0 audio files found", Modifier.padding(top = 6.dp), color = c.dim)
        TText("warn: library is empty", Modifier.padding(top = 6.dp), color = c.warn)

        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp, 64.dp).border(1.dp, c.border), contentAlignment = Alignment.Center) {
                TText("♪   0/0", color = c.dim)
            }
        }
        TText("nothing to play yet", Modifier.fillMaxWidth(), style = TerminalTheme.type.body.copy(textAlign = TextAlign.Center))
        TText(
            if (folder.isBlank()) "this device holds no audio yet. pick a folder or download music."
            else "the folder holds no audio. pick another folder, scan the whole device, or download music.",
            Modifier.fillMaxWidth().padding(top = 8.dp),
            color = c.dim,
            style = TerminalTheme.type.body.copy(textAlign = TextAlign.Center),
            maxLines = 4,
        )
        Spacer(Modifier.height(28.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            KeyButton("r", "rescan library", actions::rescan, filled = true)
            KeyButton("f", "change folder", actions::chooseFolder)
            if (folder.isNotBlank()) KeyButton("a", "scan all audio on device", actions::scanAllAudio)
            KeyButton("d", "open download & sync", { actions.openTab(Tab.Download) })
        }
    }
}

@Composable
private fun KeyButton(key: String, label: String, onClick: () -> Unit, filled: Boolean = false) {
    val c = TerminalTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(if (filled) Modifier.background(c.accent) else Modifier.border(1.dp, c.border))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val bold = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold)
        TText(key, color = if (filled) c.background else c.accent, style = bold)
        TText(label, color = if (filled) c.background else c.foreground, style = if (filled) bold else TerminalTheme.type.body)
    }
}
