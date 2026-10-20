package com.oxoghost.hexaplayer.ui.settings

import com.oxoghost.hexaplayer.ui.tokens.DesignTokens

enum class Skin { Terminal, Material }

enum class MaterialSource { Preset, Wallpaper, AlbumArt }

data class UiSettings(
    val skin: Skin = Skin.Material,
    val terminalPalette: String = DesignTokens.Terminal.DEFAULT_PALETTE,
    val materialSource: MaterialSource = MaterialSource.Preset,
    val materialPreset: String = DesignTokens.Material.DEFAULT_SCHEME,
    val pureBlack: Boolean = false,
    val tintPlayerWithCover: Boolean = true,
    val pixelCovers: Boolean = true,
)
