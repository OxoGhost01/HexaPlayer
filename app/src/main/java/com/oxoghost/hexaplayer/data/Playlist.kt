package com.oxoghost.hexaplayer.data

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList(),
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
