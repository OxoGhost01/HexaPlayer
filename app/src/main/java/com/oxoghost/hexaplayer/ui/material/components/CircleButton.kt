package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
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

/** Tonal icon button: a 56 dp circle by default, or any [shape] (squircle for shuffle). */
@Composable
fun MCircleButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    size: Dp = 56.dp,
    iconSize: Dp = 24.dp,
    shape: Shape = CircleShape,
    filled: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(container)
            .clickable(role = Role.Button, onClickLabel = contentDescription, indication = ripple(), interactionSource = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Symbol(icon, contentDescription, size = iconSize, filled = filled, tint = content)
    }
}

/** Transparent icon button with a 44 dp minimum touch target. */
@Composable
fun MIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    filled: Boolean = false,
) {
    MCircleButton(icon, contentDescription, onClick, modifier, Color.Transparent, tint, size, iconSize, CircleShape, filled)
}
