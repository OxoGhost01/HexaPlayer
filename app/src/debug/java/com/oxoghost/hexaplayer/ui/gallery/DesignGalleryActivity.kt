package com.oxoghost.hexaplayer.ui.gallery

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxoghost.hexaplayer.ui.app.HexaTheme
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettings
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.tokens.DesignTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Album used by the gallery: real library albums when readable, otherwise placeholders. */
data class GalleryAlbum(val key: String, val title: String, val artist: String, val art: Uri?)

/** Debug-only design gallery: every component of both skins, every palette, live switching. */
class DesignGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val repo = UiSettingsRepository(this)
        setContent {
            val settings by repo.settings.collectAsStateWithLifecycle(UiSettings())
            val scope = rememberCoroutineScope()
            val albums by produceState(sampleAlbums()) { value = loadAlbums(this@DesignGalleryActivity) ?: value }
            var albumSeedIndex by remember { mutableStateOf(0) }
            val albumSeed = DesignTokens.Cover.arts[albumSeedIndex % DesignTokens.Cover.arts.size].background
            val actions = GalleryActions(
                setSkin = { scope.launch { repo.setSkin(it) } },
                setTerminalPalette = { scope.launch { repo.setTerminalPalette(it) } },
                setMaterialSource = { scope.launch { repo.setMaterialSource(it) } },
                setMaterialPreset = { scope.launch { repo.setMaterialPreset(it) } },
                setPureBlack = { scope.launch { repo.setPureBlack(it) } },
                setPixelCovers = { scope.launch { repo.setPixelCovers(it) } },
                setTintPlayer = { scope.launch { repo.setTintPlayerWithCover(it) } },
                nextAlbumSeed = { albumSeedIndex++ },
            )
            HexaTheme(settings, albumSeed) {
                when (settings.skin) {
                    Skin.Terminal -> TerminalGallery(settings, albums, actions)
                    Skin.Material -> MaterialGallery(settings, albums, albumSeed, actions)
                }
            }
        }
    }

    private fun sampleAlbums() = listOf(
        GalleryAlbum("a", "First album", "Some artist", null),
        GalleryAlbum("b", "Second album", "Another artist", null),
        GalleryAlbum("c", "Third album", "Some artist", null),
        GalleryAlbum("d", "Fourth album", "A band", null),
        GalleryAlbum("e", "Fifth album", "A band", null),
        GalleryAlbum("f", "Sixth album", "Duo", null),
        GalleryAlbum("g", "Seventh album", "Solo", null),
        GalleryAlbum("h", "Eighth album", "Solo", null),
    )

    private suspend fun loadAlbums(context: Context): List<GalleryAlbum>? = withContext(Dispatchers.IO) {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) return@withContext null
        val out = LinkedHashMap<Long, GalleryAlbum>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST),
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext() && out.size < 8) {
                val id = c.getLong(0)
                if (id in out) continue
                val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), id)
                out[id] = GalleryAlbum(id.toString(), c.getString(1) ?: "?", c.getString(2) ?: "?", art)
            }
        }
        out.values.toList().takeIf { it.isNotEmpty() }
    }
}

class GalleryActions(
    val setSkin: (Skin) -> Unit,
    val setTerminalPalette: (String) -> Unit,
    val setMaterialSource: (com.oxoghost.hexaplayer.ui.settings.MaterialSource) -> Unit,
    val setMaterialPreset: (String) -> Unit,
    val setPureBlack: (Boolean) -> Unit,
    val setPixelCovers: (Boolean) -> Unit,
    val setTintPlayer: (Boolean) -> Unit,
    val nextAlbumSeed: () -> Unit,
)
