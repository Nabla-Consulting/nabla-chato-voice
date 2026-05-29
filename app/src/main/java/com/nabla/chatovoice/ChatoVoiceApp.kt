package com.nabla.chatovoice

import android.app.Application
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatoVoiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogger.init(this)

        // Catch uncaught exceptions and write to debug log before crash
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                DebugLogger.log("CRASH", "UNCAUGHT: ${throwable::class.simpleName}: ${throwable.message}")
                DebugLogger.log("CRASH", throwable.stackTraceToString().take(1000))
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
