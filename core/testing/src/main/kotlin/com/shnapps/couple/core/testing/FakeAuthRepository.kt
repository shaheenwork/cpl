package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.model.AgeAttestation
import com.shnapps.couple.core.model.AuthUser
import com.shnapps.couple.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [AuthRepository] for ViewModel tests.
 *
 * A real object with real state rather than a mock: tests arrange the world, act, and
 * assert on what happened. That avoids stubbing suspend functions with mockk, which once
 * hung a test in this repo for an hour (DECISIONS.md D-013).
 */
class FakeAuthRepository : AuthRepository {

    val user = MutableStateFlow<AuthUser?>(null)
    val profile = MutableStateFlow<UserProfile?>(null)

    /** What the server says about the age gate. Independent of [ageHint] on purpose. */
    var serverAgeConfirmed = false

    /** The device-local routing hint. Must never be enough to pass the gate by itself. */
    var ageHint = false

    var signInResult: Outcome<AuthUser> = Outcome.Success(DEFAULT_USER)
    var signUpResult: Outcome<AuthUser> = Outcome.Success(DEFAULT_USER)
    var resetResult: Outcome<Unit> = Outcome.Success(Unit)
    var confirmAgeResult: Outcome<Unit> = Outcome.Success(Unit)

    var confirmAgeCalls = 0
        private set
    var resetCalls = 0
        private set
    var signedOut = false
        private set

    override val authState: Flow<AuthUser?> = user
    override val currentProfile: Flow<UserProfile?> = profile

    override suspend fun signIn(email: String, password: String): Outcome<AuthUser> =
        signInResult.also { if (it is Outcome.Success) user.value = it.value }

    override suspend fun signUp(email: String, password: String): Outcome<AuthUser> =
        signUpResult.also { if (it is Outcome.Success) user.value = it.value }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> {
        resetCalls++
        return resetResult
    }

    override suspend fun signOut() {
        signedOut = true
        user.value = null
        profile.value = null
    }

    override suspend fun confirmAge(): Outcome<Unit> {
        confirmAgeCalls++
        if (user.value == null) return Outcome.Failure(AppError.Unauthenticated())
        return confirmAgeResult.also {
            if (it is Outcome.Success) {
                serverAgeConfirmed = true
                ageHint = true
                profile.value = (profile.value ?: UserProfile(uid = DEFAULT_USER.uid)).copy(
                    ageAttestation = AgeAttestation(confirmed = true, confirmedAtEpochMillis = 1L),
                )
            }
        }
    }

    override suspend fun hasConfirmedAge(): Boolean = user.value != null && serverAgeConfirmed

    override suspend fun ageConfirmedHint(): Boolean = ageHint

    companion object {
        val DEFAULT_USER = AuthUser(uid = "uid_test", email = "test@example.com", isEmailVerified = false)
    }
}
