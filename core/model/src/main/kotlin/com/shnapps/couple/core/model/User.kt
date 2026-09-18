package com.shnapps.couple.core.model

/**
 * The signed-in account, as the rest of the app sees it.
 *
 * Deliberately thin. Anything private — preferences, boundaries, affinity — is never part
 * of a user object that gets passed around; it lives in owner-only Firestore subcollections
 * and is read on demand (BUILD_PROMPT.md §5.1).
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val isEmailVerified: Boolean,
)

/**
 * The profile document at `users/{uid}` (§6.1).
 *
 * [coupleId] and [entitlement] are written exclusively by Cloud Functions; the security
 * rules reject a client that tries to set either (§7.2). They appear here because the app
 * reads them, not because it may write them.
 */
data class UserProfile(
    val uid: String,
    val displayName: String? = null,
    val timezone: String? = null,
    val ageAttestation: AgeAttestation? = null,
    val contentLevel: Intensity = Intensity.FLIRTY,
    val coupleId: String? = null,
    val privacySettings: PrivacySettings = PrivacySettings(),
) {
    /** No adult content path is reachable until this is true (§3.1). */
    val hasConfirmedAge: Boolean get() = ageAttestation?.confirmed == true
}

/**
 * Self-attestation that the user is 18 or over (§3.1).
 *
 * Stored with a timestamp so the record is auditable, and re-prompted whenever it is
 * missing rather than assumed from the presence of an account.
 */
data class AgeAttestation(
    val confirmed: Boolean,
    val confirmedAtEpochMillis: Long,
)

/** Per-user privacy choices (§6.1, §16.2, §3.4). */
data class PrivacySettings(
    val appLock: AppLockMode = AppLockMode.OFF,
    /** When true, notifications never carry content — only "something is waiting" (§16.2). */
    val hideNotificationPreviews: Boolean = true,
    val analyticsOptOut: Boolean = false,
)

/**
 * How the app locks itself (§3.4, §57).
 *
 * This is a privacy affordance, not a security boundary: it keeps a partner, a flatmate or
 * a passer-by out of a phone that is already unlocked. It does not defend against a
 * determined attacker with the device and the time to attack it.
 */
enum class AppLockMode {
    OFF,
    BIOMETRIC,
    PIN,
    ;

    val isEnabled: Boolean get() = this != OFF
}
