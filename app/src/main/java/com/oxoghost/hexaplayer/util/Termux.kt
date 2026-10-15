package com.oxoghost.hexaplayer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** Talks to Termux through its RUN_COMMAND API (needs allow-external-apps=true, set by setup.sh). */
object Termux {

    const val PACKAGE = "com.termux"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val FDROID_URL = "https://f-droid.org/packages/com.termux/"
    const val GITHUB_URL = "https://github.com/termux/termux-app/releases/latest"

    private const val HOME = "/data/data/com.termux/files/home"
    private const val HEXADL = "/data/data/com.termux/files/usr/bin/hexadl"

    fun isInstalled(context: Context): Boolean =
        context.packageManager.getLaunchIntentForPackage(PACKAGE) != null

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun canStartServer(context: Context): Boolean = isInstalled(context) && hasPermission(context)

    fun open(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Runs `hexadl` as a background Termux task. Returns false if Termux refused the request. */
    fun startServer(context: Context): Boolean {
        val intent = Intent("$PACKAGE.RUN_COMMAND")
            .setClassName(PACKAGE, "$PACKAGE.app.RunCommandService")
            .putExtra("$PACKAGE.RUN_COMMAND_PATH", HEXADL)
            .putExtra("$PACKAGE.RUN_COMMAND_WORKDIR", HOME)
            .putExtra("$PACKAGE.RUN_COMMAND_BACKGROUND", true)
            .putExtra("$PACKAGE.RUN_COMMAND_COMMAND_LABEL", "HexaPlayer downloader")
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
