package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols

/** One distinct shape per palette, in palette order. */
object SwatchShapes {
    private val shapes: List<Shape> = listOf(
        CircleShape,
        RoundedCornerShape(22.dp),
        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 12),
        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 8, bottomStartPercent = 50),
        CookieShape(lobes = 8, depth = 0.1f),
        RoundedCornerShape(topStartPercent = 30, topEndPercent = 50, bottomEndPercent = 30, bottomStartPercent = 50),
        RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 20, bottomStartPercent = 20),
        CookieShape(lobes = 6, depth = 0.12f),
    )

    fun forIndex(index: Int): Shape = shapes[index % shapes.size]
}

/**
 * Palette swatch: 64 dp shape split into primary (top), secondaryContainer (bottom left) and
 * tertiaryContainer (bottom right). Selected: 2 dp primary ring 3 dp away and a check badge.
 */
@Composable
fun MPaletteSwatch(
    scheme: ColorScheme,
    label: String,
    shape: Shape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val s = MaterialTheme.colorScheme
    Column(
        modifier.selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(74.dp), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(74.dp).border(2.dp, s.primary, shape))
            Canvas(Modifier.size(64.dp).clip(shape)) {
                val half = size.height / 2
                drawRect(scheme.primary, Offset.Zero, Size(size.width, half))
                drawRect(scheme.secondaryContainer, Offset(0f, half), Size(size.width / 2, half))
                drawRect(scheme.tertiaryContainer, Offset(size.width / 2, half), Size(size.width / 2, half))
            }
            if (selected) {
                Box(Modifier.size(28.dp).background(s.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Symbol(Symbols.Check, null, size = 18.dp, tint = s.onSurface)
                }
            }
        }
        Text(label, color = if (enabled) s.onSurfaceVariant else s.outline, style = MaterialTheme.typography.labelMedium)
    }
}
