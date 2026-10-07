package com.shnapps.couple.core.data.entitlement

import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
 * - **The first month is everything.** It runs from when the app was installed, which the system
 *   already knows and nothing here has to store or defend. A reinstall starts it again; that is a
 *   trade made knowingly, because the alternative is an account and this product does not have one.
 * - **A refusal is never a sales pitch.** The free pair is the gentle pair, so nothing a couple
 *   reaches for in order to *slow down* is ever behind a price — the safe word, the limits profile,
 *   Mild, and the aftercare cards are all free, for ever, with no account.
 */
data class LevelAccess(
    /** The highest level this phone may play. Never below Flirty. */
    val ceiling: Intensity,
    /** The packs bought on this phone, by the level each one opens. */
    val owned: Set<Intensity>,
    /** Everything is open and nobody has paid: the opening month, or the launch switch. */
    val freeForNow: Boolean = false,
    /** When that ends, when there is a date to show for it. For the banner, and nothing else. */
    val freeUntilMillis: Long? = null,
) {
    fun allows(level: Intensity): Boolean = level.level <= ceiling.level

    /** Whether [level] is open because it was bought, rather than because the month is still on. */
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

        /** Everything is open for this long after the app is installed. */
        const val OPENING_MONTH_MS = 30L * 24 * 60 * 60 * 1000

        /** The ceiling for a phone that has bought nothing and whose month has run out. */
        val FREE = LevelAccess(ceiling = Intensity.FLIRTY, owned = emptySet())

        /** Everything open because the opening month is still running. */
        val OPEN = LevelAccess(ceiling = Intensity.EXTREME, owned = emptySet(), freeForNow = true)

        /** The ceiling a set of bought packs adds up to: the furthest one of them reaches. */
        fun ceilingOf(owned: Set<Intensity>): Intensity =
            owned.maxByOrNull { it.level } ?: Intensity.FLIRTY
    }
}

/**
 * When this app was installed, behind a seam.
 *
 * The real answer comes from the package manager, which a pure-JVM test cannot call and a couple
 * cannot edit. A test binds it to a number instead.
 */
interface InstallDate {
    /** Epoch milliseconds, or 0 when it cannot be read — which counts as "just now". */
    suspend fun firstInstalledAtMillis(): Long
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
    private val installed: InstallDate,
    private val launch: LaunchOffer,
    private val clock: Clock,
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
        combine(preferences.preferences, billing.entitlement) { prefs, entitlement ->
            // Everything the phone has, plus anything the server says this couple holds. A
            // subscription is kept as a way in because somebody may already have one, and it opens
            // everything exactly as the top pack does.
            val owned = when {
                entitlement.premium -> prefs.ownedLevels + Intensity.EXTREME
                else -> prefs.ownedLevels
            }
            val free = freeRun(installed.firstInstalledAtMillis())
            LevelAccess(
                ceiling = if (free.on) Intensity.EXTREME else LevelAccess.ceilingOf(owned),
                owned = owned,
                freeForNow = free.on,
                freeUntilMillis = free.until,
            )
        }

    /**
     * When the free run ends, or null when there is no free run on.
     *
     * Two ways in, and the phone's own is the one that does not need a network: the opening month
     * is arithmetic on a date the system holds, so it works on a phone that has never reached
     * Firebase. The launch switch is the global one, flipped in the console rather than in a
     * release, and it wins while it is on because it is the later promise of the two.
     */
    private fun freeRun(installedAt: Long): FreeRun {
        // An install date of 0 means the package manager would not say, so this is a phone the app
        // has only just arrived on. Treating that as "installed long ago" would take the opening
        // month away from the one person who cannot argue about it.
        val monthEnds = (if (installedAt > 0) installedAt else clock.nowMillis()) +
            LevelAccess.OPENING_MONTH_MS
        val monthOn = clock.nowMillis() < monthEnds
        val ends = listOfNotNull(
            monthEnds.takeIf { monthOn },
            launch.freeUntilMillis.takeIf { launch.everythingFree },
        )
        // The switch stays authoritative even with no date set: it is the one that turns all of
        // this off without a release, so it must not need a date to work.
        return FreeRun(on = monthOn || launch.everythingFree, until = ends.maxOrNull())
    }

    /** Whether anything is free right now, and the date to say so with, if there is one. */
    private data class FreeRun(val on: Boolean, val until: Long?)
}
