package com.shnapps.couple.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Outcome of a biometric prompt. */
sealed interface BiometricResult {
    data object Success : BiometricResult

    /** The user dismissed it, or chose the PIN fallback. Not an error. */
    data object Cancelled : BiometricResult

    /** Too many failed attempts. The PIN fallback is the only way back in. */
    data object LockedOut : BiometricResult

    data class Failed(val message: String) : BiometricResult

    /** No enrolled biometrics, or no hardware. */
    data object Unavailable : BiometricResult
}

/**
 * Wraps [BiometricPrompt] in a suspend function (BUILD_PROMPT.md §3.4, §57).
 *
 * `androidx.biometric` has no stable coroutine artifact, so this is hand-rolled rather
 * than pulled from an alpha.
 *
 * Deliberately does **not** offer device-credential fallback. Falling back to the phone's
 * own PIN or pattern would defeat the point: whoever is holding an unlocked phone already
 * knows that code. The fallback is the app's own separate PIN.
 */
@Singleton
class BiometricAuthenticator @Inject constructor() {

    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeButtonText: String,
    ): BiometricResult = suspendCancellableCoroutine { continuation ->
        if (!canAuthenticate(activity)) {
            continuation.resume(BiometricResult.Unavailable)
            return@suspendCancellableCoroutine
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (continuation.isActive) continuation.resume(BiometricResult.Success)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    continuation.resume(
                        when (errorCode) {
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_CANCELED,
                            -> BiometricResult.Cancelled

                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                            -> BiometricResult.LockedOut

                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            -> BiometricResult.Unavailable

                            else -> BiometricResult.Failed(errString.toString())
                        },
                    )
                }
                // onAuthenticationFailed fires on a non-matching finger. The prompt stays
                // open and lets the user retry, so it is deliberately not resumed here.
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText(negativeButtonText)
                .setAllowedAuthenticators(AUTHENTICATORS)
                .setConfirmationRequired(false)
                .build(),
        )

        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    private companion object {
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK
    }
}
