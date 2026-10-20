package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

/** Library section tabs: 40 dp pills, horizontally scrollable. */
@Composable
fun MTabChips(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = Metrics.screenPaddingH.dp),
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { i, label ->
            MChip(label, i == selected, { onSelect(i) }, Metrics.tabChip.dp, HexaShapes.tabChip, Role.Tab, showCheck = false)
        }
    }
}

/** Filter chip (search scope, EQ presets): 36 dp, radius 12, check icon when selected. */
@Composable
fun MFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MChip(label, selected, onClick, Metrics.chip.dp, HexaShapes.chip, Role.Checkbox, showCheck = true, modifier = modifier)
}

@Composable
private fun MChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    height: Dp,
    shape: Shape,
    role: Role,
    showCheck: Boolean,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .height(height)
            .clip(shape)
            .background(if (selected) s.secondaryContainer else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, s.outlineVariant, shape))
            .selectable(selected, role = role, onClick = onClick)
            .padding(horizontal = if (showCheck && selected) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showCheck && selected) Symbol(Symbols.Check, null, size = 18.dp, tint = s.onSecondaryContainer)
        Text(
            label,
            color = if (selected) s.onSecondaryContainer else s.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}
