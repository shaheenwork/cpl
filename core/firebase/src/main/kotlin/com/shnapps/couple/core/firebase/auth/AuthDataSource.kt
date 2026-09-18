package com.shnapps.couple.core.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.model.AuthUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Authentication, wrapped so that nothing above :core:firebase ever sees a
 * Firebase type (BUILD_PROMPT.md §4.2 — enforced by :architecture).
 *
 * In the `dev` flavor this talks to the Auth emulator, so accounts can be created freely
 * and are wiped whenever the emulator restarts.
 */
@Singleton
class AuthDataSource @Inject constructor(
    private val auth: FirebaseAuth,
) {
    /** Emits on every sign-in, sign-out and token refresh. Null when signed out. */
    val authState: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    suspend fun signIn(email: String, password: String): Outcome<AuthUser> = authCall {
        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        requireNotNull(result.user).toAuthUser()
    }

    suspend fun signUp(email: String, password: String): Outcome<AuthUser> = authCall {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        requireNotNull(result.user).toAuthUser()
    }

    suspend fun sendPasswordReset(email: String): Outcome<Unit> = authCall {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun signOut() = auth.signOut()

    /**
     * Maps Firebase's exception zoo onto the app's typed errors.
     *
     * Note what is NOT distinguished: "no such account" and "wrong password" both become
     * [AppError.Unauthenticated]. Telling them apart would let anyone use the sign-in form
     * to discover whether a given email has an account here — which, for this product, is
     * itself sensitive information.
     */
    // The broad catch is the point of this function: it is the seam where Firebase's
    // exception zoo becomes a typed AppError, and an unrecognised failure must still be
    // reported rather than crash the caller.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> authCall(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: FirebaseAuthWeakPasswordException) {
        Outcome.Failure(AppError.Validation(WEAK_PASSWORD, error))
    } catch (error: FirebaseAuthUserCollisionException) {
        Outcome.Failure(AppError.Conflict(error))
    } catch (error: FirebaseAuthInvalidCredentialsException) {
        Outcome.Failure(AppError.Unauthenticated(error))
    } catch (error: FirebaseAuthInvalidUserException) {
        Outcome.Failure(AppError.Unauthenticated(error))
    } catch (error: Exception) {
        Outcome.Failure(AppError.Unknown(error))
    }

    private companion object {
        const val WEAK_PASSWORD = "weak_password"
    }
}

private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    isEmailVerified = isEmailVerified,
)
