package com.example.hexaplayer.repository

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer

/**
 * Singleton that holds and persists all audio-effect state.
 *
 * Call [initEffects] once you have a valid [audioSessionId] (from [MusicService]).
 * The effects objects keep the native AudioFx alive as long as this singleton is alive.
 */
class EqualizerRepository private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: EqualizerRepository? = null

        fun getInstance(context: Context): EqualizerRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: EqualizerRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs = context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)

    // Live effect objects — kept alive in this singleton so the native side doesn't die
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    var isInitialized = false
        private set

    fun initEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return
        // Don't re-init if already attached to the same session
        if (isInitialized && equalizer != null) return
        releaseEffects()
        try {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = eqEnabled
                restoreBandLevels(this)
            }
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = bassEnabled
                if (strengthSupported) setStrength(bassStrength)
            }
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = virtualizerEnabled
                if (strengthSupported) setStrength(virtualizerStrength)
            }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = loudnessEnabled
                setTargetGain(loudnessGain)
            }
            presetReverb = PresetReverb(0, audioSessionId).apply {
                enabled = reverbEnabled
                preset = reverbPreset
            }
            isInitialized = true
        } catch (e: Exception) {
            isInitialized = false
        }
    }

    fun releaseEffects() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        runCatching { presetReverb?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        presetReverb = null
        isInitialized = false
    }

    fun getEqualizer(): Equalizer? = equalizer
    fun getBassBoost(): BassBoost? = bassBoost
    fun getVirtualizer(): Virtualizer? = virtualizer
    fun getLoudnessEnhancer(): LoudnessEnhancer? = loudnessEnhancer
    fun getPresetReverb(): PresetReverb? = presetReverb

    var eqEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(v) {
            prefs.edit().putBoolean("eq_enabled", v).apply()
            equalizer?.enabled = v
        }

    fun setBandLevel(band: Int, levelMillibels: Short) {
        prefs.edit().putInt("band_$band", levelMillibels.toInt()).apply()
        equalizer?.setBandLevel(band.toShort(), levelMillibels)
    }

    fun getBandLevel(band: Int): Short {
        return prefs.getInt("band_$band", Int.MIN_VALUE).let {
            if (it == Int.MIN_VALUE) equalizer?.getBandLevel(band.toShort()) ?: 0
            else it.toShort()
        }
    }

    fun applyDevicePreset(presetIndex: Short) {
        val eq = equalizer ?: return
        eq.usePreset(presetIndex)
        // Persist the resulting band levels so we can restore them later
        val edit = prefs.edit()
        for (band in 0 until eq.numberOfBands) {
            edit.putInt("band_$band", eq.getBandLevel(band.toShort()).toInt())
        }
        edit.apply()
    }

    fun resetBands() {
        val eq = equalizer ?: return
        val edit = prefs.edit()
        for (band in 0 until eq.numberOfBands) {
            eq.setBandLevel(band.toShort(), 0)
            edit.putInt("band_$band", 0)
        }
        edit.apply()
    }

    private fun restoreBandLevels(eq: Equalizer) {
        for (band in 0 until eq.numberOfBands) {
            val saved = prefs.getInt("band_$band", Int.MIN_VALUE)
            if (saved != Int.MIN_VALUE) eq.setBandLevel(band.toShort(), saved.toShort())
        }
    }

    var bassEnabled: Boolean
        get() = prefs.getBoolean("bass_enabled", false)
        set(v) {
            prefs.edit().putBoolean("bass_enabled", v).apply()
            bassBoost?.enabled = v
        }

    /** 0–1000 */
    var bassStrength: Short
        get() = prefs.getInt("bass_strength", 0).toShort()
        set(v) {
            prefs.edit().putInt("bass_strength", v.toInt()).apply()
            if (bassBoost?.strengthSupported == true) bassBoost?.setStrength(v)
        }

    var virtualizerEnabled: Boolean
        get() = prefs.getBoolean("virt_enabled", false)
        set(v) {
            prefs.edit().putBoolean("virt_enabled", v).apply()
            virtualizer?.enabled = v
        }

    /** 0–1000 */
    var virtualizerStrength: Short
        get() = prefs.getInt("virt_strength", 0).toShort()
        set(v) {
            prefs.edit().putInt("virt_strength", v.toInt()).apply()
            if (virtualizer?.strengthSupported == true) virtualizer?.setStrength(v)
        }

    var loudnessEnabled: Boolean
        get() = prefs.getBoolean("loud_enabled", false)
        set(v) {
            prefs.edit().putBoolean("loud_enabled", v).apply()
            loudnessEnhancer?.enabled = v
        }

    /** Target gain in millibels (e.g. 0 = no boost, 600 = +6 dB). */
    var loudnessGain: Int
        get() = prefs.getInt("loud_gain", 0)
        set(v) {
            prefs.edit().putInt("loud_gain", v).apply()
            loudnessEnhancer?.setTargetGain(v)
        }

    var reverbEnabled: Boolean
        get() = prefs.getBoolean("reverb_enabled", false)
        set(v) {
            prefs.edit().putBoolean("reverb_enabled", v).apply()
            presetReverb?.enabled = v
        }

    /**
     * PresetReverb preset index.
     * 0=None 1=SmallRoom 2=MediumRoom 3=LargeRoom 4=MediumHall 5=LargeHall 6=Plate
     */
    var reverbPreset: Short
        get() = prefs.getInt("reverb_preset", PresetReverb.PRESET_NONE.toInt()).toShort()
        set(v) {
            prefs.edit().putInt("reverb_preset", v.toInt()).apply()
            presetReverb?.preset = v
        }
}
