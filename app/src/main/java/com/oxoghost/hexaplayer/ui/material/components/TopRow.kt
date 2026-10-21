package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

/** Main tab top row: search pill, Identify circle, Settings circle. */
@Composable
fun MTopRow(
    onSearch: () -> Unit,
    onIdentify: (() -> Unit)?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = Metrics.screenPaddingH.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(Metrics.searchPill.dp)
                .clip(HexaShapes.pill)
                .background(s.surfaceContainerHigh)
                .clickable(role = Role.Button, onClick = onSearch)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Symbol(Symbols.Search, null, tint = s.onSurfaceVariant)
            Text("Search your library", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        }
        if (onIdentify != null) {
            MCircleButton(Symbols.GraphicEq, "Identify a song", onIdentify, container = s.primaryContainer, content = s.onPrimaryContainer)
        }
        MCircleButton(Symbols.Settings, "Settings", onSettings, container = s.tertiaryContainer, content = s.onTertiaryContainer, filled = true)
    }
}

/** Sub screen top bar: 48 dp back circle, title, optional trailing buttons. */
@Composable
fun MSubTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(Metrics.topBar.dp).padding(horizontal = Metrics.screenPaddingH.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MCircleButton(Symbols.ArrowBack, "Back", onBack, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
        Text(
            title,
            Modifier.weight(1f),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}
