package com.oxoghost.hexaplayer.ui.material.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.MaterialSchemeTokens
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The runtime generator (used for album art) must reproduce the precomputed presets. */
class MaterialSchemesTest {

    @Test
    fun seedsReproducePrecomputedSchemes() {
        val mismatches = mutableListOf<String>()
        for ((name, tokens) in DesignTokens.Material.schemes) {
            val seed = DesignTokens.Material.seeds.getValue(name)
            val generated = MaterialSchemes.fromSeed(seed, monochrome = name == MaterialSchemes.GRAPHITE)
            for ((role, expected, actual) in roles(tokens, generated)) {
                if (!close(expected, actual)) {
                    mismatches += "$name.$role expected ${hex(expected)} got ${hex(actual)}"
                }
            }
        }
        assertTrue(mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    private fun roles(t: MaterialSchemeTokens, s: ColorScheme) = listOf(
        Triple("primary", t.primary, s.primary),
        Triple("onPrimary", t.onPrimary, s.onPrimary),
        Triple("primaryContainer", t.primaryContainer, s.primaryContainer),
        Triple("onPrimaryContainer", t.onPrimaryContainer, s.onPrimaryContainer),
        Triple("secondary", t.secondary, s.secondary),
        Triple("secondaryContainer", t.secondaryContainer, s.secondaryContainer),
        Triple("onSecondaryContainer", t.onSecondaryContainer, s.onSecondaryContainer),
        Triple("tertiary", t.tertiary, s.tertiary),
        Triple("tertiaryContainer", t.tertiaryContainer, s.tertiaryContainer),
        Triple("onTertiaryContainer", t.onTertiaryContainer, s.onTertiaryContainer),
        Triple("surface", t.surface, s.surface),
        Triple("surfaceContainerLowest", t.surfaceContainerLowest, s.surfaceContainerLowest),
        Triple("surfaceContainerLow", t.surfaceContainerLow, s.surfaceContainerLow),
        Triple("surfaceContainer", t.surfaceContainer, s.surfaceContainer),
        Triple("surfaceContainerHigh", t.surfaceContainerHigh, s.surfaceContainerHigh),
        Triple("surfaceContainerHighest", t.surfaceContainerHighest, s.surfaceContainerHighest),
        Triple("onSurface", t.onSurface, s.onSurface),
        Triple("onSurfaceVariant", t.onSurfaceVariant, s.onSurfaceVariant),
        Triple("outline", t.outline, s.outline),
        Triple("outlineVariant", t.outlineVariant, s.outlineVariant),
        Triple("error", t.error, s.error),
        Triple("errorContainer", t.errorContainer, s.errorContainer),
        Triple("onErrorContainer", t.onErrorContainer, s.onErrorContainer),
    )

    // One step of rounding per channel is tolerated
    private fun close(a: Color, b: Color): Boolean {
        val x = a.toArgb()
        val y = b.toArgb()
        return (0..2).all { i -> abs(((x shr (i * 8)) and 0xFF) - ((y shr (i * 8)) and 0xFF)) <= 1 }
    }

    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)
}
