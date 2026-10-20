package com.oxoghost.hexaplayer.ui.material.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens.Material.Metrics

data class MNavItem(val label: String, val icon: String)

/**
 * Floating nav pill: the active item expands into a secondaryContainer pill with a filled icon
 * and its label; inactive items are icons only. Expands with an expressive spring.
 */
@Composable
fun MFloatingNav(items: List<MNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .height(Metrics.navBar.dp)
            .clip(HexaShapes.navBar)
            .background(s.surfaceContainer)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEachIndexed { i, item ->
            val on = i == selected
            val bg by animateColorAsState(if (on) s.secondaryContainer else Color.Transparent, label = "nav")
            val fg = if (on) s.onSecondaryContainer else s.onSurfaceVariant
            Row(
                Modifier
                    .height(Metrics.navItem.dp)
                    .clip(HexaShapes.pill)
                    .background(bg)
                    .selectable(on, role = Role.Tab) { onSelect(i) }
                    .semantics { contentDescription = item.label }
                    .padding(horizontal = if (on) 18.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Symbol(item.icon, null, filled = on, tint = fg)
                AnimatedVisibility(
                    on,
                    enter = expandHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)),
                    exit = shrinkHorizontally(spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)),
                ) {
                    Text(item.label, Modifier.padding(start = 8.dp), color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Floating bottom chrome: mini player over the nav pill, 12 dp from the sides and 14 dp above
 * the navigation bar inset. Content should scroll under it with enough bottom padding.
 */
@Composable
fun MBottomChrome(
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null,
    nav: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = Metrics.floatingBottomInset.dp),
        verticalArrangement = Arrangement.spacedBy(Metrics.floatingGap.dp),
    ) {
        miniPlayer?.invoke()
        nav()
    }
}
