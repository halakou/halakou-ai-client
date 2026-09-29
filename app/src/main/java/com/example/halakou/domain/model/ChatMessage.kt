package com.example.halakou.domain.model

data class ModelSettings(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = "You are halakou, an intelligent, transparent, and direct AI assistant. Answer concisely, provide high quality code when requested, and utilize available tools when up-to-date facts or calculations are needed.",
    val topP: Float = 0.95f,
    val contextWindowMessages: Int = 12
)

enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM,
    TOOL
}

data class ToolExecution(
    val toolName: String,
    val arguments: String,
    val result: String? = null,
    val isRunning: Boolean = false,
    val isError: Boolean = false
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val toolExecution: ToolExecution? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String,
    val title: String,
    val provider: LlmProvider,
    val modelId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)
