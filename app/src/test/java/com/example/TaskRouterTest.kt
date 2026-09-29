package com.example

import com.example.halakou.domain.model.LlmProvider
import com.example.halakou.domain.orchestrator.AutonomousAggregator
import com.example.halakou.domain.orchestrator.CircuitBreaker
import com.example.halakou.domain.orchestrator.OrchestratedTaskType
import com.example.halakou.domain.orchestrator.TaskRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskRouterTest {

    private lateinit var aggregator: AutonomousAggregator
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var taskRouter: TaskRouter

    @Before
    fun setUp() {
        aggregator = AutonomousAggregator()
        circuitBreaker = CircuitBreaker()
        taskRouter = TaskRouter(aggregator, circuitBreaker)
    }

    @Test
    fun `standard text prompt routes to Atria ASI as primary text engine`() {
        val decision = taskRouter.resolveRoute(
            userPrompt = "Explain quantum computing in simple terms.",
            hasImageAttachments = false
        )

        assertEquals(OrchestratedTaskType.TEXT_ONLY, decision.taskType)
        assertFalse(decision.isVisionBypass)
        assertEquals(LlmProvider.ATRIA_ASI, decision.selectedEndpoint.provider)
        assertEquals("atria-dawn-primary", decision.selectedEndpoint.key)
    }

    @Test
    fun `image attachment automatically intercepts and routes to Vision model`() {
        val decision = taskRouter.resolveRoute(
            userPrompt = "What is written in this document?",
            hasImageAttachments = true
        )

        assertEquals(OrchestratedTaskType.VISION_MULTIMODAL, decision.taskType)
        assertTrue(decision.isVisionBypass)
        assertTrue(decision.selectedEndpoint.isVisionSupported)
    }

    @Test
    fun `vision query keywords intercept and route to Vision model`() {
        val decision = taskRouter.resolveRoute(
            userPrompt = "Look at this photo and describe this image",
            hasImageAttachments = false
        )

        assertEquals(OrchestratedTaskType.VISION_MULTIMODAL, decision.taskType)
        assertTrue(decision.isVisionBypass)
        assertTrue(decision.selectedEndpoint.isVisionSupported)
    }

    @Test
    fun `when primary text endpoint is tripped, TaskRouter selects next healthy fallback`() {
        // Trip Atria ASI
        circuitBreaker.recordFailure("atria-dawn-primary", java.io.IOException("HTTP 429: Rate limited"))
        assertFalse(circuitBreaker.canExecute("atria-dawn-primary"))

        val decision = taskRouter.resolveRoute(
            userPrompt = "Hello AI",
            hasImageAttachments = false
        )

        // Circuit breaker bypasses tripped endpoint and picks next candidate
        assertTrue(circuitBreaker.canExecute(decision.selectedEndpoint.key))
        assertTrue(decision.selectedEndpoint.key != "atria-dawn-primary")
    }
}
