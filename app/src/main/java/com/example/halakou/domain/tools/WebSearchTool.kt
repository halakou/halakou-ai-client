package com.example.halakou.domain.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchTool(private val client: OkHttpClient = defaultClient) : AgentTool {

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    override val name: String = "web_search"
    override val displayName: String = "DuckDuckGo Web Search"
    override val description: String = "Searches the live public internet using DuckDuckGo for real-time news, documentation, or facts."
    override val usageExample: String = """{"query": "Latest Kotlin version and features"}"""

    override suspend fun execute(arguments: Map<String, String>): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments["query"] ?: arguments["q"] ?: return@withContext ToolResult(
            output = "Error: Missing required parameter 'query'.",
            isSuccess = false
        )

        try {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "halakou-open-source-ai/1.0 (Android; BYOK)")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext ToolResult(
                    output = "Search HTTP error: ${response.code}",
                    isSuccess = false
                )
            }

            val json = JSONObject(responseBody)
            val abstractText = json.optString("AbstractText", "")
            val abstractSource = json.optString("AbstractSource", "")
            val abstractUrl = json.optString("AbstractURL", "")
            val heading = json.optString("Heading", "")

            val relatedTopics = json.optJSONArray("RelatedTopics")
            val snippetList = mutableListOf<String>()

            if (abstractText.isNotBlank()) {
                snippetList.add("**$heading** ($abstractSource)\n$abstractText\nSource: $abstractUrl")
            }

            if (relatedTopics != null) {
                for (i in 0 until minOf(relatedTopics.length(), 4)) {
                    val topic = relatedTopics.optJSONObject(i)
                    if (topic != null) {
                        val text = topic.optString("Text", "")
                        val firstUrl = topic.optString("FirstURL", "")
                        if (text.isNotBlank()) {
                            snippetList.add("- $text ($firstUrl)")
                        }
                    }
                }
            }

            if (snippetList.isEmpty()) {
                // If DuckDuckGo instant answer had no direct snippet, return informational result with link
                return@withContext ToolResult(
                    output = "Direct DuckDuckGo Instant Answer returned no instant card for \"$query\". Query URL: https://duckduckgo.com/?q=$encodedQuery",
                    summary = "Searched DuckDuckGo for: $query",
                    isSuccess = true
                )
            }

            val formattedOutput = snippetList.joinToString("\n\n")
            ToolResult(
                output = formattedOutput,
                summary = "DuckDuckGo: Found ${snippetList.size} live results for \"$query\"",
                isSuccess = true
            )
        } catch (e: Exception) {
            ToolResult(
                output = "Web search execution error: ${e.localizedMessage ?: "Unknown network failure"}",
                isSuccess = false
            )
        }
    }
}
