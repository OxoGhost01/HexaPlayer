package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine

/** Frame for the screens still built with Views until phase 4 (download, settings, equalizer). */
@Composable
fun THostedScreen(
    path: String,
    mode: TMode,
    context: String,
    onBack: (() -> Unit)?,
    onSettings: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    TScreen(
        header = {
            THeader(path, onBack = onBack, action = onSettings?.let { "[:set]" }, actionDescription = "Settings", onAction = onSettings)
        },
        status = { TStatusLine(mode, context) },
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}
