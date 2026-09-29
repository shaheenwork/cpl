package com.shnapps.couple.core.data.entitlement

import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.data.billing.BillingRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** What the free tier has left today, and why. */
data class Allowance(
    val cardsLeft: Int,
    val gameNightsLeft: Int,
    /** Nothing is being counted: either they are premium, or everything is free for now. */
    val unlimited: Boolean,
    /** The launch period: everything free until a date Remote Config names. */
    val everythingFreeUntilMillis: Long? = null,
) {
    val canDealCard: Boolean get() = unlimited || cardsLeft > 0
    val canPlanNight: Boolean get() = unlimited || gameNightsLeft > 0

    companion object {
        /** A day's cards on the free tier. Enough for an evening; not enough for every evening. */
        const val CARDS_A_DAY = 30

        /** Guided evenings a week on the free tier. */
        const val NIGHTS_A_WEEK = 1
    }
}

/**
 * What the free tier allows, and what it never touches (M9).
 *
 * The brief is emphatic that **no game and no tier is ever gated**. Extreme is free, all seven games
 * are free, and the limits profile and the safe word are free — charging for the top of the range
 * would make money out of the one thing a couple should never hesitate over. What the free tier
 * limits is *volume*: thirty cards a day and one guided evening a week.
 *
 * Two rules matter more than the numbers:
 *
 * - **A running session is never cut off.** The count is checked when a run or an evening *starts*,
 *   not before each card, so a couple who are mid-evening when the thirtieth card comes up finish
 *   the evening. A limit that stops a couple mid-card is not a limit, it is an interruption.
 * - **It works with no account and no network.** The counters are on the phone, because the deck is
 *   and because the brief will not have signing up as a precondition for playing. That means a
 *   determined person can clear them by clearing app data, and that is the right trade: the
 *   alternative is asking everyone to sign in before the first card.
 *
 * During the launch period `everything_free` is true in Remote Config and nothing is counted at
 * all. Switching it off is a console change rather than a release, which is what the brief asks
 * for.
 */
interface Allowances {
    val allowance: Flow<Allowance>

    /** Records a card as dealt. Called after it is shown, never as a gate on showing it. */
    suspend fun cardDealt()

    /** Records a guided evening as planned. */
    suspend fun nightPlanned()
}

@Singleton
class DefaultAllowances @Inject constructor(
    private val preferences: AppPreferencesStore,
    private val play: PlaySettingsStore,
    private val billing: BillingRepository,
    private val free: LaunchOffer,
    private val clock: Clock,
) : Allowances {

    override val allowance: Flow<Allowance> =
        combine(preferences.preferences, billing.entitlement) { prefs, entitlement ->
            val now = clock.nowMillis()
            val today = dayOf(now)
            val week = weekOf(now)
            val unlimited = entitlement.premium || free.everythingFree
            Allowance(
                // A stamp from another day means the count belongs to that day, not this one.
                cardsLeft = if (prefs.cardDay == today) {
                    (Allowance.CARDS_A_DAY - prefs.cardsDealt).coerceAtLeast(0)
                } else {
                    Allowance.CARDS_A_DAY
                },
                gameNightsLeft = if (prefs.nightWeek == week) {
                    (Allowance.NIGHTS_A_WEEK - prefs.nightsPlanned).coerceAtLeast(0)
                } else {
                    Allowance.NIGHTS_A_WEEK
                },
                unlimited = unlimited,
                everythingFreeUntilMillis = free.freeUntilMillis,
            )
        }

    override suspend fun cardDealt() {
        if (allowance.first().unlimited) return
        val now = clock.nowMillis()
        play.countCard(day = dayOf(now))
    }

    override suspend fun nightPlanned() {
        if (allowance.first().unlimited) return
        val now = clock.nowMillis()
        play.countNight(week = weekOf(now))
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val WEEK_MS = 7 * DAY_MS

        /**
         * Which day a moment falls in, as a whole number of days since the epoch.
         *
         * Deliberately UTC rather than the phone's zone. A zone-aware day would reset the count
         * when somebody flies east, and the count is a fairness measure rather than a calendar.
         */
        fun dayOf(millis: Long): Long = millis / DAY_MS

        fun weekOf(millis: Long): Long = millis / WEEK_MS
    }
}
