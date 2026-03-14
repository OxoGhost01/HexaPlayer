package com.oxoghost.hexaplayer.data

data class DownloadItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val artist: String,
    val imageUrl: String,
    val audioUrl: String,
    val outputFolder: String,
    var status: DownloadStatus = DownloadStatus.QUEUED,
    var progress: Int = 0,
    var errorMessage: String? = null
)

enum class DownloadStatus {
    QUEUED, DOWNLOADING, DONE, SKIPPED, ERROR, CANCELLED
}
