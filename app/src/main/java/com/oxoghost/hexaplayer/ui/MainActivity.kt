package com.oxoghost.hexaplayer.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oxoghost.hexaplayer.ui.app.HexaApp
import com.oxoghost.hexaplayer.ui.app.SystemAccess
import com.oxoghost.hexaplayer.ui.app.Updater
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository
import com.oxoghost.hexaplayer.ui.state.UiModel
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel

class MainActivity : AppCompatActivity() {

    private val viewModel: MusicViewModel by viewModels()
    private val uiModel: UiModel by viewModels()
    private lateinit var system: SystemAccess
    private lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        system = SystemAccess(this) { viewModel.loadLibrary() }
        updater = Updater(this)
        updater.register()
        // Users coming from 1.x already went through setup.
        if (system.hasAudio && !uiModel.onboarded) uiModel.finishOnboarding()

        val settingsRepo = UiSettingsRepository(this)
        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(null)
            settings?.let { HexaApp(it, viewModel, uiModel, system, updater, settingsRepo) }
        }

        viewModel.connectToService()
        if (system.hasAudio) viewModel.loadLibrary()
        updater.checkOnLaunch()

        // First launch asks for notifications on the onboarding screen instead.
        if (uiModel.onboarded && !system.hasNotifications) system.requestNotifications()
    }

    override fun onResume() {
        super.onResume()
        val had = system.hasAudio
        system.refresh()
        if (!had && system.hasAudio) viewModel.loadLibrary()
    }

    override fun onDestroy() {
        super.onDestroy()
        updater.unregister()
    }
}
