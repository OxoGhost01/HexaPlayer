package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/**
 * Square terminal button. Filled: [color] background (accent by default) with background-colored
 * bold text; one per area at most. Outlined: 1 dp border and text in [color], or the neutral
 * border with foreground text when [color] is null.
 */
@Composable
fun TButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    color: Color? = null,
    large: Boolean = false,
    enabled: Boolean = true,
) {
    val c = TerminalTheme.colors
    val base = if (filled) {
        modifier.background(color ?: c.accent)
    } else {
        modifier.border(1.dp, color ?: c.border)
    }
    Box(
        base
            .height(if (large) Metrics.buttonHeightLarge.dp else Metrics.buttonHeight.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(
            text,
            color = if (filled) c.background else color ?: c.foreground,
            style = TerminalTheme.type.body.copy(fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal),
        )
    }
}

/**
 * Action row `n  play next   ›`: the letter is decorative (hardware keyboard shortcut),
 * the whole row is the button.
 */
@Composable
fun TKeyAction(
    key: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    highlighted: Boolean = false,
    labelColor: Color = TerminalTheme.colors.foreground,
) {
    val c = TerminalTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(if (highlighted) c.selection else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TText(key, Modifier.width(10.dp), color = c.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
        TText(label, Modifier.weight(1f), color = labelColor)
        if (trailing != null) TText(trailing, color = c.accent)
    }
}
