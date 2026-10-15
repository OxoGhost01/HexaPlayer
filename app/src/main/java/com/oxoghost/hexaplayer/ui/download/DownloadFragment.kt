package com.oxoghost.hexaplayer.ui.download

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.adapter.DownloadAdapter
import com.oxoghost.hexaplayer.adapter.SyncAdapter
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.databinding.FragmentDownloadBinding
import com.oxoghost.hexaplayer.util.Termux
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.DownloadViewModel
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadViewModel by activityViewModels()
    private val musicViewModel: MusicViewModel by activityViewModels()

    private lateinit var jobAdapter: DownloadAdapter
    private lateinit var syncAdapter: SyncAdapter

    private val requestWritePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) binding.bannerPermission.visibility = View.GONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupInputs()
        observeViewModel()
        ensureStoragePermission()
        if (!isHidden) viewModel.onScreenShown()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateSetupCard()
            viewModel.onScreenShown()
        }
    }

    override fun onResume() {
        super.onResume()
        updateSetupCard()
    }

    /** The card always gives access to the guide, so the setup command can be re-run to update. */
    private fun updateSetupCard() {
        val b = _binding ?: return
        val connected = viewModel.connected.value == true
        val outdated = connected && viewModel.serverOutdated.value == true
        val canStart = Termux.canStartServer(requireContext())
        b.tvSetupBody.setText(
            when {
                outdated -> R.string.dl_card_outdated
                connected -> R.string.dl_card_running
                canStart -> R.string.dl_card_stopped
                else -> R.string.dl_setup_body
            }
        )
        b.tvSetupBody.setTextColor(
            ContextCompat.getColor(requireContext(), if (outdated) R.color.colorFailure else R.color.colorTextSecondary)
        )
        b.btnStartServer.visibility = if (connected) View.GONE else View.VISIBLE
    }

    private fun setupRecyclerViews() {
        jobAdapter = DownloadAdapter(
            onStop = { job -> viewModel.stop(job) },
            onRetry = { job -> viewModel.retry(job) }
        )
        binding.rvJobs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvJobs.adapter = jobAdapter
        binding.rvJobs.isNestedScrollingEnabled = false

        syncAdapter = SyncAdapter { entry -> viewModel.resync(entry) }
        binding.rvSyncs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSyncs.adapter = syncAdapter
        binding.rvSyncs.isNestedScrollingEnabled = false
    }

    private fun setupInputs() {
        binding.etServerUrl.setText(viewModel.serverUrl)
        binding.etServerToken.setText(viewModel.serverToken)
        binding.etFolder.setText(viewModel.customFolder)
        binding.switchCustomFolder.isChecked = viewModel.useCustomFolder
        binding.tilFolder.visibility =
            if (viewModel.useCustomFolder) View.VISIBLE else View.GONE

        binding.etServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        }
        binding.etServerToken.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.serverToken = binding.etServerToken.text?.toString()?.trim() ?: ""
        }
        binding.etFolder.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) viewModel.customFolder = binding.etFolder.text?.toString()?.trim() ?: ""
        }

        binding.switchCustomFolder.setOnCheckedChangeListener { _, checked ->
            viewModel.useCustomFolder = checked
            binding.tilFolder.visibility = if (checked) View.VISIBLE else View.GONE
        }

        binding.btnTestConnection.setOnClickListener {
            saveServerFields()
            viewModel.testConnection()
        }

        binding.btnDownload.setOnClickListener { submit(JobKind.DOWNLOAD) }
        binding.btnSync.setOnClickListener { submit(JobKind.SYNC) }

        binding.btnClearDone.setOnClickListener { viewModel.clearFinished() }

        binding.btnGrantPermission.setOnClickListener {
            requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        binding.btnSetupGuide.setOnClickListener { openSetupGuide() }

        binding.btnStartServer.setOnClickListener {
            saveServerFields()
            if (Termux.canStartServer(requireContext())) viewModel.startServer()
            else openSetupGuide()
        }
    }

    private fun openSetupGuide() {
        val fm = requireActivity().supportFragmentManager
        if (fm.findFragmentByTag(TermuxSetupFragment.TAG) != null) return
        fm.beginTransaction()
            .setCustomAnimations(R.anim.slide_up, R.anim.slide_down, R.anim.slide_up, R.anim.slide_down)
            .add(android.R.id.content, TermuxSetupFragment(), TermuxSetupFragment.TAG)
            .addToBackStack(TermuxSetupFragment.TAG)
            .commit()
    }

    private fun saveServerFields() {
        viewModel.serverUrl = binding.etServerUrl.text?.toString()?.trim() ?: ""
        viewModel.serverToken = binding.etServerToken.text?.toString()?.trim() ?: ""
        viewModel.customFolder = binding.etFolder.text?.toString()?.trim() ?: ""
    }

    private fun submit(kind: JobKind) {
        saveServerFields()
        val link = binding.etLink.text?.toString()?.trim() ?: ""
        if (link.isEmpty()) {
            binding.tilLink.error = getString(R.string.dl_error_empty_link)
            return
        }
        binding.tilLink.error = null
        viewModel.submit(link, kind)
        binding.etLink.text?.clear()
        hideKeyboard()
    }

    private fun observeViewModel() {
        viewModel.jobs.observe(viewLifecycleOwner) { list ->
            jobAdapter.submitList(list)
            binding.sectionJobs.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.syncs.observe(viewLifecycleOwner) { list ->
            syncAdapter.submitList(list)
            binding.sectionSyncs.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.connectionStatus.observe(viewLifecycleOwner) { status ->
            binding.tvConnectionStatus.text = status ?: ""
        }

        viewModel.connected.observe(viewLifecycleOwner) { updateSetupCard() }
        viewModel.serverOutdated.observe(viewLifecycleOwner) { updateSetupCard() }

        viewModel.checkResult.observe(viewLifecycleOwner) { ok ->
            binding.tvTestResult.visibility = if (ok == null) View.GONE else View.VISIBLE
            if (ok != null) {
                binding.tvTestResult.setText(if (ok) R.string.dl_test_success else R.string.dl_test_failure)
                binding.tvTestResult.setTextColor(
                    ContextCompat.getColor(requireContext(), if (ok) R.color.colorSuccess else R.color.colorFailure)
                )
            }
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                requireContext().toast(msg)
                viewModel.clearToastMessage()
            }
        }

        viewModel.libraryChanged.observe(viewLifecycleOwner) { changed ->
            if (changed) {
                musicViewModel.loadLibrary()
                viewModel.consumeLibraryChanged()
            }
        }
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                binding.bannerPermission.visibility = View.VISIBLE
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
