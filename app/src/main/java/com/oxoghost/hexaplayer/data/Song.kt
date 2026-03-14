package com.oxoghost.hexaplayer.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,       // milliseconds
    val relativePath: String, // e.g. "Music/Artist/Album/"
    val displayName: String,
    val customCoverUri: String? = null // app-local cover override
) : Parcelable
