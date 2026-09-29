package com.example.halakou.domain.tools

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DateTimeTool : AgentTool {
    override val name: String = "datetime"
    override val displayName: String = "Current Clock & Timezone"
    override val description: String = "Provides real-time system clock, ISO-8601 timestamps, day of week, and timezone information."
    override val usageExample: String = """{"timezone": "local"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val now = Date()
        val requestedTz = arguments["timezone"] ?: "local"

        val localFormat = SimpleDateFormat("EEEE, MMMM d, yyyy HH:mm:ss z", Locale.getDefault())
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val targetTz = if (requestedTz.equals("utc", ignoreCase = true)) {
            TimeZone.getTimeZone("UTC")
        } else {
            TimeZone.getDefault()
        }

        val targetFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (z)", Locale.getDefault()).apply {
            timeZone = targetTz
        }

        val resultString = buildString {
            appendLine("Current Local Time: ${localFormat.format(now)}")
            appendLine("UTC ISO-8601: ${isoFormat.format(now)}")
            appendLine("Timezone: ${targetTz.id} (Offset: ${targetTz.rawOffset / (1000 * 60 * 60)}h)")
            appendLine("Unix Epoch: ${now.time}")
        }

        return ToolResult(
            output = resultString.trimEnd(),
            summary = "Checked system clock: ${targetFormat.format(now)}",
            isSuccess = true
        )
    }
}
