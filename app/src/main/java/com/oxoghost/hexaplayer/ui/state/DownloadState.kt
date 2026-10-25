package com.oxoghost.hexaplayer.ui.state

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.data.JobPhase
import com.oxoghost.hexaplayer.data.SpotdlJob
import com.oxoghost.hexaplayer.data.SyncEntry
import com.oxoghost.hexaplayer.ui.app.SystemAccess
import com.oxoghost.hexaplayer.util.Termux
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.DownloadViewModel

enum class DownloaderStatus { Running, Outdated, Stopped, NotSetUp }

enum class LogKind { Done, Active, Failed, Info }

@Immutable
data class JobLogLine(val kind: LogKind, val text: String)

/** One download or sync, ready to draw. */
@Immutable
data class JobView(
    val job: SpotdlJob,
    val running: Boolean,
    /** spotDL is still looking the songs up on YouTube, nothing finished yet. */
    val matching: Boolean,
    val failed: Boolean,
    val fraction: Float,
    val counter: String,
    val detail: String,
    val log: List<JobLogLine>,
) {
    val kind: String get() = if (job.kind == JobKind.SYNC) "sync" else "download"
}

@Immutable
data class DownloadState(
    val status: DownloaderStatus,
    val statusText: String,
    val spotdlVersion: String?,
    val idleMinutes: Int,
    val useCustomFolder: Boolean,
    val customFolder: String,
    val destination: String,
    val jobs: List<JobView>,
    val syncs: List<SyncEntry>,
) {
    val running: Int get() = jobs.count { it.running }
    val hasFinished: Boolean get() = jobs.any { !it.running }
}

/** The four steps of the Termux guide. */
@Immutable
data class SetupState(
    val termuxInstalled: Boolean,
    val setupRun: Boolean,
    val permissionGranted: Boolean,
    val connected: Boolean,
    val statusText: String,
    val command: String,
) {
    val doneCount: Int get() = listOf(termuxInstalled, setupRun, permissionGranted, connected).count { it }

    /** First step not done yet (0 based), 3 once everything else is done. */
    val current: Int get() = listOf(termuxInstalled, setupRun, permissionGranted).indexOfFirst { !it }.let { if (it < 0) 3 else it }
}

interface DownloadActions {
    fun submit(link: String, kind: JobKind)
    fun stopJob(job: SpotdlJob)
    fun retryJob(job: SpotdlJob)
    fun clearFinished()
    fun resync(entry: SyncEntry)
    fun startServer()
    fun stopServer()
    fun setUseCustomFolder(on: Boolean)
    fun setCustomFolder(name: String)
    fun openSetup()
}

interface TermuxSetupActions {
    fun openTermuxPage(github: Boolean)
    fun copyAndOpenTermux()
    fun allowRunCommand()
    fun startNow()
    fun testConnection()
    fun openFullGuide()
}

private val downloadedRe = Regex("^Downloaded \"(.+?)\"")
private val skippedRe = Regex("^Skipping (.+?)(?: \\(.*\\))?$")
private val percentRe = Regex("(\\d{1,3})%")

/** spotDL output lines as `✓ title`, `↓ title 62%`, `✗ error`. */
fun parseLog(lines: List<String>): List<JobLogLine> = lines.mapNotNull { raw ->
    val line = raw.trim()
    when {
        line.isEmpty() -> null
        downloadedRe.containsMatchIn(line) -> JobLogLine(LogKind.Done, downloadedRe.find(line)!!.groupValues[1].substringAfter(" - "))
        skippedRe.containsMatchIn(line) -> JobLogLine(LogKind.Done, skippedRe.find(line)!!.groupValues[1].substringAfter(" - ") + " (on phone)")
        line.contains("error", ignoreCase = true) || line.contains("exception", ignoreCase = true) -> JobLogLine(LogKind.Failed, line.take(80))
        percentRe.containsMatchIn(line) -> JobLogLine(LogKind.Active, line.take(80))
        else -> JobLogLine(LogKind.Info, line.take(80))
    }
}.takeLast(6)

private fun jobView(job: SpotdlJob): JobView {
    val running = job.phase == JobPhase.QUEUED || job.phase == JobPhase.SERVER || job.phase == JobPhase.IMPORTING
    val total = job.serverTotal
    val counter = if (total > 0) "${job.serverDone}/$total" else ""
    val detail = when (job.phase) {
        JobPhase.DONE -> listOfNotNull(
            if (total > 0) "${job.serverDone} of $total" else "done",
            job.serverFailed.takeIf { it > 0 }?.let { "$it failed" },
        ).joinToString(" · ")
        JobPhase.STOPPED -> "stopped" + if (total > 0) " at ${job.serverDone} of $total" else ""
        JobPhase.ERROR -> job.error ?: "failed"
        else -> job.message
    }
    return JobView(
        job = job,
        running = running,
        matching = job.phase == JobPhase.SERVER && job.serverDone == 0 && job.serverFailed == 0,
        failed =job.phase == JobPhase.ERROR || job.phase == JobPhase.STOPPED,
        fraction = if (total > 0) job.serverDone.toFloat() / total else 0f,
        counter = counter,
        detail = detail,
        log = if (running) parseLog(job.log) else emptyList(),
    )
}

