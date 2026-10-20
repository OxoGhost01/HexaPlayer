package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/** `[x] label` / `[ ] label`. The whole row toggles. */
@Composable
fun TToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
) {
    val c = TerminalTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.touchMin.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TCheckMark(checked)
        Column(Modifier.weight(1f)) {
            TText(label)
            if (sublabel != null) TText(sublabel, color = c.dim, style = TerminalTheme.type.small)
        }
    }
}

/** The `[x]` / `[ ]` mark alone, for config rows and effect rows. */
@Composable
fun TCheckMark(checked: Boolean, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    TText(
        if (checked) "[x]" else "[ ]",
        modifier,
        color = if (checked) c.accent else c.dim,
        style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold),
    )
}
