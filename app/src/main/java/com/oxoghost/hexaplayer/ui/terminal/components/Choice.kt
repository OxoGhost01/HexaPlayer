package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics

/**
 * Segmented row of bordered cells (sleep presets, search scope). The selected cell uses the
 * selection background with border and text in [accent], bold.
 */
@Composable
fun TChoiceRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = TerminalTheme.colors.accent,
    height: Int = Metrics.buttonHeightLarge,
) {
    val c = TerminalTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .border(1.dp, if (on) accent else c.border)
                    .background(if (on) c.selection else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                TText(
                    label,
                    color = if (on) accent else c.foreground,
                    style = TerminalTheme.type.body.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Normal),
                )
            }
        }
    }
}

/** Value stepper `<  hip hop  >` with 40 dp arrow buttons (EQ preset, reverb room). */
@Composable
fun TStepper(
    value: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    previousDescription: String = "Previous",
    nextDescription: String = "Next",
) {
    val c = TerminalTheme.colors
    Row(
        modifier.fillMaxWidth().height(Metrics.touchMin.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (label != null) TText(label, color = c.dim)
        StepperArrow("<", previousDescription, onPrevious)
        TText(
            value,
            Modifier.weight(1f),
            style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
        )
        StepperArrow(">", nextDescription, onNext)
    }
}

@Composable
private fun StepperArrow(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp, Metrics.touchMin.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TText(glyph, color = TerminalTheme.colors.accent)
    }
}
