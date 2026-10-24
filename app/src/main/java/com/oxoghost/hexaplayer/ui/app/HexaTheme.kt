package com.oxoghost.hexaplayer.ui.app

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.oxoghost.hexaplayer.ui.material.theme.HexaMaterialTheme
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/**
 * Provides both skins' themes in a fixed structure, so switching skin recomposes with new
 * values instead of rebuilding the tree (navigation and screen state survive). Each skin's
 * screens read only their own theme.
 */
@Composable
fun HexaTheme(settings: UiSettings, albumSeed: Color? = null, content: @Composable () -> Unit) {
    HexaMaterialTheme(settings, albumSeed) {
        TerminalTheme(settings.terminalPalette, settings.pureBlack, settings.customFlavors) {
            // Material components use ripple; the terminal theme replaced the indication.
            val indication = if (settings.skin == Skin.Material) ripple() else LocalIndication.current
            CompositionLocalProvider(LocalIndication provides indication, content = content)
        }
    }
}
