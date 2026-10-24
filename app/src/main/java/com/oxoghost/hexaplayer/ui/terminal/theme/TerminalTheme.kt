package com.oxoghost.hexaplayer.ui.terminal.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

object TerminalTheme {
    val colors: TerminalColors
        @Composable @ReadOnlyComposable get() = LocalTerminalColors.current

    val type: TerminalTypography
        @Composable @ReadOnlyComposable get() = LocalTerminalTypography.current
}

@Composable
fun TerminalTheme(
    palette: String,
    pureBlack: Boolean,
    custom: Map<String, TerminalColors> = emptyMap(),
    content: @Composable () -> Unit,
) {
    val colors = TerminalPalettes.get(palette, pureBlack, custom)
    val indication = remember(colors.selection) { SelectionIndication(colors.selection) }
    CompositionLocalProvider(
        LocalTerminalColors provides colors,
        LocalTerminalTypography provides DefaultTerminalTypography,
        LocalIndication provides indication,
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.selection),
        content = content,
    )
}

/** Pressed state of terminal controls: the selection background, no ripple. */
private class SelectionIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SelectionNode(interactionSource, color)

    override fun equals(other: Any?) = other is SelectionIndication && other.color == color
    override fun hashCode() = color.hashCode()

    private class SelectionNode(
        private val source: InteractionSource,
        private val color: Color,
    ) : Modifier.Node(), DrawModifierNode {
        private var presses = 0

        override fun onAttach() {
            coroutineScope.launch {
                source.interactions.collect {
                    when (it) {
                        is PressInteraction.Press -> presses++
                        is PressInteraction.Release, is PressInteraction.Cancel -> presses--
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            if (presses > 0) drawRect(color)
            drawContent()
        }
    }
}
