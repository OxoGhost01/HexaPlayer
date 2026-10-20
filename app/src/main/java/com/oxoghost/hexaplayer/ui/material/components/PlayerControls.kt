package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

enum class MRepeat { Off, All, One }

/**
 * Shuffle and repeat (48 dp), previous / next (64 dp squircles), play / pause (112 x 80,
 * radius 28) that squishes into a rounder shape while pressed.
 */
@Composable
fun MPlayerControls(
    isPlaying: Boolean,
    shuffle: Boolean,
    repeat: MRepeat,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MIconButton(
            Symbols.Shuffle, if (shuffle) "Shuffle on" else "Shuffle off", onShuffle,
            tint = if (shuffle) s.primary else s.onSurfaceVariant, size = 48.dp,
        )
        MCircleButton(
            Symbols.SkipPrevious, "Previous", onPrevious, size = 64.dp, iconSize = 28.dp,
            shape = HexaShapes.skipButton, filled = true,
        )
        PlayButton(isPlaying, onPlayPause)
        MCircleButton(
            Symbols.SkipNext, "Next", onNext, size = 64.dp, iconSize = 28.dp,
            shape = HexaShapes.skipButton, filled = true,
        )
        MIconButton(
            if (repeat == MRepeat.One) Symbols.RepeatOne else Symbols.Repeat,
            when (repeat) { MRepeat.Off -> "Repeat off"; MRepeat.All -> "Repeat all"; MRepeat.One -> "Repeat one" },
            onRepeat,
            tint = if (repeat != MRepeat.Off) s.primary else s.onSurfaceVariant,
            size = 48.dp,
        )
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val spec = spring<androidx.compose.ui.unit.Dp>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    val radius by animateDpAsState(if (pressed) 40.dp else 28.dp, spec, label = "radius")
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "scale",
    )
    Box(
        Modifier
            .size(112.dp, 80.dp)
            .scale(scale)
            .clip(RoundedCornerShape(radius))
            .background(s.primaryContainer)
            .clickable(interaction, ripple(), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (isPlaying) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        Symbol(if (isPlaying) Symbols.Pause else Symbols.PlayArrow, null, size = 40.dp, filled = true, tint = s.onPrimaryContainer)
    }
}
