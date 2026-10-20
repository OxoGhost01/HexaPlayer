package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes

/** Home Wrapped card: tertiaryContainer, 104 dp, flat decorative shapes in tertiary. */
@Composable
fun MWrappedCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(104.dp)
            .clip(HexaShapes.card)
            .background(s.tertiaryContainer)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 20.dp, y = (-26).dp)
                .size(120.dp)
                .alpha(0.35f)
                .background(s.tertiary, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-70).dp, y = 30.dp)
                .size(90.dp, 56.dp)
                .alpha(0.25f)
                .background(s.tertiary, RoundedCornerShape(28.dp))
        )
        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = 20.dp)) {
            Text(title, color = s.onTertiaryContainer, style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize * (20f / 22f)))
            Text(subtitle, color = s.onTertiaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
