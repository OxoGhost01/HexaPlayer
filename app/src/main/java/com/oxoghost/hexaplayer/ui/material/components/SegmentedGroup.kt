package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Shape as S

/** Connected group radii: first `24 24 6 6`, middle `6`, last `6 6 24 24`, single `24`. */
fun segmentShape(index: Int, count: Int): Shape {
    val outer = S.groupOuter.dp
    val inner = S.groupInner.dp
    val top = if (index == 0) outer else inner
    val bottom = if (index == count - 1) outer else inner
    return RoundedCornerShape(top, top, bottom, bottom)
}

/** Vertical group of connected segments with 2 dp gaps. */
@Composable
fun MSegmentedGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Metrics.groupGap.dp), content = content)
}

/** One segment of a [MSegmentedGroup]. */
@Composable
fun MSegment(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    minHeight: Int = Metrics.listRow.dp.value.toInt(),
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight.dp)
            .clip(segmentShape(index, count))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )
}

/** Section label: 14 sp 600 in primary, 4 dp inset. */
@Composable
fun MSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}
