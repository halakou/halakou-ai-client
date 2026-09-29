package com.example.halakou.data.remote

import com.example.halakou.domain.model.ChatMessage
import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.model.MessageRole
import com.example.halakou.domain.model.ModelSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmGateway(
    private val client: OkHttpClient = defaultHttpClient
) {
    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Executes a chat completion call against the selected provider and model.
     */
    suspend fun generateResponse(
        provider: LlmProvider,
        modelId: String,
        apiKey: String,
        baseUrl: String,
        messages: List<ChatMessage>,
        settings: ModelSettings,
        systemPromptWithTools: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                LlmProvider.GEMINI -> callGemini(modelId, apiKey, baseUrl, messages, settings, systemPromptWithTools)
                LlmProvider.ANTHROPIC -> callAnthropic(modelId, apiKey, baseUrl, messages, settings, systemPromptWithTools)
                LlmProvider.OPENROUTER,
                LlmProvider.ATRIA_ASI,
                LlmProvider.OPENAI,
                LlmProvider.DEEPSEEK,
                LlmProvider.OLLAMA -> callOpenAiCompatible(provider, modelId, apiKey, baseUrl, messages, settings, systemPromptWithTools)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Google Gemini REST API implementation
     */
    private fun callGemini(
        modelId: String,
        apiKey: String,
        baseUrl: String,
        messages: List<ChatMessage>,
        settings: ModelSettings,
        systemPrompt: String
    ): Result<String> {
        val rootUrl = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else "https://generativelanguage.googleapis.com"
        val endpoint = "$rootUrl/v1beta/models/$modelId:generateContent?key=$apiKey"

        val rootJson = JSONObject()

        // System Instruction
        if (systemPrompt.isNotBlank()) {
            val systemInstruction = JSONObject()
            val parts = JSONArray().put(JSONObject().put("text", systemPrompt))
            systemInstruction.put("parts", parts)
            rootJson.put("systemInstruction", systemInstruction)
        }

        // Contents
        val contentsArray = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == MessageRole.USER) "user" else "model"
            val contentObj = JSONObject()
            contentObj.put("role", role)
            val partsArray = JSONArray().put(JSONObject().put("text", msg.content))
            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        rootJson.put("contents", contentsArray)

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", settings.temperature)
        genConfig.put("maxOutputTokens", settings.maxTokens)
        genConfig.put("topP", settings.topP)
        rootJson.put("generationConfig", genConfig)

        val request = Request.Builder()
            .url(endpoint)
            .post(rootJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = parseErrorMessage(responseBody, response.code)
            return Result.failure(IOException("Gemini API Error ($response.code): $errMessage"))
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            if (parts != null && parts.length() > 0) {
                val text = parts.getJSONObject(0).optString("text", "")
                return Result.success(text)
            }
        }

        return Result.failure(IOException("Gemini returned empty candidate content: $responseBody"))
    }

    /**
     * OpenAI-compatible endpoint used for OpenAI, Atria ASI (Dawn), DeepSeek, and local Ollama.
     */
    private fun callOpenAiCompatible(
        provider: LlmProvider,
        modelId: String,
        apiKey: String,
        baseUrl: String,
        messages: List<ChatMessage>,
        settings: ModelSettings,
        systemPrompt: String
    ): Result<String> {
        val rootUrl = (if (baseUrl.isNotBlank()) baseUrl else provider.defaultBaseUrl).trimEnd('/')
        val endpoint = if (rootUrl.endsWith("/chat/completions")) rootUrl else "$rootUrl/chat/completions"

        val rootJson = JSONObject()
        rootJson.put("model", modelId)
        rootJson.put("temperature", settings.temperature)
        rootJson.put("max_tokens", settings.maxTokens)

        val messagesArray = JSONArray()

        // System prompt
        if (systemPrompt.isNotBlank()) {
            val sysObj = JSONObject()
            sysObj.put("role", "system")
            sysObj.put("content", systemPrompt)
            messagesArray.put(sysObj)
        }

        for (msg in messages) {
            val roleStr = when (msg.role) {
                MessageRole.USER -> "user"
                MessageRole.ASSISTANT -> "assistant"
                MessageRole.TOOL -> "assistant"
                MessageRole.SYSTEM -> "system"
            }
            val msgObj = JSONObject()
            msgObj.put("role", roleStr)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }
        rootJson.put("messages", messagesArray)

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .header("HTTP-Referer", "https://github.com/halakou/halakou")
            .header("X-Title", "halakou AI Client")
            .post(rootJson.toString().toRequestBody(JSON_MEDIA_TYPE))

        if (apiKey.isNotBlank() && apiKey != "no-key-required") {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = parseErrorMessage(responseBody, response.code)
            return Result.failure(IOException("${provider.displayName} API Error (${response.code}): $errMessage"))
        }

        val json = JSONObject(responseBody)
        val choices = json.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.getJSONObject(0)
            val msg = choice.optJSONObject("message")
            val content = msg?.optString("content", "") ?: ""
            return Result.success(content)
        }

        return Result.failure(IOException("Empty choices array from ${provider.displayName}: $responseBody"))
    }

    /**
     * Anthropic Claude Messages API
     */
    private fun callAnthropic(
        modelId: String,
        apiKey: String,
        baseUrl: String,
        messages: List<ChatMessage>,
        settings: ModelSettings,
        systemPrompt: String
    ): Result<String> {
        val rootUrl = (if (baseUrl.isNotBlank()) baseUrl else "https://api.anthropic.com/v1").trimEnd('/')
        val endpoint = if (rootUrl.endsWith("/messages")) rootUrl else "$rootUrl/messages"

        val rootJson = JSONObject()
        rootJson.put("model", modelId)
        rootJson.put("max_tokens", settings.maxTokens)
        rootJson.put("temperature", settings.temperature)

        if (systemPrompt.isNotBlank()) {
            rootJson.put("system", systemPrompt)
        }

        val messagesArray = JSONArray()
        for (msg in messages) {
            val role = if (msg.role == MessageRole.USER) "user" else "assistant"
            val m = JSONObject()
            m.put("role", role)
            m.put("content", msg.content)
            messagesArray.put(m)
        }
        rootJson.put("messages", messagesArray)

        val request = Request.Builder()
            .url(endpoint)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(rootJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val err = parseErrorMessage(responseBody, response.code)
            return Result.failure(IOException("Anthropic API Error (${response.code}): $err"))
        }

        val json = JSONObject(responseBody)
        val contentArray = json.optJSONArray("content")
        if (contentArray != null && contentArray.length() > 0) {
            val first = contentArray.getJSONObject(0)
            val text = first.optString("text", "")
            return Result.success(text)
        }

        return Result.failure(IOException("Empty content from Anthropic: $responseBody"))
    }

    private fun parseErrorMessage(body: String, statusCode: Int): String {
        return try {
            val json = JSONObject(body)
            when {
                json.has("error") -> {
                    val err = json.get("error")
                    if (err is JSONObject) {
                        err.optString("message", err.toString())
                    } else err.toString()
                }
                json.has("message") -> json.getString("message")
                else -> body.take(200)
            }
        } catch (_: Exception) {
            if (body.isNotBlank()) body.take(200) else "HTTP $statusCode"
        }
    }
}
