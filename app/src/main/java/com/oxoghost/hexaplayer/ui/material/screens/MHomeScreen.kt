package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.common.CoverImage
import com.oxoghost.hexaplayer.ui.common.SwipeToQueue
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.common.albumKey
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.ui.common.formatDuration
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MHeroCarousel
import com.oxoghost.hexaplayer.ui.material.components.MListRow
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MTopRow
import com.oxoghost.hexaplayer.ui.material.theme.HexaShapes
import com.oxoghost.hexaplayer.ui.state.HexaActions
import com.oxoghost.hexaplayer.ui.state.HomeState
import com.oxoghost.hexaplayer.ui.state.PlaybackState

@Composable
fun MHomeScreen(home: HomeState, playback: PlaybackState, actions: HexaActions, onSeeAllRecent: () -> Unit) {
    val s = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = LocalChromePadding.current),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { MTopRow(onSearch = actions::openSearch, onIdentify = null, onSettings = actions::openSettings) }
        if (home.recentAlbums.isNotEmpty()) {
            item { SectionTitle("Recently added", onSeeAll = onSeeAllRecent) }
            item {
                MHeroCarousel(home.recentAlbums, { it.title }, actions::openAlbum) {
                    CoverImage(it.artModel, it.albumKey, Modifier.fillMaxSize())
                }
            }
        }
        if (home.recentSongs.isNotEmpty()) {
            item { SectionTitle("New songs", onSeeAll = null) }
            itemsIndexed(home.recentSongs, key = { _, song -> song.id }) { i, song ->
                SwipeToQueue(onSwipe = { actions.addToQueue(song) }, color = s.primary, modifier = Modifier.padding(horizontal = 8.dp)) {
                    MListRow(
                        title = song.title,
                        subtitle = "${song.artist} · ${formatDuration(song.duration)}",
                        playing = playback.song?.id == song.id,
                        onClick = { actions.play(home.recentSongs, i, "recently added") },
                        onLongClick = { actions.songMenu(song) },
                        onMore = { actions.songMenu(song) },
                    ) { CoverImage(song.artModel, song.albumKey, Modifier.fillMaxSize(), HexaShapes.coverRow) }
                }
            }
        }
        item {
            Column(
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clip(HexaShapes.card)
                    .background(s.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Your library", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${home.songCount} songs · ${home.albumCount} albums · ${home.artistCount} artists",
                    color = s.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MPillButton("Shuffle all", { actions.shuffle(home.allSongs, "library") }, icon = Symbols.Shuffle, size = MButtonSize.M)
                    MPillButton("Play all", { actions.play(home.allSongs, 0, "library") }, style = MButtonStyle.Tonal, size = MButtonSize.M)
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        if (onSeeAll != null) MPillButton("See all", onSeeAll, style = MButtonStyle.Text, size = MButtonSize.S)
    }
}
