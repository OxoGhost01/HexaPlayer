package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.app.UpdatePhase
import com.oxoghost.hexaplayer.ui.state.ChangeKind
import com.oxoghost.hexaplayer.ui.state.UpdateActions
import com.oxoghost.hexaplayer.ui.state.UpdateState
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockBar
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TPopup
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TUpdatePopup(state: UpdateState, actions: UpdateActions) {
    val c = TerminalTheme.colors
    TPopup(true, title = "update", onDismiss = actions::dismiss, anchorTop = true, borderColor = c.accent2) {
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TText(
                buildAnnotatedString {
                    append("v${state.current} ")
                    withStyle(SpanStyle(color = c.dim)) { append("→ ") }
                    withStyle(SpanStyle(color = c.accent2)) { append("v${state.latest}") }
                },
                style = TerminalTheme.type.title,
            )
            TText(listOf(state.size, state.released).filter { it.isNotEmpty() }.joinToString(" · "), color = c.dim)
            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.changes.forEach { line ->
                    val (prefix, color) = when (line.kind) {
                        ChangeKind.Added -> "+" to c.ok
                        ChangeKind.Changed -> "~" to c.warn
                        ChangeKind.Fixed -> "-" to c.error
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TText(prefix, color = color)
                        TText(line.text, Modifier.weight(1f), maxLines = 2)
                    }
                }
            }
            TText("full changelog on github", Modifier.clickable(role = Role.Button, onClick = actions::openRelease).padding(vertical = 4.dp), color = c.accent)
            when (state.phase) {
                UpdatePhase.Downloading, UpdatePhase.Ready -> {
                    Row {
                        TText(if (state.phase == UpdatePhase.Ready) "downloaded" else "downloading", Modifier.weight(1f), color = c.dim)
                        TText(state.progress, color = c.dim)
                    }
                    TBlockBar(if (state.phase == UpdatePhase.Ready) 1f else state.fraction, color = c.accent2)
                }
                UpdatePhase.Failed -> TText("download failed", color = c.error)
                UpdatePhase.Idle -> {}
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TButton("not now", actions::dismiss, Modifier.weight(1f), large = true)
                when (state.phase) {
                    UpdatePhase.Idle, UpdatePhase.Failed ->
                        TButton("download", actions::download, Modifier.weight(1f), filled = true, color = c.accent2, large = true)
                    UpdatePhase.Downloading -> TButton("install", {}, Modifier.weight(1f), large = true, enabled = false)
                    UpdatePhase.Ready -> TButton("install", actions::install, Modifier.weight(1f), filled = true, color = c.accent2, large = true)
                }
            }
        }
    }
}
