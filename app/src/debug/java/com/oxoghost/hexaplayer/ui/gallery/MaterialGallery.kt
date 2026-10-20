package com.oxoghost.hexaplayer.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.CookieShape
import com.oxoghost.hexaplayer.ui.material.components.MActionGrid
import com.oxoghost.hexaplayer.ui.material.components.MBottomChrome
import com.oxoghost.hexaplayer.ui.material.components.MBottomSheet
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCheckBadge
import com.oxoghost.hexaplayer.ui.material.components.MCheckbox
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MConnectedButtonGroup
import com.oxoghost.hexaplayer.ui.material.components.MDialog
import com.oxoghost.hexaplayer.ui.material.components.MEffectRow
import com.oxoghost.hexaplayer.ui.material.components.MEqSlider
import com.oxoghost.hexaplayer.ui.material.components.MFilterChip
import com.oxoghost.hexaplayer.ui.material.components.MFloatingNav
import com.oxoghost.hexaplayer.ui.material.components.MGroupOption
import com.oxoghost.hexaplayer.ui.material.components.MHeroCarousel
import com.oxoghost.hexaplayer.ui.material.components.MListRow
import com.oxoghost.hexaplayer.ui.material.components.MMiniPlayer
import com.oxoghost.hexaplayer.ui.material.components.MNavItem
import com.oxoghost.hexaplayer.ui.material.components.MPaletteSwatch
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MPlayerControls
import com.oxoghost.hexaplayer.ui.material.components.MPlayerToolbar
import com.oxoghost.hexaplayer.ui.material.components.MRepeat
import com.oxoghost.hexaplayer.ui.material.components.MResumeCard
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSelectionCircle
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar
import com.oxoghost.hexaplayer.ui.material.components.MSwitch
import com.oxoghost.hexaplayer.ui.material.components.MTabChips
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.material.components.MTopRow
import com.oxoghost.hexaplayer.ui.material.components.MWavySeek
import com.oxoghost.hexaplayer.ui.material.components.MWrappedCard
import com.oxoghost.hexaplayer.ui.material.components.SwatchShapes
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings

@Composable
fun MaterialGallery(settings: UiSettings, albums: List<GalleryAlbum>, albumSeed: Color, actions: GalleryActions) {
    val s = MaterialTheme.colorScheme
    var nav by remember { mutableIntStateOf(1) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableFloatStateOf(0.55f) }
    var sheet by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    val first = albums.first()

    Box(Modifier.fillMaxSize().background(s.surface)) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 200.dp),
        ) {
            item {
                MSubTopBar("Gallery", onBack = { actions.setSkin(Skin.Terminal) }) {
                    MCircleButton(Symbols.Terminal, "Switch to the terminal skin", { actions.setSkin(Skin.Terminal) },
                        container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
                }
            }
            appearance(settings, albumSeed, actions)
            item { MTopRow({}, {}, {}) }
            item {
                var tab by remember { mutableIntStateOf(0) }
                MTabChips(listOf("Songs", "Albums", "Artists", "Playlists", "Folders"), tab, { tab = it })
            }
            listRows(albums) { sheet = true }
            groups()
            buttons(onDialog = { dialog = true }, onSheet = { sheet = true })
            cards(albums)
            player(playing, position, { playing = !playing }, { position = it })
            tools()
        }
        MBottomChrome(Modifier.align(Alignment.BottomCenter), miniPlayer = {
            MMiniPlayer(
                title = first.title,
                artist = first.artist,
                progress = position,
                isPlaying = playing,
                onOpen = {},
                onPlayPause = { playing = !playing },
                onNext = {},
            ) { CoverImage(first.art, first.key, Modifier.fillMaxSize()) }
        }) {
            MFloatingNav(
                listOf(
                    MNavItem("Home", Symbols.Home),
                    MNavItem("Library", Symbols.LibraryMusic),
                    MNavItem("Search", Symbols.Search),
                    MNavItem("Download", Symbols.Download),
                ),
                nav,
                { nav = it },
            )
        }
    }

    if (sheet) {
        MBottomSheet(onDismiss = { sheet = false }) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(first.art, first.key, Modifier.size(72.dp), RoundedShape20)
                Column {
                    Text(first.title, style = MaterialTheme.typography.titleLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize))
                    Text("${first.artist} · 3:41", color = s.onSurfaceVariant)
                    Text("FLAC · 16 bit · 44.1 kHz", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(20.dp))
            MActionGrid(
                tiles = listOf(
                    Triple(Symbols.PlaylistPlay, "Play next") { sheet = false },
                    Triple(Symbols.QueueMusic, "Add to queue") { sheet = false },
                    Triple(Symbols.PlaylistAdd, "Add to playlist") { sheet = false },
                    Triple(Symbols.Album, "Go to album") { sheet = false },
                    Triple(Symbols.Person, "Go to artist") { sheet = false },
                    Triple(Symbols.Edit, "Edit info") { sheet = false },
                ),
                wide = Triple(Symbols.Info, "File details") { sheet = false },
            )
        }
    }
    if (dialog) {
        MDialog(
            title = "Update available",
            onDismiss = { dialog = false },
            confirmText = "Install",
            onConfirm = { dialog = false },
            icon = Symbols.SystemUpdate,
            dismissText = "Not now",
        ) { Text("Version 2.0.1 is ready. 4.2 MB.") }
    }
}

