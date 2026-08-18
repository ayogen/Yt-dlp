package com.example.download

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.model.AppSettings
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import com.example.data.model.MediaType
import com.example.engine.AppLogger
import com.example.engine.YtDlpEngine
import com.example.engine.YtDlpProcessRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DownloadManager(
    private val context: Context,
    private val database: AppDatabase,
    private val engine: YtDlpEngine
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadDao = database.downloadDao()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedFlags = ConcurrentHashMap<String, Boolean>()
    private val cancelledFlags = ConcurrentHashMap<String, Boolean>()
    private val activeDownloadCount = AtomicInteger(0)

    private val _settingsFlow = MutableStateFlow(AppSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private var queueLoopJob: Job? = null

    init {
        startQueueProcessor()
    }

    fun updateSettings(newSettings: AppSettings) {
        _settingsFlow.value = newSettings
        AppLogger.i("DownloadManager", "Settings updated. Max concurrency: ${newSettings.maxConcurrentDownloads}")
    }

    fun startQueueProcessor() {
        queueLoopJob?.cancel()
        queueLoopJob = scope.launch {
            while (true) {
                try {
                    val maxConcurrent = _settingsFlow.value.maxConcurrentDownloads
                    val currentlyRunning = activeDownloadCount.get()

                    if (currentlyRunning < maxConcurrent) {
                        val availableSlots = maxConcurrent - currentlyRunning
                        // Fetch queued tasks
                        val queuedTasks = downloadDao.getActiveAndQueuedTasksFlow()
                        // Find first queued task not currently active
                        database.downloadDao().let { dao ->
                            // Look for QUEUED tasks
                            val activeList = activeJobs.keys()
                            // Process tasks that are QUEUED
                            val allTasks = downloadDao.getTaskById("dummy") // check db
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("DownloadManager", "Queue loop error: ${e.message}")
                }
                delay(1000)
            }
        }
    }

    fun enqueueTask(task: DownloadTaskEntity) {
        scope.launch {
            AppLogger.i("DownloadManager", "Enqueueing task: ${task.title} (${task.id})", task.id)
            downloadDao.insertTask(task)
            triggerQueueProcessing()
        }
    }

    fun triggerQueueProcessing() {
        scope.launch {
            val maxConcurrent = _settingsFlow.value.maxConcurrentDownloads
            val running = activeDownloadCount.get()

            if (running >= maxConcurrent) {
                AppLogger.d("DownloadManager", "Max concurrency reached ($running / $maxConcurrent). Task will wait in queue.")
                return@launch
            }

            // We find any task with QUEUED status that doesn't have an active job
            // Using a query or observing tasks
            val queuedTask = findNextQueuedTask()
            if (queuedTask != null && !activeJobs.containsKey(queuedTask.id)) {
                startTaskExecution(queuedTask)
            }
        }
    }

    private suspend fun findNextQueuedTask(): DownloadTaskEntity? {
        var result: DownloadTaskEntity? = null
        try {
            // Find in db
            val list = downloadDao.getAllTasksFlow()
            // We can query directly
        } catch (e: Exception) {}
        return null
    }

    fun startTaskExecution(task: DownloadTaskEntity) {
        if (activeJobs.containsKey(task.id)) return

        val job = scope.launch {
            activeDownloadCount.incrementAndGet()
            pausedFlags[task.id] = false
            cancelledFlags[task.id] = false

            try {
                downloadDao.updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
                AppLogger.i("DownloadManager", "Started active download: ${task.title}", task.id)

                val result = engine.executeDownload(
                    task = task,
                    settings = _settingsFlow.value,
                    onProgress = { progress, downloaded, total, speed, eta ->
                        scope.launch {
                            downloadDao.updateTaskProgress(
                                id = task.id,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = total,
                                speed = speed,
                                eta = eta,
                                status = if (pausedFlags[task.id] == true) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING
                            )
                        }
                    },
                    isCancelled = { cancelledFlags[task.id] == true },
                    isPaused = { pausedFlags[task.id] == true }
                )

                if (result.isSuccess) {
                    val finalPath = result.getOrNull() ?: task.outputPath
                    val fileObj = File(finalPath)
                    val finalSize = if (fileObj.exists()) fileObj.length() else task.totalBytes

                    downloadDao.updateTaskStatus(
                        id = task.id,
                        status = DownloadStatus.COMPLETED,
                        completedTime = System.currentTimeMillis()
                    )

                    // Insert into History
                    downloadDao.insertHistory(
                        DownloadHistoryEntity(
                            id = task.id,
                            title = task.title,
                            url = task.url,
                            thumbnail = task.thumbnail,
                            filePath = finalPath,
                            fileSize = finalSize,
                            mediaType = task.mediaType,
                            formatDescription = task.formatDescription,
                            completedTimestamp = System.currentTimeMillis(),
                            uploader = "Media Uploader"
                        )
                    )

                    AppLogger.i("DownloadManager", "Task completed: ${task.title}", task.id)
                } else {
                    val ex = result.exceptionOrNull() ?: Exception("Unknown download error")
                    if (cancelledFlags[task.id] == true) {
                        downloadDao.updateTaskStatus(task.id, DownloadStatus.CANCELLED)
                        AppLogger.i("DownloadManager", "Task cancelled: ${task.title}", task.id)
                    } else {
                        val diagnostic = engine.classifyError(ex)
                        downloadDao.updateTaskStatus(
                            id = task.id,
                            status = DownloadStatus.FAILED,
                            errorMessage = "${diagnostic.title}: ${diagnostic.reason}\nAction: ${diagnostic.suggestedAction}"
                        )
                        AppLogger.e("DownloadManager", "Task failed: ${task.title} - ${diagnostic.reason}", task.id)
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DownloadManager", "Execution exception: ${e.message}", task.id)
                downloadDao.updateTaskStatus(task.id, DownloadStatus.FAILED, e.message)
            } finally {
                activeJobs.remove(task.id)
                pausedFlags.remove(task.id)
                cancelledFlags.remove(task.id)
                activeDownloadCount.decrementAndGet()
                triggerQueueProcessing()
            }
        }

        activeJobs[task.id] = job
    }

    fun pauseTask(taskId: String) {
        AppLogger.i("DownloadManager", "Pausing task: $taskId", taskId)
        pausedFlags[taskId] = true
        scope.launch {
            downloadDao.updateTaskStatus(taskId, DownloadStatus.PAUSED)
        }
    }

    fun resumeTask(task: DownloadTaskEntity) {
        AppLogger.i("DownloadManager", "Resuming task: ${task.id}", task.id)
        if (pausedFlags.containsKey(task.id)) {
            pausedFlags[task.id] = false
            scope.launch {
                downloadDao.updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
            }
        } else {
            startTaskExecution(task)
        }
    }

    fun cancelTask(taskId: String) {
        AppLogger.i("DownloadManager", "Cancelling task: $taskId", taskId)
        cancelledFlags[taskId] = true
        YtDlpProcessRunner.cancelTaskProcess(taskId)
        activeJobs[taskId]?.cancel()
        scope.launch {
            downloadDao.updateTaskStatus(taskId, DownloadStatus.CANCELLED)
        }
    }

    fun retryTask(task: DownloadTaskEntity) {
        AppLogger.i("DownloadManager", "Retrying task: ${task.id}", task.id)
        cancelTask(task.id)
        val resetTask = task.copy(
            status = DownloadStatus.QUEUED,
            progress = 0f,
            errorMessage = null,
            createdTimestamp = System.currentTimeMillis()
        )
        enqueueTask(resetTask)
        startTaskExecution(resetTask)
    }

    fun deleteTask(taskId: String, deleteFile: Boolean = false) {
        scope.launch {
            cancelTask(taskId)
            val task = downloadDao.getTaskById(taskId)
            if (deleteFile && task != null && task.outputPath.isNotBlank()) {
                val f = File(task.outputPath)
                if (f.exists()) f.delete()
            }
            downloadDao.deleteTask(taskId)
            AppLogger.i("DownloadManager", "Deleted task $taskId", taskId)
        }
    }

    fun clearFinished() {
        scope.launch {
            downloadDao.clearFinishedTasks()
            AppLogger.i("DownloadManager", "Cleared finished and cancelled tasks")
        }
    }
}
