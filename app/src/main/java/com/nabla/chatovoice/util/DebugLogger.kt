package com.nabla.chatovoice.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val MAX_LINES = 200
    private const val LOG_FILE = "debug_current.log"
    private const val LOG_PREV_FILE = "debug_previous.log"

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private val lock = Any()

    /** Call once from Application.onCreate() */
    fun init(context: Context) {
        val dir = context.filesDir
        val current = File(dir, LOG_FILE)
        val previous = File(dir, LOG_PREV_FILE)

        // Rotate: current → previous
        if (current.exists()) {
            previous.delete()
            current.renameTo(previous)
        }

        logFile = current
        current.createNewFile()

        // Load previous session logs into memory with separator
        val lines = mutableListOf<String>()
        if (previous.exists()) {
            val prevLines = previous.readLines()
            if (prevLines.isNotEmpty()) {
                lines.add("──── Previous session ────")
                lines.addAll(prevLines.takeLast(MAX_LINES))
                lines.add("──── Current session ────")
            }
        }
        _logs.value = lines
    }

    fun log(tag: String, msg: String) {
        val line = "[${timeFmt.format(Date())}] $tag: $msg"
        android.util.Log.d("ChatoVoice", line)
        synchronized(lock) {
            // Write to file
            logFile?.appendText(line + "\n")
            // Update in-memory flow
            val current = _logs.value.toMutableList()
            current.add(line)
            // Keep max lines but preserve the section headers
            val nonHeader = current.count { !it.startsWith("────") }
            if (nonHeader > MAX_LINES) {
                val firstNonHeader = current.indexOfFirst { !it.startsWith("────") }
                if (firstNonHeader >= 0) current.removeAt(firstNonHeader)
            }
            _logs.value = current
        }
    }

    fun clear() {
        synchronized(lock) {
            _logs.value = emptyList()
            logFile?.writeText("")
            val dir = logFile?.parentFile
            dir?.let { File(it, LOG_PREV_FILE).delete() }
        }
    }
}
