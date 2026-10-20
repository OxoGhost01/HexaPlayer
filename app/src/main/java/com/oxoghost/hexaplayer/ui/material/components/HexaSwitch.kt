package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols

/** M3 switch: on = primary track with a check in the thumb; off = Highest track, outline border. */
@Composable
fun MSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val s = MaterialTheme.colorScheme
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = if (checked) {
            { Symbol(Symbols.Check, null, size = 16.dp, tint = s.primary) }
        } else null,
        colors = SwitchDefaults.colors(
            checkedThumbColor = s.onPrimary,
            checkedTrackColor = s.primary,
            uncheckedThumbColor = s.outline,
            uncheckedTrackColor = s.surfaceContainerHighest,
            uncheckedBorderColor = s.outline,
        ),
    )
}
