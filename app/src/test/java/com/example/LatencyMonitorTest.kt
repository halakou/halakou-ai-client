package com.example

import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.orchestrator.LatencyMonitor
import com.example.halakou.domain.orchestrator.LatencyTier
import com.example.halakou.domain.orchestrator.OrchestratedEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LatencyMonitorTest {

    private lateinit var latencyMonitor: LatencyMonitor
    private lateinit var sampleEndpoints: List<OrchestratedEndpoint>

    @Before
    fun setUp() {
        latencyMonitor = LatencyMonitor()
        sampleEndpoints = listOf(
            OrchestratedEndpoint(
                key = "atria-dawn-primary",
                provider = LlmProvider.ATRIA_ASI,
                modelId = "Atria-Dawn-Preview",
                baseUrl = "https://api.atria-asi.ai/v1",
                isVisionSupported = false,
                isPrimaryText = true,
                priority = 1,
                latencyMs = 110,
                isHealthy = true
            ),
            OrchestratedEndpoint(
                key = "gemini-flash-vision",
                provider = LlmProvider.OPENROUTER,
                modelId = "google/gemini-2.0-flash-exp:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = true,
                priority = 2,
                latencyMs = 95,
                isHealthy = true
            ),
            OrchestratedEndpoint(
                key = "llama-70b-instruct",
                provider = LlmProvider.OPENROUTER,
                modelId = "meta-llama/llama-3.3-70b-instruct:free",
                baseUrl = "https://openrouter.ai/api/v1",
                isVisionSupported = false,
                priority = 3,
                latencyMs = 240,
                isHealthy = true
            )
        )
    }

    @Test
    fun `endpoints are initialized and ranked by lowest latency`() {
        latencyMonitor.initializeEndpoints(sampleEndpoints, activeEndpointKey = "atria-dawn-primary")
        val rankings = latencyMonitor.rankedGateways.value

        assertEquals(3, rankings.size)
        // Gemini has lowest initial latency (95ms) -> Rank 1
        assertEquals("gemini-flash-vision", rankings[0].stats.endpointKey)
        assertEquals(1, rankings[0].rank)
        assertEquals(95L, rankings[0].stats.averageLatencyMs)

        // Atria has 110ms -> Rank 2
        assertEquals("atria-dawn-primary", rankings[1].stats.endpointKey)
        assertEquals(2, rankings[1].rank)
        assertTrue(rankings[1].isCurrentActive)

        // Llama has 240ms -> Rank 3
        assertEquals("llama-70b-instruct", rankings[2].stats.endpointKey)
        assertEquals(3, rankings[2].rank)
    }

    @Test
    fun `recording faster observation promotes gateway to rank 1`() {
        latencyMonitor.initializeEndpoints(sampleEndpoints, activeEndpointKey = "atria-dawn-primary")

        // Record ultra-fast response for Atria ASI (e.g. 70ms)
        latencyMonitor.recordObservation("atria-dawn-primary", 70L, isSuccess = true)

        val updatedRankings = latencyMonitor.rankedGateways.value
        val topGateway = updatedRankings.first()

        assertEquals("atria-dawn-primary", topGateway.stats.endpointKey)
        assertEquals(1, topGateway.rank)
        assertTrue(topGateway.stats.averageLatencyMs < 100)
    }

    @Test
    fun `unhealthy endpoint is demoted to bottom of rankings`() {
        latencyMonitor.initializeEndpoints(sampleEndpoints)

        // Mark Gemini as failing / unhealthy
        latencyMonitor.recordObservation("gemini-flash-vision", 5000L, isSuccess = false)
        latencyMonitor.recordObservation("gemini-flash-vision", 5000L, isSuccess = false)

        val rankings = latencyMonitor.rankedGateways.value
        val lastGateway = rankings.last()

        assertEquals("gemini-flash-vision", lastGateway.stats.endpointKey)
        assertFalse(lastGateway.stats.isHealthy)
        assertEquals(LatencyTier.DEGRADED, lastGateway.stats.tier)
    }

    @Test
    fun `latency tier classification is correct`() {
        latencyMonitor.initializeEndpoints(sampleEndpoints)

        latencyMonitor.recordObservation("ultra-fast", 120L, true)
        val rankings = latencyMonitor.rankedGateways.value
        val atria = rankings.find { it.stats.endpointKey == "atria-dawn-primary" }
        assertNotNull(atria)
        assertEquals(LatencyTier.ULTRA_FAST, atria!!.stats.tier)

        val llama = rankings.find { it.stats.endpointKey == "llama-70b-instruct" }
        assertNotNull(llama)
        assertEquals(LatencyTier.FAST, llama!!.stats.tier)
    }

    @Test
    fun `getFastestGateway returns healthy lowest latency endpoint`() {
        latencyMonitor.initializeEndpoints(sampleEndpoints)
        val fastest = latencyMonitor.getFastestGateway()

        assertNotNull(fastest)
        assertEquals("gemini-flash-vision", fastest!!.stats.endpointKey)
        assertEquals(95L, fastest.stats.averageLatencyMs)
    }
}
