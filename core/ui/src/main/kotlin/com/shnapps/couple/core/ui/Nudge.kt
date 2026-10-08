package com.shnapps.couple.core.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * A short buzz for something that happened on the *other* phone.
 *
 * Only for that. A screen that buzzes at its own user's taps is a screen that buzzes constantly;
 * the one thing worth interrupting somebody for is a thing they did not do — their partner starting
 * an evening, and the phone in a pocket being the only way they would know.
 *
 * `VIBRATE` is an install-time permission, so there is nothing to ask for, and a phone that has it
 * switched off simply does nothing. It stays silent under reduce-motion too: somebody who has asked
 * the system to stop moving things has asked for less of exactly this.
 */
@Composable
fun rememberNudge(): () -> Unit {
    val context = LocalContext.current
    // A preview and a screenshot test have no vibrator and should not look for one.
    val inspecting = LocalInspectionMode.current
    return remember(context, inspecting) {
        if (inspecting) {
            {}
        } else {
            { vibrate(context) }
        }
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private fun vibrate(context: Context) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(VibratorManager::class.java)
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator?.hasVibrator() != true) return
        // Two taps rather than one long one: a buzz the length of a notification reads as an
        // alarm, and nothing in this app is an emergency.
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    } catch (error: Throwable) {
        // No vibrator, or the platform refused. There is nothing to say about it and nothing
        // to fall back to: this is a flourish, not a message.
    }
}

/** Wait, buzz, wait, buzz. Milliseconds. */
private val PATTERN = longArrayOf(0, 30, 90, 30)
