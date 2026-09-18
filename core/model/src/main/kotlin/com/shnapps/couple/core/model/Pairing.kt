package com.shnapps.couple.core.model

/**
 * Where this user is in the pairing handshake (BUILD_PROMPT.md section 8).
 *
 * Mirrors the server-written `users/{uid}.pairing` field. Clients can read it but never
 * write it: if they could, a user could forge an approval or show their partner fake
 * verification symbols.
 */
data class PairingState(
    val role: PairingRole,
    val status: PairingStatus,
    val code: String,
    /** The symbols both phones show so the creator can confirm it is really their partner. */
    val verification: List<String> = emptyList(),
    val expiresAtEpochMillis: Long? = null,
)

enum class PairingRole { CREATOR, JOINER }

enum class PairingStatus {
    /** Creator: code shared, nobody has entered it yet. */
    OPEN,

    /** Creator: someone entered the code and is waiting for approval. */
    REQUESTED,

    /** Joiner: entered a code, waiting for the creator to approve. */
    WAITING,

    /** Joiner: the creator declined, or withdrew the code. */
    DECLINED,
}

/** A freshly issued invite code. */
data class InviteCode(val code: String, val expiresAtEpochMillis: Long)
