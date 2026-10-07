package com.shnapps.couple.core.data.billing

import android.app.Activity
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.datastore.PurchaseStore
import com.shnapps.couple.core.firebase.billing.BillingRemoteDataSource
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
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

/** A pack as the store prices it, in the store's own currency and formatting. */
data class PackOffer(val level: Intensity, val productId: String, val price: String)

/**
 * The store, whichever one this build talks to (BUILD_PROMPT.md §4.3): Play in staging and prod,
 * a fake in `dev` so everything above it can be exercised with no Play account at all.
 */
interface BillingClient {
    /** What the packs cost here. A product the store does not know is left out rather than guessed. */
    suspend fun offers(): Outcome<List<PackOffer>>

    /** Opens the store's own purchase flow for one product and returns the token it produced. */
    suspend fun purchase(activity: Activity, productId: String): Outcome<String>

    /** The product ids this store account already holds — after a reinstall, or on another phone. */
    suspend fun owned(): Outcome<Set<String>>
}

/**
 * Buying the packs above the free pair (V2 rule 5).
 *
 * **The store is the authority, and the phone keeps the record.** There is no account in v2, so
 * there is no server document to write a purchase into for a phone that has never paired — and a
 * couple who only ever play in the same room never will. So what a purchase produces is a line in
 * this phone's own settings, and Play is asked again on demand (`restore`) whenever that line needs
 * proving: after a reinstall, on a second phone, or after app data is cleared.
 *
 * That is a knowing departure from "the client never decides entitlement" (D-091), and it is bounded
 * by what is being gated. These packs open *content that already shipped inside the APK*, so the
 * worst a tampered phone gets is cards it already had on disk. Every gate that costs the project
 * something — a night built on the server, an arc, a surprise — is still decided on the server from
 * the server's own record, and none of that moved.
 *
 * A subscription from before this change still counts: [entitlement] is read from the couple
 * document exactly as it was, and `LevelAccess` treats premium as opening everything.
 */
interface BillingRepository {
    /** What the server says this couple holds. Empty for a phone that has never paired. */
    val entitlement: Flow<Entitlement>

    suspend fun offers(): Outcome<List<PackOffer>>

    /** Buys the pack that opens [level], and records it on this phone. */
    suspend fun buy(activity: Activity, level: Intensity): Outcome<Set<Intensity>>

    /** Asks the store what this account holds and writes it down. The answer may be nothing. */
    suspend fun restore(): Outcome<Set<Intensity>>
}

@Singleton
class DefaultBillingRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: BillingRemoteDataSource,
    private val client: BillingClient,
    private val purchases: PurchaseStore,
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

    override suspend fun offers(): Outcome<List<PackOffer>> = client.offers()

    override suspend fun buy(activity: Activity, level: Intensity): Outcome<Set<Intensity>> {
        val product = LevelPacks.productOf(level)
            ?: return Outcome.Failure(AppError.Validation("already-free"))
        return when (val purchased = client.purchase(activity, product)) {
            is Outcome.Failure -> purchased
            is Outcome.Success -> record(setOf(level))
        }
    }

    override suspend fun restore(): Outcome<Set<Intensity>> =
        when (val held = client.owned()) {
            is Outcome.Failure -> held
            is Outcome.Success -> record(held.value.mapNotNull(LevelPacks::levelOf).toSet())
        }

    /**
     * Writes what was bought where the deck will read it.
     *
     * Additive on purpose. A store that answers "nothing" because the network is down, or because
     * this is a different Play account on a shared phone, must not be able to take a pack away —
     * the one thing worse than a purchase that will not restore is one that silently stops working.
     */
    private suspend fun record(levels: Set<Intensity>): Outcome<Set<Intensity>> {
        if (levels.isNotEmpty()) purchases.unlock(levels)
        return Outcome.Success(levels)
    }
}
