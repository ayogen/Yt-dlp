package com.example.download

import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaMetadata
import com.example.engine.YtDlpEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class DownloadRepository(
    private val database: AppDatabase,
    private val engine: YtDlpEngine,
    private val downloadManager: DownloadManager
) {
    val allTasksFlow: Flow<List<DownloadTaskEntity>> = database.downloadDao().getAllTasksFlow()
    val allHistoryFlow: Flow<List<DownloadHistoryEntity>> = database.downloadDao().getAllHistoryFlow()
    val settingsFlow: StateFlow<AppSettings> = downloadManager.settingsFlow

    suspend fun analyzeUrl(url: String): Result<MediaMetadata> {
        return engine.analyzeUrl(url, settingsFlow.value)
    }

    fun startOrEnqueueDownload(task: DownloadTaskEntity) {
        downloadManager.enqueueTask(task)
        downloadManager.startTaskExecution(task)
    }

    fun pauseDownload(taskId: String) {
        downloadManager.pauseTask(taskId)
    }

    fun resumeDownload(task: DownloadTaskEntity) {
        downloadManager.resumeTask(task)
    }

    fun cancelDownload(taskId: String) {
        downloadManager.cancelTask(taskId)
    }

    fun retryDownload(task: DownloadTaskEntity) {
        downloadManager.retryTask(task)
    }

    fun deleteDownload(taskId: String, deleteFile: Boolean = false) {
        downloadManager.deleteTask(taskId, deleteFile)
    }

    fun clearFinishedDownloads() {
        downloadManager.clearFinished()
    }

    suspend fun deleteHistory(id: String, filePath: String?, deleteFile: Boolean = false) {
        if (deleteFile && !filePath.isNullOrBlank()) {
            val f = File(filePath)
            if (f.exists()) f.delete()
        }
        database.downloadDao().deleteHistory(id)
    }

    suspend fun clearHistory() {
        database.downloadDao().clearAllHistory()
    }

    fun updateSettings(newSettings: AppSettings) {
        downloadManager.updateSettings(newSettings)
    }
}
