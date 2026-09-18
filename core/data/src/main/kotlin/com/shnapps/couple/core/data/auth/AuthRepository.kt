package com.shnapps.couple.core.data.auth

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Account lifecycle, and the profile document that hangs off it.
 *
 * An interface so that ViewModels depend on behaviour rather than on the Firebase-backed
 * implementation, and so tests can use a real fake instead of mocking suspend functions —
 * which in this repo once hung a single test for an hour (DECISIONS.md D-013).
 */
interface AuthRepository {
    val authState: Flow<AuthUser?>

    /** The signed-in user's profile, or null when signed out. Re-subscribes on sign-in. */
    val currentProfile: Flow<UserProfile?>

    suspend fun signIn(email: String, password: String): Outcome<AuthUser>

    suspend fun signUp(email: String, password: String): Outcome<AuthUser>

    suspend fun sendPasswordReset(email: String): Outcome<Unit>

    /** Signs out and wipes device-local state belonging to the departing account. */
    suspend fun signOut()

    /**
     * Sets the user's own content level (BUILD_PROMPT.md Appendix A) — how far they are
     * willing to go. Only ever their own: the couple's ceiling is the lower of the two
     * partners' levels, computed by the server, so nobody can raise it alone.
     */
    suspend fun setContentLevel(level: Intensity): Outcome<Unit>

    /** Records the 18+ attestation (BUILD_PROMPT.md §3.1). The server record is the truth. */
    suspend fun confirmAge(): Outcome<Unit>

    /**
     * Whether the signed-in user has attested to being 18 or over, per the **server**
     * record. Absence means "not confirmed", never "probably fine" (§3.1).
     */
    suspend fun hasConfirmedAge(): Boolean

    /** Fast, offline-safe routing hint only. Never sufficient to pass the age gate. */
    suspend fun ageConfirmedHint(): Boolean
}
