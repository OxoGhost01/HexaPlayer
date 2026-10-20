package com.oxoghost.hexaplayer.ui.terminal.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Terminal.Shape

/**
 * Bordered box with its [title] inset into the top border (`┌ resume ─────┐`).
 * The title sits 11 dp above the box, so leave room above it (sections use a 22 dp gap).
 */
@Composable
fun TSectionBox(
    title: String,
    modifier: Modifier = Modifier,
    titleColor: Color = TerminalTheme.colors.accent,
    borderColor: Color = TerminalTheme.colors.border,
    trailingTitle: String? = null,
    radius: Dp = Shape.sectionBox.dp,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = TerminalTheme.colors
    val bold = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold)
    Box(modifier.fillMaxWidth().semantics { paneTitle = title }) {
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(radius))
                .padding(contentPadding),
            content = content,
        )
        TText(
            title,
            Modifier
                .offset(x = 8.dp, y = (-11).dp)
                .background(c.background)
                .padding(horizontal = 4.dp),
            color = titleColor,
            style = bold,
        )
        if (trailingTitle != null) {
            TText(
                trailingTitle,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = (-11).dp)
                    .background(c.background)
                    .padding(horizontal = 4.dp),
                color = c.dim,
            )
        }
    }
}

/** The popup box itself (radius 8), for actions, sleep timer, add to playlist, update. */
@Composable
fun TPopupBox(
    title: String,
    modifier: Modifier = Modifier,
    borderColor: Color = TerminalTheme.colors.accent,
    trailingTitle: String? = "esc",
    contentPadding: PaddingValues = PaddingValues(top = 18.dp, bottom = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    TSectionBox(
        title = title,
        modifier = modifier.background(TerminalTheme.colors.background, RoundedCornerShape(Shape.popup.dp)),
        titleColor = borderColor,
        borderColor = borderColor,
        trailingTitle = trailingTitle,
        radius = Shape.popup.dp,
        contentPadding = contentPadding,
        content = content,
    )
}

/**
 * Popup over a 55 % black scrim, anchored at the bottom (or the top). Tapping the scrim or
 * going back dismisses it. Appears with an 80 ms fade.
 */
@Composable
fun TPopup(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchorTop: Boolean = false,
    borderColor: Color = TerminalTheme.colors.accent,
    trailingTitle: String? = "esc",
    contentPadding: PaddingValues = PaddingValues(top = 18.dp, bottom = 8.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(80)), exit = fadeOut(tween(80))) {
        BackHandler(onBack = onDismiss)
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Close",
                        onClick = onDismiss,
                    )
            )
            TPopupBox(
                title = title,
                modifier = Modifier
                    .align(if (anchorTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(start = 10.dp, end = 10.dp, top = if (anchorTop) 72.dp else 0.dp, bottom = 16.dp),
                borderColor = borderColor,
                trailingTitle = trailingTitle,
                contentPadding = contentPadding,
                content = content,
            )
        }
    }
}

/** 1 dp line in the border color. */
@Composable
fun TDivider(modifier: Modifier = Modifier, color: Color = TerminalTheme.colors.border) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** 1 dp dashed line, used between config rows and action headers. */
@Composable
fun TDashedDivider(modifier: Modifier = Modifier, color: Color = TerminalTheme.colors.border) {
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }
}
