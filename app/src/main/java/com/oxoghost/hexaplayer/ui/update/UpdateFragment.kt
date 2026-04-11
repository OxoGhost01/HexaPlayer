package com.oxoghost.hexaplayer.ui.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.databinding.FragmentUpdateBinding
import com.oxoghost.hexaplayer.repository.UpdateInfo
import com.oxoghost.hexaplayer.ui.MainActivity

class UpdateFragment : Fragment() {

    private var _binding: FragmentUpdateBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        val version = args.getString(ARG_VERSION, "")
        val downloadUrl = args.getString(ARG_DOWNLOAD_URL, "")
        val changelog = args.getString(ARG_CHANGELOG, "")

        // Version label
        binding.tvUpdateVersion.text = getString(R.string.update_version_label, version)

        // Changelog — convert basic markdown to readable plain text
        binding.tvUpdateChangelog.text = formatChangelog(changelog)

        // Update button label depends on whether a direct APK is available
        val isApk = downloadUrl.endsWith(".apk", ignoreCase = true)
        binding.btnUpdate.text = getString(
            if (isApk) R.string.update_install else R.string.update_download
        )

        binding.btnUpdate.setOnClickListener {
            val activity = requireActivity() as? MainActivity ?: return@setOnClickListener
            activity.startUpdate(UpdateInfo(version, downloadUrl, changelog))
            dismiss()
        }

        binding.btnNotNow.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun dismiss() {
        parentFragmentManager.popBackStack()
    }

    /**
     * Converts a GitHub release body (markdown) to a readable plain text string.
     * Strips heading markers, bold/italic markers, and converts list dashes to bullets.
     */
    private fun formatChangelog(raw: String): String {
        if (raw.isBlank()) return getString(R.string.update_no_changelog)
        return raw
            .replace(Regex("#{1,6}\\s+(.+)")) { mr -> "\n${mr.groupValues[1].uppercase()}\n" }
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            .replace(Regex("\\*(.+?)\\*"), "$1")
            .replace(Regex("^- ", RegexOption.MULTILINE), "• ")
            .replace(Regex("^\\* ", RegexOption.MULTILINE), "• ")
            .replace(Regex("\\[(.+?)\\]\\(.+?\\)"), "$1")  // [text](url) → text
            .replace(Regex("\r\n"), "\n")
            .trim()
    }

    companion object {
        private const val ARG_VERSION = "version"
        private const val ARG_DOWNLOAD_URL = "download_url"
        private const val ARG_CHANGELOG = "changelog"

        fun newInstance(info: UpdateInfo) = UpdateFragment().apply {
            arguments = bundleOf(
                ARG_VERSION to info.latestVersion,
                ARG_DOWNLOAD_URL to info.downloadUrl,
                ARG_CHANGELOG to info.changelog
            )
        }
    }
}
