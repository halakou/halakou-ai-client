package com.example.halakou.presentation.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Semantic tactile interactions used throughout the halakou user experience.
 */
enum class HapticInteraction {
    /**
     * Tactile impulse triggered when a user sends a chat message.
     * Uses a crisp, responsive keystroke tactile response.
     */
    SEND_MESSAGE,

    /**
     * Affirmative confirmation pulse triggered when an autonomous agent tool
     * finishes executing successfully.
     */
    TOOL_SUCCESS,

    /**
     * Distinct dual-pulse/rejection warning tactile triggered when the
     * Circuit Breaker trips and transparently fails over to a fallback model.
     */
    CIRCUIT_BREAKER_FALLBACK,

    /**
     * Light click for toggles, chips, and small UI interactive elements.
     */
    STANDARD_CLICK,

    /**
     * Sustained tactile press for copy actions, context menus, and long-presses.
     */
    LONG_PRESS,

    /**
     * Error or quota rejection pulse.
     */
    ERROR_ALERT
}

/**
 * System-level HapticFeedback constants with backward-compatible fallbacks across Android API levels.
 */
object AppHapticConstants {
    // Android View HapticFeedbackConstants with API guards
    val HAPTIC_CONFIRM: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }

    val HAPTIC_REJECT: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.REJECT
    } else {
        HapticFeedbackConstants.LONG_PRESS
    }

    val HAPTIC_KEYBOARD_TAP: Int = HapticFeedbackConstants.KEYBOARD_TAP
    val HAPTIC_VIRTUAL_KEY: Int = HapticFeedbackConstants.VIRTUAL_KEY
    val HAPTIC_LONG_PRESS: Int = HapticFeedbackConstants.LONG_PRESS

    val HAPTIC_GESTURE_START: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.GESTURE_START
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }

    val HAPTIC_GESTURE_END: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.GESTURE_END
    } else {
        HapticFeedbackConstants.KEYBOARD_TAP
    }
}

/**
 * Centralized tactile controller that handles Compose [HapticFeedback],
 * Android [View.performHapticFeedback], and [Vibrator] vibration effects cleanly.
 */
object HapticFeedbackHelper {

    /**
     * Executes the appropriate tactile feedback for the given [interaction].
     * Tries Android View haptics first, followed by Compose [HapticFeedback],
     * ensuring maximum hardware fidelity and zero-crash execution.
     */
    fun performHaptic(
        view: View?,
        hapticFeedback: HapticFeedback?,
        interaction: HapticInteraction
    ) {
        try {
            when (interaction) {
                HapticInteraction.SEND_MESSAGE -> {
                    // Tactile tap for sending messages
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_KEYBOARD_TAP) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                HapticInteraction.TOOL_SUCCESS -> {
                    // Affirmative confirmation pulse for successful tool execution
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_CONFIRM) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                HapticInteraction.CIRCUIT_BREAKER_FALLBACK -> {
                    // Distinct attention pulse alerting user that circuit breaker auto-routed
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_REJECT) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                HapticInteraction.STANDARD_CLICK -> {
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_VIRTUAL_KEY) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                HapticInteraction.LONG_PRESS -> {
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_LONG_PRESS) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }

                HapticInteraction.ERROR_ALERT -> {
                    val handled = view?.performHapticFeedback(AppHapticConstants.HAPTIC_REJECT) ?: false

                    if (!handled) {
                        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            }
        } catch (_: Throwable) {
            // Graceful fallback: never crash UI on hardware haptics failure
            try {
                hapticFeedback?.performHapticFeedback(
                    if (interaction == HapticInteraction.LONG_PRESS || interaction == HapticInteraction.CIRCUIT_BREAKER_FALLBACK) {
                        HapticFeedbackType.LongPress
                    } else {
                        HapticFeedbackType.TextHandleMove
                    }
                )
            } catch (_: Throwable) {
                // Device without haptic support
            }
        }
    }

    /**
     * Triggers tactile feedback directly using [Context] vibrator if available.
     */
    fun performVibration(context: Context, interaction: HapticInteraction) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = when (interaction) {
                    HapticInteraction.SEND_MESSAGE ->
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    HapticInteraction.TOOL_SUCCESS ->
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    HapticInteraction.CIRCUIT_BREAKER_FALLBACK ->
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    HapticInteraction.STANDARD_CLICK ->
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    HapticInteraction.LONG_PRESS ->
                        VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                    HapticInteraction.ERROR_ALERT ->
                        VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (interaction) {
                    HapticInteraction.SEND_MESSAGE -> vibrator.vibrate(25)
                    HapticInteraction.TOOL_SUCCESS -> vibrator.vibrate(longArrayOf(0, 30, 40, 50), -1)
                    HapticInteraction.CIRCUIT_BREAKER_FALLBACK -> vibrator.vibrate(longArrayOf(0, 60, 50, 60), -1)
                    HapticInteraction.STANDARD_CLICK -> vibrator.vibrate(15)
                    HapticInteraction.LONG_PRESS -> vibrator.vibrate(80)
                    HapticInteraction.ERROR_ALERT -> vibrator.vibrate(longArrayOf(0, 80, 50, 80), -1)
                }
            }
        } catch (_: Throwable) {
            // Ignored in restricted environments or emulator tests
        }
    }
}
