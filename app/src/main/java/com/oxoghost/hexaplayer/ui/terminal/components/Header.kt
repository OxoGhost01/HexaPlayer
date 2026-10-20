package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/**
 * Screen header: `hexa ~/lib/ songs        [:set]`. With [onBack] it starts with `[←]` instead
 * of `hexa`. [path] is dim, [current] is the last path segment, [suffix] extra dim text.
 */
@Composable
fun THeader(
    path: String,
    modifier: Modifier = Modifier,
    current: String? = null,
    suffix: String? = null,
    onBack: (() -> Unit)? = null,
    action: String? = null,
    actionColor: Color = TerminalTheme.colors.dim,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = TerminalTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.header.dp)
            .padding(start = if (onBack != null) 4.dp else Metrics.screenPaddingH.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) {
            THeaderButton("[←]", c.dim, "Back", onBack)
        } else {
            TText("hexa", color = c.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
        }
        TText(path, color = c.dim)
        if (current != null) TText(current)
        if (suffix != null) TText(suffix, color = c.dim)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) THeaderButton(action, actionColor, actionDescription, onAction)
    }
}

@Composable
internal fun THeaderButton(text: String, color: Color, description: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = Metrics.touchMin.dp)
            .widthIn(min = Metrics.touchMin.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TText(text, color = color)
    }
}
