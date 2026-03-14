package com.oxoghost.hexaplayer.data

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songs: List<Song>
)
