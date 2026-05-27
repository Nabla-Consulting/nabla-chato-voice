package com.nabla.chatovoice.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

private const val MAX_CONTEXT_CHARS = 500
private const val MAX_TOP_NODES = 20

class ChatoAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val root = rootInActiveWindow ?: return
                screenContext = extractContext(root)
                root.recycle()
            }
        }
    }

    override fun onInterrupt() {
        screenContext = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        screenContext = null
    }

    private fun extractContext(root: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()
        val childCount = minOf(root.childCount, MAX_TOP_NODES)

        for (i in 0 until childCount) {
            val child = root.getChild(i) ?: continue
            val text = child.text?.toString()
            val desc = child.contentDescription?.toString()
            val combined = listOfNotNull(text, desc)
                .filter { it.isNotBlank() }
                .joinToString(" / ")
            if (combined.isNotBlank()) {
                parts.add(combined)
            }
            child.recycle()
        }

        return parts.joinToString(" | ").take(MAX_CONTEXT_CHARS)
    }

    companion object {
        @Volatile
        var screenContext: String? = null
            private set

        @Volatile
        var isConnected: Boolean = false
            private set
    }
}