private val RoundedShape20 = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)

private fun LazyListScope.section(title: String, content: @Composable () -> Unit) {
    item {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
            MSectionLabel(title)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun LazyListScope.appearance(settings: UiSettings, albumSeed: Color, actions: GalleryActions) = section("Appearance") {
    val sources = buildList {
        if (MaterialSchemes.isWallpaperAvailable()) add(MaterialSource.Wallpaper to MGroupOption("Wallpaper", Symbols.Wallpaper))
        add(MaterialSource.AlbumArt to MGroupOption("Album art", Symbols.Album))
        add(MaterialSource.Preset to MGroupOption("Preset", Symbols.Palette))
    }
    MConnectedButtonGroup(
        sources.map { it.second },
        sources.indexOfFirst { it.first == settings.materialSource },
        { actions.setMaterialSource(sources[it].first) },
    )
    if (settings.materialSource == MaterialSource.AlbumArt) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).clip(HexaShapes.pill).background(albumSeed))
            MPillButton("Next album color", actions.nextAlbumSeed, style = MButtonStyle.Tonal, size = MButtonSize.S)
        }
    }
    Spacer(Modifier.height(12.dp))
    val presets = MaterialSchemes.presetNames
    val enabled = settings.materialSource == MaterialSource.Preset
    Column(
        Modifier.fillMaxWidth().clip(HexaShapes.card).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        presets.chunked(4).forEachIndexed { row, names ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                names.forEachIndexed { i, name ->
                    MPaletteSwatch(
                        scheme = remember(name) { MaterialSchemes.preset(name) },
                        label = name,
                        shape = SwatchShapes.forIndex(row * 4 + i),
                        selected = enabled && name == settings.materialPreset,
                        onClick = { actions.setMaterialPreset(name) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    MSegmentedGroup {
        MSegment(0, 2) {
            Column(Modifier.weight(1f)) {
                Text("Pure black", style = MaterialTheme.typography.titleSmall)
                Text("Saves battery on AMOLED screens", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MSwitch(settings.pureBlack, actions.setPureBlack)
        }
        MSegment(1, 2) {
            Column(Modifier.weight(1f)) {
                Text("Tint player with cover", style = MaterialTheme.typography.titleSmall)
                Text("Now playing takes the album colors", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MSwitch(settings.tintPlayerWithCover, actions.setTintPlayer)
        }
    }
}

private fun LazyListScope.listRows(albums: List<GalleryAlbum>, onMore: () -> Unit) = section("List rows") {
    albums.take(4).forEachIndexed { i, a ->
        MListRow(
            title = a.title,
            subtitle = "${a.artist} · 3:41",
            playing = i == 1,
            onMore = onMore,
            onLongClick = onMore,
        ) { CoverImage(a.art, a.key, Modifier.fillMaxSize(), HexaShapes.coverRow) }
    }
}

private fun LazyListScope.groups() = section("Segmented group · chips · checks") {
    val s = MaterialTheme.colorScheme
    val checked = remember { mutableStateListOf(true, false, true) }
    MSegmentedGroup {
        listOf("Bass boost", "Virtualizer", "Loudness").forEachIndexed { i, label ->
            MSegment(i, 3, onClick = { checked[i] = !checked[i] }) {
                Box(Modifier.size(40.dp).clip(HexaShapes.pill).background(listOf(s.primaryContainer, s.secondaryContainer, s.tertiaryContainer)[i]), contentAlignment = Alignment.Center) {
                    Symbol(Symbols.Equalizer, null, tint = listOf(s.onPrimaryContainer, s.onSecondaryContainer, s.onTertiaryContainer)[i])
                }
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                MCheckbox(checked[i], { checked[i] = it })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    var filter by remember { mutableIntStateOf(0) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Songs", "Albums").forEachIndexed { i, l -> MFilterChip(l, i == filter, { filter = i }) }
    }
    Spacer(Modifier.height(12.dp))
    var circle by remember { mutableStateOf(true) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MSelectionCircle(circle, { circle = it })
        MSelectionCircle(!circle, { circle = !it })
        MCheckBadge()
        var text by remember { mutableStateOf("Some artist") }
        MTextField(text, { text = it }, "Artist", Modifier.weight(1f))
    }
}

private fun LazyListScope.buttons(onDialog: () -> Unit, onSheet: () -> Unit) = section("Buttons") {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MPillButton("Get started", {}, Modifier.weight(1f))
            MPillButton("Cancel", {}, Modifier.weight(1f), style = MButtonStyle.Tonal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MPillButton("Outlined", {}, Modifier.weight(1f), style = MButtonStyle.Outlined, size = MButtonSize.M)
            MPillButton("Text", {}, Modifier.weight(1f), style = MButtonStyle.Text, size = MButtonSize.M)
            MPillButton("Small", {}, style = MButtonStyle.Tonal, size = MButtonSize.S)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MPillButton("Open dialog", onDialog, Modifier.weight(1f), style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.SystemUpdate)
            MPillButton("Open sheet", onSheet, Modifier.weight(1f), style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.MoreVert)
        }
        var preset by remember { mutableIntStateOf(1) }
        MConnectedButtonGroup(listOf("15 min", "30 min", "45 min", "60 min").map { MGroupOption(it) }, preset, { preset = it })
    }
}

private fun LazyListScope.cards(albums: List<GalleryAlbum>) = section("Cards · carousel") {
    val a = albums.first()
    MResumeCard(a.title, "${a.artist} · song 3 of 12", 0.55f, {}, {}) {
        CoverImage(a.art, a.key, Modifier.fillMaxSize(), RoundedShape20)
    }
    Spacer(Modifier.height(12.dp))
    MWrappedCard("Your 2026 Wrapped", "412 hours of music. See your year.", {})
    Spacer(Modifier.height(12.dp))
    MHeroCarousel(albums, { it.title }, {}, contentPadding = PaddingValues(0.dp)) {
        CoverImage(it.art, it.key, Modifier.fillMaxSize())
    }
}

private fun LazyListScope.player(playing: Boolean, position: Float, onPlay: () -> Unit, onSeek: (Float) -> Unit) = section("Player") {
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(MRepeat.All) }
    var sleep by remember { mutableStateOf(true) }
    MWavySeek(position, onSeek, playing)
    Spacer(Modifier.height(12.dp))
    MPlayerControls(
        isPlaying = playing,
        shuffle = shuffle,
        repeat = repeat,
        onShuffle = { shuffle = !shuffle },
        onPrevious = {},
        onPlayPause = onPlay,
        onNext = {},
        onRepeat = { repeat = MRepeat.entries[(repeat.ordinal + 1) % MRepeat.entries.size] },
    )
    Spacer(Modifier.height(16.dp))
    MPlayerToolbar({}, { sleep = !sleep }, {}, {}, sleepLabel = if (sleep) "28m" else null)
}

private fun LazyListScope.tools() = section("Equalizer · cookie") {
    val s = MaterialTheme.colorScheme
    val bands = remember { mutableStateListOf(12f, 10f, 6f, 2f, -2f, -4f, 0f, 4f, 8f, 10f) }
    val freqs = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
    Row(
        Modifier.fillMaxWidth().clip(HexaShapes.card).background(s.surfaceContainer).padding(vertical = 16.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        bands.forEachIndexed { i, v -> MEqSlider(v, { bands[i] = it }, freqs[i]) }
    }
    Spacer(Modifier.height(12.dp))
    var bass by remember { mutableStateOf(true) }
    var bassValue by remember { mutableFloatStateOf(0.4f) }
    var virt by remember { mutableStateOf(false) }
    MSegmentedGroup {
        MSegment(0, 2) { MEffectRow("Bass boost", "${(bassValue * 100).toInt()}%", bass, { bass = it }, bassValue, { bassValue = it }) }
        MSegment(1, 2) { MEffectRow("Virtualizer", "0%", virt, { virt = it }, 0f, {}) }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(192.dp).clip(CookieShape(12)).background(s.primaryContainer))
        MCircleButton(Symbols.GraphicEq, "Identify", {}, container = s.primary, content = s.onPrimary, size = 120.dp, iconSize = 48.dp)
    }
}
