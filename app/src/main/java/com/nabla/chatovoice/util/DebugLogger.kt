package com.nabla.chatovoice.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DebugLogger {
    private const val MAX_LINES = 60

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, msg: String) {
        val line = "[${timeFmt.format(Date())}] $tag: $msg"
        android.util.Log.d("ChatoVoice", line)
        val current = _logs.value.toMutableList()
        current.add(line)
        if (current.size > MAX_LINES) current.removeAt(0)
        _logs.value = current
    }

    fun clear() { _logs.value = emptyList() }
}
