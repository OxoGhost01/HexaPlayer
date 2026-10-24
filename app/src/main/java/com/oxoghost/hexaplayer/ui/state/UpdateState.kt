package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Immutable
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.ui.app.UpdatePhase
import com.oxoghost.hexaplayer.ui.app.Updater
import com.oxoghost.hexaplayer.ui.common.formatSize
import java.time.Duration
import java.time.Instant

enum class ChangeKind { Added, Changed, Fixed }

@Immutable
data class ChangeLine(val kind: ChangeKind, val text: String)

@Immutable
data class UpdateState(
    val current: String,
    val latest: String,
    val size: String,
    val released: String,
    val changes: List<ChangeLine>,
    val phase: UpdatePhase,
    val fraction: Float,
    val progress: String,
)

interface UpdateActions {
    fun download()
    fun install()
    fun dismiss()
    fun openRelease()
}

/** Bullet lines of a GitHub release body, the kind guessed from the first word. */
fun changelogLines(body: String, max: Int = 5): List<ChangeLine> = body.lines()
    .map { it.trim() }
    .filter { it.startsWith("- ") || it.startsWith("* ") }
    .map { line ->
        val text = line.drop(2)
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")
            .trim()
        val first = text.substringBefore(' ').lowercase()
        val kind = when {
            first.startsWith("add") || first.startsWith("new") -> ChangeKind.Added
            first.startsWith("fix") || first.startsWith("remov") -> ChangeKind.Fixed
            else -> ChangeKind.Changed
        }
        ChangeLine(kind, text)
    }
    .take(max)

/** released today, released 2 days ago. */
fun releasedAgo(iso: String): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return ""
    val days = Duration.between(instant, Instant.now()).toDays()
    return when {
        days <= 0 -> "released today"
        days == 1L -> "released yesterday"
        else -> "released $days days ago"
    }
}

fun Updater.toState(): UpdateState? {
    val update = info ?: return null
    val total = totalBytes.takeIf { it > 0 } ?: update.sizeBytes
    return UpdateState(
        current = BuildConfig.VERSION_NAME,
        latest = update.latestVersion,
        size = formatSize(total),
        released = releasedAgo(update.publishedAt),
        changes = changelogLines(update.changelog),
        phase = phase,
        fraction = if (total > 0) downloadedBytes.toFloat() / total else 0f,
        progress = if (total > 0) "%.1f / %.1f MB".format(downloadedBytes / 1_048_576.0, total / 1_048_576.0) else "",
    )
}
