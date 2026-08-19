package com.example.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.YtDlpApplication
import com.example.data.model.AppSettings
import com.example.data.model.AudioFormat
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.FormatInfo
import com.example.data.model.HistorySortOrder
import com.example.data.model.LogEntryEntity
import com.example.data.model.MediaMetadata
import com.example.data.model.MediaType
import com.example.data.model.OutputContainer
import com.example.engine.AppLogger
import com.example.engine.EngineDiagnosticError
import com.example.engine.FFmpegBinaryManager
import com.example.engine.FFmpegDetector
import com.example.engine.FFmpegStatus
import com.example.engine.YtDlpBinaryManager
import com.example.engine.YtDlpVersionInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AnalysisUiState {
    object Idle : AnalysisUiState()
    object Analyzing : AnalysisUiState()
    data class Success(val metadata: MediaMetadata) : AnalysisUiState()
    data class Error(val error: EngineDiagnosticError) : AnalysisUiState()
}

enum class NavigationTab(val title: String) {
    HOME("Home"),
    DOWNLOADS("Downloads"),
    HISTORY("History"),
    SETTINGS("Settings")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as YtDlpApplication
    private val repository = app.repository
    private val engine = app.engine

    private val _currentTab = MutableStateFlow(NavigationTab.HOME)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    private val _analysisState = MutableStateFlow<AnalysisUiState>(AnalysisUiState.Idle)
    val analysisState: StateFlow<AnalysisUiState> = _analysisState.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historyFilterType = MutableStateFlow<MediaType?>(null)
    val historyFilterType: StateFlow<MediaType?> = _historyFilterType.asStateFlow()

    private val _historySortOrder = MutableStateFlow(HistorySortOrder.NEWEST)
    val historySortOrder: StateFlow<HistorySortOrder> = _historySortOrder.asStateFlow()

    private val _ffmpegStatus = MutableStateFlow<FFmpegStatus?>(null)
    val ffmpegStatus: StateFlow<FFmpegStatus?> = _ffmpegStatus.asStateFlow()

    private val _isUpdatingFFmpeg = MutableStateFlow(false)
    val isUpdatingFFmpeg: StateFlow<Boolean> = _isUpdatingFFmpeg.asStateFlow()

    private val _ffmpegUpdateProgress = MutableStateFlow(0f)
    val ffmpegUpdateProgress: StateFlow<Float> = _ffmpegUpdateProgress.asStateFlow()

    private val _versionInfo = MutableStateFlow<YtDlpVersionInfo?>(null)
    val versionInfo: StateFlow<YtDlpVersionInfo?> = _versionInfo.asStateFlow()

    private val _isUpdatingYtDlp = MutableStateFlow(false)
    val isUpdatingYtDlp: StateFlow<Boolean> = _isUpdatingYtDlp.asStateFlow()

    private val _updateProgress = MutableStateFlow(0f)
    val updateProgress: StateFlow<Float> = _updateProgress.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val settings: StateFlow<AppSettings> = repository.settingsFlow

    val tasks: StateFlow<List<DownloadTaskEntity>> = repository.allTasksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<LogEntryEntity>> = AppLogger.logsFlow

