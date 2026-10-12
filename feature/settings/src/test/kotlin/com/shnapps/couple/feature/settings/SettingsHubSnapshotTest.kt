package com.shnapps.couple.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.AppLockMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings, which in v2 is a card for the pair and two groups of rows, and no account (V2 rule 1).
 *
 * The assertions that matter are the absences. There is nothing to sign out of, nothing to export
 * and nothing to delete, because the only thing this app has is a pair of phones — so the one
 * destructive control is "Unpair and wipe", and it asks before it does anything.
 *
 * Panic, the launcher-icon picker and "what you own" are gone from this screen on purpose (D-146):
 * the rows that opened nothing of their own, or that sold. A test holds that they stay gone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class SettingsHubSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Composable
    private fun hub(state: SettingsUiState, routes: SettingsRoutes = SettingsRoutes()) =
        SettingsContent(state, routes, SettingsActions())

    @Test
    fun paired() {
        capture("settings-hub") { hub(SettingsUiState(paired = true, lock = AppLockMode.PIN)) }

        composeRule.onNodeWithText("Paired").assertIsDisplayed()
        composeRule.onNodeWithText("Unpair…").assertIsDisplayed()
        // The lock says whether it is on before it is opened.
        composeRule.onNodeWithText("App lock").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ON").performScrollTo().assertIsDisplayed()
        // Nothing that looks like an account: there is not one to delete, export, or sign out of.
        for (word in listOf("Export my data", "Delete", "Sign out", "Signed in")) {
            assertThat(
                composeRule.onAllNodesWithText(word, substring = true).fetchSemanticsNodes(),
            ).isEmpty()
        }
        // The three that were taken away, and must not come back by accident.
        for (word in listOf("Panic", "HOW IT LOOKS ON YOUR HOME SCREEN", "What you own")) {
            assertThat(
                composeRule.onAllNodesWithText(word, substring = true, ignoreCase = true).fetchSemanticsNodes(),
            ).isEmpty()
        }
    }

    @Test
    fun notPaired() {
        capture("settings-not-paired") { hub(SettingsUiState(paired = false, lock = AppLockMode.OFF)) }

        composeRule.onNodeWithText("Not paired").assertIsDisplayed()
        composeRule.onNodeWithText("Pair your phones").assertIsDisplayed()
        composeRule.onNodeWithText("OFF").performScrollTo().assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Unpair", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun theQuestionBeforeUnpairing() {
        capture("settings-unpair-question") { hub(SettingsUiState(paired = true, confirmingUnpair = true)) }

        composeRule.onNodeWithText("Unpair from their phone?").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Clears the pair on both phones and everything you shared. Your own limits stay on this phone.",
        ).assertIsDisplayed()
        // The safe answer is the loud one.
        composeRule.onNodeWithText("Keep the pair").assertIsDisplayed()
        composeRule.onNodeWithText("Unpair and wipe").assertIsDisplayed()
    }

    @Test
    fun whileItIsHappening() {
        capture("settings-unpairing") { hub(SettingsUiState(paired = true, busy = true)) }

        composeRule.onNodeWithText("Unpairing…").assertIsDisplayed()
        composeRule.onNodeWithText("Clearing the pair on both phones.").assertIsDisplayed()
        // Nothing to tap: a second tap is the one that must not land.
        assertThat(composeRule.onAllNodesWithText("Unpair…").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun afterwardsItSaysSo() {
        capture("settings-unpaired") { hub(SettingsUiState(paired = false, unpaired = true)) }

        composeRule.onNodeWithText("You're unpaired").assertIsDisplayed()
        composeRule.onNodeWithText(
            "The pair, and everything you shared, is cleared on both phones. Your own limits are still here.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Pair your phones").assertIsDisplayed()
    }

    @Test
    fun whenItFailsItSaysNothingChanged() {
        capture("settings-unpair-failed") {
            hub(SettingsUiState(paired = true, error = SettingsError.OFFLINE))
        }

        composeRule.onNodeWithText("You're offline. Unpairing needs the network, so nothing has changed.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Paired").assertIsDisplayed()
    }

    @Test
    fun developerToolsAreOnlyInADebugBuild() {
        capture("settings-developer") {
            hub(SettingsUiState(), SettingsRoutes(onGallery = {}, onInspector = {}))
        }
        composeRule.onNodeWithText("Component gallery").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Content inspector").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aProductBuildHasNoDeveloperGroup() {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { hub(SettingsUiState()) } } }

        for (word in listOf("Developer", "Component gallery", "Content inspector")) {
            assertThat(composeRule.onAllNodesWithText(word).fetchSemanticsNodes()).isEmpty()
        }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
