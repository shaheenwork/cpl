package com.shnapps.couple.core.data.entitlement

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How far this phone may go, and why (V2 rule 5).
 *
 * V1 sold a subscription and limited *volume*: thirty cards a day, one guided evening a week, every
 * level free. V2 sells the top of the range instead. Mild and Flirty are free for ever; Spicy, Hot
 * and Extreme are bought once each and never expire.
 *
 * Four things about that are worth stating plainly, because each is a decision rather than a
 * detail:
 *
 * - **A pack is a step on a ladder, not an item in a basket.** Buying Hot opens Spicy with it.
 *   Everything in this app treats a level as a *ceiling* — the deck climbs towards it, a run
 *   escalates towards it, a game's rounds are chosen under it — so a couple who owned Extreme and
 *   not Spicy would have a deck with a hole in the middle of it. The price of each pack is the
 *   price of getting that far.
 * - **Nothing is ever cut off mid-evening.** The ceiling is a property of the deck, applied when a
 *   card is chosen. It is not a counter that runs out between one card and the next, so a couple at
 *   Flirty can play all night, and a couple whose month ends at 11pm finish the evening they are in
 *   at the level they started it.
 * - **Free runs until a date the owner sets in Firebase** (D-151), the same date for everybody.
 *   It used to be a month from each phone's install, which made the end of it a different day for
 *   each of the two of them and one a reinstall could restart.
 * - **A refusal is never a sales pitch.** The free pair is the gentle pair, so nothing a couple
 *   reaches for in order to *slow down* is ever behind a price — the safe word, the limits profile,
 *   Mild, and the aftercare cards are all free, for ever, with no account.
 */
data class LevelAccess(
    /** The highest level this phone may play. Never below Flirty. */
    val ceiling: Intensity,
    /**
     * The packs bought on this phone, or by the partner it is paired with, by the level each one
     * opens (D-149). One of them buying a pack opens it for both: a couple is one purchase, and
     * the phone that did not pay is not asked to.
     */
    val owned: Set<Intensity>,
    /** Everything is open and nobody has paid: the free period is on. */
    val freeForNow: Boolean = false,
    /** When that ends, when there is a date to show for it. For the banner, and nothing else. */
    val freeUntilMillis: Long? = null,
) {
    fun allows(level: Intensity): Boolean = level.level <= ceiling.level

    /** Whether [level] is open because it was bought, rather than because the free period is on. */
    fun isOwned(level: Intensity): Boolean = owned.any { it.level >= level.level }

    /** The levels nothing has opened: what the heat dial draws a lock on. */
    val locked: List<Intensity> get() = Intensity.entries.filter { !allows(it) }

    /** Whether anything at all is locked, which is the only time the store is worth mentioning. */
    val anythingLocked: Boolean get() = locked.isNotEmpty()

    companion object {
        /** Free for ever, for everybody. The app is playable with no money and no account. */
        val ALWAYS_FREE = setOf(Intensity.MILD, Intensity.FLIRTY)

        /** What there is to buy: the three above the free pair, each opening everything below it. */
        val PACKS: List<Intensity> = Intensity.entries.filterNot { it in ALWAYS_FREE }

        /** The ceiling for a phone that has bought nothing, once the free period is over. */
        val FREE = LevelAccess(ceiling = Intensity.FLIRTY, owned = emptySet())

        /** Everything open because the free period is on. */
        val OPEN = LevelAccess(ceiling = Intensity.EXTREME, owned = emptySet(), freeForNow = true)

        /** The ceiling a set of bought packs adds up to: the furthest one of them reaches. */
        fun ceilingOf(owned: Set<Intensity>): Intensity =
            owned.maxByOrNull { it.level } ?: Intensity.FLIRTY
    }
}

interface LevelAccessRepository {
    val access: Flow<LevelAccess>

    /**
     * What each pack costs here, as the store formats it.
     *
     * A level missing from the map is one the store has not priced — offline, or a build with no
     * products set up. The screen then says "Unlock Spicy" with no number rather than inventing
     * one, because a price shown wrong is worse than a price not shown.
     */
    suspend fun packPrices(): Map<Intensity, String>
}

@Singleton
class DefaultLevelAccessRepository @Inject constructor(
    private val preferences: AppPreferencesStore,
    private val billing: BillingRepository,
    private val launch: LaunchOffer,
) : LevelAccessRepository {

    /** Asked once and kept: three prices that do not change while the app is open. */
    @Volatile
    private var prices: Map<Intensity, String> = emptyMap()

    override suspend fun packPrices(): Map<Intensity, String> {
        prices.takeIf { it.isNotEmpty() }?.let { return it }
        val offers = billing.offers()
        if (offers is Outcome.Success) prices = offers.value.associate { it.level to it.price }
        return prices
    }

    override val access: Flow<LevelAccess> =
        combine(
            preferences.preferences,
            billing.entitlement,
            billing.partnerPacks,
            minutely,
        ) { prefs, entitlement, partner, _ ->
            // Everything the phone has, plus whatever the partner it is paired with has bought
            // (D-149), plus anything the server says this couple holds. A subscription is kept as a
            // way in because somebody may already have one, and it opens everything exactly as the
            // top pack does.
            val bought = prefs.ownedLevels + partner
            val owned = when {
                entitlement.premium -> bought + Intensity.EXTREME
                else -> bought
            }
            val free = launch.isFree
            LevelAccess(
                ceiling = if (free) Intensity.EXTREME else LevelAccess.ceilingOf(owned),
                owned = owned,
                freeForNow = free,
                // A date in the past is not something to announce.
                freeUntilMillis = launch.freeUntilMillis.takeIf { free },
            )
        }.distinctUntilChanged()

    private companion object {
        /**
         * How often the clock is looked at again. The free period ends at a moment nobody is
         * pressing anything, and a date moved in Firebase arrives while the app is open: neither
         * would otherwise be noticed until something else changed.
         */
        const val CLOCK_CHECK_MS = 60_000L

        val minutely: Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(CLOCK_CHECK_MS)
            }
        }
    }
}
