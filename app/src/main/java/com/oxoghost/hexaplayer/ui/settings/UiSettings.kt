package com.oxoghost.hexaplayer.ui.settings

import com.oxoghost.hexaplayer.ui.tokens.TerminalPaletteTokens
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens

enum class Skin { Terminal, Material }

enum class MaterialSource { Preset, Wallpaper, AlbumArt }

/** How a palette is drawn from its seed color: [label] is what the Appearance screen shows. */
enum class ColorStyle(val label: String) {
    /** Material's tonal spot, the 2.0 default: soft, close to grey. */
    Muted("Muted"),
    Vibrant("Vibrant"),
    /** Shifts hues around the seed for more contrast between roles. */
    Expressive("Expressive"),
    /** Keeps the seed's own color: album art looks like the cover. */
    Faithful("Faithful"),
}

data class UiSettings(
    val skin: Skin = Skin.Material,
    val terminalPalette: String = DesignTokens.Terminal.DEFAULT_PALETTE,
    val materialSource: MaterialSource = MaterialSource.Preset,
    val materialPreset: String = DesignTokens.Material.DEFAULT_SCHEME,
    val materialStyle: ColorStyle = ColorStyle.Muted,
    val pureBlack: Boolean = false,
    val tintPlayerWithCover: Boolean = true,
    val pixelCovers: Boolean = true,
    /** Terminal flavors made in the flavor editor, by name. */
    val customFlavors: Map<String, TerminalPaletteTokens> = emptyMap(),
)
