package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

/**
 * Player toolbar pill: Lyrics, Sleep timer, Queue, Equalizer. When a timer runs, its item becomes
 * a tertiaryContainer pill showing [sleepLabel] (remaining minutes).
 */
@Composable
fun MPlayerToolbar(
    onLyrics: () -> Unit,
    onSleep: () -> Unit,
    onQueue: () -> Unit,
    onEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
    sleepLabel: String? = null,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(HexaShapes.pill)
            .background(s.surfaceContainer)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        MIconButton(Symbols.Lyrics, "Lyrics", onLyrics, tint = s.onSurface, size = 48.dp)
        if (sleepLabel != null) {
            Row(
                Modifier
                    .height(48.dp)
                    .clip(HexaShapes.pill)
                    .background(s.tertiaryContainer)
                    .clickable(role = Role.Button, onClick = onSleep)
                    .semantics { contentDescription = "Sleep timer, $sleepLabel left" }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(Symbols.Bedtime, null, tint = s.onTertiaryContainer)
                Text(sleepLabel, color = s.onTertiaryContainer, style = MaterialTheme.typography.titleSmall)
            }
        } else {
            MIconButton(Symbols.Bedtime, "Sleep timer", onSleep, tint = s.onSurface, size = 48.dp)
        }
        MIconButton(Symbols.QueueMusic, "Queue", onQueue, tint = s.onSurface, size = 48.dp)
        Box(Modifier.widthIn(min = 48.dp)) { MIconButton(Symbols.Equalizer, "Equalizer", onEqualizer, tint = s.onSurface, size = 48.dp) }
    }
}
