package com.oxoghost.hexaplayer.ui.tokens

import androidx.compose.ui.graphics.Color

/** The 11 color roles of a terminal palette. */
data class TerminalPaletteTokens(
    val background: Color,
    val panel: Color,
    val selection: Color,
    val foreground: Color,
    val dim: Color,
    val accent: Color,
    val accent2: Color,
    val ok: Color,
    val warn: Color,
    val error: Color,
    val border: Color,
)

/** The 23 precomputed roles of a dark Material scheme. */
data class MaterialSchemeTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val surface: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

/** A text style from the tokens: sizes in sp, tracking in px (converted to sp by the themes). */
data class TypeToken(val size: Int, val line: Int, val weight: Int, val tracking: Float = 0f)

/** One shape of a generated placeholder cover, in unit coordinates. */
sealed interface CoverShape {
    val color: Color

    data class Circle(val cx: Float, val cy: Float, val r: Float, override val color: Color) : CoverShape
    data class Rect(val x: Float, val y: Float, val w: Float, val h: Float, override val color: Color) : CoverShape
}

data class CoverArtTokens(val background: Color, val shapes: List<CoverShape>)
