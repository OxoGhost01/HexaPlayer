package com.oxoghost.hexaplayer.ui.terminal.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.TypeToken

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

@Immutable
data class TerminalTypography(
    val hero: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val small: TextStyle,
    val tiny: TextStyle,
)

// Ligatures off so glyphs stay in their character columns
private fun TypeToken.toStyle() = TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = "calt 0, liga 0",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

val DefaultTerminalTypography = with(DesignTokens.Terminal.Type) {
    TerminalTypography(
        hero = hero.toStyle(),
        display = display.toStyle(),
        title = title.toStyle(),
        body = body.toStyle(),
        small = small.toStyle(),
        tiny = tiny.toStyle(),
    )
}

val LocalTerminalTypography = staticCompositionLocalOf { DefaultTerminalTypography }
