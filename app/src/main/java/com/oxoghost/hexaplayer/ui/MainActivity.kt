package com.oxoghost.hexaplayer.ui

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.repository.UpdateInfo
import com.oxoghost.hexaplayer.repository.UpdateRepository
import com.oxoghost.hexaplayer.ui.app.HexaApp
import com.oxoghost.hexaplayer.ui.legacy.LegacyDialogs
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.ui.update.UpdateFragment
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private val uiModel: UiModel by viewModels()
    private lateinit var dialogs: LegacyDialogs

    private val hasPermission = mutableStateOf(false)

    // ── Update / download state ───────────────────────────────────────────────
    private var pendingUpdateInfo: UpdateInfo? = null
    private var pendingDownloadId = -1L

    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == pendingDownloadId && id != -1L) {
                pendingDownloadId = -1L
                startInstall()
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) onPermissionGranted()
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notification is optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAccentTheme()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        dialogs = LegacyDialogs(this, viewModel)

        val settingsRepo = UiSettingsRepository(this)
        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(null)
            val granted by hasPermission
            settings?.let {
                HexaApp(it, viewModel, uiModel, dialogs, granted, ::requestAudioPermission)
            }
        }

        // RECEIVER_EXPORTED is required: DownloadManager is a system process and its broadcast
        // is blocked by RECEIVER_NOT_EXPORTED on API 33+. The payload is just the download ID,
        // which we verify against pendingDownloadId, so exporting is safe.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadCompleteReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        viewModel.connectToService()
        if (hasAudioPermission()) onPermissionGranted()
        checkForUpdatesInBackground()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(downloadCompleteReceiver) }
    }

    /** The screens still built with Views (download, settings, equalizer) follow the 1.x accent. */
    private fun applyAccentTheme() {
        val prefs = getSharedPreferences("hexa_prefs", MODE_PRIVATE)
        val themeRes = when (prefs.getInt("accent_index", 0)) {
            1 -> R.style.Theme_HexaPlayer_DeepPurple
            2 -> R.style.Theme_HexaPlayer_Blue
            3 -> R.style.Theme_HexaPlayer_Teal
            4 -> R.style.Theme_HexaPlayer_Pink
            5 -> R.style.Theme_HexaPlayer_Orange
            else -> R.style.Theme_HexaPlayer
        }
        setTheme(themeRes)
    }

    // ── Permissions ───────────────────────────────────────────────────────────
    private fun audioPermission() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun requestAudioPermission() {
        permissionLauncher.launch(arrayOf(audioPermission()))
    }

    private fun onPermissionGranted() {
        hasPermission.value = true
        viewModel.loadLibrary()
    }

    // ── Update screen ─────────────────────────────────────────────────────────

    /** Opens the full-screen update notification (called from background check or SettingsFragment). */
    fun showUpdateScreen(info: UpdateInfo) {
        pendingUpdateInfo = info
        if (supportFragmentManager.findFragmentByTag("update") != null) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_up, R.anim.slide_down, R.anim.slide_up, R.anim.slide_down)
            .add(android.R.id.content, UpdateFragment.newInstance(info), "update")
            .addToBackStack("update")
            .commit()
    }

    /** Called by UpdateFragment when the user taps the update button. */
    fun startUpdate(info: UpdateInfo) {
        pendingUpdateInfo = info
        if (info.downloadUrl.endsWith(".apk", ignoreCase = true)) {
            downloadAndInstall(info.downloadUrl, info.latestVersion)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)))
        }
    }

    // ── In-app APK download + install ─────────────────────────────────────────

    private fun downloadAndInstall(url: String, version: String) {
        if (!packageManager.canRequestPackageInstalls()) {
            toast(getString(R.string.update_install_permission), long = true)
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        // App-specific external dir: no storage permission needed and File.exists() works reliably
        val apkFile = java.io.File(getExternalFilesDir(null), "HexaPlayer-$version.apk")
        if (apkFile.exists()) apkFile.delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("HexaPlayer v$version")
            .setDescription(getString(R.string.update_downloading, version))
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apkFile))
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        pendingDownloadId = dm.enqueue(request)
    }

    private fun startInstall() {
        val version = pendingUpdateInfo?.latestVersion ?: return
        val file = java.io.File(getExternalFilesDir(null), "HexaPlayer-$version.apk")
        if (!file.exists()) return
        val contentUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(contentUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Checks GitHub releases on every app open; shows the update screen if a newer version is found. */
    private fun checkForUpdatesInBackground() {
        lifecycleScope.launch(Dispatchers.IO) {
            val info = UpdateRepository().checkForUpdate() ?: return@launch
            if (!UpdateRepository.isNewerVersion(BuildConfig.VERSION_NAME, info.latestVersion)) return@launch
            withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) showUpdateScreen(info)
            }
        }
    }
}
