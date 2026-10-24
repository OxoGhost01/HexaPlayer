package com.oxoghost.hexaplayer.ui.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.repository.UpdateInfo
import com.oxoghost.hexaplayer.repository.UpdateRepository
import com.oxoghost.hexaplayer.ui.state.UpdateActions
import com.oxoghost.hexaplayer.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class UpdatePhase { Idle, Downloading, Ready, Failed }

enum class UpdateCheck { Checking, UpToDate, Available, Error }

/**
 * In-app updater (1.x behaviour): checks GitHub releases, downloads the APK with DownloadManager
 * on request, then hands it to the package installer. Must be created in the activity's onCreate.
 */
@Stable
class Updater(private val activity: ComponentActivity) : UpdateActions {

    private val prefs = activity.getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)
    private val dm = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId = -1L

    var info by mutableStateOf<UpdateInfo?>(null)
        private set
    var dialogVisible by mutableStateOf(false)
        private set
    var phase by mutableStateOf(UpdatePhase.Idle)
        private set
    var downloadedBytes by mutableLongStateOf(0L)
        private set
    var totalBytes by mutableLongStateOf(0L)
        private set
    var lastCheck by mutableStateOf<UpdateCheck?>(null)
        private set

    /** Check GitHub each time the app opens (1.x always did). */
    var auto by mutableStateOf(prefs.getBoolean("update_auto", true))
        private set

    fun setAutoCheck(on: Boolean) {
        auto = on
        prefs.edit().putBoolean("update_auto", on).apply()
    }

    // RECEIVER_EXPORTED is required: DownloadManager is a system process and its broadcast
    // is blocked by RECEIVER_NOT_EXPORTED on API 33+. The payload is only the download id,
    // which is checked against ours.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId && id != -1L) refreshProgress()
        }
    }

    fun register() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            activity.registerReceiver(receiver, filter)
        }
    }

    fun unregister() {
        runCatching { activity.unregisterReceiver(receiver) }
    }

    fun checkOnLaunch() {
        if (auto) check(showDialog = true)
    }

    fun check(showDialog: Boolean = true) {
        lastCheck = UpdateCheck.Checking
        activity.lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) { UpdateRepository().checkForUpdate() }
            when {
                found == null -> lastCheck = UpdateCheck.Error
                UpdateRepository.isNewerVersion(BuildConfig.VERSION_NAME, found.latestVersion) -> {
                    info = found
                    lastCheck = UpdateCheck.Available
                    if (showDialog) dialogVisible = true
                }
                else -> lastCheck = UpdateCheck.UpToDate
            }
        }
    }

    fun showDialog() {
        if (info != null) dialogVisible = true
    }

    override fun dismiss() {
        dialogVisible = false
    }

    /** Starts the APK download, or opens the release page when the release has no APK. */
    override fun download() {
        val update = info ?: return
        if (!update.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
            return
        }
        if (!activity.packageManager.canRequestPackageInstalls()) {
            activity.toast(activity.getString(R.string.update_install_permission), long = true)
            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            return
        }
        val file = apkFile(update)
        if (file.exists()) file.delete()
        val request = DownloadManager.Request(Uri.parse(update.downloadUrl))
            .setTitle("HexaPlayer v${update.latestVersion}")
            .setDescription(activity.getString(R.string.update_downloading, update.latestVersion))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(file))
        downloadId = dm.enqueue(request)
        phase = UpdatePhase.Downloading
        downloadedBytes = 0L
        totalBytes = update.sizeBytes
        activity.lifecycleScope.launch {
            while (phase == UpdatePhase.Downloading) {
                refreshProgress()
                delay(500)
            }
        }
    }

    override fun install() {
        val update = info ?: return
        val file = apkFile(update)
        if (!file.exists()) {
            phase = UpdatePhase.Failed
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun openRelease() {
        val version = info?.latestVersion ?: return
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/OxoGhost01/HexaPlayer/releases/tag/v$version")))
        }
    }

    private fun refreshProgress() {
        if (downloadId == -1L) return
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
            if (!c.moveToFirst()) {
                phase = UpdatePhase.Failed
                return
            }
            downloadedBytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 }?.let { totalBytes = it }
            when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> phase = UpdatePhase.Ready
                DownloadManager.STATUS_FAILED -> phase = UpdatePhase.Failed
            }
        }
    }

    // App-specific external dir: no storage permission needed.
    private fun apkFile(update: UpdateInfo) = File(activity.getExternalFilesDir(null), "HexaPlayer-${update.latestVersion}.apk")
}
