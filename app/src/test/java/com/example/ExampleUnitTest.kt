package com.example

import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.orchestrator.AutonomousAggregator
import com.example.halakou.domain.orchestrator.CircuitBreaker
import com.example.halakou.domain.orchestrator.OrchestratedTaskType
import com.example.halakou.domain.orchestrator.TaskRouter
import com.example.halakou.domain.tools.CalculatorTool
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HalakouOrchestratorTest {

    @Test
    fun testCalculatorTool() = runBlocking {
        val calc = CalculatorTool()
        val result = calc.execute(mapOf("expression" to "(10 * 5) + sqrt(144) / 2"))
        assertTrue(result.isSuccess)
        assertTrue(result.output.contains("56.0"))
    }

    @Test
    fun testTaskRouterDefaultsToAtriaAsiForText() {
        val circuitBreaker = CircuitBreaker()
        val aggregator = AutonomousAggregator()
        val router = TaskRouter(aggregator, circuitBreaker)

        val textRoute = router.resolveRoute("Hello! Please explain clean architecture in Kotlin.", hasImageAttachments = false)
        assertEquals(LlmProvider.ATRIA_ASI, textRoute.selectedEndpoint.provider)
        assertEquals("Atria-Dawn-Preview", textRoute.selectedEndpoint.modelId)
        assertFalse(textRoute.isVisionBypass)
    }

    @Test
    fun testTaskRouterBypassesAtriaForVisionTasks() {
        val circuitBreaker = CircuitBreaker()
        val aggregator = AutonomousAggregator()
        val router = TaskRouter(aggregator, circuitBreaker)

        val visionRoute = router.resolveRoute("Please describe what is in this photo", hasImageAttachments = true)
        assertEquals(OrchestratedTaskType.VISION_MULTIMODAL, visionRoute.taskType)
        assertTrue(visionRoute.isVisionBypass)
        assertTrue(visionRoute.selectedEndpoint.isVisionSupported)
    }

    @Test
    fun testCircuitBreakerTripsOnRateLimit() {
        val breaker = CircuitBreaker()
        val endpoint = "test-atria-endpoint"

        assertTrue(breaker.canExecute(endpoint))

        // Simulate HTTP 429 rate limit error
        val tripped = breaker.recordFailure(endpoint, IOException("HTTP 429: Too Many Requests / Rate limit exceeded"))
        assertTrue(tripped)
        assertFalse(breaker.canExecute(endpoint))
    }
}