/** Download screen and Termux guide, over [DownloadViewModel]. */
class DownloadModel(
    private val context: Context,
    private val vm: DownloadViewModel,
    private val system: SystemAccess,
    private val onOpenSetup: () -> Unit,
) : DownloadActions, TermuxSetupActions {

    var folderSwitch by mutableStateOf(vm.useCustomFolder)
        private set
    var folderName by mutableStateOf(vm.customFolder)
        private set

    override fun submit(link: String, kind: JobKind) = vm.submit(link, kind)
    override fun stopJob(job: SpotdlJob) = vm.stop(job)
    override fun retryJob(job: SpotdlJob) = vm.retry(job)
    override fun clearFinished() = vm.clearFinished()
    override fun resync(entry: SyncEntry) = vm.resync(entry)

    override fun startServer() {
        if (Termux.canStartServer(context)) vm.startServer() else onOpenSetup()
    }

    override fun stopServer() = vm.stopServer()

    override fun setUseCustomFolder(on: Boolean) {
        vm.useCustomFolder = on
        folderSwitch = on
    }

    override fun setCustomFolder(name: String) {
        folderName = name
        vm.customFolder = name
    }

    override fun openSetup() = onOpenSetup()

    override fun openTermuxPage(github: Boolean) = openUrl(if (github) Termux.GITHUB_URL else Termux.FDROID_URL)

    override fun copyAndOpenTermux() {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("HexaPlayer setup", context.getString(R.string.dl_setup_command)))
        if (Termux.open(context)) context.toast(context.getString(R.string.dl_setup_copied), long = true)
        else context.toast(context.getString(R.string.tx_termux_missing), long = true)
    }

    override fun allowRunCommand() {
        if (!Termux.isInstalled(context)) {
            context.toast(context.getString(R.string.tx_termux_missing), long = true)
            return
        }
        system.requestPermission(Termux.PERMISSION) { granted ->
            if (!granted) context.toast(context.getString(R.string.tx_permission_denied), long = true)
        }
    }

    override fun startNow() {
        if (Termux.canStartServer(context)) {
            vm.startServer()
        } else {
            Termux.open(context)
            context.toast(context.getString(R.string.tx_start_manual), long = true)
        }
    }

    override fun testConnection() = vm.testConnection()

    override fun openFullGuide() = openUrl(GUIDE_URL)

    private fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.toast(url, long = true)
        }
    }

    companion object {
        const val GUIDE_URL = "https://github.com/OxoGhost01/HexaPlayer/blob/main/termux/README.md"
    }
}

/** Re-reads Termux's install and permission state each time the screen resumes. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return tick
}

@Composable
fun rememberDownloadState(context: Context, vm: DownloadViewModel, model: DownloadModel): DownloadState {
    val jobs by vm.jobs.observeAsState(emptyList())
    val syncs by vm.syncs.observeAsState(emptyList())
    val connected by vm.connected.observeAsState(false)
    val outdated by vm.serverOutdated.observeAsState(false)
    val statusText by vm.connectionStatus.observeAsState()
    val health by vm.health.observeAsState()
    val tick = rememberResumeTick()
    val canStart = remember(tick) { Termux.canStartServer(context) }
    return DownloadState(
        status = when {
            connected && outdated -> DownloaderStatus.Outdated
            connected -> DownloaderStatus.Running
            canStart || vm.serverSeen -> DownloaderStatus.Stopped
            else -> DownloaderStatus.NotSetUp
        },
        statusText = statusText.orEmpty(),
        spotdlVersion = health?.version,
        idleMinutes = health?.idleMinutes ?: 10,
        useCustomFolder = model.folderSwitch,
        customFolder = model.folderName,
        destination = vm.destinationRoot(),
        jobs = jobs.map(::jobView),
        syncs = syncs,
    )
}

@Composable
fun rememberSetupState(context: Context, vm: DownloadViewModel): SetupState {
    val connected by vm.connected.observeAsState(false)
    val statusText by vm.connectionStatus.observeAsState()
    val tick = rememberResumeTick()
    val installed = remember(tick) { Termux.isInstalled(context) }
    val granted = remember(tick) { installed && Termux.hasPermission(context) }
    return SetupState(
        termuxInstalled = installed,
        setupRun = vm.serverSeen || connected,
        permissionGranted = granted,
        connected = connected,
        statusText = statusText.orEmpty(),
        command = context.getString(R.string.dl_setup_command),
    )
}
