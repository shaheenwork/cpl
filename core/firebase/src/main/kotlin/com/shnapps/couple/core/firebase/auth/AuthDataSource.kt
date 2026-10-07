package com.shnapps.couple.core.firebase.auth

import com.google.firebase.FirebaseNetworkException
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

/** What a stored session turned out to be worth (see [AuthDataSource.checkSession]). */
enum class SessionCheck {
    /** The session refreshed: it is real. */
    VALID,

    /** There was no session to check. */
    SIGNED_OUT,

    /** The session is gone for good: deleted, disabled, revoked, or an emulator restart. */
    DEAD,

    /** Could not tell — offline, or a failure that says nothing about the session. */
    UNVERIFIABLE,
}

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

    /**
     * The sign-in with no credentials, for a product that has no accounts (V2).
     *
     * It exists so a pair has a uid the rules can be written against — not so anybody has an
     * account. Nothing asks for it, nothing displays it, and a phone that never pairs never calls
     * it at all.
     */
    suspend fun signInAnonymously(): Outcome<AuthUser> = authCall {
        val result = auth.signInAnonymously().await()
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
     * Asks Firebase to refresh the stored session, so a session that is no longer real is
     * discovered on open rather than by a call that never completes.
     *
     * A refresh token stops working when the account is deleted or disabled, when the
     * password changes on another device, when the session is revoked — and, in `dev`,
     * whenever the Auth emulator restarts. The SDK keeps the signed-in user in that state
     * and every Firestore call fails `UNAUTHENTICATED` while an offline write simply queues,
     * so a screen can wait for ever on a write that will never land.
     *
     * A failure that only means "not now" — no network — leaves the session alone: starting
     * offline must not sign anyone out.
     */
    // Classifying a failure is the whole job here: the exception's type and message are the input,
    // and the category is the output, so a broad catch is correct and nothing is lost by not
    // rethrowing. Cancellation is still rethrown first.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    suspend fun checkSession(): SessionCheck {
        val user = auth.currentUser ?: return SessionCheck.SIGNED_OUT
        return try {
            user.getIdToken(true).await()
            SessionCheck.VALID
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: FirebaseNetworkException) {
            SessionCheck.UNVERIFIABLE
        } catch (error: FirebaseAuthInvalidUserException) {
            SessionCheck.DEAD
        } catch (error: Exception) {
            if (error.isRevokedSession()) SessionCheck.DEAD else SessionCheck.UNVERIFIABLE
        }
    }

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

    /**
     * Firebase reports a dead refresh token as a generic [com.google.firebase.FirebaseException]
     * with the reason in its message, so the reason is what has to be read. Anything not on
     * this list is treated as "could not tell", because wrongly signing someone out is worse
     * than checking again next time.
     */
    private fun Exception.isRevokedSession(): Boolean =
        REVOKED.any { reason -> message?.contains(reason) == true }

    private companion object {
        const val WEAK_PASSWORD = "weak_password"

        val REVOKED = listOf(
            "INVALID_REFRESH_TOKEN",
            "TOKEN_EXPIRED",
            "USER_DISABLED",
            "USER_NOT_FOUND",
            "CREDENTIAL_TOO_OLD_LOGIN_AGAIN",
        )
    }
}

private fun com.google.firebase.auth.FirebaseUser.toAuthUser() = AuthUser(
    uid = uid,
    email = email,
    isEmailVerified = isEmailVerified,
)
