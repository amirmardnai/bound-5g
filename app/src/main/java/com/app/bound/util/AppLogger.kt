package com.app.bound.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val error: String? = null,
)

object AppLogger {
    private var nextId = 1L
    private const val MAX_ENTRIES = 60
    val logs = mutableStateListOf<LogEntry>()
    private val timeFormat by lazy { SimpleDateFormat("HH:mm:ss", Locale.US) }

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        val timeStr = timeFormat.format(Date())
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
        val entry = LogEntry(
            id = nextId++,
            timestamp = timeStr,
            level = level,
            tag = tag,
            message = if (message.length > 300) message.take(300) + "…" else message,
            error = throwable?.message,
        )
        while (logs.size >= MAX_ENTRIES) {
            logs.removeAt(0)
        }
        logs.add(entry)
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String, t: Throwable? = null) = log(LogLevel.WARN, tag, message, t)
    fun e(tag: String, message: String, t: Throwable? = null) = log(LogLevel.ERROR, tag, message, t)
}
