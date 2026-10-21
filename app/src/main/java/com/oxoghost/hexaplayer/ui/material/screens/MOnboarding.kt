package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.state.OnboardingActions
import com.oxoghost.hexaplayer.ui.state.OnboardingState

@Composable
fun MOnboarding(state: OnboardingState, actions: OnboardingActions) {
    val s = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .background(s.surface)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(88.dp).background(s.primaryContainer, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
                    Symbol(Symbols.Hexagon, null, size = 44.dp, filled = true, tint = s.onPrimaryContainer)
                }
                Text("Welcome", Modifier.padding(top = 24.dp), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
                Text("HexaPlayer ${state.version} · your music, offline", Modifier.padding(top = 4.dp),
                    color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            }
            MSectionLabel("Required", Modifier.padding(top = 32.dp, bottom = 4.dp))
            MSegment(0, 1, minHeight = 88) {
                IconCircle(Symbols.MusicNote)
                Texts("Music and audio", "Find and play the songs on this phone", Modifier.weight(1f))
                MPillButton(if (state.hasAudio) "Allowed" else "Allow", actions::grantAudio, size = MButtonSize.S, enabled = !state.hasAudio)
            }
            MSectionLabel("Optional", Modifier.padding(top = 24.dp, bottom = 4.dp))
            MSegmentedGroup {
                val count = if (state.asksNotifications) 2 else 1
                if (state.asksNotifications) {
                    MSegment(0, count, minHeight = 88) {
                        IconCircle(Symbols.Notifications)
                        Texts("Notifications", "Playback controls and update alerts", Modifier.weight(1f))
                        MPillButton(if (state.hasNotifications) "Allowed" else "Allow", actions::allowNotifications,
                            style = MButtonStyle.Tonal, size = MButtonSize.S, enabled = !state.hasNotifications)
                    }
                }
                MSegment(count - 1, count, minHeight = 80, onClick = actions::chooseFolder) {
                    IconCircle(Symbols.FolderOpen)
                    Texts("Library folder", "${state.folder.trim('/').ifEmpty { "All audio" }} · change anytime", Modifier.weight(1f))
                    Symbol(Symbols.ChevronRight, null, tint = s.onSurfaceVariant)
                }
            }
        }
        Text(
            "No account, no ads, no tracking. Nothing about your music leaves this phone.",
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
            color = s.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MPillButton("Cancel", actions::quit, Modifier.weight(1f), style = MButtonStyle.Tonal)
            MPillButton("Get started", actions::start, Modifier.weight(1f), enabled = state.hasAudio)
        }
    }
}

@Composable
private fun IconCircle(icon: String) {
    val s = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).background(s.surfaceContainerHighest, CircleShape), contentAlignment = Alignment.Center) {
        Symbol(icon, null, size = 22.dp, filled = true, tint = s.onSurfaceVariant)
    }
}

@Composable
private fun Texts(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
