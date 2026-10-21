package com.oxoghost.hexaplayer.ui.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TLog
import com.oxoghost.hexaplayer.ui.terminal.components.TLogLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Asks for the music permission. Replaced by the onboarding screens in phase 3. */
@Composable
fun PermissionGate(terminal: Boolean, onGrant: () -> Unit, modifier: Modifier = Modifier) {
    if (terminal) {
        Column(modifier.systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TText("hexa", color = TerminalTheme.colors.accent, style = TerminalTheme.type.display)
            Spacer(Modifier.height(8.dp))
            TLogLine(TLog.Ok, "media3 engine")
            TLogLine(TLog.Waiting, "waiting for permission: music and audio")
            TText("hexaplayer reads the music on this phone. nothing leaves it.", color = TerminalTheme.colors.dim, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            TButton("grant access ⏎", onGrant, Modifier.fillMaxWidth(), filled = true, large = true)
        }
    } else {
        val s = MaterialTheme.colorScheme
        Column(
            modifier.systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Symbol(Symbols.LibraryMusic, null, size = 64.dp, tint = s.primary)
            Text("Welcome", style = MaterialTheme.typography.displaySmall)
            Text(
                "HexaPlayer needs access to your music and audio files to play them. Nothing leaves your phone.",
                color = s.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            MPillButton("Allow access", onGrant, Modifier.fillMaxWidth())
        }
    }
}
