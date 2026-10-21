package com.oxoghost.hexaplayer.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.oxoghost.hexaplayer.data.Song
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/** The editable tags of a song, as text (empty = not set). */
data class SongTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val track: String = "",
    val disc: String = "",
    val genre: String = "",
) {
    operator fun get(field: TagField): String = when (field) {
        TagField.Title -> title
        TagField.Artist -> artist
        TagField.Album -> album
        TagField.AlbumArtist -> albumArtist
        TagField.Year -> year
        TagField.Track -> track
        TagField.Disc -> disc
        TagField.Genre -> genre
    }

    fun with(field: TagField, value: String): SongTags = when (field) {
        TagField.Title -> copy(title = value)
        TagField.Artist -> copy(artist = value)
        TagField.Album -> copy(album = value)
        TagField.AlbumArtist -> copy(albumArtist = value)
        TagField.Year -> copy(year = value)
        TagField.Track -> copy(track = value)
        TagField.Disc -> copy(disc = value)
        TagField.Genre -> copy(genre = value)
    }
}

/** Tag fields with their TagLib property names. [batch] fields can be edited on many songs at once. */
enum class TagField(val property: String, val batch: Boolean) {
    Title("TITLE", false),
    Artist("ARTIST", true),
    Album("ALBUM", true),
    AlbumArtist("ALBUMARTIST", true),
    Year("DATE", true),
    Track("TRACKNUMBER", false),
    Disc("DISCNUMBER", false),
    Genre("GENRE", true),
}

sealed interface CoverChange {
    data object Keep : CoverChange
    data class Set(val uri: Uri) : CoverChange
    data object Remove : CoverChange
}

/** What the song actions header and File details show. */
data class AudioInfo(
    val format: String,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitsPerSample: Int = 0,
    val channels: Int = 0,
    val coverWidth: Int = 0,
    val coverHeight: Int = 0,
)

/** Reads and writes tags inside the audio files with TagLib, through MediaStore file descriptors. */
class TagRepository(private val context: Context) {

    /** The tags MediaStore already knows, without opening the file. */
    fun fromLibrary(song: Song) = SongTags(
        title = song.title,
        artist = song.artist.known(),
        album = song.album.known(),
        albumArtist = song.albumArtist.known(),
        year = song.year.takeIf { it > 0 }?.toString().orEmpty(),
        track = song.track.takeIf { it > 0 }?.toString().orEmpty(),
        disc = song.disc.takeIf { it > 0 }?.toString().orEmpty(),
        genre = song.genre,
    )

    fun read(song: Song): SongTags {
        val fallback = fromLibrary(song)
        val map = open(song.uri, "r") { TagLib.getMetadata(it, readPictures = false)?.propertyMap } ?: return fallback
        return TagField.entries.fold(SongTags()) { tags, field ->
            val value = map[field.property]?.firstOrNull()?.trim().orEmpty()
            tags.with(field, value.ifEmpty { fallback[field] })
        }
    }

    fun audioInfo(song: Song): AudioInfo {
        val format = song.displayName.substringAfterLast('.', "").lowercase()
        val props = open(song.uri, "r") { TagLib.getAudioProperties(it) }
        val cover = open(song.uri, "r") { TagLib.getFrontCover(it) }?.let { pic ->
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeByteArray(pic.data, 0, pic.data.size, this)
            }
        }
        val bits = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                MediaMetadataRetriever().use { r ->
                    r.setDataSource(context, song.uri)
                    r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()
                }
            }.getOrNull() ?: 0
        } else 0
        return AudioInfo(
            format = format,
            bitrateKbps = props?.bitrate ?: 0,
            sampleRateHz = props?.sampleRate ?: 0,
            bitsPerSample = bits,
            channels = props?.channels ?: 0,
            coverWidth = cover?.outWidth?.coerceAtLeast(0) ?: 0,
            coverHeight = cover?.outHeight?.coerceAtLeast(0) ?: 0,
        )
    }

    /**
     * Writes [changes] (only the fields given) and the cover into the file. The caller must already
     * hold write access (MediaStore write request). Returns false when the file could not be written.
     */
    fun write(song: Song, changes: Map<TagField, String>, cover: CoverChange): Boolean {
        if (changes.isNotEmpty()) {
            val map = open(song.uri, "rw") { TagLib.getMetadata(it, readPictures = false)?.propertyMap } ?: return false
            changes.forEach { (field, raw) ->
                val value = raw.trim()
                if (value.isEmpty()) {
                    map.remove(field.property)
                } else {
                    map[field.property] = arrayOf(keepTotal(map[field.property]?.firstOrNull(), value, field))
                }
            }
            if (open(song.uri, "rw") { TagLib.savePropertyMap(it, map) } != true) return false
        }
        val pictures = when (cover) {
            CoverChange.Keep -> null
            CoverChange.Remove -> emptyArray()
            is CoverChange.Set -> arrayOf(loadCover(cover.uri) ?: return false)
        }
        if (pictures != null && open(song.uri, "rw") { TagLib.savePictures(it, pictures) } != true) return false
        return true
    }

    /** Asks MediaStore to read the files again so the library shows the new tags. */
    suspend fun rescan(songs: List<Song>) {
        val paths = songs.map { absolutePath(it) }.toTypedArray()
        if (paths.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            var remaining = paths.size
            MediaScannerConnection.scanFile(context, paths, null) { _, _ ->
                if (--remaining == 0 && cont.isActive) cont.resume(Unit)
            }
        }
    }

    /** MediaStore fills missing tags with "<unknown>", which is not a value to edit. */
    private fun String.known() = if (this == MediaStore.UNKNOWN_STRING) "" else this

    /** Track "3" over an existing "2/10" becomes "3/10". */
    private fun keepTotal(old: String?, new: String, field: TagField): String {
        if (field != TagField.Track && field != TagField.Disc) return new
        if ('/' in new) return new
        val total = old?.substringAfter('/', "")?.takeIf { it.isNotBlank() } ?: return new
        return "$new/$total"
    }

    private fun loadCover(uri: Uri): Picture? = runCatching {
        val bitmap = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return null
        val scale = MAX_COVER / maxOf(bitmap.width, bitmap.height).toFloat()
        val sized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val bytes = ByteArrayOutputStream().use { out ->
            sized.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        Picture(bytes, "", "Front Cover", "image/jpeg")
    }.getOrNull()

    /** TagLib closes the descriptor it gets, so each call receives its own duplicate. */
    private fun <T> open(uri: Uri, mode: String, block: (Int) -> T): T? = runCatching {
        context.contentResolver.openFileDescriptor(uri, mode)?.use { pfd: ParcelFileDescriptor ->
            block(pfd.dup().detachFd())
        }
    }.getOrNull()

    companion object {
        private const val MAX_COVER = 1000

        @Suppress("DEPRECATION")
        fun absolutePath(song: Song): String =
            "${Environment.getExternalStorageDirectory().absolutePath}/${song.relativePath}${song.displayName}"
    }
}
