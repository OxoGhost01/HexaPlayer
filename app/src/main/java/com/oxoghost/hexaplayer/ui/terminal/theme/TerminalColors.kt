package com.oxoghost.hexaplayer.ui.terminal.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.TerminalPaletteTokens

/** The 11 roles of the active terminal palette (see tokens.json > terminal.palette_roles). */
typealias TerminalColors = TerminalPaletteTokens

object TerminalPalettes {
    val names: List<String> get() = DesignTokens.Terminal.palettes.keys.toList()

    fun get(name: String, pureBlack: Boolean): TerminalColors {
        val base = DesignTokens.Terminal.palettes[name]
            ?: DesignTokens.Terminal.palettes.getValue(DesignTokens.Terminal.DEFAULT_PALETTE)
        return if (pureBlack) base.copy(background = Color.Black) else base
    }
}

val LocalTerminalColors = staticCompositionLocalOf { TerminalPalettes.get(DesignTokens.Terminal.DEFAULT_PALETTE, false) }
