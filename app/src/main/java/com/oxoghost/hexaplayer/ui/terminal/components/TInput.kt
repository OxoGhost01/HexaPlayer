package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Metrics
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Shape

/**
 * Terminal input: prompt glyph in accent (`/` search, `$` command, `>` filter, `+` new),
 * border turns accent when focused, block cursor `█` blinking at 1 s.
 */
@Composable
fun TInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    prompt: String = "/",
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = TerminalTheme.colors
    val style = TerminalTheme.type.body.copy(color = c.foreground)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    if (field.text != value) field = field.copy(text = value, selection = TextRange(value.length))
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val atEnd = field.selection.collapsed && field.selection.end == field.text.length
    val cell = rememberCellWidth()
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            RepeatMode.Restart,
        ),
        label = "blink",
    )

    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.touchMin.dp)
            .border(1.dp, if (focused) c.accent else c.border, RoundedCornerShape(Shape.input.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TText(prompt, color = c.accent)
        BasicTextField(
            value = field,
            onValueChange = {
                field = it
                if (it.text != value) onValueChange(it.text)
            },
            modifier = Modifier
                .weight(1f)
                .drawWithContent {
                    drawContent()
                    val l = layout
                    if (focused && atEnd && blink > 0.5f && l != null) {
                        val x = if (field.text.isEmpty()) 0f else l.getHorizontalPosition(field.text.length, true)
                        drawRect(c.foreground, Offset(x, 0f), Size(cell.toPx(), size.height))
                    }
                },
            textStyle = style,
            singleLine = true,
            cursorBrush = SolidColor(if (atEnd) Color.Transparent else c.foreground),
            interactionSource = interaction,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            onTextLayout = { layout = it },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (field.text.isEmpty() && placeholder.isNotEmpty()) TText(placeholder, color = c.dim)
                    inner()
                }
            },
        )
        trailing?.invoke()
    }
}
