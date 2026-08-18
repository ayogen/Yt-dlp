package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnail: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Float = 0f, // 0.0 to 100.0
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Double = 0.0,
    val etaSeconds: Long = 0L,
    val formatId: String = "best",
    val formatDescription: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val outputPath: String = "",
    val errorMessage: String? = null,
    val detailedLogs: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long? = null,
    val isPlaylist: Boolean = false,
    val playlistIndex: Int = 1,
    val playlistTotal: Int = 1,
    val audioBitrate: Int? = null,
    val targetContainer: String = "mp4",
    val embedSubs: Boolean = false,
    val embedThumbnail: Boolean = true,
    val subtitleLangs: String = ""
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val thumbnail: String = "",
    val filePath: String,
    val fileSize: Long = 0L,
    val mediaType: MediaType = MediaType.VIDEO,
    val formatDescription: String = "",
    val completedTimestamp: Long = System.currentTimeMillis(),
    val uploader: String = "",
    val durationSeconds: Long = 0L
)

@Entity(tableName = "system_logs")
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val taskId: String? = null,
    val level: LogLevel = LogLevel.INFO,
    val tag: String = "YtDlpEngine",
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)
