package com.oxoghost.hexaplayer.data

/**
 * A download/sync job driven through the HexaDL server (spotDL running in
 * Termux on the phone). spotDL writes the audio into the shared Music folder;
 * the app then indexes the finished files into the library.
 */
data class SpotdlJob(
    val id: Long,
    val kind: JobKind,
    val url: String,
    val label: String,
    val folder: String? = null,
    val root: String? = null,
    val serverId: String? = null,
    val phase: JobPhase = JobPhase.QUEUED,
    val serverDone: Int = 0,
    val serverTotal: Int = 0,
    val message: String = "",
    val error: String? = null
)

enum class JobKind { DOWNLOAD, SYNC }

enum class JobPhase {
    /** Waiting to be sent to the server. */
    QUEUED,

    /** spotDL is downloading. */
    SERVER,

    /** Indexing the finished files into the library. */
    IMPORTING,

    DONE,
    STOPPED,
    ERROR
}

/** A saved sync playlist reported by the server (`GET /syncs`). */
data class SyncEntry(
    val url: String,
    val folder: String,
    val root: String,
    val name: String
)
