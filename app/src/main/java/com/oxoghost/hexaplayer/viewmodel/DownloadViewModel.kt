package com.oxoghost.hexaplayer.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.data.JobPhase
import com.oxoghost.hexaplayer.data.SpotdlJob
import com.oxoghost.hexaplayer.data.SyncEntry
import com.oxoghost.hexaplayer.repository.DownloadRepository
import com.oxoghost.hexaplayer.repository.ServerHealth
import com.oxoghost.hexaplayer.util.Termux
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DownloadRepository()
    private val prefs = application.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)

    private val _jobs = MutableLiveData<List<SpotdlJob>>(emptyList())
    val jobs: LiveData<List<SpotdlJob>> = _jobs

    private val _syncs = MutableLiveData<List<SyncEntry>>(emptyList())
    val syncs: LiveData<List<SyncEntry>> = _syncs

    private val _connectionStatus = MutableLiveData<String?>(null)
    val connectionStatus: LiveData<String?> = _connectionStatus

    private val _connected = MutableLiveData<Boolean>(false)
    val connected: LiveData<Boolean> = _connected

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** Fired when new files have been indexed, so the library can rescan. */
    private val _libraryChanged = MutableLiveData<Boolean>(false)
    val libraryChanged: LiveData<Boolean> = _libraryChanged

    private val idCounter = AtomicLong(System.currentTimeMillis())

    /** Absolute path of the server's download root on this device (from /health). */
    private var serverRoot: String = ""

    private var connectJob: Job? = null

    // --- Settings ---------------------------------------------------------- //

    var serverUrl: String
        get() = prefs.getString("server_url", DEFAULT_URL)
            ?.takeUnless { it == OLD_DEFAULT_URL } ?: DEFAULT_URL
        set(v) = prefs.edit().putString("server_url", v.trim()).apply()

    var serverToken: String
        get() = prefs.getString("server_token", "") ?: ""
        set(v) = prefs.edit().putString("server_token", v.trim()).apply()

    /** Custom destination folder name. Blank = auto (playlist name). */
    var customFolder: String
        get() = prefs.getString("custom_folder", "") ?: ""
        set(v) = prefs.edit().putString("custom_folder", v.trim()).apply()

    var useCustomFolder: Boolean
        get() = prefs.getBoolean("use_custom_folder", false)
        set(v) = prefs.edit().putBoolean("use_custom_folder", v).apply()

    // --- Connection -------------------------------------------------------- //

    /** Called when the Download tab becomes visible: connect, and start Termux if allowed. */
    fun onScreenShown() {
        if (_connected.value == true || connectJob?.isActive == true) return
        connect(autoStart = true)
    }

    fun testConnection() = connect(autoStart = false)

    private fun connect(autoStart: Boolean) {
        val server = serverUrl
        if (server.isBlank()) {
            _connectionStatus.value = "Set the server address first"
            _connected.value = false
            return
        }
        _connectionStatus.value = "Connecting…"
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                onConnected(repo.health(server, serverToken))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (autoStart && isLocalServer() && Termux.canStartServer(getApplication())) {
                    launchServerAndWait()
                } else {
                    _connected.value = false
                    _connectionStatus.value = "Not connected. Is the downloader running in Termux?"
                }
            }
        }
    }

    /** Asks Termux to run `hexadl`, then waits for the server to answer. */
    fun startServer() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch { launchServerAndWait() }
    }

    private suspend fun launchServerAndWait() {
        if (!Termux.startServer(getApplication())) {
            _connected.value = false
            _connectionStatus.value = "Termux refused to start. Check the setup guide."
            return
        }
        _connectionStatus.value = "Starting the downloader…"
        repeat(20) {
            delay(1500)
            try {
                onConnected(repo.health(serverUrl, serverToken))
                return
            } catch (_: Exception) { }
        }
        _connected.value = false
        _connectionStatus.value = "Downloader did not start. Did the setup finish in Termux?"
    }

    private fun onConnected(health: ServerHealth) {
        serverRoot = health.root
        _connected.value = true
        _connectionStatus.value = "Connected · spotDL ${health.version}"
        refreshSyncs()
    }

    private fun isLocalServer(): Boolean {
        val url = serverUrl
        return "127.0.0.1" in url || "localhost" in url
    }

    fun refreshSyncs() {
        val server = serverUrl
        if (server.isBlank()) return
        viewModelScope.launch {
            try {
                _syncs.value = repo.listSyncs(server, serverToken)
            } catch (_: Exception) { /* ignore */ }
        }
    }

    // --- Jobs -------------------------------------------------------------- //

    fun submit(link: String, kind: JobKind) {
        val url = link.trim()
        if (url.isBlank()) return
        if (serverUrl.isBlank()) {
            _toastMessage.value = "Set the server address first"
            return
        }
        val folder = if (useCustomFolder && customFolder.isNotBlank()) customFolder else null
        val label = folder ?: prettyLabel(url)
        val job = SpotdlJob(
            id = idCounter.incrementAndGet(),
            kind = kind,
            url = url,
            label = label,
            phase = JobPhase.QUEUED,
            message = if (kind == JobKind.SYNC) "Preparing sync…" else "Preparing download…"
        )
        upsert(job)
        runJob(job, folder)
    }

    /** Re-sync an already-saved playlist. */
    fun resync(entry: SyncEntry) {
        val job = SpotdlJob(
            id = idCounter.incrementAndGet(),
            kind = JobKind.SYNC,
            url = entry.url,
            label = entry.name,
            phase = JobPhase.QUEUED,
            message = "Preparing sync…"
        )
        upsert(job)
        runJob(job, entry.folder.ifBlank { null })
    }

    private fun runJob(job: SpotdlJob, folder: String?) {
        val server = serverUrl
        val token = serverToken
        viewModelScope.launch {
            try {
                // Make sure we know the server's download root for indexing.
                if (serverRoot.isBlank()) {
                    serverRoot = repo.health(server, token).root
                }

                val serverId = repo.startJob(server, token, job.url, job.kind, folder)
                var current = job.copy(
                    serverId = serverId,
                    phase = JobPhase.SERVER,
                    message = "Downloading…"
                )
                upsert(current)

                // Poll until the server finishes.
                while (true) {
                    delay(1500)
                    val s = repo.pollJob(server, token, serverId)
                    current = current.copy(
                        serverDone = s.done,
                        serverTotal = s.total,
                        message = s.message
                    )
                    upsert(current)
                    when (s.status) {
                        "done" -> {
                            indexFiles(current, server, token, s.folders, folder)
                            return@launch
                        }
                        "error" -> {
                            upsert(current.copy(
                                phase = JobPhase.ERROR,
                                error = s.error ?: "Server error"
                            ))
                            return@launch
                        }
                    }
                }
            } catch (e: Exception) {
                upsert(job.copy(phase = JobPhase.ERROR, error = e.message?.take(150)))
            }
        }
    }

    /**
     * The files are already on the phone (spotDL wrote them into the shared
     * Music folder). We just tell MediaStore to index them so they show up in
     * the library.
     */
    private suspend fun indexFiles(
        job: SpotdlJob,
        server: String,
        token: String,
        folders: List<String>,
        requestedFolder: String?
    ) {
        val targetFolders = when {
            !requestedFolder.isNullOrBlank() -> listOf(requestedFolder)
            folders.isNotEmpty() -> folders
            else -> listOf("")
        }

        val remoteFiles = targetFolders.flatMap { f ->
            try {
                repo.tree(server, token, f.ifBlank { null })
            } catch (_: Exception) {
                emptyList()
            }
        }.distinctBy { it.path }

        upsert(job.copy(phase = JobPhase.IMPORTING, message = "Adding to library…"))

        val root = serverRoot.trimEnd('/')
        if (root.isNotBlank() && remoteFiles.isNotEmpty()) {
            scanFiles(remoteFiles.map { "$root/${it.path}" }.toTypedArray())
            _libraryChanged.value = true
        }

        upsert(job.copy(
            phase = JobPhase.DONE,
            message = if (remoteFiles.isEmpty()) "No files found"
            else "${remoteFiles.size} track(s) in your library"
        ))
        if (job.kind == JobKind.SYNC) refreshSyncs()
    }

    private suspend fun scanFiles(paths: Array<String>) = suspendCancellableCoroutine { cont ->
        var remaining = paths.size
        MediaScannerConnection.scanFile(getApplication(), paths, null) { _, _ ->
            if (--remaining == 0 && cont.isActive) cont.resume(Unit)
        }
    }

    fun clearFinished() {
        _jobs.value = (_jobs.value ?: emptyList()).filter {
            it.phase != JobPhase.DONE && it.phase != JobPhase.ERROR
        }
    }

    fun consumeLibraryChanged() { _libraryChanged.value = false }
    fun clearToastMessage() { _toastMessage.value = null }

    private fun upsert(job: SpotdlJob) {
        val list = (_jobs.value ?: emptyList()).toMutableList()
        val idx = list.indexOfFirst { it.id == job.id }
        if (idx >= 0) list[idx] = job else list.add(0, job)
        _jobs.value = list
    }

    private fun prettyLabel(url: String): String = when {
        url.contains("playlist") -> "Playlist"
        url.contains("album") -> "Album"
        url.contains("artist") -> "Artist"
        url.contains("track") -> "Track"
        else -> "Download"
    }

    companion object {
        private const val DEFAULT_URL = "http://127.0.0.1:47823"
        private const val OLD_DEFAULT_URL = "http://127.0.0.1:8000"
    }
}
