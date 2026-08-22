package com.example.engine

import android.util.Log
import com.example.data.db.LogDao
import com.example.data.model.LogEntryEntity
import com.example.data.model.LogLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object AppLogger {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logDao: LogDao? = null

    private val _logsFlow = MutableStateFlow<List<LogEntryEntity>>(emptyList())
    val logsFlow: StateFlow<List<LogEntryEntity>> = _logsFlow.asStateFlow()

    fun init(dao: LogDao) {
        logDao = dao
    }

    fun log(level: LogLevel, tag: String, message: String, taskId: String? = null) {
        // Redact sensitive patterns (passwords, tokens, cookies)
        val sanitized = sanitize(message)

        when (level) {
            LogLevel.DEBUG -> Log.d(tag, sanitized)
            LogLevel.INFO -> Log.i(tag, sanitized)
            LogLevel.WARNING -> Log.w(tag, sanitized)
            LogLevel.ERROR -> Log.e(tag, sanitized)
        }

        val entity = LogEntryEntity(
            taskId = taskId,
            level = level,
            tag = tag,
            message = sanitized,
            timestamp = System.currentTimeMillis()
        )

        val current = _logsFlow.value.toMutableList()
        if (current.size > 300) {
            current.removeAt(current.size - 1)
        }
        current.add(0, entity)
        _logsFlow.value = current

        logDao?.let { dao ->
            scope.launch {
                try {
                    dao.insertLog(entity)
                } catch (e: Exception) {
                    // Ignore db errors during logging
                }
            }
        }
    }

    fun d(tag: String, msg: String, taskId: String? = null) = log(LogLevel.DEBUG, tag, msg, taskId)
    fun i(tag: String, msg: String, taskId: String? = null) = log(LogLevel.INFO, tag, msg, taskId)
    fun w(tag: String, msg: String, taskId: String? = null) = log(LogLevel.WARNING, tag, msg, taskId)
    fun e(tag: String, msg: String, taskId: String? = null) = log(LogLevel.ERROR, tag, msg, taskId)

    fun sanitize(input: String): String {
        return input
            .replace(Regex("(?i)(password|token|auth|cookie|key)=[^&\\s]+"), "$1=***REDACTED***")
            .replace(Regex("(?i)Bearer\\s+[a-zA-Z0-9._\\-]+"), "Bearer ***REDACTED***")
    }
}
