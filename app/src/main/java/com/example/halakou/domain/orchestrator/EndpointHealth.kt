package com.example.halakou.domain.orchestrator

import com.example.halakou.domain.model.LlmProvider

data class OrchestratedEndpoint(
    val key: String,
    val provider: LlmProvider,
    val modelId: String,
    val baseUrl: String,
    val isVisionSupported: Boolean,
    val isPrimaryText: Boolean = false,
    val priority: Int = 10,
    var latencyMs: Long = 0,
    var isHealthy: Boolean = true,
    var lastCheckTimestamp: Long = 0
)

data class FallbackEvent(
    val fromModel: String,
    val toModel: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
