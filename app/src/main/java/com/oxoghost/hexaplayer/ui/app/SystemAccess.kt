package com.oxoghost.hexaplayer.ui.app

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.oxoghost.hexaplayer.util.toast

/**
 * Everything that needs the activity: permissions, the image and folder pickers and the
 * MediaStore write confirmation. Must be created in the activity's onCreate.
 */
class SystemAccess(private val activity: ComponentActivity, private val onAudioGranted: () -> Unit) {

    var hasAudio by mutableStateOf(granted(audioPermission))
        private set
    var hasNotifications by mutableStateOf(!asksNotifications || granted(Manifest.permission.POST_NOTIFICATIONS))
        private set

    private var pendingImage: ((Uri) -> Unit)? = null
    private var pendingFolder: ((String) -> Unit)? = null
    private var pendingWrite: ((Boolean) -> Unit)? = null

    private val audioLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        if (hasAudio) onAudioGranted()
    }

    private val notificationLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasNotifications = it
    }

    private val imageLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            pendingImage?.invoke(uri)
        }
        pendingImage = null
    }

    private val folderLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val callback = pendingFolder
        pendingFolder = null
        if (uri == null || callback == null) return@registerForActivityResult
        val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (id == null || !id.startsWith("primary:")) {
            activity.toast("Pick a folder on the internal storage")
        } else {
            callback(id.substringAfter(':').trim('/'))
        }
    }

    private val writeLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        finishWrite(it.resultCode == Activity.RESULT_OK)
    }

    private val legacyWriteLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        finishWrite(it)
    }

    val asksNotifications: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun refresh() {
        hasAudio = granted(audioPermission)
        hasNotifications = !asksNotifications || granted(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun requestAudio() = audioLauncher.launch(arrayOf(audioPermission))

    fun requestNotifications() {
        if (asksNotifications) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun pickImage(onPicked: (Uri) -> Unit) {
        pendingImage = onPicked
        imageLauncher.launch(arrayOf("image/*"))
    }

    /** Lets the user choose a folder; the callback gets its path relative to the storage root. */
    fun pickFolder(onPicked: (String) -> Unit) {
        pendingFolder = onPicked
        folderLauncher.launch(null)
    }

    /** Asks Android for write access to [uris] (one confirmation for all on Android 11+). */
    fun requestWrite(uris: List<Uri>, onResult: (Boolean) -> Unit) {
        if (uris.isEmpty()) return onResult(false)
        pendingWrite = onResult
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                val sender = MediaStore.createWriteRequest(activity.contentResolver, uris).intentSender
                writeLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> try {
                activity.contentResolver.openFileDescriptor(uris.first(), "rw")?.close()
                finishWrite(true)
            } catch (e: RecoverableSecurityException) {
                writeLauncher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
            } catch (_: Exception) {
                legacyWrite()
            }
            else -> legacyWrite()
        }
    }

    fun quit() = activity.finish()

    private fun legacyWrite() {
        if (granted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) finishWrite(true)
        else legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    private fun finishWrite(ok: Boolean) {
        val callback = pendingWrite
        pendingWrite = null
        callback?.invoke(ok)
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        val audioPermission: String
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
