package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Scalloped "cookie" (M3 expressive shape): r = R * (1 - depth * (1 - cos(lobes * θ)) / 2).
 * 12 lobes for Identify, 9 for the Wrapped decoration.
 */
class CookieShape(private val lobes: Int = 12, private val depth: Float = 0.12f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = min(cx, cy)
        val steps = lobes * 24
        val path = Path()
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val r = radius * (1 - depth * (1 - cos(lobes * t)) / 2)
            val x = cx + (r * cos(t - PI / 2)).toFloat()
            val y = cy + (r * sin(t - PI / 2)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
