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
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings, which in v2 is four groups and no account (V2 rule 1).
 *
 * The assertions that matter are the absences. There is nothing to sign out of, nothing to export
 * and nothing to delete, because the only thing this app has is a pair of phones — so the one
 * destructive control is "Unpair and wipe", and it says in one sentence exactly what it clears.
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

    @Test
    fun theWholeOfSettings() {
        val state = SettingsUiState(
            paired = true,
            entitlement = Entitlement(tier = Tier.PREMIUM),
            access = LevelAccess(ceiling = Intensity.HOT, owned = setOf(Intensity.HOT)),
        )
        capture("settings-hub") { SettingsContent(state, SettingsRoutes(), SettingsActions()) }
        // Nothing here is an account: this phone is either on its own or half of a pair (V2).
        composeRule.onNodeWithText("Paired with their phone.").assertIsDisplayed()
        // What they own, said as a fact. Nobody is sold anything from a settings list.
        composeRule.onNodeWithText("Hot and below. Restore, or go further.").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("HOW IT LOOKS ON YOUR HOME SCREEN").assertIsDisplayed()
        // The one destructive thing in the app, and the sentence says the whole of it.
        composeRule.onNodeWithText("Unpair and wipe").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(
            "Clears the pair on both phones and everything you shared. Your own limits stay on this phone.",
        ).performScrollTo().assertIsDisplayed()
        // Nothing that looks like an account: there is not one to delete, export, or sign out of.
        for (word in listOf("Export my data", "Delete", "Sign out", "Signed in")) {
            assertThat(
                composeRule.onAllNodesWithText(word, substring = true).fetchSemanticsNodes(),
            ).isEmpty()
        }
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
