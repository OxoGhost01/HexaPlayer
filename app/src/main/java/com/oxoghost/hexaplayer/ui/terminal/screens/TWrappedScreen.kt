package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.repository.PeriodKind
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.capturing
import com.oxoghost.hexaplayer.ui.common.plural
import com.oxoghost.hexaplayer.ui.common.shareImage
import com.oxoghost.hexaplayer.ui.state.WrappedActions
import com.oxoghost.hexaplayer.ui.state.WrappedState
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockChart
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCharBarRow
import com.oxoghost.hexaplayer.ui.terminal.components.TChoiceRow
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TStepper
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import kotlinx.coroutines.launch

private val periodFlag = mapOf(PeriodKind.Week to "--week", PeriodKind.Month to "--month", PeriodKind.Year to "--year")

@Composable
fun TWrappedScreen(state: WrappedState, actions: WrappedActions, pixelCovers: Boolean, onClose: () -> Unit) {
    val c = TerminalTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    val layer = rememberGraphicsLayer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = page == 1) { page = 0 }
    TScreen(
        header = {
            THeader(
                "wrapped", onBack = if (page == 1) ({ page = 0 }) else onClose,
                action = "${page + 1}/2", actionDescription = if (page == 0) "Next page" else "First page",
                onAction = { page = 1 - page },
            )
        },
        status = { TStatusLine(TMode.NOR, state.period.label, position = state.stats?.let { plural(it.plays, "play") }) },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TChoiceRow(PeriodKind.entries.map { it.name.lowercase() }, state.period.kind.ordinal, { actions.setKind(PeriodKind.entries[it]) }, height = 40)
                TStepper(
                    state.period.label,
                    { if (state.hasEarlier) actions.shift(-1) },
                    { if (!state.period.isCurrent) actions.shift(1) },
                    previousDescription = "Earlier",
                    nextDescription = "Later",
                )
            }
            Column(Modifier.background(c.background).capturing(layer).padding(horizontal = 12.dp, vertical = 8.dp)) {
                when {
                    state.loading -> TText("reading play history...", color = c.dim)
                    state.stats == null || state.stats.empty -> Empty(state)
                    page == 0 -> Summary(state)
                    else -> Tops(state, pixelCovers)
                }
            }
        }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TButton("export png", {
                scope.launch { shareImage(context, layer.toImageBitmap(), "hexaplayer-wrapped", "Share your Wrapped") }
            }, Modifier.weight(1f), large = true, enabled = state.stats?.empty == false)
            if (page == 0) TButton("tops →", { page = 1 }, Modifier.weight(1f), filled = true, large = true)
            else TButton("save top 50", actions::saveTop50, Modifier.weight(1f), filled = true, large = true, enabled = state.stats?.empty == false)
        }
    }
}

@Composable
private fun Empty(state: WrappedState) {
    val c = TerminalTheme.colors
    TText(buildAnnotatedString {
        withStyle(SpanStyle(color = c.accent)) { append("$ ") }
        append("hexa wrapped ${periodFlag[state.period.kind]}")
    })
    TText("no plays in ${state.title} yet.", Modifier.padding(top = 12.dp), color = c.dim)
    TText("a song counts after 30 s or half its length.", Modifier.padding(top = 4.dp), color = c.dim, maxLines = 2)
}

