package com.oxoghost.hexaplayer.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.R

@OptIn(ExperimentalTextApi::class)
private fun symbolsFont(fill: Float) = FontFamily(
    Font(
        R.font.material_symbols_rounded,
        variationSettings = FontVariation.Settings(FontVariation.Setting("FILL", fill)),
    )
)

private val Outlined = symbolsFont(0f)
private val Filled = symbolsFont(1f)

/**
 * A Material Symbols Rounded icon. [icon] is a code point from [Symbols].
 * Sized in dp so it never grows with the system font scale.
 */
@Composable
fun Symbol(
    icon: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    val fontSize = with(LocalDensity.current) { size.toSp() }
    val semantics = if (contentDescription != null) {
        Modifier.clearAndSetSemantics {
            this.contentDescription = contentDescription
            role = Role.Image
        }
    } else {
        Modifier.clearAndSetSemantics {}
    }
    Box(modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
        BasicText(
            text = icon,
            style = TextStyle(
                fontFamily = if (filled) Filled else Outlined,
                fontSize = fontSize,
                lineHeight = fontSize,
                color = tint,
                textAlign = TextAlign.Center,
            ),
        )
    }
}
