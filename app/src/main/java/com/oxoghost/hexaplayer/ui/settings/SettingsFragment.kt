package com.oxoghost.hexaplayer.ui.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.oxoghost.hexaplayer.BuildConfig
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.databinding.FragmentSettingsBinding
import com.oxoghost.hexaplayer.repository.UpdateRepository
import com.oxoghost.hexaplayer.util.dpToPx
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: com.oxoghost.hexaplayer.viewmodel.MusicViewModel by activityViewModels()

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
        setupDonateButton()
        setupAccentColors()
        setupMusicFolder()
        setupLibrarySettings()
        setupPlaybackSettings()
        setupUpdateCheck()
    }

    private fun setupDonateButton() {
        binding.btnDonate.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/sponsors/OxoGhost01"))
            startActivity(intent)
        }
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
        binding.btnScan.isEnabled = false
        viewModel.loadLibrary()
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (!loading) {
                binding.btnScan.isEnabled = true
                Snackbar.make(requireView(), getString(R.string.settings_scan_done), Snackbar.LENGTH_SHORT).show()
                viewModel.isLoading.removeObservers(viewLifecycleOwner)
            }
        }
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

    private fun setupUpdateCheck() {
        binding.tvVersion.text = "Version ${BuildConfig.VERSION_NAME}"
        binding.btnCheckUpdates.setOnClickListener { checkForUpdates() }
    }

    private fun checkForUpdates() {
        binding.btnCheckUpdates.isEnabled = false
        binding.tvUpdateStatus.visibility = View.VISIBLE
        binding.tvUpdateStatus.text = getString(R.string.update_checking)

        lifecycleScope.launch(Dispatchers.IO) {
            val info = UpdateRepository().checkForUpdate()
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnCheckUpdates.isEnabled = true
                if (info == null) {
                    binding.tvUpdateStatus.text = getString(R.string.update_error)
                    return@withContext
                }
                if (UpdateRepository.isNewerVersion(BuildConfig.VERSION_NAME, info.latestVersion)) {
                    binding.tvUpdateStatus.text =
                        getString(R.string.update_available_label, info.latestVersion)
                    (requireActivity() as? com.oxoghost.hexaplayer.ui.MainActivity)
                        ?.showUpdateScreen(info)
                } else {
                    binding.tvUpdateStatus.text = getString(R.string.update_up_to_date)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
