package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.Tab

/** Library content when the scan found no audio. */
@Composable
fun MEmptyLibrary(folder: String, actions: HexaActions, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = LocalChromePadding.current),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(180.dp, 150.dp)) {
            Box(Modifier.size(110.dp).background(s.surfaceContainerHigh, RoundedCornerShape(32.dp)))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset((-8).dp, 0.dp)
                    .size(96.dp)
                    .background(s.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(Symbols.MusicOff, null, size = 40.dp, tint = s.onSecondaryContainer)
            }
        }
        Text(
            "No music here yet",
            Modifier.padding(top = 28.dp),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            if (folder.isBlank()) {
                buildAnnotatedString { append("No audio files were found on this device.") }
            } else {
                buildAnnotatedString {
                    append("Your library folder ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = s.onSurface)) { append(folder.trim('/')) }
                    append(" has no audio files.")
                }
            },
            Modifier.padding(top = 8.dp),
            color = s.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Column(Modifier.fillMaxWidth().padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MPillButton("Rescan library", actions::rescan, Modifier.fillMaxWidth(), icon = Symbols.Refresh)
            MPillButton("Choose another folder", actions::chooseFolder, Modifier.fillMaxWidth(), style = MButtonStyle.Tonal, icon = Symbols.FolderOpen)
            MPillButton("Download music", { actions.openTab(Tab.Download) }, Modifier.fillMaxWidth(), style = MButtonStyle.Outlined, icon = Symbols.Download)
            if (folder.isNotBlank()) {
                MPillButton("Scan all audio on this device", actions::scanAllAudio, Modifier.fillMaxWidth(), style = MButtonStyle.Text)
            }
        }
    }
}
