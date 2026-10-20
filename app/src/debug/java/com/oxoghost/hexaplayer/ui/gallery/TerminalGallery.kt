package com.oxoghost.hexaplayer.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.PixelCover
import com.oxoghost.hexaplayer.ui.common.PlaceholderCover
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.terminal.components.TActionKeyBar
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockBar
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockChart
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCharBarRow
import com.oxoghost.hexaplayer.ui.terminal.components.TCharProgress
import com.oxoghost.hexaplayer.ui.terminal.components.TChoiceRow
import com.oxoghost.hexaplayer.ui.terminal.components.TCodeBlock
import com.oxoghost.hexaplayer.ui.terminal.components.TConfigHeader
import com.oxoghost.hexaplayer.ui.terminal.components.TConfigRow
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TKey
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyAction
import com.oxoghost.hexaplayer.ui.terminal.components.TKeyBar
import com.oxoghost.hexaplayer.ui.terminal.components.TListRow
import com.oxoghost.hexaplayer.ui.terminal.components.TLog
import com.oxoghost.hexaplayer.ui.terminal.components.TLogLine
import com.oxoghost.hexaplayer.ui.terminal.components.TMiniLine
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TParentColumn
import com.oxoghost.hexaplayer.ui.terminal.components.TPopup
import com.oxoghost.hexaplayer.ui.terminal.components.TPreviewPane
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TSeekBar
import com.oxoghost.hexaplayer.ui.terminal.components.TSpark
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TStepper
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.components.TToggle
import com.oxoghost.hexaplayer.ui.terminal.components.TValueKind
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalPalettes
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

@Composable
fun TerminalGallery(settings: UiSettings, albums: List<GalleryAlbum>, actions: GalleryActions) {
    val c = TerminalTheme.colors
    var popup by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableFloatStateOf(0.55f) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            THeader(
                path = "~/dev/",
                current = "gallery",
                action = "[material]",
                actionDescription = "Switch to the material skin",
                onAction = { actions.setSkin(Skin.Material) },
            )
            LazyColumn(Modifier.weight(1f)) {
                flavors(settings, actions)
                rows(albums, settings)
                boxes(onPopup = { popup = true })
                controls()
                progress(position) { position = it }
                charts()
                logs()
                config(settings, actions)
                covers(albums, settings)
                modes()
            }
            TStatusLine(TMode.THEME, settings.terminalPalette.lowercase(), position = "tap to apply")
            TMiniLine(
                title = albums.first().title,
                artist = albums.first().artist,
                positionMs = (position * 242_000).toLong(),
                durationMs = 242_000,
                isPlaying = playing,
                onOpen = {},
                onPlayPause = { playing = !playing },
                onNext = {},
            )
            TKeyBar(
                listOf(TKey("1", "home"), TKey("2", "lib"), TKey("3", "find"), TKey("4", "dl")),
                selected = tab,
                onSelect = { tab = it },
            )
        }
        TPopup(popup, title = albums.first().title, onDismiss = { popup = false }) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PixelCover(albums.first().art, albums.first().key, 8, Modifier.size(48.dp), settings.pixelCovers)
                Column {
                    TText(albums.first().artist)
                    TText("${albums.first().title} · 2024 · 3:41", color = c.dim, style = TerminalTheme.type.small)
                    TText("flac · 16 bit · 44.1 kHz", color = c.dim, style = TerminalTheme.type.small)
                }
            }
            TDashedDivider()
            listOf(
                Triple("n", "play next", ""), Triple("q", "add to queue", ""), Triple("p", "add to playlist", "›"),
                Triple("a", "go to album", "›"), Triple("r", "go to artist", "›"), Triple("e", "edit song info", "›"),
            ).forEachIndexed { i, (k, l, r) ->
                TKeyAction(k, l, { popup = false }, trailing = r.ifEmpty { null }, highlighted = i == 0)
            }
        }
    }
}

private fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item {
        Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 22.dp)) {
            TText("# $title", color = TerminalTheme.colors.dim, style = TerminalTheme.type.small)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

private fun LazyListScope.flavors(settings: UiSettings, actions: GalleryActions) = section("flavors") {
    val c = TerminalTheme.colors
    TChoiceRow(listOf("terminal", "material"), 0, { if (it == 1) actions.setSkin(Skin.Material) }, height = 44)
    Spacer(Modifier.height(12.dp))
    TerminalPalettes.names.forEach { name ->
        val p = TerminalPalettes.get(name, false)
        val on = name == settings.terminalPalette
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(if (on) c.selection else c.background)
                .clickable { actions.setTerminalPalette(name) }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TText(if (on) "●" else " ", color = c.accent)
            TText(name.lowercase(), Modifier.weight(1f), color = if (on) c.accent else c.foreground)
            Row(Modifier.border(1.dp, c.border)) {
                listOf(p.background, p.foreground, p.accent, p.accent2, p.ok, p.error).forEach {
                    Box(Modifier.size(16.dp, 22.dp).background(it))
                }
            }
        }
    }
    TDashedDivider()
    TToggle(settings.pureBlack, actions.setPureBlack, "pure black background", sublabel = "for amoled screens")
    TToggle(settings.pixelCovers, actions.setPixelCovers, "pixel covers", sublabel = "off: real covers, square")
}

private fun LazyListScope.rows(albums: List<GalleryAlbum>, settings: UiSettings) = section("rows · parent column · preview") {
    var cursor by remember { mutableIntStateOf(1) }
    var lib by remember { mutableIntStateOf(1) }
    Row(Modifier.height(48.dp * 5)) {
        TParentColumn(listOf("songs", "albums", "artists", "playlists", "folders"), lib, { lib = it })
        Column(Modifier.weight(1f)) {
            albums.take(5).forEachIndexed { i, a ->
                TListRow(
                    title = a.title,
                    subtitle = if (lib == 0) a.artist else null,
                    trailing = if (lib == 0) "3:41" else "${9 + i}",
                    cursor = i == cursor,
                    playing = i == 0,
                    directory = lib != 0,
                    onClick = { cursor = i },
                )
            }
        }
    }
    val a = albums.getOrElse(cursor) { albums.first() }
    TPreviewPane(
        lines = listOf(
            AnnotatedString(a.artist),
            AnnotatedString("2024 · 9 songs · 38:12"),
            AnnotatedString("flac · 16 bit"),
        ),
        onOpen = {},
        cover = { PixelCover(a.art, a.key, 12, Modifier.size(96.dp), settings.pixelCovers) },
    )
    TText("VIS mode", color = TerminalTheme.colors.dim, style = TerminalTheme.type.small)
    TListRow("Selected song", subtitle = "Artist", trailing = "4:02", selected = true)
    TListRow("Not selected", subtitle = "Artist", trailing = "3:18", selected = false)
    TActionKeyBar(listOf(TKey("n", "next"), TKey("q", "queue"), TKey("p", "playlist"), TKey("e", "tags")), {})
}

private fun LazyListScope.boxes(onPopup: () -> Unit) = section("section boxes · buttons") {
    val c = TerminalTheme.colors
    TSectionBox("resume", borderColor = c.accent) {
        TText("last session · 14 oct 23:41", color = c.dim)
        TText(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) { append("Song title") }
            withStyle(SpanStyle(color = c.dim)) { append(" Artist") }
        })
        TCharProgress(0.5f, style = TerminalTheme.type.body, height = 20.dp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TButton("▶︎ resume", {}, filled = true)
            TButton("forget", {})
        }
    }
    Spacer(Modifier.height(22.dp))
    TSectionBox("wrapped 2026", titleColor = c.accent2) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TText("your year is ready")
                TText("412 h · 1,284 songs · 38 artists", color = c.dim)
            }
            TButton("open", {}, color = c.accent2)
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TButton("⟳ sync", {}, Modifier.weight(1f), filled = true, large = true)
        TButton("↓ download", {}, Modifier.weight(1f), color = c.accent, large = true)
    }
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TButton("cancel timer", {}, Modifier.weight(1f), color = c.error)
        TButton("set 30m", {}, Modifier.weight(1f), filled = true, color = c.warn)
    }
    Spacer(Modifier.height(6.dp))
    TButton("open popup (song actions)", onPopup, Modifier.fillMaxWidth())
}

