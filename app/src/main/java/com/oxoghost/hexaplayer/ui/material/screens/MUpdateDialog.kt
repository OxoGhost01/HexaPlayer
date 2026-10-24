package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.app.UpdatePhase
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MDialog
import com.oxoghost.hexaplayer.ui.material.components.MLinearProgress
import com.oxoghost.hexaplayer.ui.state.ChangeKind
import com.oxoghost.hexaplayer.ui.state.UpdateActions
import com.oxoghost.hexaplayer.ui.state.UpdateState

@Composable
fun MUpdateDialog(state: UpdateState, actions: UpdateActions) {
    val s = MaterialTheme.colorScheme
    MDialog(
        title = "Update available",
        onDismiss = actions::dismiss,
        confirmText = when (state.phase) {
            UpdatePhase.Idle -> "Download"
            UpdatePhase.Failed -> "Try again"
            else -> "Install"
        },
        onConfirm = if (state.phase == UpdatePhase.Ready) actions::install else actions::download,
        icon = Symbols.SystemUpdate,
        dismissText = "Not now",
        confirmEnabled = state.phase != UpdatePhase.Downloading,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                listOf("Version ${state.latest}", state.size).filter { it.isNotEmpty() }.joinToString(" · "),
                Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = s.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            state.changes.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Symbol(
                        when (line.kind) {
                            ChangeKind.Added -> Symbols.AddCircle
                            ChangeKind.Changed -> Symbols.Speed
                            ChangeKind.Fixed -> Symbols.Build
                        },
                        null,
                        tint = s.primary,
                    )
                    Text(line.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            when (state.phase) {
                UpdatePhase.Downloading, UpdatePhase.Ready -> Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        Text(if (state.phase == UpdatePhase.Ready) "Downloaded" else "Downloading", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(state.progress.replace(" / ", " of "), color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    }
                    MLinearProgress(if (state.phase == UpdatePhase.Ready) 1f else state.fraction, Modifier.fillMaxWidth())
                }
                UpdatePhase.Failed -> Text("The download failed.", color = s.error)
                UpdatePhase.Idle -> {}
            }
        }
    }
}
