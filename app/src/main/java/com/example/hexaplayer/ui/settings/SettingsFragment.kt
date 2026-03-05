package com.example.hexaplayer.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.hexaplayer.R
import com.example.hexaplayer.databinding.FragmentSettingsBinding
import com.example.hexaplayer.util.dpToPx
import com.google.android.material.snackbar.Snackbar

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: com.example.hexaplayer.viewmodel.MusicViewModel by activityViewModels()

    private val accentColors = intArrayOf(
        R.color.accent_purple_primary,
        R.color.accent_deep_purple_primary,
        R.color.accent_blue_primary,
        R.color.accent_teal_primary,
        R.color.accent_pink_primary,
        R.color.accent_orange_primary,
    )
    private val accentNames = intArrayOf(
        R.string.accent_purple,
        R.string.accent_deep_purple,
        R.string.accent_blue,
        R.string.accent_teal,
        R.string.accent_pink,
        R.string.accent_orange,
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAccentColors()
        setupMusicFolder()
        setupLibrarySettings()
        setupPlaybackSettings()
    }

    private fun setupAccentColors() {
        val container = binding.accentContainer
        val selectedIndex = viewModel.accentIndex
        val size = 52.dpToPx(requireContext())
        val margin = 6.dpToPx(requireContext())
        val strokeWidth = 3.dpToPx(requireContext())

        updateAccentNameLabel(selectedIndex)

        accentColors.forEachIndexed { index, colorRes ->
            val colorInt = ContextCompat.getColor(requireContext(), colorRes)

            val outerCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
                if (index == selectedIndex) {
                    setStroke(strokeWidth, Color.WHITE)
                }
            }

            val circleView = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                background = outerCircle
                setOnClickListener { onAccentSelected(index) }
            }
            container.addView(circleView)
        }
    }

    private fun onAccentSelected(index: Int) {
        if (viewModel.accentIndex == index) return
        viewModel.accentIndex = index
        requireActivity().recreate()
    }

    private fun updateAccentNameLabel(index: Int) {
        binding.tvAccentName.text = getString(accentNames[index])
    }

    private fun setupMusicFolder() {
        binding.etFolder.setText(viewModel.musicFolder)

        binding.etFolder.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAndRescan()
                true
            } else false
        }

        binding.btnScan.setOnClickListener { saveAndRescan() }
    }

    private fun saveAndRescan() {
        viewModel.musicFolder = binding.etFolder.text?.toString()?.trim() ?: ""
        viewModel.loadLibrary()
        Snackbar.make(requireView(), getString(R.string.settings_scan_done), Snackbar.LENGTH_SHORT).show()
    }

    private fun setupLibrarySettings() {
        val sortOptions = resources.getStringArray(R.array.sort_options)
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.spinnerSort.adapter = adapter
        binding.spinnerSort.setSelection(viewModel.sortOrder)
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (viewModel.sortOrder != pos) {
                    viewModel.sortOrder = pos
                    viewModel.loadLibrary()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.switchShowUnknown.isChecked = viewModel.showUnknownSongs
        binding.switchShowUnknown.setOnCheckedChangeListener { _, checked ->
            viewModel.showUnknownSongs = checked
            viewModel.loadLibrary()
        }
    }

    private fun setupPlaybackSettings() {
        binding.switchGapless.isChecked = viewModel.gaplessPlayback
        binding.switchGapless.setOnCheckedChangeListener { _, checked ->
            viewModel.gaplessPlayback = checked
        }

        binding.switchSkipSilence.isChecked = viewModel.skipSilence
        binding.switchSkipSilence.setOnCheckedChangeListener { _, checked ->
            viewModel.skipSilence = checked
        }

        binding.switchResumeOnTrackChange.isChecked = viewModel.resumeOnTrackChange
        binding.switchResumeOnTrackChange.setOnCheckedChangeListener { _, checked ->
            viewModel.resumeOnTrackChange = checked
        }

        updateCrossfadeLabel(viewModel.crossfadeTenths)
        binding.seekCrossfade.progress = viewModel.crossfadeTenths
        binding.seekCrossfade.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, value: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateCrossfadeLabel(value)
                    viewModel.crossfadeTenths = value
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun updateCrossfadeLabel(tenths: Int) {
        binding.tvCrossfadeVal.text = if (tenths == 0) {
            getString(R.string.settings_crossfade_off)
        } else {
            val secs = tenths / 10.0
            "%.1fs".format(secs)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
