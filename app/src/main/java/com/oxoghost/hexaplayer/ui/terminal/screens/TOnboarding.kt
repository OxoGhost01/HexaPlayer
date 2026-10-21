package com.oxoghost.hexaplayer.ui.terminal.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.oxoghost.hexaplayer.ui.state.OnboardingActions
import com.oxoghost.hexaplayer.ui.state.OnboardingState
import com.oxoghost.hexaplayer.ui.terminal.components.TButton
import com.oxoghost.hexaplayer.ui.terminal.components.TDashedDivider
import com.oxoghost.hexaplayer.ui.terminal.components.TLog
import com.oxoghost.hexaplayer.ui.terminal.components.TLogLine
import com.oxoghost.hexaplayer.ui.terminal.components.TSectionBox
import com.oxoghost.hexaplayer.ui.terminal.components.TText
import com.oxoghost.hexaplayer.ui.terminal.theme.TerminalTheme

/** 3 x 4 pixel letters of the block logo, one string per row. */
private val glyphs = mapOf(
    'H' to listOf("1.1", "111", "1.1", "1.1"),
    'E' to listOf("111", "11.", "1..", "111"),
    'X' to listOf("1.1", ".1.", "1.1", "1.1"),
    'A' to listOf("111", "1.1", "111", "1.1"),
    'P' to listOf("111", "1.1", "111", "1.."),
    'L' to listOf("1..", "1..", "1..", "111"),
    'Y' to listOf("1.1", "1.1", ".1.", ".1."),
    'R' to listOf("111", "1.1", "11.", "1.1"),
    ' ' to listOf("..", "..", "..", ".."),
)

@Composable
private fun BlockLogo(text: String, modifier: Modifier = Modifier) {
    val color = TerminalTheme.colors.accent
    val columns = text.sumOf { (glyphs[it]?.first()?.length ?: 0) + 1 } - 1
    Canvas(modifier.aspectRatio(columns / 4f).semantics { contentDescription = text }) {
        val px = size.width / columns
        var x = 0
        text.forEach { ch ->
            val rows = glyphs[ch] ?: return@forEach
            rows.forEachIndexed { y, row ->
                row.forEachIndexed { dx, cell ->
                    if (cell == '1') drawRect(color, Offset((x + dx) * px, y * px), Size(px + 0.5f, px + 0.5f))
                }
            }
            x += rows.first().length + 1
        }
    }
}

@Composable
fun TOnboarding(state: OnboardingState, actions: OnboardingActions) {
    val c = TerminalTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .systemBarsPadding()
            .padding(horizontal = 12.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            BlockLogo("HEXA PLAYER", Modifier.fillMaxWidth(0.8f).padding(top = 24.dp))
            TText("v${state.version} · local music, no tracking", Modifier.padding(top = 10.dp), color = c.dim)
            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TLogLine(TLog.Ok, "media3 engine ready")
                TLogLine(TLog.Ok, "lrclib lyrics source")
                if (state.hasAudio) TLogLine(TLog.Ok, "music and audio granted") else TLogLine(TLog.Waiting, "waiting for permissions")
            }
            TSectionBox("required", borderColor = c.accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        TText("music and audio")
                        TText("find and play songs on this phone", color = c.dim, maxLines = 2)
                    }
                    TButton(if (state.hasAudio) "granted" else "grant", actions::grantAudio, filled = !state.hasAudio, enabled = !state.hasAudio, large = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            TSectionBox("optional", titleColor = c.dim) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.asksNotifications) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                TText("notifications")
                                TText("playback controls, update alerts", color = c.dim, maxLines = 2)
                            }
                            TButton(if (state.hasNotifications) "allowed" else "allow", actions::allowNotifications,
                                enabled = !state.hasNotifications, large = true)
                        }
                        TDashedDivider()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            TText("library folder")
                            TText(
                                buildAnnotatedString {
                                    withStyle(SpanStyle(color = c.dim)) { append("/sdcard/") }
                                    withStyle(SpanStyle(color = c.accent2)) { append(state.folder.trim('/').ifEmpty { "(all audio)" }) }
                                }
                            )
                        }
                        TButton("change", actions::chooseFolder, large = true)
                    }
                }
            }
        }
        TText("nothing leaves your device. no account, no analytics.", Modifier.padding(top = 16.dp), color = c.dim, maxLines = 2)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TButton(":q quit", actions::quit, Modifier.weight(1f), large = true)
            TButton("start ⏎", actions::start, Modifier.weight(2f), filled = true, large = true, enabled = state.hasAudio)
        }
    }
}
