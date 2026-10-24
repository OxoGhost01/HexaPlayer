package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.ui.state.DownloadActions
import com.oxoghost.hexaplayer.ui.state.DownloadState
import com.oxoghost.hexaplayer.ui.state.DownloaderStatus
import com.oxoghost.hexaplayer.ui.state.JobView
import com.oxoghost.hexaplayer.ui.state.LogKind
import com.oxoghost.hexaplayer.ui.terminal.components.TBlockBar
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TCheckMark
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.THeader
import com.oxoghost.hexaplayer.ui.terminal.components.TInput
import com.oxoghost.hexaplayer.ui.terminal.components.TMode
import com.oxoghost.hexaplayer.ui.terminal.components.TStatusLine
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme
import kotlinx.coroutines.launch

@Composable
fun TDownloadScreen(state: DownloadState, actions: DownloadActions) {
    val c = TerminalTheme.colors
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
    TScreen(
        header = { THeader("~/dl", action = "[guide]", actionDescription = "Downloader setup guide", onAction = actions::openSetup) },
        status = {
            TStatusLine(
                TMode.DL,
                if (state.running > 0) "${state.running} running" else state.statusText.ifEmpty { "idle" },
                position = state.spotdlVersion?.let { "spotDL $it" },
            )
        },
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            ServerRow(state, actions)
            TDashedDivider()
            TText("spotify link · playlist, album or track", Modifier.padding(top = 12.dp, bottom = 6.dp), color = c.dim)
            TInput(
                link,
                {
                    link = it
                    missingLink = false
                },
                prompt = "$",
                placeholder = "open.spotify.com/...",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                trailing = {
                    Box(
                        Modifier.clickable(role = Role.Button, onClickLabel = "Paste the link") {
                            scope.launch {
                                clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.let { link = it.toString().trim() }
                            }
                        }.padding(horizontal = 4.dp, vertical = 8.dp)
                    ) { TText("paste", color = c.dim) }
                },
            )
            if (missingLink) TText("paste a spotify link first", Modifier.padding(top = 4.dp), color = c.error)
            Row(
                Modifier
                    .padding(vertical = 10.dp)
                    .clickable(role = Role.Checkbox) { actions.setUseCustomFolder(!state.useCustomFolder) }
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TCheckMark(state.useCustomFolder)
                TText("folder")
                TText("${state.destination}/" + if (state.useCustomFolder) state.customFolder.ifBlank { "<name>" } else "<name>", color = c.dim)
            }
            if (state.useCustomFolder) {
                TInput(state.customFolder, actions::setCustomFolder, Modifier.padding(bottom = 10.dp), prompt = ">", placeholder = "folder name")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TButton("⟳ sync", { submit(JobKind.SYNC) }, Modifier.weight(1f), filled = true, large = true)
                TButton("↓ download", { submit(JobKind.DOWNLOAD) }, Modifier.weight(1f), color = c.accent, large = true)
            }
            if (state.jobs.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TText("jobs", Modifier.weight(1f), color = c.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
                    if (state.hasFinished) {
                        Box(Modifier.clickable(role = Role.Button, onClick = actions::clearFinished).padding(8.dp)) { TText("clear done", color = c.dim) }
                    }
                }
                state.jobs.forEach { job -> if (job.running) RunningJob(job, actions) else FinishedJob(job, actions) }
            }
            if (state.syncs.isNotEmpty()) {
                TText("synced", Modifier.padding(top = 20.dp, bottom = 4.dp), color = c.accent, style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
                state.syncs.forEach { sync ->
                    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        TText(sync.name, Modifier.weight(1f))
                        Box(Modifier.clickable(role = Role.Button, onClickLabel = "Re-sync ${sync.name}") { actions.resync(sync) }.padding(8.dp)) {
                            TText("⟳ re-sync", color = c.accent2)
                        }
                    }
                }
            }
            Box(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ServerRow(state: DownloadState, actions: DownloadActions) {
    val c = TerminalTheme.colors
    val (dot, text, action, onAction, actionColor) = when (state.status) {
        DownloaderStatus.Running -> ServerLine(c.ok, "termux · spotdl ${state.spotdlVersion.orEmpty()} · idle stop ${state.idleMinutes}m", "stop", actions::stopServer, c.error)
        DownloaderStatus.Outdated -> ServerLine(c.warn, "out of date · run the setup again", "guide", actions::openSetup, c.accent)
        DownloaderStatus.Stopped -> ServerLine(c.dim, "stopped · starts when you download", "start", actions::startServer, c.accent)
        DownloaderStatus.NotSetUp -> ServerLine(c.error, "not set up yet", "guide", actions::openSetup, c.accent)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TText("●", color = dot)
        TText("hexadl", style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
        TText(text, Modifier.weight(1f), color = c.dim, maxLines = 2)
        Box(Modifier.clickable(role = Role.Button, onClick = onAction).padding(8.dp)) { TText(action, color = actionColor) }
    }
}

private data class ServerLine(val dot: Color, val text: String, val action: String, val onAction: () -> Unit, val actionColor: Color)

@Composable
private fun RunningJob(job: JobView, actions: DownloadActions) {
    val c = TerminalTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .border(1.dp, c.border, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            TText(job.kind, color = c.accent2)
            TText(" " + job.job.label, Modifier.weight(1f), style = TerminalTheme.type.body.copy(fontWeight = FontWeight.Bold))
            TText(job.counter, color = c.dim)
        }
        TBlockBar(job.fraction)
        job.log.forEach { line ->
            val (glyph, color) = when (line.kind) {
                LogKind.Done -> "✓" to c.ok
                LogKind.Active -> "↓" to c.foreground
                LogKind.Failed -> "✗" to c.error
                LogKind.Info -> "·" to c.dim
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TText(glyph, color = color)
                TText(line.text, color = if (line.kind == LogKind.Info) c.dim else color)
            }
        }
        if (job.log.isEmpty()) TText(job.detail, color = c.dim, maxLines = 2)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Box(Modifier.clickable(role = Role.Button, onClickLabel = "Stop ${job.job.label}") { actions.stopJob(job.job) }.padding(8.dp)) {
                TText("stop", color = c.error)
            }
        }
    }
}

@Composable
private fun FinishedJob(job: JobView, actions: DownloadActions) {
    val c = TerminalTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TText(if (job.failed) "err" else "done", Modifier.width(40.dp), color = if (job.failed) c.error else c.ok)
            Column(Modifier.weight(1f)) {
                TText(job.job.label)
                TText("${job.kind} · ${job.detail}", color = c.dim, maxLines = 2, style = TerminalTheme.type.small)
            }
            Box(Modifier.clickable(role = Role.Button, onClickLabel = "Run ${job.job.label} again") { actions.retryJob(job.job) }.padding(8.dp)) {
                TText("run again", color = c.accent, style = TerminalTheme.type.body.copy(textAlign = TextAlign.End))
            }
        }
        TDashedDivider()
    }
}
