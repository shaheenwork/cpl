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
    val notificationSettings: NotificationSettings = NotificationSettings(),
    /** Present only while a pairing handshake is in progress. Server-written. */
    val pairing: PairingState? = null,
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

/** Per-user privacy choices (§6.1, §3.4). What a notification may say lives in [NotificationSettings]. */
data class PrivacySettings(
    val appLock: AppLockMode = AppLockMode.OFF,
    val analyticsOptOut: Boolean = false,
)

/**
 * The three notification choices of §16.2, and the hours nobody wants to hear from anyone.
 *
 * [showContent] never means "put private content in a notification" — nothing in a payload could
 * carry any (§16.2). It chooses between saying which kind of thing happened and saying nothing
 * but the app's name, which is what "hide sensitive previews" means on a shared lock screen.
 */
data class NotificationSettings(
    val enabled: Boolean = true,
    val showContent: Boolean = false,
    val quietHours: QuietHours? = null,
)

/** A window on the user's own clock, each end "HH:MM". Wraps around midnight. */
data class QuietHours(val from: String, val to: String) {
    companion object {
        val NIGHT = QuietHours(from = "22:00", to = "07:00")
    }
}

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
