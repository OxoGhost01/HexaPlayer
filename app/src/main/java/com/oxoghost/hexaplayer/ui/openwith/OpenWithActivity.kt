package com.oxoghost.hexaplayer.ui.openwith

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.oxoghost.hexaplayer.ui.MainActivity
import com.oxoghost.hexaplayer.ui.app.HexaTheme
import com.oxoghost.hexaplayer.ui.settings.Skin
import com.oxoghost.hexaplayer.ui.settings.UiSettingsRepository

/** "Open with HexaPlayer": a sheet over the calling app that plays one file. */
class OpenWithActivity : ComponentActivity() {

    private var model: OpenWithModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val uri = intent?.data ?: return finish()
        val openWith = OpenWithModel(this, uri, lifecycleScope) { openApp ->
            if (openApp) startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        }
        model = openWith
        openWith.start()

        val settingsRepo = UiSettingsRepository(this)
        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(null)
            val ui = settings ?: return@setContent
            HexaTheme(ui) {
                BackHandler(onBack = openWith::close)
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(remember { MutableInteractionSource() }, indication = null, onClick = openWith::close),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (ui.skin == Skin.Terminal) TOpenWithSheet(openWith.state, openWith, ui.pixelCovers)
                    else MOpenWithSheet(openWith.state, openWith)
                }
            }
        }
    }

    override fun onDestroy() {
        model?.release()
        super.onDestroy()
    }
}
