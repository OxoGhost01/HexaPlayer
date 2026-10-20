package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

enum class MButtonStyle { Filled, Tonal, Outlined, Text }
enum class MButtonSize(val height: Int) { L(Metrics.buttonL), M(Metrics.buttonM), S(Metrics.buttonS) }

/** Pill button. Filled = primary, Tonal = secondaryContainer, Outlined = 1 dp outline, Text. */
@Composable
fun MPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: MButtonStyle = MButtonStyle.Filled,
    size: MButtonSize = MButtonSize.L,
    icon: String? = null,
    enabled: Boolean = true,
    contentColor: Color? = null,
) {
    val s = MaterialTheme.colorScheme
    val (bg, fg) = when (style) {
        MButtonStyle.Filled -> s.primary to s.onPrimary
        MButtonStyle.Tonal -> s.secondaryContainer to s.onSecondaryContainer
        MButtonStyle.Outlined -> Color.Transparent to s.primary
        MButtonStyle.Text -> Color.Transparent to s.primary
    }
    val content = contentColor ?: fg
    Row(
        modifier
            .height(size.height.dp)
            .clip(CircleShape)
            .background(bg)
            .then(if (style == MButtonStyle.Outlined) Modifier.border(1.dp, s.outline, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = if (style == MButtonStyle.Text) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Symbol(icon, null, size = 20.dp, filled = true, tint = content)
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** One option of a [MConnectedButtonGroup]. */
data class MGroupOption(val label: String, val icon: String? = null)

/**
 * Connected button group (sleep presets, color source): 4 dp gaps, outer radius 20, inner 8.
 * The selected segment morphs into a full pill in primary.
 */
@Composable
fun MConnectedButtonGroup(
    options: List<MGroupOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Int = Metrics.buttonL,
) {
    val s = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val pill = (height / 2).dp
            val spring = spring<androidx.compose.ui.unit.Dp>(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)
            val start by animateDpAsState(if (on || i == 0) if (on) pill else 20.dp else 8.dp, spring, label = "start")
            val end by animateDpAsState(if (on || i == options.lastIndex) if (on) pill else 20.dp else 8.dp, spring, label = "end")
            val bg by animateColorAsState(if (on) s.primary else s.surfaceContainerHigh, label = "bg")
            val fg = if (on) s.onPrimary else s.onSurface
            val shape = RoundedCornerShape(start, end, end, start)
            Row(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(shape)
                    .background(bg)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                if (o.icon != null) Symbol(o.icon, null, tint = fg)
                Text(o.label, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
    }
}
