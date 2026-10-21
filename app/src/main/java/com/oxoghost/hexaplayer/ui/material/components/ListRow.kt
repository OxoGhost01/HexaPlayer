package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

/**
 * 64 dp list row: cover, title (15 sp 600), subtitle, `more_vert`. The playing row gets the
 * surfaceContainer background, a primary title and the `graphic_eq` marker.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MListRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    selected: Boolean? = null,
    gestures: Modifier? = null,
    leading: @Composable () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.listRow.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected == true -> s.secondaryContainer
                    playing -> s.surfaceContainer
                    else -> Color.Transparent
                }
            )
            .then(gestures ?: Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick))
            .padding(start = 8.dp, end = if (selected != null) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp)) {
            leading()
            if (selected == true) MCheckBadge(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) s.primary else s.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                color = s.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected != null) {
            MSelectionCircle(selected, null)
        } else {
            if (playing) Symbol(Symbols.GraphicEq, "Playing", tint = s.primary)
            if (onMore != null) MIconButton(Symbols.MoreVert, "More actions for $title", onMore)
        }
    }
}
