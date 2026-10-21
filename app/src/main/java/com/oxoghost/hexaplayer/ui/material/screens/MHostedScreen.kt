package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar

/** Frame for the screens still built with Views until phase 4 (download, settings, equalizer). */
@Composable
fun MHostedScreen(title: String?, onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (title != null && onBack != null) MSubTopBar(title, onBack, large = false)
        Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = LocalChromePadding.current)) { content() }
    }
}
