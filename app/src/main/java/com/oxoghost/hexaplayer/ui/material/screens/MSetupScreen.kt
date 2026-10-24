package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar
import com.oxoghost.hexaplayer.ui.state.SetupState
import com.oxoghost.hexaplayer.ui.state.TermuxSetupActions

@Composable
fun MSetupScreen(state: SetupState, actions: TermuxSetupActions, onBack: () -> Unit) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(s.surface).statusBarsPadding()) {
        MSubTopBar("Downloader setup", onBack, large = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "spotDL runs in the free Termux app, on this phone. Nothing goes to an outside server. Steps 1 to 3 are one-time.",
                color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge,
            )
            MSegmentedGroup {
                Step(0, "Install Termux", state.termuxInstalled, state.current == 0) {
                    if (state.termuxInstalled) {
                        Body("Installed. Use the F-Droid or GitHub build, not the Play Store one.")
                    } else {
                        Body("Get Termux from F-Droid or GitHub (the universal APK), then open it once.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MPillButton("F-Droid", { actions.openTermuxPage(false) }, size = MButtonSize.M)
                            MPillButton("GitHub", { actions.openTermuxPage(true) }, style = MButtonStyle.Tonal, size = MButtonSize.M)
                        }
                    }
                }
                Step(1, "Run the setup command", state.setupRun, state.current == 1) {
                    Text(
                        state.command,
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(s.surfaceContainerLowest)
                            .padding(16.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    MPillButton("Copy and open Termux", actions::copyAndOpenTermux, style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.ContentCopy)
                }
                Step(2, "Let HexaPlayer start Termux", state.permissionGranted, state.current == 2) {
                    Body("Starts the downloader when you need it, stops it after 10 minutes idle.")
                    if (!state.permissionGranted) MPillButton("Allow", actions::allowRunCommand, size = MButtonSize.M)
                }
                Step(3, "Start it once (optional)", state.connected, false) {
                    Body(if (state.connected) "The downloader answers." else "Checks that everything works.")
                    if (state.statusText.isNotEmpty()) Body(state.statusText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MPillButton("Start now", actions::startNow, style = MButtonStyle.Outlined, size = MButtonSize.M)
                        MPillButton("Test", actions::testConnection, style = MButtonStyle.Text, size = MButtonSize.M)
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(s.tertiaryContainer)
                    .padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Symbol(Symbols.Lightbulb, null, tint = s.onTertiaryContainer)
                Text("If downloads stop with the screen off, turn off battery optimization for Termux.",
                    color = s.onTertiaryContainer, style = MaterialTheme.typography.bodyLarge)
            }
            MPillButton("Full guide on GitHub", actions::openFullGuide, Modifier.padding(bottom = 16.dp), style = MButtonStyle.Text, icon = Symbols.OpenInNew)
        }
    }
}

@Composable
private fun Step(index: Int, title: String, done: Boolean, current: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val s = MaterialTheme.colorScheme
    MSegment(index, 4, container = if (current) s.surfaceContainerHigh else s.surfaceContainer) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .then(if (done) Modifier.background(s.primary, CircleShape) else Modifier.border(2.dp, if (current) s.primary else s.outline, CircleShape)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Symbol(Symbols.Check, null, tint = s.onPrimary)
                    else Text("${index + 1}", color = if (current) s.primary else s.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    content()
                }
            }
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
}
