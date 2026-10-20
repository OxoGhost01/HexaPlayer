package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Multi-browse carousel: the first visible item is large (176 dp), the next medium (112), the
 * rest small (56, round). Sizes animate as the row scrolls.
 */
@Composable
fun <T> MHeroCarousel(
    items: List<T>,
    title: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    cover: @Composable (T) -> Unit,
) {
    val state = rememberLazyListState()
    val first by remember { derivedStateOf { state.firstVisibleItemIndex } }
    LazyRow(
        modifier,
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        itemsIndexed(items) { i, item ->
            val target = when (i - first) {
                0 -> 176.dp
                1 -> 112.dp
                else -> 56.dp
            }
            val size by animateDpAsState(target, label = "carousel")
            Column(Modifier.width(size)) {
                Box(
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(minOf(28.dp, size / 2)))
                        .clickable(role = Role.Button, onClickLabel = title(item)) { onClick(item) }
                ) { cover(item) }
                if (i == first) {
                    Text(
                        title(item),
                        Modifier.padding(top = 8.dp).height(24.dp),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
