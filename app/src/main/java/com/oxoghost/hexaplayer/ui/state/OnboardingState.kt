package com.oxoghost.hexaplayer.ui.state

import androidx.compose.runtime.Immutable

/** First launch: what is granted so far and the chosen library folder (blank = whole device). */
@Immutable
data class OnboardingState(
    val version: String,
    val hasAudio: Boolean,
    val asksNotifications: Boolean,
    val hasNotifications: Boolean,
    val folder: String,
)

interface OnboardingActions {
    fun grantAudio()
    fun allowNotifications()
    fun chooseFolder()
    fun start()
    fun quit()
}
