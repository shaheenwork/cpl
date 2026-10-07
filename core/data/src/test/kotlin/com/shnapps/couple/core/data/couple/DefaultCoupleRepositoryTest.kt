package com.shnapps.couple.core.data.couple

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.pairing.PairingDataSource
import com.shnapps.couple.core.model.InviteCode
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeIdentity
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Pairing is the one thing in v2 that needs the server, so it is the one thing that makes an
 * identity (V2 rule 1, rule 2).
 *
 * Nobody signs in any more. A phone arrives at pairing with no uid at all, and the server needs one
 * to hang a pair on — so it is minted here, at the moment it is first needed, and nowhere else. The
 * rule that matters is the negative one: a phone that only ever plays together never authenticates,
 * so it never appears in the project and nothing about it is stored anywhere.
 *
 * It is done in the repository rather than in the pairing screen so that a future caller cannot
 * reach the server without an identity by forgetting to ask for one.
 */
class DefaultCoupleRepositoryTest {

    private val identity = FakeIdentity()
    private val pairing = RecordingPairingDataSource()
    private val repository = DefaultCoupleRepository(FakeAuthRepository(), pairing, identity)

    @Test
    fun `showing a code makes an identity first`() = runTest {
        repository.createInviteCode()

        assertThat(identity.identityCalls).isEqualTo(1)
        assertThat(pairing.calls).containsExactly("create")
    }

    @Test
    fun `entering a code makes one too`() = runTest {
        repository.requestPairing("482917")

        assertThat(identity.identityCalls).isEqualTo(1)
        assertThat(pairing.calls).containsExactly("request:482917")
    }

    @Test
    fun `no identity means no call, so the server is never reached without one`() = runTest {
        identity.serverIdentity = Outcome.Failure(AppError.Network())

        val result = repository.createInviteCode()

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(pairing.calls).isEmpty()
    }

    /** A real object with real state, not a mock: the fakes rule (D-013). */
    private class RecordingPairingDataSource : PairingDataSource {
        val calls = mutableListOf<String>()

        override suspend fun createPairingCode(): Outcome<InviteCode> {
            calls += "create"
            return Outcome.Success(InviteCode(code = "482917", expiresAtEpochMillis = 0L))
        }

        override suspend fun requestPairing(code: String): Outcome<List<String>> {
            calls += "request:$code"
            return Outcome.Success(listOf("a", "b", "c"))
        }

        override suspend fun respondToPairing(approve: Boolean): Outcome<Unit> {
            calls += "respond:$approve"
            return Outcome.Success(Unit)
        }

        override suspend fun cancelPairing(): Outcome<Unit> {
            calls += "cancel"
            return Outcome.Success(Unit)
        }

        override suspend fun unpairCouple(): Outcome<Unit> {
            calls += "unpair"
            return Outcome.Success(Unit)
        }
    }
}
