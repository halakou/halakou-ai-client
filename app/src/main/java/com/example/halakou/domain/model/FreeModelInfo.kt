package com.example.halakou.domain.model

enum class FreeModelCategory(val displayName: String, val iconEmoji: String) {
    VISION("Vision & Multimodal", "👁️"),
    REASONING("Deep Reasoning & Math", "🧠"),
    CODING("Code & Engineering", "💻"),
    GENERAL("Fast Everyday Chat", "⚡")
}

data class FreeModelInfo(
    val id: String,
    val name: String,
    val provider: LlmProvider,
    val isVisionCapable: Boolean,
    val category: FreeModelCategory,
    val description: String,
    val contextLength: Int = 32768,
    val pricing: String = "100% Free",
    val isSelected: Boolean = false
)
