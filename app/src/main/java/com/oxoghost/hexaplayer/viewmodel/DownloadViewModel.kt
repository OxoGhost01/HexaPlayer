package com.oxoghost.hexaplayer.viewmodel

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import android.util.Log
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
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

    /** True when the running server is older than this app needs. */
    private val _serverOutdated = MutableLiveData(false)
    val serverOutdated: LiveData<Boolean> = _serverOutdated

    /** Result of the last connection check: true, false, or null while checking. */
    private val _checkResult = MutableLiveData<Boolean?>(null)
    val checkResult: LiveData<Boolean?> = _checkResult

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** Fired when new files have been indexed, so the library can rescan. */
    private val _libraryChanged = MutableLiveData<Boolean>(false)
    val libraryChanged: LiveData<Boolean> = _libraryChanged

    private val idCounter = AtomicLong(System.currentTimeMillis())

    private var connectJob: Job? = null

    private val stopping = mutableSetOf<Long>()

    private val serverMutex = Mutex()

    init {
        _syncs.value = loadSyncCache()
    }

    // --- Settings ---------------------------------------------------------- //

    /** Custom destination folder name. Blank = auto (playlist name). */
    var customFolder: String
        get() = prefs.getString("custom_folder", "") ?: ""
        set(v) = prefs.edit().putString("custom_folder", v.trim()).apply()

    var useCustomFolder: Boolean
        get() = prefs.getBoolean("use_custom_folder", false)
        set(v) = prefs.edit().putBoolean("use_custom_folder", v).apply()

    // --- Connection -------------------------------------------------------- //

    /** Called when the Download tab becomes visible. Only checks, never starts the server. */
    fun onScreenShown() {
        if (connectJob?.isActive == true) return
        connect()
    }

    fun testConnection() = connect()

    private fun connect() {
        _connectionStatus.value = "Connecting…"
        _checkResult.value = null
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            try {
                onConnected(repo.health())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Health check failed", e)
                setConnected(false)
                _connectionStatus.value =
                    if (Termux.canStartServer(getApplication()))
                        "Not running. It starts by itself when you download."
                    else "Not connected. Is the downloader running in Termux?"
            }
        }
    }

    /** Asks Termux to run `hexadl`, then waits for the server to answer. */
    fun startServer() {
        connectJob?.cancel()
        connectJob = viewModelScope.launch { launchServerAndWait() }
    }

    /** Makes sure the server answers before a job, starting it through Termux if needed. */
    private suspend fun ensureServer(): Boolean = serverMutex.withLock {
        try {
            onConnected(repo.health())
            return true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        if (!Termux.canStartServer(getApplication())) {
            setConnected(false)
            _connectionStatus.value = "Not connected. Is the downloader running in Termux?"
            return false
        }
        launchServerAndWait()
    }

    private suspend fun launchServerAndWait(): Boolean {
        if (!Termux.startServer(getApplication())) {
            setConnected(false)
            _connectionStatus.value = "Termux refused to start. Check the setup guide."
            return false
        }
        _connectionStatus.value = "Starting the downloader…"
        _checkResult.value = null
        repeat(20) {
            delay(1500)
            try {
                onConnected(repo.health())
                return true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
        setConnected(false)
        _connectionStatus.value = "Downloader did not start. Run the setup command in Termux again."
        return false
    }

    private fun onConnected(health: ServerHealth) {
        setConnected(true)
        _serverOutdated.value = health.serverVersion < MIN_SERVER_VERSION
        _connectionStatus.value = "Connected · spotDL ${health.version}"
        refreshSyncs()
    }

    private fun setConnected(ok: Boolean) {
        _connected.value = ok
        _checkResult.value = ok
    }

    fun refreshSyncs() {
        viewModelScope.launch {
            try {
                val list = repo.listSyncs()
                _syncs.value = list
                saveSyncCache(list)
            } catch (_: Exception) { }
        }
    }

    /** The synced playlists are remembered so they show while the server is stopped. */
    private fun loadSyncCache(): List<SyncEntry> = try {
        val arr = JSONArray(prefs.getString("syncs_cache", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SyncEntry(o.getString("url"), o.optString("folder"), o.optString("root"), o.optString("name"))
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveSyncCache(list: List<SyncEntry>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("url", it.url).put("folder", it.folder).put("root", it.root).put("name", it.name))
        }
        prefs.edit().putString("syncs_cache", arr.toString()).apply()
    }

    // --- Jobs -------------------------------------------------------------- //

    fun submit(link: String, kind: JobKind) {
        val url = link.trim()
        if (url.isBlank()) return
        val folder = if (useCustomFolder && customFolder.isNotBlank()) customFolder else null
        startJob(kind, url, folder ?: prettyLabel(url), folder, libraryFolder())
    }

    /** Re-sync an already-saved playlist. */
    fun resync(entry: SyncEntry) {
        startJob(JobKind.SYNC, entry.url, entry.name, entry.folder.ifBlank { null }, entry.root.ifBlank { null })
    }

    /** Runs a stopped or failed job again. spotDL skips the tracks already on disk. */
    fun retry(job: SpotdlJob) {
        _jobs.value = (_jobs.value ?: emptyList()).filter { it.id != job.id }
        startJob(job.kind, job.url, job.label, job.folder, job.root)
    }

    fun stop(job: SpotdlJob) {
        val serverId = job.serverId ?: return
        stopping += job.id
        upsert(job.copy(message = "Stopping…"))
        viewModelScope.launch {
            try {
                repo.stopJob(serverId)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _toastMessage.value = "Could not stop: ${e.message?.take(100)}"
            }
        }
    }

    private fun startJob(kind: JobKind, url: String, label: String, folder: String?, root: String?) {
        val job = SpotdlJob(
            id = idCounter.incrementAndGet(),
            kind = kind,
            url = url,
            label = label,
            folder = folder,
            root = root,
            phase = JobPhase.QUEUED,
            message = if (kind == JobKind.SYNC) "Preparing sync…" else "Preparing download…"
        )
        upsert(job)
        runJob(job)
    }

    /** Where downloads land, relative to storage: the library folder or the server default. */
    fun destinationRoot(): String = libraryFolder() ?: "Music/HexaPlayer"

    /** The Music Library folder from Settings (relative to storage), if any. */
    private fun libraryFolder(): String? =
        getApplication<Application>().getSharedPreferences("hexa_prefs", Context.MODE_PRIVATE)
            .getString("music_folder", "")?.trim()?.trim('/')?.ifBlank { null }

    private fun runJob(job: SpotdlJob) {
        viewModelScope.launch {
            try {
                upsert(job.copy(message = "Starting the downloader…"))
                if (!ensureServer()) {
                    upsert(job.copy(phase = JobPhase.ERROR, error = "Downloader not running. See the setup guide."))
                    return@launch
                }
                val serverId = repo.startJob(job.url, job.kind, job.folder, job.root)
                var current = job.copy(
                    serverId = serverId,
                    phase = JobPhase.SERVER,
                    message = "Downloading…"
                )
                upsert(current)

                while (true) {
                    delay(1500)
                    val s = repo.pollJob(serverId)
                    current = current.copy(
                        serverDone = s.done,
                        serverTotal = s.total,
                        message = if (job.id in stopping && s.status == "running") "Stopping…" else s.message
                    )
                    upsert(current)
                    if (s.status != "running") stopping -= job.id
                    when (s.status) {
                        "done", "stopped" -> {
                            indexFiles(current, s.files, if (s.status == "done") JobPhase.DONE else JobPhase.STOPPED)
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
                if (e is CancellationException) throw e
                upsert(job.copy(phase = JobPhase.ERROR, error = e.message?.take(150)))
            }
        }
    }

    /** spotDL already wrote the files to shared storage; MediaStore just has to index them. */
    private suspend fun indexFiles(job: SpotdlJob, files: List<String>, phase: JobPhase) {
        if (files.isNotEmpty()) {
            upsert(job.copy(phase = JobPhase.IMPORTING, message = "Adding to library…"))
            scanFiles(files.toTypedArray())
            _libraryChanged.value = true
        }
        upsert(job.copy(phase = phase))
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
            it.phase != JobPhase.DONE && it.phase != JobPhase.ERROR && it.phase != JobPhase.STOPPED
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
        private const val TAG = "DownloadViewModel"
        private const val MIN_SERVER_VERSION = 2
    }
}
