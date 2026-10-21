package com.oxoghost.hexaplayer.ui.legacy

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.databinding.DialogCreatePlaylistBinding
import com.oxoghost.hexaplayer.databinding.DialogEditSongBinding
import com.oxoghost.hexaplayer.databinding.DialogSleepTimerBinding
import com.oxoghost.hexaplayer.ui.common.albumArtUri
import com.oxoghost.hexaplayer.ui.common.artModel
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

/**
 * The 1.x dialogs (song menu, playlists, edit song info, sleep timer), kept working from the
 * Compose screens until phase 3 replaces them. Must be created in the activity's onCreate.
 */
class LegacyDialogs(private val activity: AppCompatActivity, private val vm: MusicViewModel) {

    private var pendingSongUri: Uri? = null
    private var pendingValues: ContentValues? = null
    private var pendingCover: ((Uri) -> Unit)? = null

    private val writeRequest = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val uri = pendingSongUri
        val values = pendingValues
        pendingSongUri = null
        pendingValues = null
        if (result.resultCode == Activity.RESULT_OK && uri != null && values != null) {
            try {
                activity.contentResolver.update(uri, values, null, null)
                vm.loadLibrary()
                activity.toast(activity.getString(R.string.song_info_updated))
            } catch (_: Exception) {
                activity.toast(activity.getString(R.string.metadata_permission_needed))
            }
        }
    }

    private val imagePicker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            pendingCover?.invoke(uri)
        }
        pendingCover = null
    }

    private fun str(id: Int) = activity.getString(id)

    fun songMenu(song: Song) {
        val items = arrayOf(str(R.string.add_to_playlist), str(R.string.play_next), str(R.string.play_last), str(R.string.edit_song_info))
        MaterialAlertDialogBuilder(activity)
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> addToPlaylist(listOf(song))
                    1 -> {
                        vm.addToQueueNext(song)
                        activity.toast(str(R.string.plays_next))
                    }
                    2 -> {
                        vm.addToQueueLast(song)
                        activity.toast(str(R.string.added_to_queue))
                    }
                    3 -> editSongInfo(song)
                }
            }
            .show()
    }

    /** Adds one song, or a whole album, to a new or existing playlist. */
    fun addToPlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        val ids = songs.map { it.id }
        val playlists = vm.playlists.value ?: emptyList()
        val names = (listOf(str(R.string.new_playlist)) + playlists.map { it.name }).toTypedArray()
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.add_to_playlist))
            .setItems(names) { _, which ->
                if (which == 0) {
                    createPlaylist { vm.addSongsToPlaylist(it.id, ids) }
                } else {
                    val playlist = playlists[which - 1]
                    vm.addSongsToPlaylist(playlist.id, ids)
                    activity.toast(
                        if (ids.size == 1) str(R.string.song_added)
                        else activity.getString(R.string.songs_added_to, ids.size, playlist.name)
                    )
                }
            }
            .show()
    }

    fun createPlaylist(onCreated: ((Playlist) -> Unit)? = null) {
        val view = DialogCreatePlaylistBinding.inflate(activity.layoutInflater)
        var cover: Uri? = null
        view.btnPickImage.setOnClickListener {
            pickImage { uri ->
                cover = uri
                showCover(view, uri)
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.create_playlist))
            .setView(view.root)
            .setPositiveButton(str(R.string.ok)) { _, _ ->
                val name = view.etName.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val playlist = vm.createPlaylist(name, cover?.toString())
                    onCreated?.invoke(playlist)
                    activity.toast(str(R.string.playlist_created))
                }
            }
            .setNegativeButton(str(R.string.cancel), null)
            .show()
    }

    fun playlistMenu(playlist: Playlist) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(playlist.name)
            .setItems(arrayOf(str(R.string.add_songs), str(R.string.edit_playlist), str(R.string.delete_playlist))) { _, which ->
                when (which) {
                    0 -> addSongsTo(playlist)
                    1 -> editPlaylist(playlist)
                    2 -> {
                        vm.deletePlaylist(playlist.id)
                        activity.toast(str(R.string.playlist_deleted))
                    }
                }
            }
            .show()
    }

    private fun editPlaylist(playlist: Playlist) {
        val view = DialogCreatePlaylistBinding.inflate(activity.layoutInflater)
        var cover: Uri? = null
        view.etName.setText(playlist.name)
        playlist.coverUri?.let { showCover(view, Uri.parse(it)) }
        view.btnPickImage.setOnClickListener {
            pickImage { uri ->
                cover = uri
                showCover(view, uri)
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.edit_playlist))
            .setView(view.root)
            .setPositiveButton(str(R.string.ok)) { _, _ ->
                val name = view.etName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                vm.editPlaylist(playlist.id, name, cover?.toString())
                activity.toast(str(R.string.playlist_updated))
            }
            .setNegativeButton(str(R.string.cancel), null)
            .show()
    }

    fun addSongsTo(playlist: Playlist) {
        val current = playlist.songIds.toSet()
        val available = (vm.songs.value ?: emptyList()).filter { it.id !in current }
        if (available.isEmpty()) {
            activity.toast(str(R.string.no_songs_to_add))
            return
        }
        val names = available.map { "${it.title}  ·  ${it.artist}" }.toTypedArray()
        val checked = BooleanArray(available.size)
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.add_songs))
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(str(R.string.ok)) { _, _ ->
                val selected = available.filterIndexed { i, _ -> checked[i] }
                if (selected.isNotEmpty()) {
                    vm.addSongsToPlaylist(playlist.id, selected.map { it.id })
                    activity.toast(str(R.string.songs_added))
                }
            }
            .setNegativeButton(str(R.string.cancel), null)
            .show()
    }

    fun editSongInfo(song: Song) {
        val view = DialogEditSongBinding.inflate(activity.layoutInflater)
        view.etTitle.setText(song.title)
        view.etArtist.setText(song.artist)
        view.etAlbum.setText(song.album)
        view.ivSongCover.load(song.artModel) {
            placeholder(R.drawable.ic_music_note)
            error(R.drawable.ic_music_note)
        }
        if (song.customCoverUri != null) view.btnClearCover.visibility = View.VISIBLE
        var cover: Uri? = null
        view.btnPickCover.setOnClickListener {
            pickImage { uri ->
                cover = uri
                view.ivSongCover.load(uri) {
                    placeholder(R.drawable.ic_music_note)
                    error(R.drawable.ic_music_note)
                }
                view.btnClearCover.visibility = View.VISIBLE
            }
        }
        view.btnClearCover.setOnClickListener {
            cover = null
            vm.clearCustomCover(song.id)
            view.ivSongCover.load(albumArtUri(song.albumId)) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            view.btnClearCover.visibility = View.GONE
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.edit_song_dialog_title))
            .setView(view.root)
            .setPositiveButton(str(R.string.ok)) { _, _ ->
                val title = view.etTitle.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.title
                val artist = view.etArtist.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.artist
                val album = view.etAlbum.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.album
                cover?.let { vm.setCustomCover(song.id, it.toString()) }
                updateMetadata(song, title, artist, album)
            }
            .setNegativeButton(str(R.string.cancel), null)
            .show()
    }

    private fun updateMetadata(song: Song, title: String, artist: String, album: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
        }
        try {
            activity.contentResolver.update(song.uri, values, null, null)
            vm.loadLibrary()
            activity.toast(str(R.string.song_info_updated))
        } catch (e: SecurityException) {
            val sender = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createWriteRequest(activity.contentResolver, listOf(song.uri)).intentSender
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    (e as? android.app.RecoverableSecurityException)?.userAction?.actionIntent?.intentSender
                else -> null
            }
            if (sender != null) {
                pendingValues = values
                pendingSongUri = song.uri
                writeRequest.launch(IntentSenderRequest.Builder(sender).build())
            } else {
                activity.toast(str(R.string.metadata_permission_needed))
            }
        }
    }

    fun sleepTimer() {
        val active = vm.sleepTimerActive.value ?: false
        val options = mutableListOf("15 min", "30 min", "45 min", "60 min", str(R.string.sleep_timer_custom))
        if (active) options.add(0, str(R.string.sleep_timer_cancel))
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.sleep_timer))
            .setItems(options.toTypedArray()) { _, which ->
                if (active && which == 0) {
                    vm.cancelSleepTimer()
                } else {
                    val idx = if (active) which - 1 else which
                    if (idx == 4) customSleepTimer() else vm.setSleepTimer(listOf(15, 30, 45, 60)[idx] * 60L)
                }
            }
            .show()
    }

    private fun customSleepTimer() {
        val view = DialogSleepTimerBinding.inflate(activity.layoutInflater)
        view.npHours.minValue = 0
        view.npHours.maxValue = 10
        view.npMinutes.minValue = 0
        view.npMinutes.maxValue = 59
        view.npSeconds.minValue = 0
        view.npSeconds.maxValue = 59
        view.npMinutes.value = 30
        MaterialAlertDialogBuilder(activity)
            .setTitle(str(R.string.sleep_timer))
            .setView(view.root)
            .setPositiveButton(str(R.string.ok)) { _, _ ->
                val total = view.npHours.value * 3600L + view.npMinutes.value * 60L + view.npSeconds.value
                if (total > 0) vm.setSleepTimer(total.coerceIn(10L, 36000L))
            }
            .setNegativeButton(str(R.string.cancel), null)
            .show()
    }

    private fun pickImage(onPicked: (Uri) -> Unit) {
        pendingCover = onPicked
        imagePicker.launch(arrayOf("image/*"))
    }

    private fun showCover(view: DialogCreatePlaylistBinding, uri: Uri) {
        view.ivPlaylistCover.load(uri) {
            crossfade(true)
            placeholder(R.drawable.ic_playlist)
            error(R.drawable.ic_playlist)
        }
        view.ivPlaylistCover.setPadding(0, 0, 0, 0)
        view.ivPlaylistCover.clearColorFilter()
        view.ivPlaylistCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        view.btnPickImage.text = str(R.string.change_cover)
    }
}
