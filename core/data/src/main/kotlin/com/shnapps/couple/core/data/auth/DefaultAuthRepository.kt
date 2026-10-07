package com.shnapps.couple.core.data.auth

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.firebase.auth.AuthDataSource
import com.shnapps.couple.core.firebase.auth.SessionCheck
import com.shnapps.couple.core.firebase.user.UserProfileDataSource
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withTimeoutOrNull
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase-backed [AuthRepository]. Everything above this layer deals in [AuthUser] and
 * [UserProfile]; Firebase types stop here (BUILD_PROMPT.md §4.2).
 */
@Singleton
class DefaultAuthRepository @Inject constructor(
    private val authDataSource: AuthDataSource,
    private val userProfileDataSource: UserProfileDataSource,
    private val appPreferences: AppPreferencesStore,
    private val clock: Clock,
    @ApplicationScope appScope: CoroutineScope,
) : AuthRepository {
    override val authState: Flow<AuthUser?> = authDataSource.authState

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val currentProfile: Flow<UserProfile?> = authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(null)
            } else {
                // A listener can be refused mid-flight, most often during sign-out. That ends
                // this stream quietly and the auth change that follows starts the next one;
                // uncaught, it would crash the app from the shared scope below.
                userProfileDataSource.observeProfile(user.uid).catch { }
            }
        }
        // Shared: splash routing, pairing state and couple membership all read this, and a
        // cold flow would open one Firestore listener per collector (BUILD_PROMPT.md section
        // 75). WhileSubscribed lets the listener close when no screen needs it.
        .shareIn(appScope, SharingStarted.WhileSubscribed(PROFILE_STOP_TIMEOUT_MS), replay = 1)

    override suspend fun signIn(email: String, password: String): Outcome<AuthUser> {
        val result = authDataSource.signIn(email, password)
        if (result is Outcome.Success) ensureProfile(result.value)
        return result
    }

    override suspend fun signInAnonymously(): Outcome<AuthUser> {
        val result = authDataSource.signInAnonymously()
        if (result is Outcome.Success) ensureProfile(result.value)
        return result
    }

    override suspend fun signUp(email: String, password: String): Outcome<AuthUser> {
        val result = authDataSource.signUp(email, password)
        if (result is Outcome.Success) ensureProfile(result.value)
        return result
    }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> =
        authDataSource.sendPasswordReset(email)

    /**
     * Signs out and wipes device-local state.
     *
     * Clearing preferences here is deliberate: the app-lock PIN and the routing hints
     * belong to the account that just left, and leaving them behind would carry one
     * person's lock into the next person's session on a shared device.
     */
    override suspend fun signOut() {
        authDataSource.signOut()
        appPreferences.clear()
    }

    /**
     * Checks the stored session on open, and signs out one that is gone for good.
     *
     * Only [SessionCheck.DEAD] ends the session. Being unable to tell — offline, most often —
     * keeps it, because signing someone out for being on a train would be worse than asking
     * again later.
     */
    override suspend fun validateSession(): Boolean = when (authDataSource.checkSession()) {
        SessionCheck.VALID, SessionCheck.UNVERIFIABLE -> true
        SessionCheck.SIGNED_OUT -> false
        SessionCheck.DEAD -> {
            signOut()
            false
        }
    }

    override suspend fun setContentLevel(level: Intensity): Outcome<Unit> {
        val uid = authDataSource.currentUser?.uid
            ?: return Outcome.Failure(AppError.Unauthenticated())
        return userProfileDataSource.updateContentLevel(uid, level)
    }

    /**
     * Records the 18+ attestation (§3.1).
     *
     * Written to Firestore first, because that is the record of truth; the local hint is
     * only a routing shortcut for the splash screen and is updated after the write lands.
     */
    override suspend fun confirmAge(): Outcome<Unit> {
        val uid = authDataSource.currentUser?.uid
            ?: return Outcome.Failure(AppError.Unauthenticated())

        // Bounded on purpose. A Firestore write is queued offline and retried for ever, so
        // without this the gate could hold someone on a spinner with nothing to tap. Saying
        // "that didn't save" and offering the button again is the honest failure.
        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { userProfileDataSource.confirmAge(uid) }
            ?: return Outcome.Failure(AppError.Network())

        return result.also {
            if (it is Outcome.Success) appPreferences.setAgeConfirmedHint(true)
        }
    }

    /**
     * Whether the signed-in user has attested to being 18 or over.
     *
     * Absence is treated as "not confirmed", never as "probably fine" — a missing record
     * re-prompts (§3.1).
     */
    override suspend fun hasConfirmedAge(): Boolean {
        val uid = authDataSource.currentUser?.uid ?: return false
        val profile = userProfileDataSource.getProfile(uid).getOrNull()
        return profile?.hasConfirmedAge == true
    }

    /** Fast, offline-safe routing hint. The server record above remains the truth. */
    override suspend fun ageConfirmedHint(): Boolean =
        appPreferences.preferences.first().ageConfirmedHint

    private suspend fun ensureProfile(user: AuthUser) {
        userProfileDataSource.ensureProfile(
            uid = user.uid,
            timezone = TimeZone.getDefault().id,
            nowMillis = clock.nowMillis(),
        )
    }

    private companion object {
        const val PROFILE_STOP_TIMEOUT_MS = 5_000L
        const val WRITE_TIMEOUT_MS = 10_000L
    }
}
