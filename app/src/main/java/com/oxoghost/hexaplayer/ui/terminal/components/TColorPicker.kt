package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** #RRGGBB of an opaque color. */
fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

fun parseHex(text: String): Color? {
    val t = text.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/** HSV picker: saturation / value square, hue bar and a hex input. */
@Composable
fun TColorPicker(color: Color, onChange: (Color) -> Unit, modifier: Modifier = Modifier) {
    val c = TerminalTheme.colors
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) } }
    val change by rememberUpdatedState(onChange)
    fun emit(h: Float, s: Float, v: Float) = change(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))))
    var hexText by remember(color) { mutableStateOf(color.hex()) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .border(1.dp, c.border)
                .semantics { contentDescription = "Saturation and brightness" }
                .pointerInput(hsv[0]) {
                    fun at(p: Offset) = emit(hsv[0], (p.x / size.width).coerceIn(0f, 1f), 1f - (p.y / size.height).coerceIn(0f, 1f))
                    detectTapGestures { at(it) }
                }
                .pointerInput(hsv[0]) {
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        emit(hsv[0], (ch.position.x / size.width).coerceIn(0f, 1f), 1f - (ch.position.y / size.height).coerceIn(0f, 1f))
                    }
                }
        ) {
            val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f)))
            drawRect(Brush.horizontalGradient(listOf(Color.White, pure)))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val x = hsv[1] * size.width
            val y = (1f - hsv[2]) * size.height
            drawCircle(Color.White, 8.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .border(1.dp, c.border)
                .semantics { contentDescription = "Hue" }
                .pointerInput(hsv[1], hsv[2]) { detectTapGestures { emit((it.x / size.width).coerceIn(0f, 1f) * 360f, hsv[1], hsv[2]) } }
                .pointerInput(hsv[1], hsv[2]) {
                    detectDragGestures { ch, _ ->
                        ch.consume()
                        emit((ch.position.x / size.width).coerceIn(0f, 1f) * 360f, hsv[1], hsv[2])
                    }
                }
        ) {
            drawRect(Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
            val x = hsv[0] / 360f * size.width
            drawRect(Color.White, Offset(x - 2.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
        }
        TInput(
            hexText,
            { text ->
                hexText = text
                parseHex(text)?.let(change)
            },
            prompt = "#",
            placeholder = "RRGGBB",
            trailing = { TText(if (parseHex(hexText) != null) "ok" else "?", color = if (parseHex(hexText) != null) c.ok else c.error) },
        )
    }
}
