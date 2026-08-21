package com.example.download

import android.content.Context
import com.example.data.SettingsPreferencesManager
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
    private val lastProgressUpdate = ConcurrentHashMap<String, Long>()

    private val settingsPreferencesManager = SettingsPreferencesManager(context)
    private val _settingsFlow = MutableStateFlow(settingsPreferencesManager.loadSettings())
    val settingsFlow: StateFlow<AppSettings> = _settingsFlow.asStateFlow()

    private var queueLoopJob: Job? = null

    init {
        // Cleanup orphaned in-progress tasks on application startup
        recoverOrphanedTasks()
        startQueueProcessor()
    }

    private fun recoverOrphanedTasks() {
        scope.launch {
            try {
                // Reset tasks that were interrupted mid-flight on sudden process death back to QUEUED
                downloadDao.resetInterruptedTasksToQueued()
                val queued = downloadDao.getQueuedTasks()
                // Ensure queue positions are assigned cleanly
                queued.forEachIndexed { index, task ->
                    if (task.queuePosition != index) {
                        downloadDao.updateTaskPosition(task.id, index)
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("DownloadManager", "Task recovery note: ${e.message}")
            }
        }
    }

    fun updateSettings(newSettings: AppSettings) {
        _settingsFlow.value = newSettings
        settingsPreferencesManager.saveSettings(newSettings)
        AppLogger.i("DownloadManager", "Settings updated. Max concurrency: ${newSettings.maxConcurrentDownloads}")
        triggerQueueProcessing()
    }

    fun startQueueProcessor() {
        queueLoopJob?.cancel()
        queueLoopJob = scope.launch {
            while (true) {
                try {
                    val maxConcurrent = _settingsFlow.value.maxConcurrentDownloads.coerceAtLeast(1)
                    val currentlyRunning = activeDownloadCount.get()

                    if (currentlyRunning < maxConcurrent) {
                        val availableSlots = maxConcurrent - currentlyRunning
                        val queuedTasks = downloadDao.getQueuedTasks()
                        for (task in queuedTasks.take(availableSlots)) {
                            if (!activeJobs.containsKey(task.id)) {
                                startTaskExecution(task)
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.w("DownloadManager", "Queue loop warning: ${e.message}")
                }
                delay(1200)
            }
        }
    }

    fun enqueueTask(task: DownloadTaskEntity) {
        scope.launch {
            val queued = downloadDao.getQueuedTasks()
            val nextPos = if (queued.isEmpty()) 0 else (queued.maxOfOrNull { it.queuePosition } ?: 0) + 1
            val taskWithPos = task.copy(queuePosition = nextPos)
            AppLogger.i("DownloadManager", "Enqueueing task at position $nextPos: ${taskWithPos.title} (${taskWithPos.id})", taskWithPos.id)
            downloadDao.insertTask(taskWithPos)
            triggerQueueProcessing()
        }
    }

    fun enqueueTasks(tasks: List<DownloadTaskEntity>) {
        if (tasks.isEmpty()) return
        scope.launch {
            val queued = downloadDao.getQueuedTasks()
            var currentNextPos = if (queued.isEmpty()) 0 else (queued.maxOfOrNull { it.queuePosition } ?: 0) + 1
            val tasksWithPos = tasks.map { task ->
                task.copy(queuePosition = currentNextPos++)
            }
            AppLogger.i("DownloadManager", "Batch enqueueing ${tasksWithPos.size} tasks (starting position: ${tasksWithPos.firstOrNull()?.queuePosition})")
            downloadDao.insertTasks(tasksWithPos)
            triggerQueueProcessing()
        }
    }

    fun triggerQueueProcessing() {
        scope.launch {
            val maxConcurrent = _settingsFlow.value.maxConcurrentDownloads.coerceAtLeast(1)
            val running = activeDownloadCount.get()

            if (running >= maxConcurrent) {
                AppLogger.d("DownloadManager", "Max concurrency reached ($running / $maxConcurrent). Tasks remain queued.")
                return@launch
            }

            val queuedTasks = downloadDao.getQueuedTasks()
            val available = maxConcurrent - running
            for (task in queuedTasks.take(available)) {
                if (!activeJobs.containsKey(task.id)) {
                    startTaskExecution(task)
                }
            }
        }
    }

    fun moveQueueItemUp(taskId: String) {
        scope.launch {
            val queued = downloadDao.getQueuedTasks()
            val index = queued.indexOfFirst { it.id == taskId }
            if (index > 0) {
                val current = queued[index]
                val prev = queued[index - 1]
                downloadDao.updateTaskPosition(current.id, prev.queuePosition)
                downloadDao.updateTaskPosition(prev.id, current.queuePosition)
                AppLogger.i("DownloadManager", "Moved queued task UP: ${current.title}")
            }
        }
    }

    fun moveQueueItemDown(taskId: String) {
        scope.launch {
            val queued = downloadDao.getQueuedTasks()
            val index = queued.indexOfFirst { it.id == taskId }
            if (index >= 0 && index < queued.size - 1) {
                val current = queued[index]
                val next = queued[index + 1]
                downloadDao.updateTaskPosition(current.id, next.queuePosition)
                downloadDao.updateTaskPosition(next.id, current.queuePosition)
                AppLogger.i("DownloadManager", "Moved queued task DOWN: ${current.title}")
            }
        }
    }

    fun reorderQueue(orderedTaskIds: List<String>) {
        scope.launch {
            orderedTaskIds.forEachIndexed { pos, id ->
                downloadDao.updateTaskPosition(id, pos)
            }
            AppLogger.i("DownloadManager", "Queue reordered successfully (${orderedTaskIds.size} tasks)")
        }
    }

    fun startTaskExecution(task: DownloadTaskEntity) {
        if (activeJobs.containsKey(task.id)) return

        val job = scope.launch {
            activeDownloadCount.incrementAndGet()
            pausedFlags[task.id] = false
            cancelledFlags[task.id] = false

            // Start foreground service for reliable background execution & notifications
            DownloadForegroundService.startService(context)

            try {
                downloadDao.updateTaskStatus(task.id, DownloadStatus.DOWNLOADING)
                AppLogger.i("DownloadManager", "Started active download: ${task.title} (Attempt: ${task.retryAttempt + 1})", task.id)

                val result = if (task.mediaType == MediaType.IMAGE) {
                    ImageDownloader.downloadImage(
                        context = context,
                        imageUrl = task.url,
                        suggestedTitle = task.title,
                        customExt = task.targetContainer.ifBlank { null },
                        safTreeUri = _settingsFlow.value.safTreeUri.ifBlank { null },
                        isCancelled = { cancelledFlags[task.id] == true },
                        isPaused = { pausedFlags[task.id] == true },
                        onProgress = { progress, downloaded, total, speed, eta ->
                            val now = System.currentTimeMillis()
                            val lastTime = lastProgressUpdate[task.id] ?: 0L
                            val isPaused = pausedFlags[task.id] == true
                            if (now - lastTime >= 250L || progress >= 100f || isPaused) {
                                lastProgressUpdate[task.id] = now
                                scope.launch {
                                    val currentStatus = if (isPaused) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING
                                    downloadDao.updateTaskProgress(
                                        id = task.id,
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        speed = if (isPaused) 0.0 else speed,
                                        eta = if (isPaused) 0L else eta,
                                        status = currentStatus
                                    )

                                    DownloadForegroundService.updateProgress(
                                        context = context,
                                        taskId = task.id,
                                        title = task.title,
                                        progress = progress,
                                        downloaded = downloaded,
                                        total = total,
                                        speed = if (isPaused) 0.0 else speed,
                                        activeCount = activeDownloadCount.get()
                                    )
                                }
                            }
                        },
                        onLog = { msg ->
                            AppLogger.d("DownloadManager", "[Image] $msg", task.id)
                        }
                    ).map { it.finalPathOrSafUri }
                } else {
                    engine.executeDownload(
                        task = task,
                        settings = _settingsFlow.value,
                        onProgress = { progress, downloaded, total, speed, eta ->
                            val now = System.currentTimeMillis()
                            val lastTime = lastProgressUpdate[task.id] ?: 0L
                            val isPaused = pausedFlags[task.id] == true
                            if (now - lastTime >= 250L || progress >= 100f || isPaused) {
                                lastProgressUpdate[task.id] = now
                                scope.launch {
                                    val currentStatus = if (isPaused) DownloadStatus.PAUSED else DownloadStatus.DOWNLOADING
                                    downloadDao.updateTaskProgress(
                                        id = task.id,
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = total,
                                        speed = if (isPaused) 0.0 else speed,
                                        eta = if (isPaused) 0L else eta,
                                        status = currentStatus
                                    )

                                    // Update Foreground Notification
                                    DownloadForegroundService.updateProgress(
                                        context = context,
                                        taskId = task.id,
                                        title = task.title,
                                        progress = progress,
                                        downloaded = downloaded,
                                        total = total,
                                        speed = if (isPaused) 0.0 else speed,
                                        activeCount = activeDownloadCount.get()
                                    )
                                }
                            }
                        },
                        isCancelled = { cancelledFlags[task.id] == true },
                        isPaused = { pausedFlags[task.id] == true }
                    )
                }

                if (result.isSuccess) {
                    val finalPath = result.getOrNull() ?: task.outputPath
                    val finalSize = StorageUtils.getFileSize(context, finalPath).let { if (it > 0) it else task.totalBytes }

                    downloadDao.updateTaskCompleted(
                        id = task.id,
                        status = DownloadStatus.COMPLETED,
                        outputPath = finalPath,
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
                            qualityLabel = task.qualityLabel,
                            status = DownloadStatus.COMPLETED,
                            completedTimestamp = System.currentTimeMillis(),
                            uploader = "Media Creator"
                        )
                    )

                    AppLogger.i("DownloadManager", "Task completed: ${task.title} -> $finalPath", task.id)
                } else {
                    val ex = result.exceptionOrNull() ?: Exception("Unknown download error")
                    if (cancelledFlags[task.id] == true) {
                        downloadDao.updateTaskStatus(task.id, DownloadStatus.CANCELLED)
                        AppLogger.i("DownloadManager", "Task cancelled: ${task.title}", task.id)
                    } else if (pausedFlags[task.id] == true) {
                        downloadDao.updateTaskStatus(task.id, DownloadStatus.PAUSED)
                        AppLogger.i("DownloadManager", "Task paused: ${task.title}", task.id)
                    } else {
                        val isTransient = isTransientNetworkError(ex)
                        val maxRetries = _settingsFlow.value.retryCount
                        val currentAttempt = task.retryAttempt

                        if (isTransient && currentAttempt < maxRetries) {
                            val nextAttempt = currentAttempt + 1
                            val backoffMs = (1000L * (1 shl nextAttempt)).coerceAtMost(30000L)
                            AppLogger.w("DownloadManager", "Transient failure on ${task.title}. Retrying ($nextAttempt/$maxRetries) in ${backoffMs}ms...", task.id)
                            
                            val retryMsg = "Connection interrupted. Retrying ($nextAttempt/$maxRetries)..."
                            downloadDao.updateTaskRetry(
                                id = task.id,
                                attempt = nextAttempt,
                                status = DownloadStatus.QUEUED,
                                errorMessage = retryMsg
                            )
                            
                            // Exponential backoff before triggering queue
                            delay(backoffMs)
                        } else {
                            val diagnostic = engine.classifyError(ex)
                            val userFriendlyMessage = "${diagnostic.title}: ${diagnostic.reason}\nAction: ${diagnostic.suggestedAction}"
                            downloadDao.updateTaskStatus(
                                id = task.id,
                                status = DownloadStatus.FAILED,
                                errorMessage = userFriendlyMessage
                            )
                            AppLogger.e("DownloadManager", "Task failed permanently: ${task.title} - ${diagnostic.reason}", task.id)
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("DownloadManager", "Execution exception: ${e.message}", task.id)
                downloadDao.updateTaskStatus(task.id, DownloadStatus.FAILED, e.message)
            } finally {
                activeJobs.remove(task.id)
                pausedFlags.remove(task.id)
                cancelledFlags.remove(task.id)
                val remaining = activeDownloadCount.decrementAndGet()

                if (remaining <= 0) {
                    DownloadForegroundService.stopService(context)
                }

                triggerQueueProcessing()
            }
        }

        activeJobs[task.id] = job
    }

    fun pauseTask(taskId: String) {
        AppLogger.i("DownloadManager", "Pausing task: $taskId", taskId)
        pausedFlags[taskId] = true
        YtDlpProcessRunner.cancelTaskProcess(taskId)
        scope.launch {
            downloadDao.updateTaskStatus(taskId, DownloadStatus.PAUSED)
            triggerQueueProcessing()
        }
    }

    fun resumeTask(task: DownloadTaskEntity) {
        AppLogger.i("DownloadManager", "Resuming task: ${task.id}", task.id)
        if (pausedFlags.containsKey(task.id)) {
            pausedFlags[task.id] = false
            scope.launch {
                downloadDao.updateTaskStatus(task.id, DownloadStatus.QUEUED)
                triggerQueueProcessing()
            }
        } else {
            scope.launch {
                downloadDao.updateTaskStatus(task.id, DownloadStatus.QUEUED)
                triggerQueueProcessing()
            }
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
        AppLogger.i("DownloadManager", "Retrying task manually: ${task.id}", task.id)
        cancelTask(task.id)
        val resetTask = task.copy(
            status = DownloadStatus.QUEUED,
            progress = 0f,
            errorMessage = null,
            retryAttempt = 0,
            createdTimestamp = System.currentTimeMillis()
        )
        enqueueTask(resetTask)
    }

    fun deleteTask(taskId: String, deleteFile: Boolean = false) {
        scope.launch {
            cancelTask(taskId)
            val task = downloadDao.getTaskById(taskId)
            if (deleteFile && task != null && task.outputPath.isNotBlank()) {
                StorageUtils.deleteMediaFile(context, task.outputPath)
            }
            downloadDao.deleteTask(taskId)
            AppLogger.i("DownloadManager", "Deleted task $taskId", taskId)
        }
    }

    fun clearFinished() {
        scope.launch {
            downloadDao.clearFinishedTasks()
            AppLogger.i("DownloadManager", "Cleared finished, failed and cancelled tasks")
        }
    }

    private fun isTransientNetworkError(e: Throwable): Boolean {
        val msg = (e.message ?: "").lowercase()
        return msg.contains("timeout") ||
                msg.contains("timed out") ||
                msg.contains("connection reset") ||
                msg.contains("connection refused") ||
                msg.contains("broken pipe") ||
                msg.contains("unable to download webpage") ||
                msg.contains("unknownhost") ||
                msg.contains("temporary failure") ||
                msg.contains("http error 500") ||
                msg.contains("http error 502") ||
                msg.contains("http error 503") ||
                msg.contains("http error 504") ||
                msg.contains("http error 429") ||
                msg.contains("interrupted") ||
                msg.contains("socket")
    }
}
