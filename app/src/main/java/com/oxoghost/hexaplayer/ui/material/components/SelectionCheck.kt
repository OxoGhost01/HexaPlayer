package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols

/** Checkbox: 22 dp, radius 6, primary fill with check when on, 2 dp onSurfaceVariant border off. */
@Composable
fun MCheckbox(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    CheckMark(checked, onCheckedChange, modifier, 22.dp, RoundedCornerShape(6.dp))
}

/** Multi-select circle: 24 dp. */
@Composable
fun MSelectionCircle(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    CheckMark(checked, onCheckedChange, modifier, 24.dp, CircleShape)
}

/** Check badge drawn on a selected cover. */
@Composable
fun MCheckBadge(modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Box(modifier.size(24.dp).background(s.primary, CircleShape), contentAlignment = Alignment.Center) {
        Symbol(Symbols.Check, null, size = 18.dp, tint = s.onPrimary)
    }
}

@Composable
private fun CheckMark(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier, size: Dp, shape: Shape) {
    val s = MaterialTheme.colorScheme
    val toggle = if (onCheckedChange != null) {
        Modifier.size(44.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .then(
                    if (checked) Modifier.background(s.primary, shape)
                    else Modifier.border(2.dp, s.onSurfaceVariant, shape)
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Symbol(Symbols.Check, null, size = size - 4.dp, tint = s.onPrimary)
        }
    }
}
