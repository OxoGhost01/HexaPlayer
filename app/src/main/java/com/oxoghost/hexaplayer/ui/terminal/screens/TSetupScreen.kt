package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.state.SetupState
import com.oxoghost.hexaplayer.ui.state.TermuxSetupActions
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCodeBlock
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

val setupTips = listOf(
    "if downloads stop with the screen off, turn off battery optimization for termux.",
    "the first setup can take 10 to 30 minutes: some parts are built on the phone.",
    "to update or repair the downloader, run the setup command again.",
    "without step 3, open termux and type hexadl before downloading.",
)

/** The 1.x Termux guide as four steps; done steps turn ok, the current one accent. */
@Composable
fun TSetupScreen(state: SetupState, actions: TermuxSetupActions, onBack: () -> Unit) {
    val c = TerminalTheme.colors
    var tips by rememberSaveable { mutableStateOf(false) }
    TScreen(
        header = { THeader("setup ", current = "downloader", onBack = onBack, action = "${state.doneCount}/4 done", onAction = actions::testConnection, actionDescription = "Test the connection") },
        status = {
            Box(Modifier.clickable(role = Role.Button, onClick = actions::openFullGuide)) {
                TStatusLine(TMode.SETUP, "full guide on github")
            }
        },
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TText(
                "spotDL runs inside Termux on this phone. nothing goes to an outside server. steps 1 to 3 are one-time.",
                Modifier.padding(top = 8.dp), color = c.dim, maxLines = 4,
            )
            Step(1, "install termux", state.termuxInstalled, state.current == 0) {
                if (state.termuxInstalled) {
                    TText("installed. use the F-Droid or GitHub build, not the Play Store one.", color = c.dim, maxLines = 3)
                } else {
                    TText("get termux from F-Droid or GitHub (the universal apk), then open it once.", color = c.dim, maxLines = 3)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("f-droid", { actions.openTermuxPage(false) }, Modifier.weight(1f), filled = true)
                        TButton("github", { actions.openTermuxPage(true) }, Modifier.weight(1f))
                    }
                }
            }
            Step(2, "run setup command", state.setupRun, state.current == 1) {
                TCodeBlock(state.command)
                TButton("copy + open termux", actions::copyAndOpenTermux, Modifier.fillMaxWidth(), filled = state.current == 1, large = true)
            }
            Step(3, "let hexa start termux", state.permissionGranted, state.current == 2) {
                TText("starts the downloader when you download. stops after 10 min idle.", maxLines = 3)
                if (state.permissionGranted) {
                    TText("✓ permission granted", color = c.ok)
                } else {
                    TText("✗ permission not granted", color = c.warn)
                    TButton("allow", actions::allowRunCommand, Modifier.fillMaxWidth(), filled = true, large = true)
                }
            }
            Step(4, "start (optional)", state.connected, state.current == 3 && !state.connected) {
                TText(if (state.connected) "the downloader answers." else "checks that everything works.", color = c.dim)
                if (state.statusText.isNotEmpty()) TText(state.statusText, color = c.dim, maxLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TButton("start now", actions::startNow, Modifier.weight(1f), large = true)
                    TButton("test", actions::testConnection, Modifier.weight(1f), large = true)
                }
            }
            Box(Modifier.clickable(role = Role.Button) { tips = !tips }.padding(vertical = 8.dp)) {
                TText(if (tips) "▼ tips" else "▶ tips", color = c.accent)
            }
            if (tips) setupTips.forEach { TText("· $it", color = c.dim, maxLines = 3) }
            Box(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun Step(number: Int, title: String, done: Boolean, current: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val c = TerminalTheme.colors
    val color = when {
        done -> c.ok
        current -> c.accent
        else -> c.border
    }
    TSectionBox(
        "[$number/4] $title" + if (done) " ✓" else "",
        titleColor = if (!done && !current) c.dim else color,
        borderColor = color,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
