package com.shnapps.couple.core.data.billing

import android.app.Activity
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.billing.BillingRemoteDataSource
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What the store offers, for the paywall to say plainly what it costs (§19). */
data class PremiumOffer(val productId: String, val price: String, val period: String)

/**
 * The store, whichever one this build talks to (BUILD_PROMPT.md §4.3): Play in staging and prod,
 * a fake in `dev` so everything below it can be exercised with no Play account at all.
 *
 * It only ever produces a **purchase token**. What that token is worth is the server's to say.
 */
interface BillingClient {
    suspend fun offer(): Outcome<PremiumOffer>

    /** Opens the store's own purchase flow and returns the token it produced. */
    suspend fun purchase(activity: Activity): Outcome<String>

    /** A purchase this account already has, if any — after a reinstall, or on another device. */
    suspend fun restore(): Outcome<String?>
}

/**
 * Subscriptions (§19).
 *
 * The client never decides entitlement: it hands the server a token, and reads back what the
 * server wrote. Both partners are covered by one subscription, which is why what is read here is
 * the couple's copy rather than this account's own record.
 */
interface BillingRepository {
    val entitlement: Flow<Entitlement>

    suspend fun offer(): Outcome<PremiumOffer>

    /** Buys premium, then tells the server. Returns what the server decided it is worth. */
    suspend fun subscribe(activity: Activity): Outcome<Entitlement>

    /** Restores a purchase this account already has. Nothing is granted without the server. */
    suspend fun restore(): Outcome<Entitlement>
}

@Singleton
class DefaultBillingRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: BillingRemoteDataSource,
    private val client: BillingClient,
) : BillingRepository {

    private val coupleIds: Flow<String?> = auth.currentProfile
        .map { it?.coupleId }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val entitlement: Flow<Entitlement> = coupleIds.flatMapLatest { coupleId ->
        if (coupleId == null) {
            flowOf(Entitlement())
        } else {
            remote.observe(coupleId)
                .map { doc ->
                    Entitlement(
                        tier = if (doc.tier == "PREMIUM") Tier.PREMIUM else Tier.FREE,
                        state = doc.state?.let { name ->
                            SubscriptionState.entries.firstOrNull { it.name == name }
                        },
                        expiresAtMillis = doc.expiresAtMillis,
                    )
                }
                // A couple with no record has bought nothing, which is not an error.
                .catch { emit(Entitlement()) }
        }
    }

    override suspend fun offer(): Outcome<PremiumOffer> = client.offer()

    override suspend fun subscribe(activity: Activity): Outcome<Entitlement> =
        when (val purchased = client.purchase(activity)) {
            is Outcome.Failure -> purchased
            is Outcome.Success -> verify(purchased.value)
        }

    override suspend fun restore(): Outcome<Entitlement> =
        when (val existing = client.restore()) {
            is Outcome.Failure -> existing
            is Outcome.Success -> existing.value
                ?.let { verify(it) }
                ?: Outcome.Failure(AppError.NotFound())
        }

    /** The only way anything becomes premium here: the server says so. */
    private suspend fun verify(purchaseToken: String): Outcome<Entitlement> =
        when (val verified = remote.verify(purchaseToken)) {
            is Outcome.Failure -> verified
            is Outcome.Success -> Outcome.Success(
                Entitlement(
                    tier = if (verified.value == "PREMIUM") Tier.PREMIUM else Tier.FREE,
                    state = SubscriptionState.ACTIVE,
                ),
            )
        }
}
