package com.shnapps.couple.core.testing

import com.shnapps.couple.core.data.entitlement.Allowance
import com.shnapps.couple.core.data.entitlement.Allowances
import com.shnapps.couple.core.data.entitlement.LaunchOffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [Allowances]: what a test says the free tier has left.
 *
 * Unlimited by default, because most tests are not about the free tier and a limit they did not ask
 * for would make them fail for the wrong reason.
 */
class FakeAllowances(
    unlimited: Boolean = true,
    cardsLeft: Int = Allowance.CARDS_A_DAY,
    gameNightsLeft: Int = Allowance.NIGHTS_A_WEEK,
) : Allowances {

    val state = MutableStateFlow(
        Allowance(cardsLeft = cardsLeft, gameNightsLeft = gameNightsLeft, unlimited = unlimited),
    )

    override val allowance: Flow<Allowance> = state

    /** How many cards and evenings were counted. */
    var cardsCounted: Int = 0
        private set

    var nightsCounted: Int = 0
        private set

    override suspend fun cardDealt() {
        cardsCounted += 1
    }

    override suspend fun nightPlanned() {
        nightsCounted += 1
    }
}

/** A [LaunchOffer] a test sets by hand. Off by default: most tests are not about the launch. */
class FakeLaunchOffer(
    override val everythingFree: Boolean = false,
    override val freeUntilMillis: Long? = null,
    override val daysLeft: Int? = null,
    override val shouldRemind: Boolean = false,
) : LaunchOffer
