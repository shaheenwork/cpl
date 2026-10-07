package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.identity.Identity
import com.shnapps.couple.core.model.AuthUser

/**
 * Who this phone is, for a test. A real object with real state, not a mock (D-013).
 *
 * The whole of identity in v2 is two facts: whether somebody here has said they are eighteen, and
 * whether this phone has an identity with the server yet. Both are set directly.
 */
class FakeIdentity(
    var ageConfirmed: Boolean = false,
    var serverIdentity: Outcome<AuthUser> = Outcome.Success(ANONYMOUS),
) : Identity {

    var confirmations = 0
        private set

    var identityCalls = 0
        private set

    override suspend fun hasConfirmedAge(): Boolean = ageConfirmed

    override suspend fun confirmAge() {
        confirmations += 1
        ageConfirmed = true
    }

    override suspend fun ensureServerIdentity(): Outcome<AuthUser> {
        identityCalls += 1
        return serverIdentity
    }

    companion object {
        val ANONYMOUS = AuthUser(uid = "anon-uid", email = null, isEmailVerified = false)
    }
}
