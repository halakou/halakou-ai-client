package com.example.halakou.domain.orchestrator

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

enum class CircuitState {
    CLOSED,     // Healthy & normal
    OPEN,       // Tripped - route bypassed
    HALF_OPEN   // Probe testing
}

data class CircuitStatus(
    val state: CircuitState = CircuitState.CLOSED,
    val failureCount: Int = 0,
    val lastFailureTimestamp: Long = 0,
    val lastFailureReason: String = ""
)

/**
 * Zero-Dependency, high-resilience Circuit Breaker for Autonomous AI Orchestration.
 * Tracks endpoint failures (HTTP 429 RateLimit, HTTP 403 Quota, timeouts)
 * and trips instantly to allow zero-friction auto-fallback.
 */
class CircuitBreaker(
    private val failureThreshold: Int = 1, // Trip immediately on rate-limit / quota exhausted
    private val cooldownDurationMs: Long = 60_000 // 1 minute cooldown
) {
    private val endpointCircuits = ConcurrentHashMap<String, CircuitStatus>()

    fun canExecute(endpointKey: String): Boolean {
        val status = endpointCircuits[endpointKey] ?: return true
        return when (status.state) {
            CircuitState.CLOSED -> true
            CircuitState.OPEN -> {
                val elapsed = System.currentTimeMillis() - status.lastFailureTimestamp
                if (elapsed > cooldownDurationMs) {
                    // Transition to HALF_OPEN to test recovery
                    endpointCircuits[endpointKey] = status.copy(state = CircuitState.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            CircuitState.HALF_OPEN -> true
        }
    }

    fun recordSuccess(endpointKey: String) {
        endpointCircuits[endpointKey] = CircuitStatus(state = CircuitState.CLOSED, failureCount = 0)
    }

    fun recordFailure(endpointKey: String, error: Throwable): Boolean {
        val isTrippingError = isCircuitTrippingError(error)
        val current = endpointCircuits[endpointKey] ?: CircuitStatus()
        val newCount = current.failureCount + 1
        val shouldTrip = isTrippingError || newCount >= failureThreshold

        endpointCircuits[endpointKey] = current.copy(
            state = if (shouldTrip) CircuitState.OPEN else CircuitState.CLOSED,
            failureCount = newCount,
            lastFailureTimestamp = System.currentTimeMillis(),
            lastFailureReason = error.localizedMessage ?: "Unknown failure"
        )

        return shouldTrip
    }

    fun isCircuitTrippingError(error: Throwable): Boolean {
        val msg = error.message?.lowercase() ?: ""
        return when {
            error is SocketTimeoutException -> true
            msg.contains("429") || msg.contains("rate limit") -> true
            msg.contains("403") || msg.contains("quota") || msg.contains("credit") -> true
            msg.contains("502") || msg.contains("503") || msg.contains("504") -> true
            msg.contains("overloaded") || msg.contains("capacity") -> true
            else -> false
        }
    }

    fun getStatus(endpointKey: String): CircuitStatus {
        return endpointCircuits[endpointKey] ?: CircuitStatus()
    }

    fun resetAll() {
        endpointCircuits.clear()
    }
}
