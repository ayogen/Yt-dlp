package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.DownloadHistoryEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.DownloadTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY queuePosition ASC, createdTimestamp DESC")
    fun getAllTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'ANALYZING', 'DOWNLOADING', 'PAUSED', 'PROCESSING') ORDER BY queuePosition ASC, createdTimestamp ASC")
    fun getActiveAndQueuedTasksFlow(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'QUEUED' ORDER BY queuePosition ASC, createdTimestamp ASC")
    suspend fun getQueuedTasks(): List<DownloadTaskEntity>

    @Query("UPDATE download_tasks SET status = 'QUEUED', speedBytesPerSec = 0.0, etaSeconds = 0 WHERE status IN ('DOWNLOADING', 'ANALYZING', 'PROCESSING')")
    suspend fun resetInterruptedTasksToQueued()

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    fun getTaskByIdFlow(id: String): Flow<DownloadTaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<DownloadTaskEntity>)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET queuePosition = :position WHERE id = :id")
    suspend fun updateTaskPosition(id: String, position: Int)

    @Query("UPDATE download_tasks SET retryAttempt = :attempt, status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateTaskRetry(id: String, attempt: Int, status: DownloadStatus, errorMessage: String? = null)

    @Query("UPDATE download_tasks SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBytesPerSec = :speed, etaSeconds = :eta, status = :status WHERE id = :id")
    suspend fun updateTaskProgress(
        id: String,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
        speed: Double,
        eta: Long,
        status: DownloadStatus
    )

    @Query("UPDATE download_tasks SET status = :status, errorMessage = :errorMessage, completedTimestamp = :completedTime WHERE id = :id")
    suspend fun updateTaskStatus(
        id: String,
        status: DownloadStatus,
        errorMessage: String? = null,
        completedTime: Long? = null
    )

    @Query("UPDATE download_tasks SET status = :status, outputPath = :outputPath, completedTimestamp = :completedTime WHERE id = :id")
    suspend fun updateTaskCompleted(
        id: String,
        status: DownloadStatus,
        outputPath: String,
        completedTime: Long
    )

    @Query("UPDATE download_tasks SET detailedLogs = detailedLogs || :logLine WHERE id = :id")
    suspend fun appendTaskLog(id: String, logLine: String)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)

    @Query("DELETE FROM download_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearFinishedTasks()

    // History queries
    @Query("SELECT * FROM download_history ORDER BY completedTimestamp DESC")
    fun getAllHistoryFlow(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE title LIKE '%' || :query || '%' OR uploader LIKE '%' || :query || '%' ORDER BY completedTimestamp DESC")
    fun searchHistoryFlow(query: String): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: DownloadHistoryEntity)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteHistory(id: String)

    @Query("DELETE FROM download_history")
    suspend fun clearAllHistory()
}
