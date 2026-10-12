package com.shnapps.couple.feature.store

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.billing.LevelPacks
import com.shnapps.couple.core.data.billing.PackOffer
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Intensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store (V2 rule 5) — and the promises on it: three packs, a price each, a plain way out, and
 * no dark patterns. No countdown, no "offer ends", no "most popular", no disguised close.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class StoreSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val offers = LevelPacks.productIds.mapNotNull { product ->
        LevelPacks.levelOf(product)?.let { level ->
            PackOffer(level = level, productId = product, price = "£${level.level}.99")
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun threePacksPriced() {
        capture("store-packs") {
            StoreContent(StoreUiState(access = LevelAccess.FREE, offers = offers), StoreActions())
        }
        composeRule.onNodeWithText("Go as far as you like.").assertIsDisplayed()
        // What nobody pays for, first; then each pack with its price once and its own button.
        composeRule.onNodeWithText("Mild and Flirty").assertIsDisplayed()
        composeRule.onNodeWithText("£3.99").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock Spicy").assertIsDisplayed()
        composeRule.onNodeWithText("Unlock Extreme").performScrollTo().assertIsDisplayed()
        // The way out is the bar's back label, in the same place as on every other screen.
        composeRule.onNodeWithContentDescription("Not now").assertExists()
        // Nothing that counts down, expires, or implies it will.
        for (pattern in listOf("offer ends", "limited", "hurry", "only today", "most popular")) {
            assertThat(
                composeRule.onAllNodesWithText(pattern, substring = true, ignoreCase = true)
                    .fetchSemanticsNodes(),
            ).isEmpty()
        }
    }

    @Test
    fun eachPackSaysWhichLevelsItOpens() {
        capture("store-ladder") {
            StoreContent(StoreUiState(access = LevelAccess.FREE, offers = offers), StoreActions())
        }

        // The ladder, in words for anything that cannot see the chips: Hot opens Spicy under it.
        composeRule.onNodeWithContentDescription("Opens Mild, Flirty, Spicy").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("Opens Mild, Flirty, Spicy, Hot").performScrollTo().assertExists()
        composeRule.onNodeWithContentDescription("Opens Mild, Flirty, Spicy, Hot, Extreme")
            .performScrollTo().assertExists()
    }

    @Test
    fun arrivingFromALockedLevel() {
        val state = StoreUiState(asked = Intensity.SPICY, access = LevelAccess.FREE, offers = offers)
        capture("store-asked") { StoreContent(state, StoreActions()) }
        composeRule.onNodeWithText("Spicy, and everything under it.").assertIsDisplayed()
    }

    @Test
    fun duringTheFreePeriod() {
        val state = StoreUiState(
            access = LevelAccess(
                ceiling = Intensity.EXTREME,
                owned = emptySet(),
                freeForNow = true,
                freeUntilMillis = NOVEMBER_14,
            ),
            offers = offers,
        )
        capture("store-free-month") { StoreContent(state, StoreActions()) }
        composeRule.onNodeWithText("Everything is open until 14 November").assertIsDisplayed()
        composeRule.onNodeWithText("Then Mild and Flirty stay free, and the rest are one-time packs.")
            .assertIsDisplayed()
        // Nobody is sold what they already have: the price is information, with a date, not a button.
        composeRule.onNodeWithText("Open until 14 November, then £3.99").performScrollTo().assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Unlock Spicy").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun whatTheyAlreadyOwn() {
        val state = StoreUiState(
            access = LevelAccess(ceiling = Intensity.HOT, owned = setOf(Intensity.HOT)),
            offers = offers,
        )
        capture("store-owned") { StoreContent(state, StoreActions()) }
        // The ladder, shown: Spicy came with Hot, and only Extreme is still for sale.
        assertThat(composeRule.onAllNodesWithText("Yours, for good.").fetchSemanticsNodes()).hasSize(2)
        composeRule.onNodeWithText("Unlock Extreme").performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L

        /**
         * 14 November 2026, midday UTC — the date the design's banner says.
         *
         * Midday rather than midnight so the day is the same one in every zone a test machine
         * might be in: a banner that reads "13 November" on a European CI box and "14 November"
         * here would be a flake nobody could reproduce.
         */
        const val NOVEMBER_14 = 1_794_657_600_000L
    }
}
