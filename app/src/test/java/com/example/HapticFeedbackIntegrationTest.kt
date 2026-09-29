package com.example

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.test.core.app.ApplicationProvider
import com.example.halakou.presentation.mvi.ChatSideEffect
import com.example.halakou.presentation.util.AppHapticConstants
import com.example.halakou.presentation.util.HapticFeedbackHelper
import com.example.halakou.presentation.util.HapticInteraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HapticFeedbackIntegrationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `haptic interaction enum contains required tactile interactions`() {
        val interactions = HapticInteraction.values().toList()
        assertTrue("Must contain SEND_MESSAGE", interactions.contains(HapticInteraction.SEND_MESSAGE))
        assertTrue("Must contain TOOL_SUCCESS", interactions.contains(HapticInteraction.TOOL_SUCCESS))
        assertTrue("Must contain CIRCUIT_BREAKER_FALLBACK", interactions.contains(HapticInteraction.CIRCUIT_BREAKER_FALLBACK))
        assertTrue("Must contain STANDARD_CLICK", interactions.contains(HapticInteraction.STANDARD_CLICK))
        assertTrue("Must contain LONG_PRESS", interactions.contains(HapticInteraction.LONG_PRESS))
        assertTrue("Must contain ERROR_ALERT", interactions.contains(HapticInteraction.ERROR_ALERT))
    }

    @Test
    fun `app haptic constants map to valid Android HapticFeedbackConstants`() {
        assertNotNull(AppHapticConstants.HAPTIC_CONFIRM)
        assertNotNull(AppHapticConstants.HAPTIC_REJECT)
        assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, AppHapticConstants.HAPTIC_KEYBOARD_TAP)
        assertEquals(HapticFeedbackConstants.LONG_PRESS, AppHapticConstants.HAPTIC_LONG_PRESS)
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY, AppHapticConstants.HAPTIC_VIRTUAL_KEY)
    }

    @Test
    fun `performHaptic runs cleanly with null view and null compose haptic`() {
        for (interaction in HapticInteraction.values()) {
            // Should never throw, graceful fallback
            HapticFeedbackHelper.performHaptic(null, null, interaction)
        }
    }

    @Test
    fun `performHaptic dispatches to Compose HapticFeedback when view is null`() {
        var lastType: HapticFeedbackType? = null
        val mockComposeHaptic = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                lastType = hapticFeedbackType
            }
        }

        HapticFeedbackHelper.performHaptic(null, mockComposeHaptic, HapticInteraction.SEND_MESSAGE)
        assertEquals(HapticFeedbackType.TextHandleMove, lastType)

        HapticFeedbackHelper.performHaptic(null, mockComposeHaptic, HapticInteraction.TOOL_SUCCESS)
        assertEquals(HapticFeedbackType.LongPress, lastType)

        HapticFeedbackHelper.performHaptic(null, mockComposeHaptic, HapticInteraction.CIRCUIT_BREAKER_FALLBACK)
        assertEquals(HapticFeedbackType.LongPress, lastType)

        HapticFeedbackHelper.performHaptic(null, mockComposeHaptic, HapticInteraction.STANDARD_CLICK)
        assertEquals(HapticFeedbackType.TextHandleMove, lastType)
    }

    @Test
    fun `performHaptic invokes view performHapticFeedback with matching constants`() {
        var lastViewConstant: Int? = null
        val testView = object : View(context) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean {
                lastViewConstant = feedbackConstant
                return true
            }

            override fun performHapticFeedback(feedbackConstant: Int, flags: Int): Boolean {
                lastViewConstant = feedbackConstant
                return true
            }
        }

        HapticFeedbackHelper.performHaptic(testView, null, HapticInteraction.SEND_MESSAGE)
        assertEquals(AppHapticConstants.HAPTIC_KEYBOARD_TAP, lastViewConstant)

        HapticFeedbackHelper.performHaptic(testView, null, HapticInteraction.TOOL_SUCCESS)
        assertEquals(AppHapticConstants.HAPTIC_CONFIRM, lastViewConstant)

        HapticFeedbackHelper.performHaptic(testView, null, HapticInteraction.CIRCUIT_BREAKER_FALLBACK)
        assertEquals(AppHapticConstants.HAPTIC_REJECT, lastViewConstant)

        HapticFeedbackHelper.performHaptic(testView, null, HapticInteraction.STANDARD_CLICK)
        assertEquals(AppHapticConstants.HAPTIC_VIRTUAL_KEY, lastViewConstant)
    }

    @Test
    fun `performVibration runs cleanly in Robolectric test environment`() {
        for (interaction in HapticInteraction.values()) {
            HapticFeedbackHelper.performVibration(context, interaction)
        }
    }

    @Test
    fun `chat side effect trigger haptic carries correct interaction payload`() {
        val sendHaptic = ChatSideEffect.TriggerHaptic(HapticInteraction.SEND_MESSAGE)
        assertEquals(HapticInteraction.SEND_MESSAGE, sendHaptic.interaction)

        val toolSuccessHaptic = ChatSideEffect.TriggerHaptic(HapticInteraction.TOOL_SUCCESS)
        assertEquals(HapticInteraction.TOOL_SUCCESS, toolSuccessHaptic.interaction)

        val fallbackHaptic = ChatSideEffect.TriggerHaptic(HapticInteraction.CIRCUIT_BREAKER_FALLBACK)
        assertEquals(HapticInteraction.CIRCUIT_BREAKER_FALLBACK, fallbackHaptic.interaction)
    }
}
