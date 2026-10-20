package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/** Value type of a config row: strings are accent2, booleans accent, numbers warn. */
enum class TValueKind { Text, Bool, Number }

/** `[playback]` section header of the config.toml settings view. */
@Composable
fun TConfigHeader(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(start = Metrics.screenPaddingH.dp, end = Metrics.screenPaddingH.dp, bottom = 4.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        TText("[$name]", color = TerminalTheme.colors.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
    }
}

/** `key   = value   ctl` row; tap edits in place or opens a picker. */
@Composable
fun TConfigRow(
    key: String,
    value: String,
    kind: TValueKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    control: String? = null,
) {
    val c = TerminalTheme.colors
    val valueColor = when (kind) {
        TValueKind.Text -> c.accent2
        TValueKind.Bool -> c.accent
        TValueKind.Number -> c.warn
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(Metrics.touchMin.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Metrics.screenPaddingH.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TText(key, Modifier.width(150.dp))
            TText("=", color = c.dim)
            TText(value, Modifier.weight(1f), color = valueColor)
            if (control != null) TText(control, color = c.dim)
        }
        TDashedDivider()
    }
}
