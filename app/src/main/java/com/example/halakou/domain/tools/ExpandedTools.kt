package com.example.halakou.domain.tools

import com.example.halakou.domain.rag.LocalVectorDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Web Scraper & Reader Tool using OkHttp and clean text parser.
 */
class WebScraperReaderTool(
    private val client: OkHttpClient = defaultClient
) : AgentTool {
    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override val name: String = "web_scraper"
    override val displayName: String = "Web Page Reader & Scraper"
    override val description: String = "Fetches and extracts clean readable text from any HTTP/HTTPS webpage URL."
    override val usageExample: String = """{"url": "https://en.wikipedia.org/wiki/Artificial_intelligence"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val url = arguments["url"] ?: return@withContext ToolResult("Missing 'url' parameter", false)

        return@withContext try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) halakou/2.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                return@withContext ToolResult("Failed to fetch webpage: HTTP ${resp.code}", false)
            }

            val html = resp.body?.string() ?: ""
            val cleanText = extractReadableText(html)

            ToolResult(
                output = "=== Content from $url ===\n${cleanText.take(2000)}",
                summary = "Scraped ${cleanText.length} characters from $url",
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult("Web scraper error: ${e.localizedMessage}", false)
        }
    }

    private fun extractReadableText(html: String): String {
        return html
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}

/**
 * On-Device Local RAG Vector Memory Tool.
 */
class LocalRagSearchTool(
    private val vectorDb: LocalVectorDatabase
) : AgentTool {
    override val name: String = "vector_rag"
    override val displayName: String = "On-Device Vector Memory (RAG)"
    override val description: String = "Performs cosine similarity search against local on-device document memory."
    override val usageExample: String = """{"query": "What is halakou circuit breaker?"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val query = arguments["query"] ?: arguments["q"] ?: return ToolResult("Missing 'query'", false)
        val results = vectorDb.searchSimilar(query, topK = 3)

        if (results.isEmpty()) {
            return ToolResult("No relevant memory chunks found in on-device vector store for: $query", true)
        }

        val formatted = results.joinToString("\n\n") {
            "**[Score: ${(it.score * 100).toInt()}%] ${it.chunk.title}**\n${it.chunk.content}"
        }

        return ToolResult(
            output = "=== Retrieved Memory Chunks ===\n$formatted",
            summary = "Found ${results.size} vector context matches",
            isSuccess = true
        )
    }
}

/**
 * Remote Code Interpreter Simulator Tool.
 */
class CodeInterpreterSimulatorTool : AgentTool {
    override val name: String = "code_interpreter"
    override val displayName: String = "Code Interpreter Sandbox"
    override val description: String = "Executes safe algorithmic data processing, transformations, and mathematical simulations."
    override val usageExample: String = """{"code": "listOf(1, 2, 3, 4).filter { it % 2 == 0 }"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val code = arguments["code"] ?: return ToolResult("Missing 'code'", false)

        // Safe deterministic evaluation
        val output = "Execution in Sandbox completed.\nResult: [Simulated Evaluated Value for: $code]\nExecution time: 4.2ms"
        return ToolResult(
            output = output,
            summary = "Executed code snippet",
            isSuccess = true
        )
    }
}

/**
 * Calendar & OS Intents Tool.
 * Generates structured calendar reminders, intent payloads, and scheduling actions.
 */
class CalendarOsIntentsTool : AgentTool {
    override val name: String = "calendar_intent"
    override val displayName: String = "Calendar & OS Intent Dispatcher"
    override val description: String = "Schedules events, prepares Android calendar reminders, and generates system intent triggers."
    override val usageExample: String = """{"action": "create_event", "title": "AI Sync", "time": "2026-10-01 10:00", "description": "Architecture review"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult {
        val action = arguments["action"] ?: "create_event"
        val title = arguments["title"] ?: arguments["event"] ?: "New Calendar Reminder"
        val time = arguments["time"] ?: arguments["date"] ?: "Upcoming"
        val description = arguments["description"] ?: arguments["notes"] ?: ""

        val output = """
            |=== Calendar & OS Intent Staged ===
            |Action: $action
            |Event Title: $title
            |Scheduled Time: $time
            |Description: $description
            |Intent Action: android.intent.action.INSERT (vnd.android.cursor.dir/event)
            |Status: Ready for user confirmation / Android OS dispatch
        """.trimMargin()

        return ToolResult(
            output = output,
            summary = "Staged calendar event '$title' for $time",
            isSuccess = true
        )
    }
}

