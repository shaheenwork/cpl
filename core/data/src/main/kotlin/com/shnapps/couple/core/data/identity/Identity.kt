package com.shnapps.couple.core.data.identity

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.AuthUser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who this phone is, in a product that has no accounts (V2_DESIGN_PROMPT.md, rule 1).
 *
 * V1 had an account: an email, a password, a profile document, and a sign-in screen in front of
 * everything. V2 has none of that. The only identity is **this phone**, and — once paired — the
 * other one. Nothing on any screen asks who somebody is.
 *
 * Two things follow, and they are the whole of this class:
 *
 * - **The age attestation lives here.** There is no server record to hold it and no account it
 *   could belong to, so it is kept on the phone that made it. That is a weaker guarantee than a
 *   server record and it is the honest one: a device-local flag is what a determined person clears,
 *   and the alternative — an account — is the thing the product is deliberately without.
 * - **A server identity is created only when something needs one**, and the only thing that does is
 *   pairing. A phone that never pairs never authenticates, never writes a document and never
 *   appears in the project at all, which is what "nothing leaves this phone until you ask it to"
 *   has to mean to be true.
 */
interface Identity {
    /** Whether somebody on this phone has attested to being 18 or over. */
    suspend fun hasConfirmedAge(): Boolean

    /** Records the attestation. On this phone, and nowhere else. */
    suspend fun confirmAge()

    /**
     * The identity the server knows this phone by, made on demand.
     *
     * Anonymous: there are no credentials to give and none to ask for. It exists so that Firestore
     * rules and the callables have a uid to attach a pair to — not so that anybody has an account.
     */
    suspend fun ensureServerIdentity(): Outcome<AuthUser>
}

@Singleton
class DefaultIdentity @Inject constructor(
    private val preferences: AppPreferencesStore,
    private val auth: AuthRepository,
) : Identity {

    override suspend fun hasConfirmedAge(): Boolean = preferences.preferences.first().ageConfirmedHint

    override suspend fun confirmAge() = preferences.setAgeConfirmedHint(true)

    override suspend fun ensureServerIdentity(): Outcome<AuthUser> {
        val existing = auth.authState.first()
        return if (existing != null) Outcome.Success(existing) else auth.signInAnonymously()
    }
}

/**
 * Bound beside the thing it binds.
 *
 * `DataModule` is one flat list of every repository in the app and it is already at the size
 * detekt allows. A concept that arrived with v2 brings its own module rather than pushing that
 * list over the edge.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class IdentityModule {
    @Binds
    abstract fun bindIdentity(impl: DefaultIdentity): Identity
}
