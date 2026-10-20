package com.oxoghost.hexaplayer.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * A cover with the generated placeholder underneath: when there is no art (or it fails to load)
 * the placeholder simply stays visible.
 */
@Composable
fun CoverImage(
    model: Any?,
    albumKey: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    contentDescription: String? = null,
) {
    Box(modifier.clip(shape)) {
        PlaceholderCover(albumKey, Modifier.matchParentSize())
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
