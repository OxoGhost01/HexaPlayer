package com.oxoghost.hexaplayer.ui.material.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.ui.common.Symbol
import com.oxoghost.hexaplayer.ui.common.Symbols
import com.oxoghost.hexaplayer.ui.material.components.MButtonSize
import com.oxoghost.hexaplayer.ui.material.components.MButtonStyle
import com.oxoghost.hexaplayer.ui.material.components.MCircleButton
import com.oxoghost.hexaplayer.ui.material.components.MIconButton
import com.oxoghost.hexaplayer.ui.material.components.MLinearProgress
import com.oxoghost.hexaplayer.ui.material.components.MPillButton
import com.oxoghost.hexaplayer.ui.material.components.MSectionLabel
import com.oxoghost.hexaplayer.ui.material.components.MSegment
import com.oxoghost.hexaplayer.ui.material.components.MSegmentedGroup
import com.oxoghost.hexaplayer.ui.material.components.MSwitch
import com.oxoghost.hexaplayer.ui.material.components.MTextField
import com.oxoghost.hexaplayer.ui.state.DownloadActions
import com.oxoghost.hexaplayer.ui.state.DownloadState
import com.oxoghost.hexaplayer.ui.state.DownloaderStatus
import com.oxoghost.hexaplayer.ui.state.JobView
import kotlinx.coroutines.launch

@Composable
fun MDownloadScreen(state: DownloadState, actions: DownloadActions) {
    val s = MaterialTheme.colorScheme
    var link by rememberSaveable { mutableStateOf("") }
    var missingLink by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    fun submit(kind: JobKind) {
        if (link.isBlank()) {
            missingLink = true
            return
        }
        actions.submit(link, kind)
        link = ""
        missingLink = false
        focus.clearFocus()
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = LocalChromePadding.current),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Download & Sync", Modifier.weight(1f), style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold))
            MCircleButton(Symbols.Help, "Downloader setup guide", actions::openSetup, container = s.surfaceContainerHigh, content = s.onSurface)
        }
        StatusCard(state, actions)
        MTextField(
            link,
            {
                link = it
                missingLink = false
            },
            "Spotify link",
            Modifier.fillMaxWidth(),
            supportingText = if (missingLink) "Paste a Spotify link first" else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            trailing = {
                MIconButton(Symbols.ContentPaste, "Paste", {
                    scope.launch {
                        clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { link = it.toString().trim() }
                    }
                })
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Custom folder name", style = MaterialTheme.typography.titleMedium)
                Text("Default: ${state.destination}/<name>", color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            MSwitch(state.useCustomFolder, actions::setUseCustomFolder)
        }
        if (state.useCustomFolder) {
            MTextField(state.customFolder, actions::setCustomFolder, "Folder name", Modifier.fillMaxWidth(),
                supportingText = "Saved to ${state.destination}/${state.customFolder.ifBlank { "<name>" }}")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MPillButton("Sync", { submit(JobKind.SYNC) }, Modifier.weight(1f), icon = Symbols.Sync)
            MPillButton("Download", { submit(JobKind.DOWNLOAD) }, Modifier.weight(1f), style = MButtonStyle.Tonal, icon = Symbols.Download)
        }
        if (state.jobs.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MSectionLabel("Downloads", Modifier.weight(1f))
                if (state.hasFinished) MPillButton("Clear done", actions::clearFinished, style = MButtonStyle.Text, size = MButtonSize.S)
            }
            MSegmentedGroup {
                state.jobs.forEachIndexed { i, job -> JobSegment(i, state.jobs.size, job, actions) }
            }
        }
        if (state.syncs.isNotEmpty()) {
            MSectionLabel("Synced playlists", Modifier.padding(top = 8.dp))
            MSegmentedGroup(Modifier.padding(bottom = 16.dp)) {
                state.syncs.forEachIndexed { i, sync ->
                    MSegment(i, state.syncs.size) {
                        Column(Modifier.weight(1f)) {
                            Text(sync.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sync.folder.ifBlank { sync.root }, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                        MPillButton("Re-sync", { actions.resync(sync) }, style = MButtonStyle.Tonal, size = MButtonSize.M, icon = Symbols.Sync)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: DownloadState, actions: DownloadActions) {
    val s = MaterialTheme.colorScheme
    val (title, subtitle) = when (state.status) {
        DownloaderStatus.Running -> "Downloader running" to "In Termux · stops after ${state.idleMinutes} min idle"
        DownloaderStatus.Outdated -> "Downloader out of date" to "Run the setup command again in Termux"
        DownloaderStatus.Stopped -> "Downloader stopped" to "Starts by itself when you download"
        DownloaderStatus.NotSetUp -> "Downloader not set up" to "Follow the guide once to use Download & Sync"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(s.secondaryContainer)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Symbol(Symbols.Terminal, null, tint = s.onSecondaryContainer)
        Column(Modifier.weight(1f)) {
            Text(title, color = s.onSecondaryContainer, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = s.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
        }
        when (state.status) {
            DownloaderStatus.Running -> MPillButton("Stop", actions::stopServer, style = MButtonStyle.Outlined, size = MButtonSize.M, contentColor = s.onSecondaryContainer)
            DownloaderStatus.Stopped -> MPillButton("Start", actions::startServer, style = MButtonStyle.Outlined, size = MButtonSize.M, contentColor = s.onSecondaryContainer)
            else -> MPillButton("Guide", actions::openSetup, size = MButtonSize.M)
        }
    }
}

@Composable
private fun JobSegment(index: Int, count: Int, job: JobView, actions: DownloadActions) {
    val s = MaterialTheme.colorScheme
    MSegment(index, count) {
        if (!job.running) {
            Symbol(if (job.failed) Symbols.Error else Symbols.CheckCircle, null, tint = if (job.failed) s.error else s.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(job.job.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (job.running) {
                listOf(job.kind.replaceFirstChar { it.uppercase() }, job.counter.replace("/", " of "), job.log.lastOrNull()?.text ?: job.detail)
                    .filter { it.isNotEmpty() }.joinToString(" · ")
            } else job.detail
            Text(line, color = s.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (job.running) MLinearProgress(job.fraction, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        if (job.running) {
            MCircleButton(Symbols.Stop, "Stop ${job.job.label}", { actions.stopJob(job.job) }, container = s.surfaceContainerHigh, content = s.onSurface, size = 48.dp)
        } else {
            MPillButton("Run again", { actions.retryJob(job.job) }, style = MButtonStyle.Text, size = MButtonSize.M)
        }
    }
}
