package com.shnapps.couple.core.firebase.user

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.firestoreCall
import com.shnapps.couple.core.model.AgeAttestation
import com.shnapps.couple.core.model.AppLockMode
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.PairingRole
import com.shnapps.couple.core.model.PairingState
import com.shnapps.couple.core.model.PairingStatus
import com.shnapps.couple.core.model.PrivacySettings
import com.shnapps.couple.core.model.UserProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `users/{uid}` document (BUILD_PROMPT.md §6.1).
 *
 * Owner-only by rule rather than by convention: no partner can read this path, and the
 * rules reject any client write to `coupleId`, `entitlement` or `contentLevelEffective`
 * (§7.2).
 *
 * The private subcollections — `preferences`, `boundaries`, `affinity` — are deliberately
 * NOT handled here. They arrive in Phases 5 and 6 with their own data sources, so the type
 * carrying a user's most sensitive answers is never casually reachable from the type that
 * carries their display name.
 */
@Singleton
class UserProfileDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    fun observeProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val registration = firestore.collection(USERS).document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.toUserProfile(uid))
            }
        awaitClose { registration.remove() }
    }

    suspend fun getProfile(uid: String): Outcome<UserProfile?> = call {
        firestore.collection(USERS).document(uid).get().await()
            .takeIf { it.exists() }
            ?.toUserProfile(uid)
    }

    /**
     * Creates the profile if it is missing, and leaves an existing one untouched.
     *
     * Merge rather than overwrite: signing in on a second device must not wipe the profile
     * written from the first.
     */
    suspend fun ensureProfile(uid: String, timezone: String, nowMillis: Long): Outcome<Unit> = call {
        val document = firestore.collection(USERS).document(uid)
        if (document.get().await().exists()) return@call
        document.set(
            mapOf(
                "createdAt" to nowMillis,
                "lastActiveAt" to nowMillis,
                "timezone" to timezone,
                "contentLevel" to Intensity.FLIRTY.level,
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * Records the 18+ self-attestation (§3.1).
     *
     * The time is the **server's**, via `serverTimestamp()`. The attestation is an audit
     * record, and a device clock is neither trustworthy nor reliably correct; the security
     * rules reject any client-chosen time for this field.
     */
    suspend fun confirmAge(uid: String): Outcome<Unit> = call {
        firestore.collection(USERS).document(uid).set(
            mapOf(
                "ageAttestation" to mapOf(
                    "confirmed" to true,
                    "at" to FieldValue.serverTimestamp(),
                ),
            ),
            SetOptions.merge(),
        ).await()
    }

    suspend fun updatePrivacySettings(uid: String, settings: PrivacySettings): Outcome<Unit> = call {
        firestore.collection(USERS).document(uid).set(
            mapOf(
                "privacySettings" to mapOf(
                    "appLock" to settings.appLock.name,
                    "hideNotificationPreviews" to settings.hideNotificationPreviews,
                    "analyticsOptOut" to settings.analyticsOptOut,
                ),
            ),
            SetOptions.merge(),
        ).await()
    }

    /**
     * The user's own content level (BUILD_PROMPT.md Appendix A): how far *they* are willing
     * to go. The couple's effective level is the lower of the two partners' and is computed
     * by the server; nothing here can raise a partner's ceiling.
     */
    suspend fun updateContentLevel(uid: String, level: Intensity): Outcome<Unit> = call {
        firestore.collection(USERS).document(uid)
            .set(mapOf("contentLevel" to level.level), SetOptions.merge()).await()
    }

    suspend fun updateDisplayName(uid: String, displayName: String): Outcome<Unit> = call {
        firestore.collection(USERS).document(uid)
            .set(mapOf("displayName" to displayName), SetOptions.merge()).await()
    }

    private inline fun <T> call(block: () -> T): Outcome<T> = firestoreCall(block)

    private companion object {
        const val USERS = "users"
    }
}

private fun DocumentSnapshot.toUserProfile(uid: String): UserProfile {
    val attestation = get("ageAttestation") as? Map<*, *>
    val privacy = get("privacySettings") as? Map<*, *>

    return UserProfile(
        uid = uid,
        displayName = getString("displayName"),
        timezone = getString("timezone"),
        ageAttestation = attestation?.let {
            AgeAttestation(
                confirmed = it["confirmed"] as? Boolean ?: false,
                confirmedAtEpochMillis = (it["at"] as? Timestamp)?.toDate()?.time ?: 0L,
            )
        },
        contentLevel = getLong("contentLevel")?.toInt()
            ?.let { level -> runCatching { Intensity.ofLevel(level) }.getOrNull() }
            ?: Intensity.FLIRTY,
        coupleId = getString("coupleId"),
        privacySettings = PrivacySettings(
            appLock = (privacy?.get("appLock") as? String)
                ?.let { mode -> runCatching { AppLockMode.valueOf(mode) }.getOrNull() }
                ?: AppLockMode.OFF,
            hideNotificationPreviews = privacy?.get("hideNotificationPreviews") as? Boolean ?: true,
            analyticsOptOut = privacy?.get("analyticsOptOut") as? Boolean ?: false,
        ),
        pairing = (get("pairing") as? Map<*, *>)?.toPairingState(),
    )
}

/**
 * Tolerant parse: an unrecognised role or status yields null rather than a crash, so a
 * newer server state never takes down an older client.
 */
private fun Map<*, *>.toPairingState(): PairingState? {
    val role = (this["role"] as? String)?.let { runCatching { PairingRole.valueOf(it) }.getOrNull() }
    val status = (this["status"] as? String)?.let { runCatching { PairingStatus.valueOf(it) }.getOrNull() }
    val code = this["code"] as? String
    if (role == null || status == null || code == null) return null
    return PairingState(
        role = role,
        status = status,
        code = code,
        verification = (this["verification"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
        expiresAtEpochMillis = (this["expiresAtMs"] as? Number)?.toLong(),
    )
}
