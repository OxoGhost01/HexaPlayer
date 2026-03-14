package com.oxoghost.hexaplayer.data

data class JamendoTrack(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String,
    val albumImage: String,
    val audioUrl: String // direct MP3 URL (follows CC license)
)
