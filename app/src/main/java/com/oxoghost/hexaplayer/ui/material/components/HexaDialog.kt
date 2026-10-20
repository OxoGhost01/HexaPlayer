package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

/** M3 dialog: surfaceContainerHigh, radius 28, centered icon in secondary, text buttons. */
@Composable
fun MDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    dismissText: String? = null,
    confirmEnabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
) {
    val s = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        icon = icon?.let { { Symbol(it, null, tint = s.secondary) } },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = text,
        confirmButton = { TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmText, style = MaterialTheme.typography.labelLarge) } },
        dismissButton = dismissText?.let { { TextButton(onClick = onDismiss) { Text(it, style = MaterialTheme.typography.labelLarge) } } },
        shape = HexaShapes.dialog,
        containerColor = s.surfaceContainerHigh,
        iconContentColor = s.secondary,
    )
}