private fun LazyListScope.controls() = section("toggle · choice · stepper · input") {
    var on by remember { mutableStateOf(true) }
    var choice by remember { mutableIntStateOf(1) }
    var preset by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("harb") }
    var link by remember { mutableStateOf("") }
    val presets = listOf("hip hop", "flat", "bass", "rock", "vocal")
    TToggle(on, { on = it }, "gapless playback")
    TChoiceRow(listOf("15m", "30m", "45m", "60m"), choice, { choice = it }, accent = TerminalTheme.colors.warn)
    Spacer(Modifier.height(8.dp))
    TStepper(presets[preset], { preset = (preset + presets.size - 1) % presets.size }, { preset = (preset + 1) % presets.size }, label = "preset")
    Spacer(Modifier.height(8.dp))
    TInput(query, { query = it }, prompt = "/", trailing = { TText("1/6", color = TerminalTheme.colors.dim) })
    Spacer(Modifier.height(8.dp))
    TInput(link, { link = it }, prompt = "$", placeholder = "spotify link")
}

private fun LazyListScope.progress(position: Float, onSeek: (Float) -> Unit) = section("progress") {
    TCharProgress(position)
    TSeekBar(position, onSeek)
    TBlockBar(0.65f)
    TBlockBar(0.4f, color = TerminalTheme.colors.warn)
}

private fun LazyListScope.charts() = section("char bars · block chart · spark") {
    TSectionBox("most played · october") {
        TCharBarRow("First song", 148, 148)
        TCharBarRow("Second song", 117, 148)
        TCharBarRow("Third song", 96, 148)
        TCharBarRow("Fourth song", 71, 148)
    }
    Spacer(Modifier.height(22.dp))
    TSectionBox("hours per month") {
        TBlockChart(listOf(0.3f, 0.5f, 0.45f, 0.7f, 0.9f, 0.6f, 0.4f, 0.55f, 0.8f, 1f, 0.35f, 0.2f), rows = 4)
    }
    Spacer(Modifier.height(12.dp))
    TSpark(listOf(0.1f, 0.4f, 0.7f, 1f, 0.8f, 0.5f, 0.3f, 0.6f, 0.9f, 0.4f, 0.2f, 0.5f, 0.7f))
}

private fun LazyListScope.logs() = section("log lines · code block") {
    TLogLine(TLog.Ok, "media3 engine")
    TLogLine(TLog.Waiting, "waiting for permissions")
    TLogLine(TLog.Done, "Song one")
    TLogLine(TLog.Progress(62), "Song two")
    TLogLine(TLog.Pending, "Song three")
    TLogLine(TLog.Failed(), "Song four")
    Spacer(Modifier.height(8.dp))
    TCodeBlock("apt update && apt full-upgrade -y && curl -fsSL https://raw.githubusercontent.com/OxoGhost01/HexaPlayer/main/termux/setup.sh | bash")
}

private fun LazyListScope.config(settings: UiSettings, actions: GalleryActions) = section("config rows") {
    TConfigHeader("appearance")
    TConfigRow("interface", "terminal", TValueKind.Text, { actions.setSkin(Skin.Material) }, control = "›")
    TConfigRow("pure_black", settings.pureBlack.toString(), TValueKind.Bool, { actions.setPureBlack(!settings.pureBlack) }, control = if (settings.pureBlack) "[x]" else "[ ]")
    TConfigHeader("playback")
    TConfigRow("crossfade_s", "0.0", TValueKind.Number, {}, control = "›")
}

private fun LazyListScope.covers(albums: List<GalleryAlbum>, settings: UiSettings) = section("pixel covers · placeholders · glyphs") {
    val a = albums.first()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        listOf(26 to 96, 16 to 72, 13 to 56, 12 to 48, 8 to 36, 6 to 28).forEach { (res, size) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelCover(a.art, a.key, res, Modifier.size(size.dp), settings.pixelCovers)
                TText("$res", color = TerminalTheme.colors.dim, style = TerminalTheme.type.tiny)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("a", "b", "c", "d", "e", "f", "g", "h").forEach { k ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PlaceholderCover(k, Modifier.size(36.dp))
                PixelCover(null, k, 8, Modifier.size(36.dp))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    TText("▶︎ |◀︎ ▶︎| ‖ ⇄ ↻ ≡ ⟳ ↓ ✓ ✗ ● ━ ─ █ ░ ▀ ▄ ├─ └─ → ← · ♪")
    TText("▁▂▃▄▅▆▇█ ⇄ shuffle ↻ repeat ⟳ sync ♪ note", color = TerminalTheme.colors.accent)
    Box(Modifier.width(1.dp))
}

private fun LazyListScope.modes() = section("status line modes") {
    TMode.entries.forEach { TStatusLine(it, "context", position = "1/1") ; Spacer(Modifier.height(2.dp)) }
}
