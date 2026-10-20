package com.oxoghost.hexaplayer.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.oxoghost.hexaplayer.ui.material.theme.HexaMaterialTheme
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** Applies the theme of the selected skin. Settings changes recompose, no restart. */
@Composable
fun HexaTheme(settings: UiSettings, albumSeed: Color? = null, content: @Composable () -> Unit) {
    when (settings.skin) {
        Skin.Terminal -> TerminalTheme(settings.terminalPalette, settings.pureBlack, content)
        Skin.Material -> HexaMaterialTheme(settings, albumSeed, content)
    }
}
