package com.shnapps.couple.core.data.billing

import android.app.Activity
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.datastore.PurchaseStore
import com.shnapps.couple.core.firebase.billing.BillingRemoteDataSource
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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
 * **A couple buys a pack once** (D-149). What a phone holds is told to the couple's own record, and
 * the other phone reads it, so the partner who did not pay goes as far as the one who did. That is
 * the same bounded departure, one step further: the record opens content already on both phones.
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

    /**
     * What the other half of this couple has bought (D-149), by the level each pack opens.
     *
     * One of them buying a pack opens it for both, so a phone that bought nothing still reads this.
     * Empty for a phone with nobody paired, and for a partner who has bought nothing — and empty,
     * not an error, when the couple's record cannot be read, because the one thing worse than a
     * level that did not arrive is a screen that says something has gone wrong about it.
     */
    val partnerPacks: Flow<Set<Intensity>>

    /**
     * Tells the couple how far this phone's own packs reach, and keeps telling them for as long as
     * this is running (D-149). It does not return: the caller launches it once, in a scope that
     * lives as long as the app is in use, and it writes again whenever what the phone holds or who
     * it is paired with changes — so a pack bought before pairing reaches a partner who joins later.
     *
     * The beat is the repository's, never a loop in a ViewModel (D-076).
     */
    suspend fun shareOwnership()
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

    /** Who this phone is, and who it is paired with: the two facts the couple's record is keyed by. */
    private val whoAndWith: Flow<Pair<String, String>?> = auth.currentProfile
        .map { profile -> profile?.coupleId?.let { couple -> profile.uid to couple } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val partnerPacks: Flow<Set<Intensity>> = whoAndWith.flatMapLatest { ids ->
        if (ids == null) {
            flowOf(emptySet())
        } else {
            val (uid, coupleId) = ids
            remote.observePacks(coupleId)
                .map { byMember ->
                    byMember.filterKeys { it != uid }.values
                        .mapNotNull { level -> Intensity.entries.firstOrNull { it.level == level } }
                        .toSet()
                }
                .catch { emit(emptySet()) }
        }
    }

    override suspend fun shareOwnership() {
        combine(whoAndWith, purchases.ownedLevels) { ids, owned ->
            // A level a pack can be: the free pair has nothing to say, and nothing held says nothing.
            val reach = owned.filter { it !in LevelAccess.ALWAYS_FREE }.maxOfOrNull { it.level }
            if (ids == null || reach == null) null else Triple(ids.first, ids.second, reach)
        }
            .distinctUntilChanged()
            .filterNotNull()
            // The latest wins: a write waiting on the network must not hold up a newer one.
            .collectLatest { (uid, coupleId, level) -> remote.publishPack(coupleId, uid, level) }
    }

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
