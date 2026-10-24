package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.rememberAlbumSeed
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.material.components.MConnectedButtonGroup
import com.oxoghost.hexaplayer.ui.material.components.MFloatingNav
import com.oxoghost.hexaplayer.ui.material.components.MGroupOption
import com.oxoghost.hexaplayer.ui.material.components.MMiniPlayer
import com.oxoghost.hexaplayer.ui.material.components.MNavItem
import com.oxoghost.hexaplayer.ui.material.components.MPaletteSwatch
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSubTopBar
import com.oxoghost.hexaplayer.ui.material.components.SwatchShapes
import com.oxoghost.hexaplayer.ui.material.theme.MaterialSchemes
import com.oxoghost.hexaplayer.ui.settings.MaterialSource
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.state.PlaybackState
import com.oxoghost.hexaplayer.ui.state.SettingsActions
import com.oxoghost.hexaplayer.ui.state.SettingsState

/** Interface switch, live preview, color source, palettes, pure black and player tint. */
@Composable
fun MAppearanceScreen(
    state: SettingsState,
    actions: SettingsActions,
    playback: PlaybackState,
    onBack: () -> Unit,
) {
    val s = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(s.surface).statusBarsPadding()) {
        MSubTopBar("Appearance", onBack, large = false)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MSectionLabel("Interface")
            MConnectedButtonGroup(
                listOf(
                    MGroupOption("Terminal", Symbols.Terminal),
                    MGroupOption("Material", Symbols.Palette),
                ),
                state.ui.skin.ordinal,
                { actions.setSkin(Skin.entries[it]) },
            )
            Preview(playback)
            ColorSource(state, actions)
            Palettes(state, actions, rememberAlbumSeed(playback.song))
            MSegmentedGroup(Modifier.padding(bottom = 16.dp)) {
                SwitchSegment(0, 2, "Pure black", "Saves battery on AMOLED screens", state.ui.pureBlack, actions::setPureBlack)
                SwitchSegment(1, 2, "Tint player with cover", "Now playing takes the album colors", state.ui.tintPlayerWithCover, actions::setTintPlayer)
            }
        }
    }
}

/** The floating chrome drawn with the current colors, as a live preview. */
@Composable
private fun Preview(playback: PlaybackState) {
    val s = MaterialTheme.colorScheme
    val song = playback.song
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.surfaceContainerLow).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MMiniPlayer(
            title = song?.title ?: "Nothing playing",
            artist = song?.artist ?: "Pick a song to see its colors",
            progress = playback.fraction,
            isPlaying = playback.isPlaying,
            onOpen = {},
            onPlayPause = {},
            onNext = {},
        ) { CoverImage(song?.artModel, song?.albumKey, Modifier.fillMaxSize()) }
        MFloatingNav(previewNav, 0, {})
    }
}

private val previewNav = listOf(
    MNavItem("Home", Symbols.Home),
    MNavItem("Library", Symbols.LibraryMusic),
    MNavItem("Search", Symbols.Search),
    MNavItem("Download", Symbols.Download),
)

@Composable
private fun ColorSource(state: SettingsState, actions: SettingsActions) {
    val sources = buildList {
        if (MaterialSchemes.isWallpaperAvailable()) {
            add(MaterialSource.Wallpaper to MGroupOption("Wallpaper", Symbols.Wallpaper))
        }
        add(MaterialSource.AlbumArt to MGroupOption("Album art", Symbols.Album))
        add(MaterialSource.Preset to MGroupOption("Preset", Symbols.Palette))
    }
    MSectionLabel("Color source", Modifier.padding(top = 4.dp))
    MConnectedButtonGroup(
        sources.map { it.second },
        sources.indexOfFirst { it.first == state.ui.materialSource },
        { actions.setMaterialSource(sources[it].first) },
    )
}

@Composable
private fun Palettes(state: SettingsState, actions: SettingsActions, albumSeed: Color?) {
    val s = MaterialTheme.colorScheme
    val presets = MaterialSchemes.presetNames
    val source = state.ui.materialSource
    MSectionLabel("Palette", Modifier.padding(top = 4.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(s.surfaceContainer).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        presets.chunked(4).forEachIndexed { row, names ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                names.forEachIndexed { i, name ->
                    // The "Album art" swatch is the album-art source, drawn from the current cover.
                    val albumArt = name == MaterialSchemes.ALBUM_ART
                    MPaletteSwatch(
                        scheme = remember(name, albumArt && albumSeed != null, albumSeed) {
                            if (albumArt && albumSeed != null) MaterialSchemes.fromSeed(albumSeed) else MaterialSchemes.preset(name)
                        },
                        label = name,
                        shape = SwatchShapes.forIndex(row * 4 + i),
                        selected = if (albumArt) source == MaterialSource.AlbumArt
                        else source == MaterialSource.Preset && name == state.ui.materialPreset,
                        onClick = { if (albumArt) actions.setMaterialSource(MaterialSource.AlbumArt) else actions.setMaterialPreset(name) },
                    )
                }
            }
        }
    }
    if (source == MaterialSource.AlbumArt) {
        Text("Album art follows the cover of the song playing.", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
