package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import kotlin.math.cos
import kotlin.math.sin
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/**
 * Monospace text for the terminal skin. Glyphs missing from JetBrains Mono (⇄ ↻ ⟳ ♪) are drawn
 * in one character cell instead of falling back to another font.
 */
@Composable
fun TText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TerminalTheme.type.body,
    color: Color = TerminalTheme.colors.foreground,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) = TText(AnnotatedString(text), modifier, style, color, maxLines, overflow)

@Composable
fun TText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TerminalTheme.type.body,
    color: Color = TerminalTheme.colors.foreground,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val prepared = remember(text) { replaceDrawnGlyphs(text) }
    val inline = if (prepared.second) drawnGlyphContent(color) else emptyMap()
    BasicText(
        text = prepared.first,
        modifier = modifier,
        style = style.merge(TextStyle(color = color)),
        maxLines = maxLines,
        overflow = overflow,
        softWrap = maxLines > 1,
        inlineContent = inline,
    )
}

/** Width of one character cell of [style] (JetBrains Mono is monospaced). */
@Composable
fun rememberCellWidth(style: TextStyle = TerminalTheme.type.body): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        with(density) { measurer.measure("0000000000", style).size.width.toDp() / 10 }
    }
}

private val drawnGlyphs = mapOf('⇄' to "g_shuffle", '↻' to "g_repeat", '⟳' to "g_sync", '♪' to "g_note")

private fun replaceDrawnGlyphs(text: AnnotatedString): Pair<AnnotatedString, Boolean> {
    if (text.none { it in drawnGlyphs }) return text to false
    return buildAnnotatedString {
        var start = 0
        text.forEachIndexed { i, ch ->
            val id = drawnGlyphs[ch] ?: return@forEachIndexed
            append(text.subSequence(start, i))
            appendInlineContent(id, ch.toString())
            start = i + 1
        }
        append(text.subSequence(start, text.length))
    } to true
}

private fun drawnGlyphContent(color: Color): Map<String, InlineTextContent> {
    val placeholder = Placeholder(0.6.em, 1.em, PlaceholderVerticalAlign.TextCenter)
    fun glyph(draw: DrawScope.(Color) -> Unit) = InlineTextContent(placeholder) {
        Canvas(Modifier.fillMaxSize()) { draw(color) }
    }
    return mapOf(
        "g_shuffle" to glyph { drawShuffle(it) },
        "g_repeat" to glyph { drawCircleArrow(it, gapped = false) },
        "g_sync" to glyph { drawCircleArrow(it, gapped = true) },
        "g_note" to glyph { drawNote(it) },
    )
}

private fun DrawScope.stroke() = Stroke(width = size.width * 0.13f, cap = StrokeCap.Round)

private fun DrawScope.arrowHead(c: Color, tip: Offset, dirX: Float, dirY: Float, length: Float, width: Float) {
    // Two barbs swept back from the tip, 35 degrees either side of the direction
    val cosA = 0.819f
    val sinA = 0.574f
    for (sign in listOf(1f, -1f)) {
        val bx = -(dirX * cosA - sign * dirY * sinA)
        val by = -(dirY * cosA + sign * dirX * sinA)
        drawLine(c, tip, Offset(tip.x + bx * length, tip.y + by * length), width, StrokeCap.Round)
    }
}

private fun DrawScope.drawShuffle(c: Color) {
    val w = size.width
    val h = size.height
    val s = stroke().width
    val head = w * 0.32f
    val y1 = h * 0.38f
    val y2 = h * 0.62f
    drawLine(c, Offset(w * 0.06f, y1), Offset(w * 0.94f, y1), s, StrokeCap.Round)
    arrowHead(c, Offset(w * 0.94f, y1), 1f, 0f, head, s)
    drawLine(c, Offset(w * 0.06f, y2), Offset(w * 0.94f, y2), s, StrokeCap.Round)
    arrowHead(c, Offset(w * 0.06f, y2), -1f, 0f, head, s)
}

/** Clockwise open circle arrow (↻, and ⟳ with a wider gap). */
private fun DrawScope.drawCircleArrow(c: Color, gapped: Boolean) {
    val r = size.width * 0.44f
    val center = Offset(size.width / 2, size.height / 2)
    val s = stroke().width
    val start = -40f
    val sweep = if (gapped) 270f else 300f
    drawArc(
        color = c,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = center - Offset(r, r),
        size = Size(r * 2, r * 2),
        style = Stroke(width = s, cap = StrokeCap.Round),
    )
    val end = Math.toRadians((start + sweep).toDouble())
    val tip = Offset(center.x + r * cos(end).toFloat(), center.y + r * sin(end).toFloat())
    // Clockwise tangent in screen coordinates
    arrowHead(c, tip, -sin(end).toFloat(), cos(end).toFloat(), size.width * 0.36f, s)
}

private fun DrawScope.drawNote(c: Color) {
    val w = size.width
    val h = size.height
    val head = Rect(Offset(w * 0.12f, h * 0.62f), Size(w * 0.46f, h * 0.2f))
    drawOval(c, head.topLeft, head.size)
    val stemX = head.right - w * 0.06f
    drawLine(c, Offset(stemX, head.center.y), Offset(stemX, h * 0.16f), stroke().width)
    val flag = Path().apply {
        moveTo(stemX, h * 0.16f)
        quadraticTo(w * 0.95f, h * 0.3f, w * 0.82f, h * 0.5f)
    }
    drawPath(flag, c, style = stroke())
}
