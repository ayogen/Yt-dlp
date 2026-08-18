package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.LogEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDao {
    @Query("SELECT * FROM system_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsFlow(limit: Int = 200): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM system_logs WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun getLogsForTaskFlow(taskId: String): Flow<List<LogEntryEntity>>

    @Insert
    suspend fun insertLog(log: LogEntryEntity)

    @Query("DELETE FROM system_logs")
    suspend fun clearLogs()
}
