package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

/** 32 x 4 dp drag handle in outline. */
@Composable
fun MDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 10.dp, bottom = 16.dp)
            .size(32.dp, 4.dp)
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
    )
}

/** Bottom sheet: surfaceContainerLow, top radius 28, 50 % black scrim, custom handle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = HexaShapes.sheetTop,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = { MDragHandle() },
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), content = content)
    }
}

/** Song actions tile: 64 dp, surfaceContainerHigh, radius 20, icon in primary + label. */
@Composable
fun MActionTile(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, centered: Boolean = false, height: Int = 64) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(s.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(12.dp),
    ) {
        Symbol(icon, null, tint = s.primary)
        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/** Two-column grid of [MActionTile]s plus an optional full-width tile. */
@Composable
fun MActionGrid(
    tiles: List<Triple<String, String, () -> Unit>>,
    modifier: Modifier = Modifier,
    wide: Triple<String, String, () -> Unit>? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (icon, label, action) -> MActionTile(icon, label, action, Modifier.weight(1f)) }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        if (wide != null) MActionTile(wide.first, wide.second, wide.third, Modifier.fillMaxWidth(), centered = true, height = 56)
    }
}
