@file:Suppress("DEPRECATION")
package com.oxoghost.hexaplayer.repository

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** A preset saved by the user: one level per device band, in millibels. */
data class UserPreset(val name: String, val levelsMb: List<Int>)

/**
 * Singleton that holds and persists all audio-effect state.
 *
 * Call [initEffects] once you have a valid [audioSessionId] (from [MusicService]).
 * The effects objects keep the native AudioFx alive as long as this singleton is alive.
 */
class EqualizerRepository private constructor(private val context: Context) {

    companion object {
        /** Centre frequencies of the 1.x curves below. */
        val CURVE_FREQS = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        /** The 1.x presets, in dB for each of [CURVE_FREQS]. */
        val CURVES: List<Pair<String, FloatArray>> = listOf(
            "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass Boost" to floatArrayOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Treble Boost" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 2f, 4f, 5f, 6f),
            "Rock" to floatArrayOf(4f, 3f, 2f, 0f, -1f, 0f, 0f, 2f, 3f, 4f),
            "Pop" to floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1f),
            "Electronic" to floatArrayOf(4f, 4f, 1f, -2f, -1f, 2f, 1f, 2f, 4f, 5f),
            "Hip-Hop" to floatArrayOf(5f, 4f, 1f, -1f, -1f, -1f, 1f, 2f, 3f, 4f),
            "Jazz" to floatArrayOf(0f, 0f, 0f, 2f, 4f, 4f, 3f, 2f, 2f, 3f),
            "Classical" to floatArrayOf(4f, 3f, 2f, 0f, 0f, -1f, 0f, 2f, 3f, 4f),
            "R&B" to floatArrayOf(6f, 4f, 0f, -2f, -2f, 2f, 2f, 3f, 4f, 5f),
            "Acoustic" to floatArrayOf(4f, 3f, 2f, 1f, 2f, 3f, 3f, 2f, 2f, 2f),
            "Dance" to floatArrayOf(5f, 4f, 1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f),
            "Lounge" to floatArrayOf(-1f, -1f, 0f, 2f, 3f, 3f, 2f, 1f, 1f, 1f),
            "Metal" to floatArrayOf(5f, 4f, -1f, -2f, 0f, 3f, 4f, 5f, 5f, 5f),
            "Piano" to floatArrayOf(0f, 0f, 0f, 3f, 4f, 5f, 3f, 4f, 5f, 3f),
            "Vocal Boost" to floatArrayOf(-2f, -2f, 0f, 3f, 5f, 5f, 4f, 2f, 0f, -1f),
            "Late Night" to floatArrayOf(3f, 2f, 0f, -1f, -2f, -2f, 0f, 2f, 4f, 5f),
        )

        @Volatile
        private var INSTANCE: EqualizerRepository? = null

        fun getInstance(context: Context): EqualizerRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: EqualizerRepository(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val prefs = context.getSharedPreferences("eq_prefs", Context.MODE_PRIVATE)

    // Live effect objects, kept alive in this singleton so the native side doesn't die
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var presetReverb: PresetReverb? = null

    var isInitialized = false
        private set
    private var session = 0

    fun initEffects(audioSessionId: Int) {
        if (audioSessionId == 0) return
        // Don't re-init if already attached to the same session
        if (isInitialized && equalizer != null && audioSessionId == session) return
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
            session = audioSessionId
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

    /** Name of the preset the bands came from, or null once a band is moved by hand. */
    var presetName: String?
        get() = prefs.getString("preset_name", null)
        set(v) { prefs.edit().putString("preset_name", v).apply() }

    /** Applies one of the 1.x curves (gains in dB for 32 Hz .. 16 kHz) to the nearest device bands. */
    fun applyCurve(gainsDb: FloatArray) {
        val eq = equalizer ?: return
        val (minMb, maxMb) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
        for (band in 0 until eq.numberOfBands) {
            val hz = eq.getCenterFreq(band.toShort()) / 1000
            val nearest = CURVE_FREQS.indices.minByOrNull { abs(CURVE_FREQS[it] - hz) } ?: continue
            setBandLevel(band, (gainsDb.getOrElse(nearest) { 0f } * 100).toInt().coerceIn(minMb, maxMb).toShort())
        }
    }

    var userPresets: List<UserPreset>
        get() = runCatching {
            val arr = JSONArray(prefs.getString("user_presets", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val levels = o.getJSONArray("levels")
                UserPreset(o.getString("name"), (0 until levels.length()).map { levels.getInt(it) })
            }
        }.getOrDefault(emptyList())
        set(list) {
            val arr = JSONArray()
            list.forEach { arr.put(JSONObject().put("name", it.name).put("levels", JSONArray(it.levelsMb))) }
            prefs.edit().putString("user_presets", arr.toString()).apply()
        }

    fun applyUserPreset(preset: UserPreset) {
        preset.levelsMb.forEachIndexed { band, level -> setBandLevel(band, level.toShort()) }
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
