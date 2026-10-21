package com.oxoghost.hexaplayer.data

import java.util.UUID

data class Playlist(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val songIds: List<Long> = emptyList(),
    val coverUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val editedAt: Long = 0L,
) {
    /** Last edit, or the creation time for playlists saved before 2.0. */
    val lastEdited: Long get() = if (editedAt > 0) editedAt else createdAt
}
