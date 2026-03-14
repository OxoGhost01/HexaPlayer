package com.oxoghost.hexaplayer.ui.equalizer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.databinding.FragmentEqualizerBinding
import com.oxoghost.hexaplayer.repository.EqualizerRepository
import com.oxoghost.hexaplayer.service.MusicService
import com.oxoghost.hexaplayer.util.dpToPx
import kotlin.math.abs

class EqualizerFragment : Fragment() {

    private var _binding: FragmentEqualizerBinding? = null
    private val binding get() = _binding!!

    private val repo by lazy { EqualizerRepository.getInstance(requireContext()) }

    /** True while we're programmatically updating the EQ preset spinner to avoid feedback loops. */
    private var ignorePresetSelection = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEqualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo.initEffects(MusicService.audioSessionId)

        if (!repo.isInitialized) {
            binding.tvEqUnavailable.visibility = View.VISIBLE
        }

        setupEqualizer()
        setupBassBoost()
        setupVirtualizer()
        setupLoudnessEnhancer()
        setupReverb()
    }

    override fun onResume() {
        super.onResume()
        // Re-init if the service restarted and gave us a new session
        if (!repo.isInitialized && MusicService.audioSessionId != 0) {
            repo.initEffects(MusicService.audioSessionId)
            if (repo.isInitialized) {
                binding.tvEqUnavailable.visibility = View.GONE
                setupEqualizer()
            }
        }
    }

    // Custom presets: gains in dB for 10 standard bands
    // Bands: ~32 64 125 250 500 1k 2k 4k 8k 16k Hz
    private data class CustomPreset(val name: String, val gainsDb: FloatArray)
    private val customPresets = listOf(
        CustomPreset("Flat",        floatArrayOf( 0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f,  0f)),
        CustomPreset("Bass Boost",  floatArrayOf( 6f,  5f,  4f,  2f,  0f,  0f,  0f,  0f,  0f,  0f)),
        CustomPreset("Treble Boost",floatArrayOf( 0f,  0f,  0f,  0f,  0f,  0f,  2f,  4f,  5f,  6f)),
        CustomPreset("Rock",        floatArrayOf( 4f,  3f,  2f,  0f, -1f,  0f,  0f,  2f,  3f,  4f)),
        CustomPreset("Pop",         floatArrayOf(-2f, -1f,  0f,  2f,  4f,  4f,  2f,  0f, -1f, -1f)),
        CustomPreset("Electronic",  floatArrayOf( 4f,  4f,  1f, -2f, -1f,  2f,  1f,  2f,  4f,  5f)),
        CustomPreset("Hip-Hop",     floatArrayOf( 5f,  4f,  1f, -1f, -1f, -1f,  1f,  2f,  3f,  4f)),
        CustomPreset("Jazz",        floatArrayOf( 0f,  0f,  0f,  2f,  4f,  4f,  3f,  2f,  2f,  3f)),
        CustomPreset("Classical",   floatArrayOf( 4f,  3f,  2f,  0f,  0f, -1f,  0f,  2f,  3f,  4f)),
        CustomPreset("R&B",         floatArrayOf( 6f,  4f,  0f, -2f, -2f,  2f,  2f,  3f,  4f,  5f)),
        CustomPreset("Acoustic",    floatArrayOf( 4f,  3f,  2f,  1f,  2f,  3f,  3f,  2f,  2f,  2f)),
        CustomPreset("Dance",       floatArrayOf( 5f,  4f,  1f,  0f,  2f,  3f,  4f,  3f,  1f,  0f)),
        CustomPreset("Lounge",      floatArrayOf(-1f, -1f,  0f,  2f,  3f,  3f,  2f,  1f,  1f,  1f)),
        CustomPreset("Metal",       floatArrayOf( 5f,  4f, -1f, -2f,  0f,  3f,  4f,  5f,  5f,  5f)),
        CustomPreset("Piano",       floatArrayOf( 0f,  0f,  0f,  3f,  4f,  5f,  3f,  4f,  5f,  3f)),
        CustomPreset("Vocal Boost", floatArrayOf(-2f, -2f,  0f,  3f,  5f,  5f,  4f,  2f,  0f, -1f)),
        CustomPreset("Late Night",  floatArrayOf( 3f,  2f,  0f, -1f, -2f, -2f,  0f,  2f,  4f,  5f)),
    )

    private fun applyCustomPreset(eq: android.media.audiofx.Equalizer, gainsDb: FloatArray) {
        val stdFreqsHz = intArrayOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val bandRange  = eq.bandLevelRange
        val minMb      = bandRange[0].toInt()
        val maxMb      = bandRange[1].toInt()
        for (band in 0 until eq.numberOfBands) {
            val centerHz = eq.getCenterFreq(band.toShort()) / 1000
            val stdIdx   = stdFreqsHz.indices.minByOrNull { kotlin.math.abs(stdFreqsHz[it] - centerHz) } ?: continue
            val gainDb   = if (stdIdx < gainsDb.size) gainsDb[stdIdx] else 0f
            val levelMb  = (gainDb * 100).toInt().coerceIn(minMb, maxMb).toShort()
            repo.setBandLevel(band, levelMb)
        }
    }

    private fun setupEqualizer() {
        val eq = repo.getEqualizer()

        binding.switchEq.isChecked = repo.eqEnabled
        binding.switchEq.setOnCheckedChangeListener { _, checked -> repo.eqEnabled = checked }

        if (eq == null) return

        // Build preset spinner: device presets + custom presets + "Custom"
        val numDevicePresets = eq.numberOfPresets.toInt()
        val deviceNames  = (0 until numDevicePresets).map { eq.getPresetName(it.toShort()) }
        val customNames  = customPresets.map { it.name }
        val allNames     = deviceNames + customNames + listOf(getString(R.string.eq_preset_custom))
        val totalPresets = allNames.size - 1  // everything except last "Custom" entry

        val presetAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, allNames)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        ignorePresetSelection = true
        binding.spinnerEqPreset.adapter = presetAdapter
        binding.spinnerEqPreset.setSelection(allNames.size - 1)  // "Custom"
        ignorePresetSelection = false

        binding.spinnerEqPreset.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (ignorePresetSelection) return
                when {
                    pos < numDevicePresets -> {
                        repo.applyDevicePreset(pos.toShort())
                        refreshBandSeekBars()
                    }
                    pos < totalPresets -> {
                        val customIdx = pos - numDevicePresets
                        applyCustomPreset(eq, customPresets[customIdx].gainsDb)
                        refreshBandSeekBars()
                    }
                    // last item = "Custom" — user adjusted manually, nothing to do
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.btnEqReset.setOnClickListener {
            repo.resetBands()
            refreshBandSeekBars()
            ignorePresetSelection = true
            binding.spinnerEqPreset.setSelection(allNames.size - 1)  // "Custom"
            ignorePresetSelection = false
        }

        buildBandRows(eq)
    }

    private fun buildBandRows(eq: android.media.audiofx.Equalizer) {
        val container = binding.eqBandsContainer
        container.removeAllViews()

        val bandRange = eq.bandLevelRange // [minMillibels, maxMillibels], e.g. [-1500, 1500]
        val minMb = bandRange[0].toInt()
        val maxMb = bandRange[1].toInt()
        val rangeMb = maxMb - minMb

        val accentColor = ContextCompat.getColor(requireContext(), R.color.colorPrimary)
        val dividerColor = ContextCompat.getColor(requireContext(), R.color.colorDivider)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.colorTextSecondary)

        for (band in 0 until eq.numberOfBands) {
            val freqHz = eq.getCenterFreq(band.toShort()) / 1000 // millihertz → Hz
            val freqLabel = formatFreq(freqHz)
            val currentLevel = repo.getBandLevel(band)

            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 4.dpToPx(requireContext()) }
            }

            val freqText = TextView(requireContext()).apply {
                text = freqLabel
                textSize = 12f
                setTextColor(textSecondary)
                layoutParams = LinearLayout.LayoutParams(72.dpToPx(requireContext()), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            val seekBar = SeekBar(requireContext()).apply {
                max = rangeMb
                progress = currentLevel - minMb
                progressTintList = android.content.res.ColorStateList.valueOf(accentColor)
                thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(dividerColor)
                tag = band
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val dbText = TextView(requireContext()).apply {
                text = formatDb(currentLevel.toInt())
                textSize = 12f
                setTextColor(accentColor)
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(54.dpToPx(requireContext()), LinearLayout.LayoutParams.WRAP_CONTENT)
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val levelMb = (progress + minMb).toShort()
                        repo.setBandLevel(band, levelMb)
                        dbText.text = formatDb(levelMb.toInt())
                        // Switch preset to "Custom"
                        val numPresets = eq.numberOfPresets.toInt()
                        ignorePresetSelection = true
                        binding.spinnerEqPreset.setSelection(numPresets)
                        ignorePresetSelection = false
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })

            row.addView(freqText)
            row.addView(seekBar)
            row.addView(dbText)
            container.addView(row)
        }
    }

    private fun refreshBandSeekBars() {
        val eq = repo.getEqualizer() ?: return
        val bandRange = eq.bandLevelRange
        val minMb = bandRange[0].toInt()
        val container = binding.eqBandsContainer

        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as? LinearLayout ?: continue
            val seekBar = row.getChildAt(1) as? SeekBar ?: continue
            val dbText = row.getChildAt(2) as? TextView ?: continue
            val band = i
            val levelMb = repo.getBandLevel(band)
            seekBar.progress = levelMb - minMb
            dbText.text = formatDb(levelMb.toInt())
        }
    }

    private fun formatFreq(hz: Int): String = when {
        hz >= 1000 -> "${hz / 1000}kHz"
        else -> "${hz}Hz"
    }

    private fun formatDb(millibels: Int): String {
        val db = millibels / 100.0
        return if (db >= 0) "+%.1f".format(db) else "%.1f".format(db)
    }

    private fun setupBassBoost() {
        binding.switchBass.isChecked = repo.bassEnabled
        binding.switchBass.setOnCheckedChangeListener { _, checked -> repo.bassEnabled = checked }

        binding.seekBass.progress = repo.bassStrength.toInt()
        binding.tvBassVal.text = formatStrength(repo.bassStrength.toInt())

        binding.seekBass.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    repo.bassStrength = value.toShort()
                    binding.tvBassVal.text = formatStrength(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupVirtualizer() {
        binding.switchVirtualizer.isChecked = repo.virtualizerEnabled
        binding.switchVirtualizer.setOnCheckedChangeListener { _, checked ->
            repo.virtualizerEnabled = checked
        }

        binding.seekVirtualizer.progress = repo.virtualizerStrength.toInt()
        binding.tvVirtVal.text = formatStrength(repo.virtualizerStrength.toInt())

        binding.seekVirtualizer.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    repo.virtualizerStrength = value.toShort()
                    binding.tvVirtVal.text = formatStrength(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupLoudnessEnhancer() {
        binding.switchLoudness.isChecked = repo.loudnessEnabled
        binding.switchLoudness.setOnCheckedChangeListener { _, checked ->
            repo.loudnessEnabled = checked
        }

        binding.seekLoudness.progress = repo.loudnessGain
        binding.tvLoudVal.text = formatDb(repo.loudnessGain)

        binding.seekLoudness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    repo.loudnessGain = value
                    binding.tvLoudVal.text = formatDb(value)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupReverb() {
        binding.switchReverb.isChecked = repo.reverbEnabled
        binding.switchReverb.setOnCheckedChangeListener { _, checked ->
            repo.reverbEnabled = checked
        }

        val presets = resources.getStringArray(R.array.reverb_presets)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, presets)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerReverb.adapter = adapter
        binding.spinnerReverb.setSelection(repo.reverbPreset.toInt().coerceIn(0, presets.size - 1))

        binding.spinnerReverb.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                repo.reverbPreset = pos.toShort()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Formats a 0–1000 strength to a percentage string. */
    private fun formatStrength(value: Int): String = "${value / 10}%"

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