    val filteredHistory: StateFlow<List<DownloadHistoryEntity>> = combine(
        repository.allHistoryFlow,
        _historySearchQuery,
        _historyFilterType,
        _historySortOrder
    ) { history, query, filterType, sortOrder ->
        var list = history
        if (query.isNotBlank()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.uploader.contains(query, ignoreCase = true) ||
                        it.formatDescription.contains(query, ignoreCase = true)
            }
        }
        if (filterType != null) {
            list = list.filter { it.mediaType == filterType }
        }
        when (sortOrder) {
            HistorySortOrder.NEWEST -> list.sortedByDescending { it.completedTimestamp }
            HistorySortOrder.OLDEST -> list.sortedBy { it.completedTimestamp }
            HistorySortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSize }
            HistorySortOrder.NAME_ASC -> list.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshDiagnostics()
    }

    fun selectTab(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun setHistoryFilterType(type: MediaType?) {
        _historyFilterType.value = type
    }

    fun setHistorySortOrder(order: HistorySortOrder) {
        _historySortOrder.value = order
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _ffmpegStatus.value = FFmpegDetector.detect(getApplication())
            _versionInfo.value = YtDlpBinaryManager.getVersionInfo(getApplication())
        }
    }

    fun analyzeUrl(url: String) {
        if (url.isBlank()) {
            _toastMessage.value = "Please enter a valid URL"
            return
        }

        viewModelScope.launch {
            _analysisState.value = AnalysisUiState.Analyzing
            AppLogger.i("MainViewModel", "Analyzing: $url")
            val result = repository.analyzeUrl(url)
            if (result.isSuccess) {
                _analysisState.value = AnalysisUiState.Success(result.getOrThrow())
            } else {
                val ex = result.exceptionOrNull() ?: Exception("Unknown analysis failure")
                val diag = engine.classifyError(ex)
                _analysisState.value = AnalysisUiState.Error(diag)
            }
        }
    }

    fun clearAnalysis() {
        _analysisState.value = AnalysisUiState.Idle
    }

    fun startDownload(
        metadata: MediaMetadata,
        selectedFormat: FormatInfo?,
        mediaType: MediaType,
        targetContainer: OutputContainer,
        audioBitrate: Int?,
        embedSubs: Boolean,
        embedThumbnail: Boolean,
        selectedPlaylistIndices: Set<Int> = emptySet()
    ) {
        viewModelScope.launch {
            if (metadata.isPlaylist && selectedPlaylistIndices.isNotEmpty()) {
                val totalSelected = selectedPlaylistIndices.size
                var idx = 1
                selectedPlaylistIndices.forEach { itemIndex ->
                    val entry = metadata.playlistEntries.getOrNull(itemIndex)
                    if (entry != null) {
                        val task = DownloadTaskEntity(
                            id = UUID.randomUUID().toString(),
                            url = entry.url,
                            title = entry.title,
                            thumbnail = entry.thumbnail.ifBlank { metadata.thumbnail },
                            status = DownloadStatus.QUEUED,
                            formatId = selectedFormat?.formatId ?: "best",
                            formatDescription = selectedFormat?.displayResolution ?: "Best",
                            mediaType = mediaType,
                            isPlaylist = true,
                            playlistIndex = idx,
                            playlistTotal = totalSelected,
                            audioBitrate = audioBitrate,
                            targetContainer = targetContainer.ext,
                            embedSubs = embedSubs,
                            embedThumbnail = embedThumbnail
                        )
                        repository.startOrEnqueueDownload(task)
                        idx++
                    }
                }
                _toastMessage.value = "Enqueued $totalSelected playlist videos"
            } else {
                val formatDesc = if (mediaType == MediaType.AUDIO) {
                    "Audio (${targetContainer.ext.uppercase()} - ${audioBitrate ?: 320}kbps)"
                } else {
                    "${selectedFormat?.displayResolution ?: "Best"} (${targetContainer.ext.uppercase()})"
                }

                val task = DownloadTaskEntity(
                    id = UUID.randomUUID().toString(),
                    url = metadata.webpageUrl,
                    title = metadata.title,
                    thumbnail = metadata.thumbnail,
                    status = DownloadStatus.QUEUED,
                    formatId = selectedFormat?.formatId ?: "best",
                    formatDescription = formatDesc,
                    totalBytes = selectedFormat?.filesize ?: selectedFormat?.filesizeApprox ?: 0L,
                    mediaType = mediaType,
                    audioBitrate = audioBitrate,
                    targetContainer = targetContainer.ext,
                    embedSubs = embedSubs,
                    embedThumbnail = embedThumbnail
                )
                repository.startOrEnqueueDownload(task)
                _toastMessage.value = "Download started: ${metadata.title.take(30)}..."
            }

            _analysisState.value = AnalysisUiState.Idle
            _currentTab.value = NavigationTab.DOWNLOADS
        }
    }

    fun pauseTask(taskId: String) = repository.pauseDownload(taskId)
    fun resumeTask(task: DownloadTaskEntity) = repository.resumeDownload(task)
    fun cancelTask(taskId: String) = repository.cancelDownload(taskId)
    fun retryTask(task: DownloadTaskEntity) = repository.retryDownload(task)
    fun deleteTask(taskId: String, deleteFile: Boolean = false) = repository.deleteDownload(taskId, deleteFile)
    fun clearFinishedTasks() = repository.clearFinishedDownloads()

    fun deleteHistory(id: String, filePath: String?, deleteFile: Boolean = false) {
        viewModelScope.launch {
            repository.deleteHistory(id, filePath, deleteFile)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        repository.updateSettings(newSettings)
        _toastMessage.value = "Settings saved successfully"
    }

    fun onDownloadLocationSelected(treeUri: android.net.Uri) {
        val success = com.example.download.StorageUtils.takePersistableUriPermission(getApplication(), treeUri)
        val displayName = com.example.download.StorageUtils.getDisplayNameForTreeUri(getApplication(), treeUri.toString())
        val updated = settings.value.copy(
            downloadLocationUri = treeUri.toString(),
            downloadLocationDisplayName = displayName
        )
        repository.updateSettings(updated)
        if (success) {
            _toastMessage.value = "Download location set to: $displayName"
        } else {
            _toastMessage.value = "Download location set to: $displayName (Permission requested)"
        }
    }

    fun resetDownloadLocationToDefault() {
        val updated = settings.value.copy(
            downloadLocationUri = "",
            downloadLocationDisplayName = "Default App Storage"
        )
        repository.updateSettings(updated)
        _toastMessage.value = "Download location reset to default storage"
    }

    fun updateYtDlpBinary() {
        viewModelScope.launch {
            _isUpdatingYtDlp.value = true
            _updateProgress.value = 0f
            val result = YtDlpBinaryManager.updateBinary(getApplication()) { prog ->
                _updateProgress.value = prog
            }
            _isUpdatingYtDlp.value = false
            if (result.isSuccess) {
                _toastMessage.value = "yt-dlp updated to version ${result.getOrNull()}"
                refreshDiagnostics()
            } else {
                _toastMessage.value = "Update failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun installOrUpdateFFmpeg() {
        viewModelScope.launch {
            _isUpdatingFFmpeg.value = true
            _ffmpegUpdateProgress.value = 0f
            val result = FFmpegBinaryManager.installOrUpdateFFmpeg(getApplication()) { prog ->
                _ffmpegUpdateProgress.value = prog
            }
            _isUpdatingFFmpeg.value = false
            if (result.isSuccess) {
                val status = result.getOrNull()
                _ffmpegStatus.value = status
                _toastMessage.value = "FFmpeg installed successfully: ${status?.version ?: "Ready"}"
                refreshDiagnostics()
            } else {
                _toastMessage.value = "FFmpeg installation failed: ${result.exceptionOrNull()?.message}"
                refreshDiagnostics()
            }
        }
    }

    fun copyToClipboard(text: String, label: String = "Copied text") {
        val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        _toastMessage.value = "$label copied to clipboard"
    }
}