@Composable
private fun Summary(state: WrappedState) {
    val c = TerminalTheme.colors
    val stats = state.stats ?: return
    TText(buildAnnotatedString {
        withStyle(SpanStyle(color = c.accent)) { append("$ ") }
        append("hexa wrapped ${periodFlag[state.period.kind]} ${state.period.label}")
    })
    Row(Modifier.padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        HexLogo(Modifier.size(104.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            TText("you@hexaplayer", color = c.accent)
            TText("─".repeat(14), color = c.dim)
            val top = state.songs.firstOrNull()
            listOf(
                state.period.kind.name.lowercase() to state.period.label,
                "played" to if (state.hours > 0 || state.minutes > 0) "${state.hours} h ${state.minutes} m" else "${stats.listenedMs / 1000} s",
                "plays" to "%,d".format(stats.plays),
                "songs" to "${stats.songs} unique",
                "artists" to "${stats.artists}",
                "top song" to (top?.title ?: "-"),
                "top art." to (stats.topArtists.firstOrNull()?.name ?: "-"),
                "peak" to state.peak,
                "streak" to plural(stats.longestStreak, "day"),
            ).forEach { (k, v) ->
                Row {
                    TText(k, Modifier.width(80.dp), color = c.accent2)
                    TText(v)
                }
            }
        }
    }
    Row(Modifier.padding(bottom = 16.dp)) {
        listOf(c.background, c.error, c.ok, c.warn, c.accent, c.accent2, c.dim, c.foreground).forEach {
            Box(Modifier.size(28.dp, 20.dp).background(it))
        }
    }
    val max = stats.buckets.maxOrNull()?.takeIf { it > 0 } ?: 1L
    val unit = when (state.period.kind) {
        PeriodKind.Year -> "hours per month"
        PeriodKind.Month -> "hours per day"
        PeriodKind.Week -> "hours per day"
    }
    TSectionBox(unit) {
        val dense = stats.buckets.size > 12
        TBlockChart(stats.buckets.map { it.toFloat() / max }, rows = 6, color = c.accent, gap = !dense)
        TText(
            state.period.bucketLabels.joinToString(if (dense) "" else " ") { if (dense) it.last().toString() else it.lowercase() },
            color = c.dim,
        )
    }
}

@Composable
private fun Tops(state: WrappedState, pixelCovers: Boolean) {
    val c = TerminalTheme.colors
    val stats = state.stats ?: return
    TText(buildAnnotatedString {
        withStyle(SpanStyle(color = c.accent)) { append("$ ") }
        append("hexa wrapped --top 5")
    })
    TSectionBox("top songs", Modifier.padding(top = 16.dp), borderColor = c.accent) {
        val max = state.songs.maxOfOrNull { it.plays } ?: 1
        state.songs.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TText("${s.rank}", Modifier.width(22.dp), color = c.accent)
                Column(Modifier.weight(1f)) {
                    TCharBarRow(s.title, s.plays, max, labelWidth = 150.dp, color = c.accent)
                    TText(s.artist, color = c.dim, style = TerminalTheme.type.small)
                }
            }
        }
    }
    TSectionBox("top artists", Modifier.padding(top = 16.dp), titleColor = c.accent2) {
        val max = stats.topArtists.maxOfOrNull { it.ms } ?: 1L
        stats.topArtists.forEachIndexed { i, a ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TText("${i + 1}", Modifier.width(22.dp), color = c.dim)
                TCharBarRow(a.name, (a.ms / 3_600_000).toInt().coerceAtLeast(if (a.ms > 0) 1 else 0), (max / 3_600_000).toInt().coerceAtLeast(1),
                    Modifier.weight(1f), labelWidth = 150.dp)
                TText("h", color = c.dim)
            }
        }
    }
    if (state.albums.isNotEmpty()) {
        TSectionBox("top albums", Modifier.padding(top = 16.dp), titleColor = c.warn) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.albums.forEach { a ->
                    PixelCover(a.cover?.artModel, a.cover?.albumKey ?: a.albumId.toString(), 12, Modifier.size(72.dp), pixelCovers, a.title)
                }
            }
        }
    }
}

/** The hexagon logo of the fastfetch-style block, in pixels. */
private val hexRows = listOf(
    "....####....",
    "..##....##..",
    ".#..####..#.",
    "#..#....#..#",
    "#..#.##.#..#",
    "#..#.##.#..#",
    "#..#....#..#",
    ".#..####..#.",
    "..##....##..",
    "....####....",
)

@Composable
private fun HexLogo(modifier: Modifier) {
    val color = TerminalTheme.colors.accent
    Canvas(modifier.semantics { contentDescription = "HexaPlayer logo" }) {
        val px = size.width / hexRows.first().length
        hexRows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') drawRect(color, Offset(x * px, y * px), Size(px + 0.5f, px + 0.5f))
            }
        }
    }
}
