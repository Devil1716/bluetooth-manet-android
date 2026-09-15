package com.devil1716.bluetoothmanet

import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Locale

/**
 * In-process diagnostics. No message bodies, file bytes, or key material.
 * Verbose lines stay in this bounded ring; they are not shipped anywhere.
 */
object MeshDiagnostics {
    const val MAX_EVENTS = 80
    private const val MAX_DETAIL_CHARS = 80

    data class Event(
        val timestampMs: Long,
        val category: String,
        val event: String,
        val detail: String
    )

    private val events = ArrayDeque<Event>(MAX_EVENTS)

    @JvmStatic
    @JvmOverloads
    fun record(category: String, event: String, detail: String = "") {
        val entry = Event(
            timestampMs = System.currentTimeMillis(),
            category = sanitizeToken(category),
            event = sanitizeToken(event),
            detail = sanitizeDetail(detail)
        )
        synchronized(events) {
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(entry)
        }
    }

    @JvmStatic
    fun snapshot(): List<Event> {
        synchronized(events) {
            return ArrayList(events)
        }
    }

    @JvmStatic
    fun clear() {
        synchronized(events) { events.clear() }
    }

    @JvmStatic
    fun sanitizeDetail(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var text = raw.replace('\n', ' ').replace('\r', ' ').trim()
        if (text.contains('|') || text.contains("BEGIN") || text.contains("PRIVATE")) {
            return "redacted"
        }
        if (looksLikeHexSecret(text)) return "redacted"
        if (text.length > MAX_DETAIL_CHARS) {
            text = text.substring(0, MAX_DETAIL_CHARS)
        }
        return text
    }

    private fun sanitizeToken(raw: String?): String {
        val value = raw?.trim().orEmpty().lowercase(Locale.US)
        if (value.isEmpty()) return "unknown"
        return value.replace(Regex("[^a-z0-9._-]"), "_").take(32)
    }

    private fun looksLikeHexSecret(text: String): Boolean {
        val compact = text.replace(" ", "")
        if (compact.length < 32) return false
        return compact.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    }
}
