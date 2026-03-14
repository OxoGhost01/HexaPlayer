package com.oxoghost.hexaplayer.viewmodel

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.DownloadItem
import com.oxoghost.hexaplayer.data.DownloadStatus
import com.oxoghost.hexaplayer.data.JamendoTrack
import com.oxoghost.hexaplayer.repository.DownloadRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DownloadRepository()
    private val prefs = application.getSharedPreferences("download_prefs", Context.MODE_PRIVATE)

    private val _downloads = MutableLiveData<List<DownloadItem>>(emptyList())
    val downloads: LiveData<List<DownloadItem>> = _downloads

    private val _searchResults = MutableLiveData<List<JamendoTrack>>(emptyList())
    val searchResults: LiveData<List<JamendoTrack>> = _searchResults

    private val _isSearching = MutableLiveData(false)
    val isSearching: LiveData<Boolean> = _isSearching

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    private val queue = ArrayDeque<DownloadItem>()
    private var isProcessingQueue = false
    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val cancelledIds = mutableSetOf<Long>()

    var downloadFolder: String
        get() = prefs.getString("download_folder", defaultFolder()) ?: defaultFolder()
        set(value) = prefs.edit().putString("download_folder", value).apply()

    var jamendoClientId: String
        get() = prefs.getString("jamendo_client_id", "") ?: ""
        set(value) = prefs.edit().putString("jamendo_client_id", value).apply()

    private fun defaultFolder(): String =
        "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath}/HexaPlayer"

    fun search(query: String) {
        if (query.isBlank()) return
        val clientId = jamendoClientId
        if (clientId.isBlank()) {
            _toastMessage.value = getApplication<Application>().getString(R.string.dl_jamendo_no_client_id)
            return
        }
        _isSearching.value = true
        _searchResults.value = emptyList()
        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { repo.search(query, clientId) }
                _searchResults.value = results
                if (results.isEmpty()) {
                    _toastMessage.value = getApplication<Application>().getString(R.string.dl_no_results)
                }
            } catch (e: Exception) {
                _toastMessage.value = "Search failed: ${e.message?.take(120)}"
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun downloadTrack(track: JamendoTrack) {
        val item = DownloadItem(
            id = idCounter.incrementAndGet(),
            title = track.name,
            artist = track.artistName,
            imageUrl = track.albumImage,
            audioUrl = track.audioUrl,
            outputFolder = downloadFolder
        )
        queue.addLast(item)
        _downloads.value = (_downloads.value ?: emptyList()) + item
        processQueue()
        _toastMessage.value = getApplication<Application>()
            .getString(R.string.dl_added_to_queue, track.name)
    }

    private fun processQueue() {
        if (isProcessingQueue) return
        if (queue.isEmpty()) return
        isProcessingQueue = true
        viewModelScope.launch {
            while (queue.isNotEmpty()) {
                val item = queue.removeFirst()
                processItem(item)
            }
            isProcessingQueue = false
        }
    }

    private suspend fun processItem(item: DownloadItem) {
        updateItem(item.copy(status = DownloadStatus.DOWNLOADING, progress = 0))
        try {
            val finalStatus = repo.download(
                item,
                cancelFlag = { cancelledIds.contains(item.id) }
            ) { progress ->
                updateItem(item.copy(status = DownloadStatus.DOWNLOADING, progress = progress))
            }
            cancelledIds.remove(item.id)
            val finalProgress = if (finalStatus == DownloadStatus.DONE) 100 else item.progress
            updateItem(item.copy(status = finalStatus, progress = finalProgress))
        } catch (e: Exception) {
            updateItem(item.copy(
                status = DownloadStatus.ERROR,
                errorMessage = e.message?.take(150)
            ))
        }
    }

    fun cancelDownload(item: DownloadItem) {
        if (item.status == DownloadStatus.QUEUED) {
            queue.removeAll { it.id == item.id }
            updateItem(item.copy(status = DownloadStatus.CANCELLED))
        } else if (item.status == DownloadStatus.DOWNLOADING) {
            cancelledIds.add(item.id)
        }
    }

    fun clearFinished() {
        _downloads.value = (_downloads.value ?: emptyList()).filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING
        }
    }

    fun clearToastMessage() {
        _toastMessage.value = null
    }

    private fun updateItem(updated: DownloadItem) {
        val list = (_downloads.value ?: emptyList()).toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx >= 0) list[idx] = updated else list.add(updated)
        _downloads.postValue(list)
    }
}
