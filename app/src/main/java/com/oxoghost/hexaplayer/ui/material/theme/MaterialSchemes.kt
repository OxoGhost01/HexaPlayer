package com.oxoghost.hexaplayer.ui.material.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.dynamiccolor.DynamicColor
import com.materialkolor.dynamiccolor.MaterialDynamicColors
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeMonochrome
import com.materialkolor.scheme.SchemeTonalSpot
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.MaterialSchemeTokens

/** Builds the dark Material color schemes used by the material skin. */
object MaterialSchemes {

    const val GRAPHITE = "Graphite"

    /** The palette swatch that stands for the album-art color source. */
    const val ALBUM_ART = "Album art"

    /** The spec version that reproduces the precomputed schemes in tokens.json. */
    internal val SPEC = ColorSpec.SpecVersion.SPEC_2021

    private val dynamicColors = MaterialDynamicColors()

    val presetNames: List<String> get() = DesignTokens.Material.schemes.keys.toList()

    fun isWallpaperAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** A preset from tokens.json; roles the tokens don't list come from the same seed. */
    fun preset(name: String): ColorScheme {
        val key = if (name in DesignTokens.Material.schemes) name else DesignTokens.Material.DEFAULT_SCHEME
        val tokens = DesignTokens.Material.schemes.getValue(key)
        val seed = DesignTokens.Material.seeds[key] ?: DesignTokens.Material.seeds.getValue(DesignTokens.Material.DEFAULT_SCHEME)
        return fromSeed(seed, monochrome = key == GRAPHITE).withTokens(tokens)
    }

    /** TonalSpot dark scheme for any seed (album art, future sources). */
    fun fromSeed(seed: Color, monochrome: Boolean = false): ColorScheme {
        val hct = Hct.fromInt(seed.toArgb())
        val scheme = if (monochrome) SchemeMonochrome(hct, true, 0.0, SPEC)
        else SchemeTonalSpot(hct, true, 0.0, SPEC)
        return scheme.toColorScheme()
    }

    fun wallpaper(context: Context, fallback: String): ColorScheme =
        if (isWallpaperAvailable()) dynamicDarkColorScheme(context) else preset(fallback)

    /**
     * Pure black: surface and the lowest container become black and every container
     * level shifts one step down (tokens.json > material.amoled).
     */
    fun ColorScheme.pureBlack(): ColorScheme = copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = surfaceContainerLowest,
        surfaceContainer = surfaceContainerLow,
        surfaceContainerHigh = surfaceContainer,
        surfaceContainerHighest = surfaceContainerHigh,
    )

    private fun ColorScheme.withTokens(t: MaterialSchemeTokens): ColorScheme = copy(
        primary = t.primary,
        onPrimary = t.onPrimary,
        primaryContainer = t.primaryContainer,
        onPrimaryContainer = t.onPrimaryContainer,
        secondary = t.secondary,
        secondaryContainer = t.secondaryContainer,
        onSecondaryContainer = t.onSecondaryContainer,
        tertiary = t.tertiary,
        tertiaryContainer = t.tertiaryContainer,
        onTertiaryContainer = t.onTertiaryContainer,
        background = t.surface,
        onBackground = t.onSurface,
        surface = t.surface,
        surfaceContainerLowest = t.surfaceContainerLowest,
        surfaceContainerLow = t.surfaceContainerLow,
        surfaceContainer = t.surfaceContainer,
        surfaceContainerHigh = t.surfaceContainerHigh,
        surfaceContainerHighest = t.surfaceContainerHighest,
        onSurface = t.onSurface,
        onSurfaceVariant = t.onSurfaceVariant,
        outline = t.outline,
        outlineVariant = t.outlineVariant,
        error = t.error,
        errorContainer = t.errorContainer,
        onErrorContainer = t.onErrorContainer,
    )

    private fun DynamicScheme.toColorScheme(): ColorScheme {
        val d = dynamicColors
        fun c(color: DynamicColor) = Color(color.getArgb(this))
        return darkColorScheme(
            primary = c(d.primary()),
            onPrimary = c(d.onPrimary()),
            primaryContainer = c(d.primaryContainer()),
            onPrimaryContainer = c(d.onPrimaryContainer()),
            inversePrimary = c(d.inversePrimary()),
            secondary = c(d.secondary()),
            onSecondary = c(d.onSecondary()),
            secondaryContainer = c(d.secondaryContainer()),
            onSecondaryContainer = c(d.onSecondaryContainer()),
            tertiary = c(d.tertiary()),
            onTertiary = c(d.onTertiary()),
            tertiaryContainer = c(d.tertiaryContainer()),
            onTertiaryContainer = c(d.onTertiaryContainer()),
            background = c(d.background()),
            onBackground = c(d.onBackground()),
            surface = c(d.surface()),
            onSurface = c(d.onSurface()),
            surfaceVariant = c(d.surfaceVariant()),
            onSurfaceVariant = c(d.onSurfaceVariant()),
            surfaceTint = c(d.surfaceTint()),
            inverseSurface = c(d.inverseSurface()),
            inverseOnSurface = c(d.inverseOnSurface()),
            error = c(d.error()),
            onError = c(d.onError()),
            errorContainer = c(d.errorContainer()),
            onErrorContainer = c(d.onErrorContainer()),
            outline = c(d.outline()),
            outlineVariant = c(d.outlineVariant()),
            scrim = c(d.scrim()),
            surfaceBright = c(d.surfaceBright()),
            surfaceDim = c(d.surfaceDim()),
            surfaceContainer = c(d.surfaceContainer()),
            surfaceContainerHigh = c(d.surfaceContainerHigh()),
            surfaceContainerHighest = c(d.surfaceContainerHighest()),
            surfaceContainerLow = c(d.surfaceContainerLow()),
            surfaceContainerLowest = c(d.surfaceContainerLowest()),
        )
    }
}
