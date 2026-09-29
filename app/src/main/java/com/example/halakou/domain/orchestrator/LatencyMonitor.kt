package com.example.halakou.domain.orchestrator

import com.example.halakou.domain.model.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

enum class LatencyTier(val displayName: String, val thresholdMs: Long) {
    ULTRA_FAST("Ultra Fast", 150),
    FAST("Fast", 300),
    MODERATE("Moderate", 600),
    DEGRADED("High Latency", Long.MAX_VALUE)
}

data class EndpointLatencyStats(
    val endpointKey: String,
    val provider: LlmProvider,
    val modelId: String,
    val baseUrl: String,
    val isVision: Boolean,
    val isPrimaryText: Boolean,
    val lastLatencyMs: Long = 0,
    val averageLatencyMs: Long = 0,
    val minLatencyMs: Long = 0,
    val sampleCount: Int = 0,
    val successCount: Int = 0,
    val isHealthy: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val tier: LatencyTier
        get() = when {
            !isHealthy -> LatencyTier.DEGRADED
            averageLatencyMs > 0 && averageLatencyMs < LatencyTier.ULTRA_FAST.thresholdMs -> LatencyTier.ULTRA_FAST
            averageLatencyMs > 0 && averageLatencyMs < LatencyTier.FAST.thresholdMs -> LatencyTier.FAST
            averageLatencyMs > 0 && averageLatencyMs < LatencyTier.MODERATE.thresholdMs -> LatencyTier.MODERATE
            else -> LatencyTier.DEGRADED
        }

    val successRatePercentage: Int
        get() = if (sampleCount > 0) ((successCount.toFloat() / sampleCount) * 100).roundToInt() else 100
}

data class RankedGateway(
    val rank: Int,
    val stats: EndpointLatencyStats,
    val isCurrentActive: Boolean = false
)

/**
 * LatencyMonitor Utility
 * 
 * Actively monitors, tracks, benchmarks, and ranks all AI gateways in the config queue.
 * Helps users identify and select the fastest available endpoint with lowest latency.
 */
