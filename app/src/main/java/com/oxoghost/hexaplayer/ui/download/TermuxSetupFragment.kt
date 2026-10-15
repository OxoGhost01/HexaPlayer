package com.oxoghost.hexaplayer.ui.download

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.databinding.FragmentTermuxSetupBinding
import com.oxoghost.hexaplayer.util.Termux
import com.oxoghost.hexaplayer.util.toast
import com.oxoghost.hexaplayer.viewmodel.DownloadViewModel

class TermuxSetupFragment : Fragment() {

    private var _binding: FragmentTermuxSetupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DownloadViewModel by activityViewModels()

    private val requestRunCommand = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        refreshStatus()
        if (!granted) requireContext().toast(getString(R.string.tx_permission_denied), long = true)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTermuxSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }
        binding.btnInstallFdroid.setOnClickListener { openUrl(Termux.FDROID_URL) }
        binding.btnInstallGithub.setOnClickListener { openUrl(Termux.GITHUB_URL) }
        binding.btnFullGuide.setOnClickListener { openUrl(GUIDE_URL) }

        binding.btnCopyOpen.setOnClickListener {
            val context = requireContext()
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(
                ClipData.newPlainText("HexaPlayer setup", getString(R.string.dl_setup_command))
            )
            if (!Termux.open(context)) requireContext().toast(getString(R.string.tx_termux_missing), long = true)
            else requireContext().toast(getString(R.string.dl_setup_copied), long = true)
        }

        binding.btnPermission.setOnClickListener {
            if (!Termux.isInstalled(requireContext())) requireContext().toast(getString(R.string.tx_termux_missing), long = true)
            else requestRunCommand.launch(Termux.PERMISSION)
        }

        binding.btnStart.setOnClickListener {
            if (Termux.canStartServer(requireContext())) {
                viewModel.startServer()
                parentFragmentManager.popBackStack()
            } else {
                Termux.open(requireContext())
                requireContext().toast(getString(R.string.tx_start_manual), long = true)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val context = requireContext()
        val installed = Termux.isInstalled(context)
        val granted = installed && Termux.hasPermission(context)
        binding.tvInstallStatus.setText(if (installed) R.string.tx_installed else R.string.tx_not_installed)
        binding.rowInstall.visibility = if (installed) View.GONE else View.VISIBLE
        binding.tvPermissionStatus.setText(
            if (granted) R.string.tx_permission_granted else R.string.tx_permission_missing
        )
        binding.btnPermission.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: Exception) {
            requireContext().toast(url, long = true)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "termux_setup"
        private const val GUIDE_URL =
            "https://github.com/OxoGhost01/HexaPlayer/blob/main/termux/README.md"
    }
}
