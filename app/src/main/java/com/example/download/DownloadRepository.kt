package com.example.download

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaMetadata
import com.example.engine.YtDlpEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

class DownloadRepository(
    private val database: AppDatabase,
    private val engine: YtDlpEngine,
    private val downloadManager: DownloadManager,
    private val context: Context? = null
) {
    val allTasksFlow: Flow<List<DownloadTaskEntity>> = database.downloadDao().getAllTasksFlow()
    val allHistoryFlow: Flow<List<DownloadHistoryEntity>> = database.downloadDao().getAllHistoryFlow()
    val settingsFlow: StateFlow<AppSettings> = downloadManager.settingsFlow

    suspend fun analyzeMediaCollection(url: String): Result<com.example.data.model.MediaCollection> {
        return engine.analyzeMediaCollection(url, settingsFlow.value)
    }

    suspend fun analyzeUrl(url: String): Result<MediaMetadata> {
        return engine.analyzeUrl(url, settingsFlow.value)
    }

    fun startOrEnqueueDownload(task: DownloadTaskEntity) {
        downloadManager.enqueueTask(task)
        downloadManager.startTaskExecution(task)
    }

    fun startOrEnqueueDownloads(tasks: List<DownloadTaskEntity>) {
        downloadManager.enqueueTasks(tasks)
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

    fun moveQueueItemUp(taskId: String) {
        downloadManager.moveQueueItemUp(taskId)
    }

    fun moveQueueItemDown(taskId: String) {
        downloadManager.moveQueueItemDown(taskId)
    }

    fun reorderQueue(orderedTaskIds: List<String>) {
        downloadManager.reorderQueue(orderedTaskIds)
    }

    suspend fun deleteHistory(id: String, filePath: String?, deleteFile: Boolean = false, appContext: Context? = null) {
        if (deleteFile && !filePath.isNullOrBlank()) {
            val ctx = appContext ?: context
            if (ctx != null) {
                StorageUtils.deleteMediaFile(ctx, filePath)
            }
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
