package com.oxoghost.hexaplayer.ui.nav

import android.net.Uri
import com.oxoghost.hexaplayer.ui.state.SettingsPage
import com.oxoghost.hexaplayer.ui.state.Tab

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val DOWNLOAD = "download"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val EQUALIZER = "equalizer"
    const val ALBUM = "album/{id}"
    const val ARTIST = "artist/{name}"
    const val PLAYLIST = "playlist/{id}"
    const val EDIT = "edit/{ids}"
    const val SETUP = "setup"
    const val APPEARANCE = "appearance"
    const val SETTINGS_PAGE = "settings/{page}"
    const val WRAPPED = "wrapped"
    const val FLAVOR = "flavor?name={name}"

    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist/${Uri.encode(name)}"
    fun playlist(id: String) = "playlist/${Uri.encode(id)}"
    fun edit(ids: List<Long>) = "edit/${ids.joinToString(",")}"
    fun settingsPage(page: SettingsPage) = "settings/${page.name}"
    fun flavor(name: String?) = if (name == null) "flavor" else "flavor?name=${Uri.encode(name)}"

    fun of(tab: Tab) = when (tab) {
        Tab.Home -> HOME
        Tab.Library -> LIBRARY
        Tab.Search -> SEARCH
        Tab.Download -> DOWNLOAD
    }

    /** The tab highlighted for a route (details belong to Library), null for full screens. */
    fun tabOf(route: String?): Tab? = when {
        route == HOME -> Tab.Home
        route == LIBRARY || route == ALBUM || route == ARTIST || route == PLAYLIST -> Tab.Library
        route == SEARCH -> Tab.Search
        route == DOWNLOAD -> Tab.Download
        else -> null
    }
}