class LatencyMonitor(
    private val client: OkHttpClient = defaultClient
) {
    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private val historyMap = ConcurrentHashMap<String, MutableList<Long>>()
    private val statsMap = ConcurrentHashMap<String, EndpointLatencyStats>()

    private val _rankedGateways = MutableStateFlow<List<RankedGateway>>(emptyList())
    val rankedGateways: StateFlow<List<RankedGateway>> = _rankedGateways.asStateFlow()

    private val _isBenchmarking = MutableStateFlow(false)
    val isBenchmarking: StateFlow<Boolean> = _isBenchmarking.asStateFlow()

    /**
     * Initializes or updates stats tracking for a list of endpoints.
     */
    fun initializeEndpoints(endpoints: List<OrchestratedEndpoint>, activeEndpointKey: String? = null) {
        for (ep in endpoints) {
            if (!statsMap.containsKey(ep.key)) {
                val initialLatency = if (ep.latencyMs > 0) ep.latencyMs else 120L
                val initialStats = EndpointLatencyStats(
                    endpointKey = ep.key,
                    provider = ep.provider,
                    modelId = ep.modelId,
                    baseUrl = ep.baseUrl,
                    isVision = ep.isVisionSupported,
                    isPrimaryText = ep.isPrimaryText,
                    lastLatencyMs = initialLatency,
                    averageLatencyMs = initialLatency,
                    minLatencyMs = initialLatency,
                    sampleCount = 1,
                    successCount = 1,
                    isHealthy = ep.isHealthy
                )
                statsMap[ep.key] = initialStats
                historyMap[ep.key] = mutableListOf(initialLatency)
            }
        }
        recalculateRankings(activeEndpointKey)
    }

    /**
     * Records a live observation whenever an endpoint executes a query.
     */
    fun recordObservation(endpointKey: String, latencyMs: Long, isSuccess: Boolean, activeEndpointKey: String? = null) {
        val current = statsMap[endpointKey] ?: return
        val history = historyMap.getOrPut(endpointKey) { mutableListOf() }

        if (isSuccess && latencyMs > 0) {
            history.add(latencyMs)
            if (history.size > 10) history.removeAt(0)
        }

        val avg = if (history.isNotEmpty()) history.average().toLong() else latencyMs
        val min = if (history.isNotEmpty()) (history.minOrNull() ?: latencyMs) else latencyMs
        val newSampleCount = current.sampleCount + 1
        val newSuccessCount = current.successCount + (if (isSuccess) 1 else 0)

        val updated = current.copy(
            lastLatencyMs = latencyMs,
            averageLatencyMs = avg,
            minLatencyMs = min,
            sampleCount = newSampleCount,
            successCount = newSuccessCount,
            isHealthy = isSuccess || (newSuccessCount.toFloat() / newSampleCount > 0.5f),
            lastUpdated = System.currentTimeMillis()
        )

        statsMap[endpointKey] = updated
        recalculateRankings(activeEndpointKey)
    }

    /**
     * Benchmarks all endpoints concurrently and updates rankings.
     */
    suspend fun benchmarkAll(endpoints: List<OrchestratedEndpoint>, activeEndpointKey: String? = null): List<RankedGateway> = withContext(Dispatchers.IO) {
        _isBenchmarking.value = true
        try {
            val deferredList = endpoints.map { ep ->
                async {
                    val measured = probeEndpoint(ep)
                    Pair(ep, measured)
                }
            }

            val results = deferredList.awaitAll()
            for ((ep, measured) in results) {
                recordObservation(
                    endpointKey = ep.key,
                    latencyMs = measured.first,
                    isSuccess = measured.second,
                    activeEndpointKey = activeEndpointKey
                )
            }

            recalculateRankings(activeEndpointKey)
            _rankedGateways.value
        } finally {
            _isBenchmarking.value = false
        }
    }

    /**
     * Performs a lightweight probe ping against the endpoint.
     */
    private suspend fun probeEndpoint(ep: OrchestratedEndpoint): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val probeUrl = when {
            ep.provider == LlmProvider.OPENROUTER -> "https://openrouter.ai/api/v1/models"
            ep.provider == LlmProvider.GEMINI -> "${ep.baseUrl.trimEnd('/')}/v1beta/models"
            ep.provider == LlmProvider.OLLAMA -> "${ep.baseUrl.trimEnd('/')}/api/tags"
            else -> "${ep.baseUrl.trimEnd('/')}/"
        }

        return@withContext try {
            val req = Request.Builder()
                .url(probeUrl)
                .header("User-Agent", "halakou-latency-monitor/2.0")
                .header("Cache-Control", "no-cache")
                .head() // Try fast HEAD method first
                .build()

            val resp = client.newCall(req).execute()
            val latency = System.currentTimeMillis() - start
            val healthy = resp.isSuccessful || resp.code in listOf(401, 403, 404, 405)
            resp.close()
            Pair(latency, healthy)
        } catch (_: Exception) {
            val fallbackLatency = System.currentTimeMillis() - start
            Pair(if (fallbackLatency > 0) fallbackLatency else 9999L, false)
        }
    }

    /**
     * Recalculates ranked gateways sorted by health and lowest average latency.
     */
    fun recalculateRankings(activeEndpointKey: String? = null) {
        val sortedList = statsMap.values
            .sortedWith(
                compareByDescending<EndpointLatencyStats> { it.isHealthy }
                    .thenBy { it.averageLatencyMs }
                    .thenByDescending { it.successRatePercentage }
            )
            .mapIndexed { index, stats ->
                RankedGateway(
                    rank = index + 1,
                    stats = stats,
                    isCurrentActive = stats.endpointKey == activeEndpointKey
                )
            }

        _rankedGateways.value = sortedList
    }

    /**
     * Gets the current fastest healthy gateway.
     */
    fun getFastestGateway(): RankedGateway? {
        return _rankedGateways.value.firstOrNull { it.stats.isHealthy }
    }
}
