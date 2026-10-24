package com.oxoghost.hexaplayer.ui.state

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.oxoghost.hexaplayer.repository.EqualizerRepository
import com.oxoghost.hexaplayer.repository.UserPreset
import com.oxoghost.hexaplayer.service.MusicService

@Immutable
data class EqBand(val label: String, val db: Float)

enum class EqPresetKind { Device, Curve, User }

@Immutable
data class EqPreset(val name: String, val kind: EqPresetKind, val index: Int)

/** Everything the equalizer screen shows. Strengths are 0..1, loudness in dB (0..15). */
@Immutable
data class EqualizerState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<EqBand> = emptyList(),
    val rangeDb: Float = 15f,
    val presets: List<EqPreset> = emptyList(),
    val presetName: String? = null,
    val bassEnabled: Boolean = false,
    val bass: Float = 0f,
    val virtualizerEnabled: Boolean = false,
    val virtualizer: Float = 0f,
    val loudnessEnabled: Boolean = false,
    val loudnessDb: Float = 0f,
    val reverb: Int = 0,
) {
    val reverbName: String get() = reverbNames[reverb.coerceIn(0, reverbNames.lastIndex)]

    companion object {
        val reverbNames = listOf("none", "small room", "medium room", "large room", "medium hall", "large hall", "plate")
        const val MAX_LOUDNESS_DB = 15f
    }
}

interface EqualizerActions {
    fun setEnabled(on: Boolean)
    fun setBand(index: Int, db: Float)
    fun applyPreset(preset: EqPreset)
    fun stepPreset(delta: Int)
    fun reset()
    fun savePreset(name: String)
    fun deletePreset(preset: EqPreset)
    fun setBass(on: Boolean)
    fun setBassStrength(value: Float)
    fun setVirtualizer(on: Boolean)
    fun setVirtualizerStrength(value: Float)
    fun setLoudness(on: Boolean)
    fun setLoudnessDb(value: Float)
    fun setReverb(index: Int)
}

/** Reads and writes the audio effects through [EqualizerRepository], keeping a snapshot for the UI. */
@Stable
class EqualizerModel(context: Context) : EqualizerActions {

    private val repo = EqualizerRepository.getInstance(context)
    var state by mutableStateOf(EqualizerState())
        private set

    init {
        repo.initEffects(MusicService.audioSessionId)
        refresh()
    }

    fun refresh() {
        if (!repo.isInitialized) repo.initEffects(MusicService.audioSessionId)
        val eq = repo.getEqualizer()
        val bands = eq?.let { e ->
            (0 until e.numberOfBands).map { b ->
                EqBand(frequencyLabel(e.getCenterFreq(b.toShort()) / 1000), repo.getBandLevel(b) / 100f)
            }
        }.orEmpty()
        val device = eq?.let { e -> (0 until e.numberOfPresets).map { EqPreset(e.getPresetName(it.toShort()), EqPresetKind.Device, it) } }.orEmpty()
        val curves = EqualizerRepository.CURVES.mapIndexed { i, (name, _) -> EqPreset(name, EqPresetKind.Curve, i) }
        val users = repo.userPresets.mapIndexed { i, p -> EqPreset(p.name, EqPresetKind.User, i) }
        state = EqualizerState(
            available = repo.isInitialized && eq != null,
            enabled = repo.eqEnabled,
            bands = bands,
            rangeDb = eq?.bandLevelRange?.let { maxOf(-it[0].toInt(), it[1].toInt()) / 100f } ?: 15f,
            presets = users + curves + device.filter { d -> curves.none { it.name.equals(d.name, ignoreCase = true) } },
            presetName = repo.presetName,
            bassEnabled = repo.bassEnabled,
            bass = repo.bassStrength / 1000f,
            virtualizerEnabled = repo.virtualizerEnabled,
            virtualizer = repo.virtualizerStrength / 1000f,
            loudnessEnabled = repo.loudnessEnabled,
            loudnessDb = repo.loudnessGain / 100f,
            reverb = if (repo.reverbEnabled) repo.reverbPreset.toInt() else 0,
        )
    }

    override fun setEnabled(on: Boolean) {
        repo.eqEnabled = on
        refresh()
    }

    override fun setBand(index: Int, db: Float) {
        val range = state.rangeDb
        repo.setBandLevel(index, (db.coerceIn(-range, range) * 100).toInt().toShort())
        repo.presetName = null
        refresh()
    }

    override fun applyPreset(preset: EqPreset) {
        when (preset.kind) {
            EqPresetKind.Device -> repo.applyDevicePreset(preset.index.toShort())
            EqPresetKind.Curve -> repo.applyCurve(EqualizerRepository.CURVES[preset.index].second)
            EqPresetKind.User -> repo.userPresets.getOrNull(preset.index)?.let(repo::applyUserPreset)
        }
        repo.presetName = preset.name
        refresh()
    }

    override fun stepPreset(delta: Int) {
        val presets = state.presets
        if (presets.isEmpty()) return
        val current = presets.indexOfFirst { it.name == state.presetName }
        val next = if (current < 0) (if (delta > 0) 0 else presets.lastIndex) else (current + delta).mod(presets.size)
        applyPreset(presets[next])
    }

    override fun reset() {
        repo.resetBands()
        repo.presetName = "Flat"
        refresh()
    }

    override fun savePreset(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val levels = state.bands.indices.map { repo.getBandLevel(it).toInt() }
        repo.userPresets = repo.userPresets.filterNot { it.name.equals(clean, ignoreCase = true) } + UserPreset(clean, levels)
        repo.presetName = clean
        refresh()
    }

    override fun deletePreset(preset: EqPreset) {
        if (preset.kind != EqPresetKind.User) return
        repo.userPresets = repo.userPresets.filterIndexed { i, _ -> i != preset.index }
        if (repo.presetName == preset.name) repo.presetName = null
        refresh()
    }

    override fun setBass(on: Boolean) {
        repo.bassEnabled = on
        refresh()
    }

    override fun setBassStrength(value: Float) {
        repo.bassStrength = (value.coerceIn(0f, 1f) * 1000).toInt().toShort()
        refresh()
    }

    override fun setVirtualizer(on: Boolean) {
        repo.virtualizerEnabled = on
        refresh()
    }

    override fun setVirtualizerStrength(value: Float) {
        repo.virtualizerStrength = (value.coerceIn(0f, 1f) * 1000).toInt().toShort()
        refresh()
    }

    override fun setLoudness(on: Boolean) {
        repo.loudnessEnabled = on
        refresh()
    }

    override fun setLoudnessDb(value: Float) {
        repo.loudnessGain = (value.coerceIn(0f, EqualizerState.MAX_LOUDNESS_DB) * 100).toInt()
        refresh()
    }

    /** 0 turns reverb off, as 1.x did with its switch. */
    override fun setReverb(index: Int) {
        val i = index.mod(EqualizerState.reverbNames.size)
        repo.reverbEnabled = i != 0
        if (i != 0) repo.reverbPreset = i.toShort()
        refresh()
    }

    private fun frequencyLabel(hz: Int): String = if (hz >= 1000) "${hz / 1000}k" else "$hz"
}

@Composable
fun rememberEqualizerModel(): EqualizerModel {
    val context = LocalContext.current
    return remember { EqualizerModel(context.applicationContext) }
}
