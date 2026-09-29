package com.example.halakou.domain.tools

import java.util.concurrent.ConcurrentHashMap

class TextScratchpadTool : AgentTool {
    override val name: String = "scratchpad"
    override val displayName: String = "Local Scratchpad / File Reader"
    override val description: String = "Reads, writes, or lists persistent text notes and code drafts in local app storage."
    override val usageExample: String = """{"action": "write", "key": "project_notes", "content": "App architecture notes..."}"""

    companion object {
        private val notesStore = ConcurrentHashMap<String, String>().apply {
            put("default_memo", "Welcome to halakou BYOK AI Client. Privacy-first, local Room storage, hardware-backed Android KeyStore.")
        }
    }

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val action = arguments["action"]?.lowercase() ?: "read"
        val key = arguments["key"] ?: "default_memo"
        val content = arguments["content"] ?: ""

        return when (action) {
            "write", "save" -> {
                notesStore[key] = content
                ToolResult(
                    output = "Successfully saved entry '$key' (${content.length} characters).",
                    summary = "Saved scratchpad: $key",
                    isSuccess = true
                )
            }
            "read", "get" -> {
                val value = notesStore[key]
                if (value != null) {
                    ToolResult(
                        output = "=== Scratchpad Content [$key] ===\n$value",
                        summary = "Read scratchpad: $key",
                        isSuccess = true
                    )
                } else {
                    ToolResult(
                        output = "Scratchpad entry '$key' not found. Available keys: ${notesStore.keys.joinToString(", ")}",
                        isSuccess = false
                    )
                }
            }
            "list" -> {
                val list = notesStore.entries.joinToString("\n") { (k, v) -> "- **$k**: ${v.take(60)}..." }
                ToolResult(
                    output = "Active Scratchpad Notes:\n$list",
                    summary = "Listed ${notesStore.size} scratchpad items",
                    isSuccess = true
                )
            }
            "clear" -> {
                notesStore.remove(key)
                ToolResult(output = "Cleared scratchpad key '$key'", isSuccess = true)
            }
            else -> ToolResult(output = "Unknown action '$action'. Valid actions: read, write, list, clear.", isSuccess = false)
        }
    }
}
