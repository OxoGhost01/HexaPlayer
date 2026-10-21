package com.oxoghost.hexaplayer.ui.common

import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.repository.AudioInfo
import java.util.Locale

/** flac · 16 bit · 44.1 kHz (upper-case format for the material skin). */
fun AudioInfo.qualityLine(upper: Boolean = false): String = listOfNotNull(
    format.takeIf { it.isNotEmpty() }?.let { if (upper) it.uppercase(Locale.ROOT) else it },
    bitsPerSample.takeIf { it > 0 }?.let { "$it bit" },
    sampleRateHz.takeIf { it > 0 }?.let { "%s kHz".format(trimZero(it / 1000.0)) },
).joinToString(" · ")

/** Label and value pairs for File details. */
fun fileDetails(song: Song, info: AudioInfo?): List<Pair<String, String>> = listOfNotNull(
    "path" to "/sdcard/${song.relativePath}${song.displayName}",
    "format" to (info?.format ?: song.format).ifEmpty { "unknown" },
    info?.bitrateKbps?.takeIf { it > 0 }?.let { "bitrate" to "$it kbps" },
    info?.sampleRateHz?.takeIf { it > 0 }?.let { "sample rate" to "${trimZero(it / 1000.0)} kHz" },
    info?.bitsPerSample?.takeIf { it > 0 }?.let { "bit depth" to "$it bit" },
    info?.channels?.takeIf { it > 0 }?.let { "channels" to if (it == 1) "mono" else if (it == 2) "stereo" else "$it" },
    "size" to formatSize(song.size).ifEmpty { "unknown" },
    "length" to formatDuration(song.duration),
    song.dateAdded.takeIf { it > 0 }?.let { "added" to formatDay(it * 1000) },
)

/** low-tide-radio, for terminal paths. */
fun slug(text: String): String =
    text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').ifEmpty { "-" }

private fun trimZero(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(Locale.ROOT, value)
