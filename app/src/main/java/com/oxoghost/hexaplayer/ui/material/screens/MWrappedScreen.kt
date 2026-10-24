package com.oxoghost.hexaplayer.ui.material.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.capturing
import com.oxoghost.hexaplayer.ui.common.formatLengthLong
import com.oxoghost.hexaplayer.ui.common.plural
import com.oxoghost.hexaplayer.ui.common.shareImage
import com.oxoghost.hexaplayer.ui.material.components.CookieShape
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MFilterChip
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.state.WrappedActions
import com.oxoghost.hexaplayer.ui.state.WrappedState
import kotlinx.coroutines.launch

private val PeriodKind.noun: String get() = name.lowercase()

/** Two story pages: the totals on primaryContainer, the tops on tertiaryContainer. */
@Composable
fun MWrappedScreen(state: WrappedState, actions: WrappedActions, onClose: () -> Unit) {
    val s = MaterialTheme.colorScheme
    var page by rememberSaveable { mutableIntStateOf(0) }
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = page == 1) { page = 0 }
    val (bg, fg) = if (page == 0) s.primaryContainer to s.onPrimaryContainer else s.tertiaryContainer to s.onTertiaryContainer
    val accent = if (page == 0) s.primary else s.tertiary
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(if (i <= page) fg else fg.copy(alpha = 0.3f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page == 1) MIconButton(Symbols.ArrowBack, "Previous page", { page = 0 }, tint = fg)
            Text("HexaPlayer Wrapped", Modifier.weight(1f).padding(start = 12.dp), color = fg, style = MaterialTheme.typography.titleMedium)
            MIconButton(Symbols.Close, "Close Wrapped", onClose, tint = fg)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PeriodKind.entries.forEach { kind -> MFilterChip(kind.name, kind == state.period.kind, { actions.setKind(kind) }) }
            Box(Modifier.weight(1f))
            MIconButton(Symbols.ArrowBack, "Earlier", { if (state.hasEarlier) actions.shift(-1) }, tint = if (state.hasEarlier) fg else fg.copy(alpha = 0.3f))
            MIconButton(Symbols.ArrowForward, "Later", { if (!state.period.isCurrent) actions.shift(1) },
                tint = if (state.period.isCurrent) fg.copy(alpha = 0.3f) else fg)
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(bg).capturing(layer)) {
            Decorations(accent)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                when {
                    state.loading -> {}
                    state.stats == null || state.stats.empty -> Text(
                        "No plays in ${state.title} yet. A song counts after 30 seconds or half its length.",
                        Modifier.padding(top = 32.dp), color = fg, style = MaterialTheme.typography.headlineSmall,
                    )
                    page == 0 -> Summary(state, fg)
                    else -> Tops(state, fg)
                }
            }
        }
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val ready = state.stats?.empty == false
            MPillButton("Share", {
                scope.launch { shareImage(context, layer.toImageBitmap(), "hexaplayer-wrapped", "Share your Wrapped") }
            }, Modifier.weight(1f), style = MButtonStyle.Tonal, icon = Symbols.IosShare, enabled = ready)
            if (page == 0) MPillButton("Your top songs", { page = 1 }, Modifier.weight(1f), icon = Symbols.ArrowForward)
            else MPillButton("Save top 50", actions::saveTop50, Modifier.weight(1f), icon = Symbols.PlaylistAdd, enabled = ready)
        }
    }
}

@Composable
private fun BoxScope.Decorations(color: Color) {
    Box(
        Modifier.align(Alignment.TopEnd).offset(80.dp, 40.dp).size(260.dp).alpha(0.22f).clip(CookieShape(9, 0.1f)).background(color)
    )
    Box(
        Modifier.align(Alignment.BottomStart).offset((-40).dp, (-40).dp).size(200.dp, 90.dp).alpha(0.25f)
            .clip(RoundedCornerShape(45.dp)).background(color)
    )
}

