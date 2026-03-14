package com.oxoghost.hexaplayer.ui.download

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.adapter.DownloadAdapter
import com.oxoghost.hexaplayer.adapter.JamendoAdapter
import com.oxoghost.hexaplayer.databinding.FragmentDownloadBinding
import com.oxoghost.hexaplayer.viewmodel.DownloadViewModel
import com.google.android.material.snackbar.Snackbar

class DownloadFragment : Fragment() {

    private var _binding: FragmentDownloadBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadViewModel by activityViewModels()
    private lateinit var jamendoAdapter: JamendoAdapter
    private lateinit var downloadAdapter: DownloadAdapter

    private val pickFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val path = uriToPath(uri) ?: uri.toString()
            viewModel.downloadFolder = path
            binding.tvFolderPath.text = path
        }
    }

    private val requestWritePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled at download time */ }

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
    }

    private fun setupRecyclerViews() {
        jamendoAdapter = JamendoAdapter { track ->
            viewModel.downloadTrack(track)
        }
        binding.rvResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvResults.adapter = jamendoAdapter

        downloadAdapter = DownloadAdapter { item -> viewModel.cancelDownload(item) }
        binding.rvDownloads.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloads.adapter = downloadAdapter
    }

    private fun setupInputs() {
        binding.tvFolderPath.text = viewModel.downloadFolder

        // Restore saved client ID
        binding.etClientId.setText(viewModel.jamendoClientId)
        binding.etClientId.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.jamendoClientId = binding.etClientId.text?.toString()?.trim() ?: ""
            }
        }

        binding.btnPickFolder.setOnClickListener {
            pickFolder.launch(null)
        }

        binding.btnSearch.setOnClickListener { startSearch() }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                startSearch()
                true
            } else false
        }

        binding.btnClearDone.setOnClickListener {
            viewModel.clearFinished()
        }
    }

    private fun startSearch() {
        // Save client ID before searching
        viewModel.jamendoClientId = binding.etClientId.text?.toString()?.trim() ?: ""

        val query = binding.etSearch.text?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            binding.tilSearch.error = getString(R.string.dl_error_empty_query)
            return
        }
        binding.tilSearch.error = null
        viewModel.search(query)
        hideKeyboard()
    }

    private fun observeViewModel() {
        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            jamendoAdapter.submitList(results)
            updateSections(results.size, viewModel.downloads.value?.size ?: 0)
            if (results.isNotEmpty()) {
                binding.tvResultsHeader.text = getString(R.string.dl_results_header, results.size)
            }
        }

        viewModel.isSearching.observe(viewLifecycleOwner) { searching ->
            binding.progressSearch.visibility = if (searching) View.VISIBLE else View.GONE
            binding.btnSearch.isEnabled = !searching
            if (searching) {
                binding.tvEmpty.visibility = View.GONE
                binding.scrollContent.visibility = View.GONE
            } else {
                updateSections(
                    viewModel.searchResults.value?.size ?: 0,
                    viewModel.downloads.value?.size ?: 0
                )
            }
        }

        viewModel.downloads.observe(viewLifecycleOwner) { list ->
            downloadAdapter.submitList(list.toList())
            updateSections(viewModel.searchResults.value?.size ?: 0, list.size)
        }

        viewModel.toastMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
                viewModel.clearToastMessage()
            }
        }
    }

    private fun updateSections(resultCount: Int, downloadCount: Int) {
        val hasResults = resultCount > 0
        val hasDownloads = downloadCount > 0
        val hasContent = hasResults || hasDownloads

        binding.sectionResults.visibility = if (hasResults) View.VISIBLE else View.GONE
        binding.sectionDownloads.visibility = if (hasDownloads) View.VISIBLE else View.GONE
        binding.dividerSections.visibility =
            if (hasResults && hasDownloads) View.VISIBLE else View.GONE

        val isSearching = viewModel.isSearching.value == true
        binding.scrollContent.visibility = if (hasContent && !isSearching) View.VISIBLE else View.GONE
        binding.tvEmpty.visibility =
            if (!hasContent && !isSearching) View.VISIBLE else View.GONE
    }

    private fun ensureStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestWritePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                binding.bannerPermission.visibility = View.VISIBLE
            }
        }
    }

    private fun uriToPath(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val parts = docId.split(":")
            if (parts.getOrNull(0) == "primary") {
                val sub = parts.getOrNull(1) ?: ""
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (sub.isEmpty()) base else "$base/$sub"
            } else null
        } catch (e: Exception) {
            null
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
