package com.oxoghost.hexaplayer.ui.material.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import com.oxoghost.hexaplayer.ui.tokens.TypeToken

@OptIn(ExperimentalTextApi::class)
val Figtree = FontFamily(
    listOf(400, 500, 600, 700, 800, 900).map { w ->
        Font(
            R.font.figtree,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)

private fun TypeToken.toStyle() = TextStyle(
    fontFamily = Figtree,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun style(size: Int, line: Int, weight: Int) = TypeToken(size, line, weight).toStyle()

/** Figtree on the M3 type scale (tokens.json > material.type). */
val HexaTypography = with(DesignTokens.Material.Type) {
    Typography(
        displayLarge = displayHero.toStyle(),
        displayMedium = display.toStyle(),
        displaySmall = display.toStyle(),
        headlineLarge = headline.toStyle(),
        headlineMedium = headline.toStyle(),
        headlineSmall = style(24, 32, 700),
        titleLarge = title.toStyle(),
        titleMedium = titleMedium.toStyle(),
        titleSmall = bodyLarge.toStyle(),
        bodyLarge = style(16, 24, 400),
        bodyMedium = body.toStyle(),
        bodySmall = caption.toStyle(),
        labelLarge = style(14, 20, 600),
        labelMedium = label.toStyle(),
        labelSmall = caption.toStyle(),
    )
}
