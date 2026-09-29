package com.example

import com.example.halakou.domain.orchestrator.CircuitBreaker
import com.example.halakou.domain.orchestrator.CircuitState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @Before
    fun setUp() {
        circuitBreaker = CircuitBreaker(failureThreshold = 1, cooldownDurationMs = 60_000)
    }

    @Test
    fun `initial state allows execution`() {
        assertTrue(circuitBreaker.canExecute("atria-dawn-primary"))
        assertEquals(CircuitState.CLOSED, circuitBreaker.getStatus("atria-dawn-primary").state)
    }

    @Test
    fun `trips instantly on HTTP 429 rate limit error`() {
        val error429 = IOException("Atria ASI API Error (429): Rate limit exceeded, tokens exhausted")
        val shouldTrip = circuitBreaker.recordFailure("atria-dawn-primary", error429)

        assertTrue(shouldTrip)
        assertFalse(circuitBreaker.canExecute("atria-dawn-primary"))
        assertEquals(CircuitState.OPEN, circuitBreaker.getStatus("atria-dawn-primary").state)
    }

    @Test
    fun `trips instantly on HTTP 403 quota error`() {
        val error403 = IOException("HTTP 403: Quota exhausted or insufficient credits")
        val shouldTrip = circuitBreaker.recordFailure("primary-endpoint", error403)

        assertTrue(shouldTrip)
        assertFalse(circuitBreaker.canExecute("primary-endpoint"))
        assertEquals(CircuitState.OPEN, circuitBreaker.getStatus("primary-endpoint").state)
    }

    @Test
    fun `trips on socket timeout`() {
        val timeout = SocketTimeoutException("connect timed out")
        val shouldTrip = circuitBreaker.recordFailure("timeout-endpoint", timeout)

        assertTrue(shouldTrip)
        assertFalse(circuitBreaker.canExecute("timeout-endpoint"))
    }

    @Test
    fun `independent endpoints do not block each other during fallback`() {
        // Trip primary endpoint
        circuitBreaker.recordFailure("atria-dawn-primary", IOException("HTTP 429: Too Many Requests"))
        assertFalse(circuitBreaker.canExecute("atria-dawn-primary"))

        // Fallback endpoint must remain healthy and executable
        assertTrue(circuitBreaker.canExecute("openrouter-gemini-vision-free"))
        assertTrue(circuitBreaker.canExecute("openrouter-llama70b-free"))
    }

    @Test
    fun `recordSuccess resets failure status`() {
        circuitBreaker.recordFailure("test-ep", IOException("HTTP 429"))
        assertFalse(circuitBreaker.canExecute("test-ep"))

        circuitBreaker.recordSuccess("test-ep")
        assertTrue(circuitBreaker.canExecute("test-ep"))
        assertEquals(CircuitState.CLOSED, circuitBreaker.getStatus("test-ep").state)
    }
}
