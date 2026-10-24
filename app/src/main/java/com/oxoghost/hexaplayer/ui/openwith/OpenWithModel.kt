package com.oxoghost.hexaplayer.ui.openwith

import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.oxoghost.hexaplayer.repository.QueueStore
import com.oxoghost.hexaplayer.repository.SavedItem
import com.oxoghost.hexaplayer.repository.SavedQueue
import com.oxoghost.hexaplayer.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the Open with sheet shows about the file. */
@Immutable
data class OpenWithFile(
    val name: String,
    val folder: String,
    val format: String,
    val sizeBytes: Long,
    val bitrateKbps: Int,
    val title: String?,
    val artist: String?,
    val inLibrary: Boolean,
)

@Immutable
data class OpenWithState(
    val file: OpenWithFile?,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val copying: Boolean,
    val copied: Boolean,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

interface OpenWithActions {
    fun togglePlay()
    fun seekBy(deltaMs: Long)
    fun seekTo(fraction: Float)
    fun copyToLibrary()
    fun addToQueue()
    fun openFullPlayer()
    fun close()
}

/**
 * Plays one external file through the music service in a temporary mode: the current queue is
 * kept aside and put back (and resumed if it was playing) when the sheet closes.
 */
@Stable
class OpenWithModel(
    private val context: Context,
    private val uri: Uri,
    private val scope: CoroutineScope,
    private val onFinish: (openApp: Boolean) -> Unit,
) : OpenWithActions {

    var state by mutableStateOf(OpenWithState(null, false, 0L, 0L, copying = false, copied = false))
        private set

    private var controller: MediaController? = null
    private var snapshot: SavedQueue? = null
    private var snapshotPlaying = false
    private var finished = false

    fun start() {
        scope.launch { state = state.copy(file = withContext(Dispatchers.IO) { readFile() }) }
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val ctrl = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = ctrl
            takeSnapshot(ctrl)
            ctrl.setMediaItem(externalItem())
            ctrl.prepare()
            ctrl.play()
            scope.launch {
                while (isActive) {
                    state = state.copy(isPlaying = ctrl.isPlaying, positionMs = ctrl.currentPosition, durationMs = ctrl.duration.coerceAtLeast(0L))
                    delay(250)
                }
            }
        }, MoreExecutors.directExecutor())
    }

    private fun takeSnapshot(ctrl: MediaController) {
        if (ctrl.mediaItemCount > 0) {
            val items = (0 until ctrl.mediaItemCount).mapNotNull { SavedItem.of(ctrl.getMediaItemAt(it)) }
            snapshot = SavedQueue(
                items = items,
                index = ctrl.currentMediaItemIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                positionMs = ctrl.currentPosition,
                shuffle = ctrl.sessionExtras.getBoolean(MusicService.EXTRA_SHUFFLE_ON, false),
                repeat = ctrl.repeatMode,
                source = null,
                savedAt = System.currentTimeMillis(),
            ).takeIf { items.isNotEmpty() }
            snapshotPlaying = ctrl.isPlaying
        } else {
            snapshot = QueueStore(context).load()
        }
    }

    private fun externalItem(): MediaItem {
        val file = state.file
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId("${QueueStore.EXTERNAL_PREFIX}${System.nanoTime()}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(file?.title ?: file?.name ?: uri.lastPathSegment)
                    .setArtist(file?.artist ?: "Opened with HexaPlayer")
                    .build()
            )
            .build()
    }

    /** Puts the kept queue back, resuming it if it was playing before. */
    private fun restore(ctrl: MediaController) {
        val saved = snapshot
        if (saved == null) {
            ctrl.stop()
            ctrl.clearMediaItems()
            return
        }
        ctrl.setMediaItems(saved.items.map { it.toMediaItem() }, saved.index, saved.positionMs)
        ctrl.repeatMode = saved.repeat
        ctrl.prepare()
        if (saved.shuffle) {
            ctrl.sendCustomCommand(
                SessionCommand(MusicService.CMD_SHUFFLE, Bundle.EMPTY),
                Bundle().apply { putBoolean(MusicService.EXTRA_FORCE_ON, true) },
            )
        }
        if (snapshotPlaying) ctrl.play()
    }

    override fun togglePlay() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    override fun seekBy(deltaMs: Long) {
        val ctrl = controller ?: return
        ctrl.seekTo((ctrl.currentPosition + deltaMs).coerceIn(0L, ctrl.duration.coerceAtLeast(0L)))
    }

    override fun seekTo(fraction: Float) {
        val ctrl = controller ?: return
        if (ctrl.duration > 0) ctrl.seekTo((ctrl.duration * fraction).toLong())
    }

    override fun copyToLibrary() {
        if (state.copying || state.copied) return
        state = state.copy(copying = true)
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { copy() }.getOrDefault(false) }
            state = state.copy(copying = false, copied = ok, file = state.file?.copy(inLibrary = state.file?.inLibrary == true || ok))
        }
    }

    /** Copies the file into the library folder through MediaStore (Android 10 and up). */
    private fun copy(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val name = state.file?.name ?: return false
        val folder = context.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)
            .getString("music_folder", "")?.trim('/')?.ifBlank { null } ?: "Music"
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "$folder/")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val target = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        val copied = resolver.openInputStream(uri)?.use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) }
        } != null
        if (!copied) {
            resolver.delete(target, null, null)
            return false
        }
        resolver.update(target, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return true
    }

    override fun addToQueue() {
        val ctrl = controller ?: return finish(false)
        restore(ctrl)
        val item = externalItem().buildUpon().setMediaId("uq:${System.nanoTime()}").build()
        ctrl.addMediaItem(item)
        finish(false, restored = true)
    }

    override fun openFullPlayer() {
        finished = true
        onFinish(true)
    }

    override fun close() = finish(false)

    /** Called when the activity goes away without a choice: put the queue back. */
    fun release() {
        if (!finished) controller?.let(::restore)
        finished = true
        controller?.release()
        controller = null
    }

    private fun finish(openApp: Boolean, restored: Boolean = false) {
        if (finished) return
        if (!restored) controller?.let(::restore)
        finished = true
        onFinish(openApp)
    }

    private fun readFile(): OpenWithFile {
        val resolver = context.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        var size = 0L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    size = c.getLong(1)
                }
            }
        }
        val retriever = MediaMetadataRetriever()
        val (title, artist, bitrate) = runCatching {
            retriever.setDataSource(context, uri)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() },
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0) / 1000,
            )
        }.getOrDefault(Triple(null, null, 0))
        runCatching { retriever.release() }
        return OpenWithFile(
            name = name,
            folder = folderOf(),
            format = name.substringAfterLast('.', "").lowercase(),
            sizeBytes = size,
            bitrateKbps = bitrate,
            title = title,
            artist = artist,
            inLibrary = isInLibrary(name, size),
        )
    }

    private fun folderOf(): String {
        if (uri.scheme == "file") return uri.path?.substringBeforeLast('/')?.let { "$it/" }.orEmpty()
        val path = uri.lastPathSegment.orEmpty()
        // Documents uris look like "primary:Download/demo.mp3".
        return if (':' in path) "/sdcard/" + path.substringAfter(':').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        else uri.authority.orEmpty()
    }

    /** In the library: a MediaStore audio file inside the library folder (any folder when none is set). */
    private fun isInLibrary(name: String, size: Long): Boolean {
        val folder = context.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)
            .getString("music_folder", "")?.trim('/').orEmpty()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media.RELATIVE_PATH),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?",
                arrayOf(name, size.toString()),
                null,
            )?.use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0).orEmpty() else null }
                    .any { path -> folder.isEmpty() || path.trim('/').startsWith(folder) }
            } ?: false
        }.getOrDefault(false)
    }
}