@Composable
private fun Summary(state: WrappedState, fg: Color) {
    val stats = state.stats ?: return
    val s = MaterialTheme.colorScheme
    val inHours = state.hours >= 1
    val minutes = stats.listenedMs / 60_000
    val (big, unit) = when {
        inHours -> "${state.hours}" to "hours of music"
        minutes >= 1 -> "$minutes" to "minutes of music"
        else -> "${stats.listenedMs / 1000}" to "seconds of music"
    }
    Text(
        if (state.period.isCurrent) "This ${state.period.kind.noun} you listened to" else "In ${state.period.label} you listened to",
        Modifier.padding(top = 24.dp), color = fg, style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        big,
        color = fg,
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 112.sp, fontWeight = FontWeight.Black, lineHeight = 112.sp),
    )
    Text(unit, color = fg,
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black))
    if (state.days >= 1) {
        Text("That is ${state.days} days, all from your own files.", Modifier.padding(top = 8.dp), color = fg, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(24.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("%,d".format(stats.plays), if (stats.plays == 1) "song played" else "songs played", Modifier.weight(1f))
        StatCard("${stats.longestStreak}", "day streak", Modifier.weight(1f))
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard("${stats.artists}", if (stats.artists == 1) "artist" else "artists", Modifier.weight(1f))
        StatCard(state.peak, "peak hour", Modifier.weight(1f))
    }
    Text("Listening per ${if (state.period.kind == PeriodKind.Year) "month" else "day"}", Modifier.padding(top = 24.dp, bottom = 8.dp),
        color = fg, style = MaterialTheme.typography.titleSmall)
    val max = stats.buckets.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        stats.buckets.forEach { v ->
            Box(
                Modifier.weight(1f).fillMaxHeight((v.toFloat() / max).coerceIn(0.03f, 1f)).clip(RoundedCornerShape(4.dp))
                    .background(if (v > 0) s.surface else fg.copy(alpha = 0.15f))
            )
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier) {
    val s = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(28.dp)).background(s.surface).padding(20.dp)) {
        Text(value, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1)
        Text(label, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Tops(state: WrappedState, fg: Color) {
    val stats = state.stats ?: return
    val s = MaterialTheme.colorScheme
    val first = state.songs.firstOrNull() ?: return
    Text("Your song of the ${state.period.kind.noun}", Modifier.padding(top = 16.dp), color = fg, style = MaterialTheme.typography.headlineSmall)
    Row(Modifier.padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CoverImage(first.song?.artModel, first.song?.albumKey ?: first.title, Modifier.size(140.dp), RoundedCornerShape(36.dp))
        Column {
            Text(first.title, color = fg, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(first.artist, color = fg, style = MaterialTheme.typography.titleMedium)
            Text("${plural(first.plays, "play")} · ${formatLengthLong(first.ms)}", Modifier.padding(top = 8.dp), color = fg,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        }
    }
    val rest = state.songs.drop(1)
    if (rest.isNotEmpty()) {
        MSegmentedGroup {
            rest.forEachIndexed { i, song ->
                MSegment(i, rest.size, container = s.surface) {
                    CoverImage(song.song?.artModel, song.song?.albumKey ?: song.title, Modifier.size(48.dp), RoundedCornerShape(14.dp))
                    Text("${song.rank}", color = s.tertiary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                    Text("${song.plays}", color = s.onSurfaceVariant)
                }
            }
        }
    }
    stats.topArtists.firstOrNull()?.let { artist ->
        Row(
            Modifier.padding(vertical = 16.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.surface).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val cover = state.songs.firstOrNull { it.artist == artist.name }?.song
            CoverImage(cover?.artModel, cover?.albumKey ?: artist.name, Modifier.size(64.dp), CircleShape)
            Column {
                Text("Artist of the ${state.period.kind.noun}", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Text("${artist.name} · ${formatLengthLong(artist.ms)}", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}
