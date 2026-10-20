package com.oxoghost.hexaplayer.ui.material.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes.pureBlack
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.UiSettings

/**
 * Material skin theme. [albumSeed] is the color picked from the current cover, used when the
 * color source is Album art (falls back to the preset when there is no cover).
 */
@Composable
fun HexaMaterialTheme(settings: UiSettings, albumSeed: Color? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val target = remember(settings.materialSource, settings.materialPreset, settings.pureBlack, albumSeed) {
        val base = when (settings.materialSource) {
            MaterialSource.Preset -> MaterialSchemes.preset(settings.materialPreset)
            MaterialSource.Wallpaper -> MaterialSchemes.wallpaper(context, settings.materialPreset)
            MaterialSource.AlbumArt -> albumSeed?.let { MaterialSchemes.fromSeed(it) }
                ?: MaterialSchemes.preset(settings.materialPreset)
        }
        if (settings.pureBlack) base.pureBlack() else base
    }
    val scheme = animateColorScheme(target)
    MaterialTheme(colorScheme = scheme, typography = HexaTypography, shapes = HexaShapes.m3) {
        CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
    }
}

/** Palette, source and pure black changes fade over 300 ms instead of jumping. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec: AnimationSpec<Color> = tween(300)

    @Composable
    fun a(c: Color) = animateColorAsState(c, spec, label = "scheme").value

    return target.copy(
        primary = a(target.primary),
        onPrimary = a(target.onPrimary),
        primaryContainer = a(target.primaryContainer),
        onPrimaryContainer = a(target.onPrimaryContainer),
        inversePrimary = a(target.inversePrimary),
        secondary = a(target.secondary),
        onSecondary = a(target.onSecondary),
        secondaryContainer = a(target.secondaryContainer),
        onSecondaryContainer = a(target.onSecondaryContainer),
        tertiary = a(target.tertiary),
        onTertiary = a(target.onTertiary),
        tertiaryContainer = a(target.tertiaryContainer),
        onTertiaryContainer = a(target.onTertiaryContainer),
        background = a(target.background),
        onBackground = a(target.onBackground),
        surface = a(target.surface),
        onSurface = a(target.onSurface),
        surfaceVariant = a(target.surfaceVariant),
        onSurfaceVariant = a(target.onSurfaceVariant),
        surfaceTint = a(target.surfaceTint),
        inverseSurface = a(target.inverseSurface),
        inverseOnSurface = a(target.inverseOnSurface),
        error = a(target.error),
        onError = a(target.onError),
        errorContainer = a(target.errorContainer),
        onErrorContainer = a(target.onErrorContainer),
        outline = a(target.outline),
        outlineVariant = a(target.outlineVariant),
        surfaceBright = a(target.surfaceBright),
        surfaceDim = a(target.surfaceDim),
        surfaceContainer = a(target.surfaceContainer),
        surfaceContainerHigh = a(target.surfaceContainerHigh),
        surfaceContainerHighest = a(target.surfaceContainerHighest),
        surfaceContainerLow = a(target.surfaceContainerLow),
        surfaceContainerLowest = a(target.surfaceContainerLowest),
    )
}
