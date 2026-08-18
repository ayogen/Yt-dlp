package com.example.data.db

import androidx.room.TypeConverter
import com.example.data.model.DownloadStatus
import com.example.data.model.LogLevel
import com.example.data.model.MediaType

class Converters {
    @TypeConverter
    fun fromDownloadStatus(value: DownloadStatus): String = value.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        try { DownloadStatus.valueOf(value) } catch (e: Exception) { DownloadStatus.QUEUED }

    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        try { MediaType.valueOf(value) } catch (e: Exception) { MediaType.VIDEO }

    @TypeConverter
    fun fromLogLevel(value: LogLevel): String = value.name

    @TypeConverter
    fun toLogLevel(value: String): LogLevel =
        try { LogLevel.valueOf(value) } catch (e: Exception) { LogLevel.INFO }
}
