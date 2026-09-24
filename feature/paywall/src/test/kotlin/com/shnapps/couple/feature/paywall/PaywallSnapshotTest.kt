package com.shnapps.couple.feature.paywall

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.billing.PremiumOffer
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Gate
import com.shnapps.couple.core.model.SubscriptionState
import com.shnapps.couple.core.model.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The paywall (BUILD_PROMPT.md §19) — and the promises on it: what is included, what it costs,
 * a plain way out, and no dark patterns. No countdown, no "offer ends", no disguised close.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class PaywallSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val offer = PremiumOffer(productId = "afterhours.premium.monthly", price = "£4.99", period = "P1M")

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun arrivingFromALimit() {
        capture("paywall-gate") {
            PaywallContent(PaywallUiState(gate = Gate.NIGHTS_THIS_WEEK, offer = offer), PaywallActions())
        }
        composeRule.onNodeWithText("That's this week's nights.").assertIsDisplayed()
        composeRule.onNodeWithText("£4.99 a month, cancel any time.").assertIsDisplayed()
        composeRule.onNodeWithText("Go premium").assertIsDisplayed()
        // The way out is a plain one, in the same place as on every other screen.
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
        // Nothing that counts down, expires, or implies it will (§19).
        for (pattern in listOf("offer ends", "limited", "hurry", "only today")) {
            assertThat(composeRule.onAllNodesWithText(pattern, substring = true, ignoreCase = true)
                .fetchSemanticsNodes()).isEmpty()
        }
    }

    @Test
    fun openedOnItsOwn() {
        capture("paywall-plain") { PaywallContent(PaywallUiState(offer = offer), PaywallActions()) }
        composeRule.onNodeWithText("More of it.").assertIsDisplayed()
        composeRule.onNodeWithText("I already subscribed").assertIsDisplayed()
    }

    @Test
    fun alreadyIn() {
        val state = PaywallUiState(
            entitlement = Entitlement(tier = Tier.PREMIUM, state = SubscriptionState.CANCELED),
            offer = offer,
        )
        capture("paywall-premium") { PaywallContent(state, PaywallActions()) }
        composeRule.onNodeWithText("Premium, and set to end. Everything stays on until it does.").assertIsDisplayed()
        // Nobody is sold what they already have.
        assertThat(composeRule.onAllNodesWithText("Go premium").fetchSemanticsNodes()).isEmpty()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
