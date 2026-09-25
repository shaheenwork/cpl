package com.shnapps.couple.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.Entitlement
import com.shnapps.couple.core.model.Tier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings and the end of an account (BUILD_PROMPT.md §14.9) — including the part that has to be
 * said out loud before anyone taps it: deleting an account takes the couple with it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class AccountSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun everythingAboutThisAccount() {
        val state = SettingsUiState(
            email = "alex@example.com",
            paired = true,
            entitlement = Entitlement(tier = Tier.PREMIUM),
        )
        capture("settings-hub") { SettingsContent(state, SettingsRoutes(), SettingsActions()) }
        composeRule.onNodeWithText("Signed in as alex@example.com.").assertIsDisplayed()
        composeRule.onNodeWithText("Export my data").assertIsDisplayed()
        composeRule.onNodeWithText("Delete my account").assertIsDisplayed()
        composeRule.onNodeWithText("Unpair").assertIsDisplayed()
    }

    @Test
    fun whatAnExportLeaves() {
        val state = SettingsUiState(
            email = "alex@example.com",
            export = ExportSummary(fileName = "afterhours-1790000000000.json", bytes = 20_480),
        )
        capture("settings-exported") { SettingsContent(state, SettingsRoutes(), SettingsActions()) }
        // Below the fold on a phone this size, but on the screen and readable by a screen reader.
        composeRule.onNodeWithText("afterhours-1790000000000.json — 20480 bytes, on this phone.").assertExists()
        composeRule.onNodeWithText("Our copy is deleted after a week. Yours is yours.").assertExists()
    }

    @Test
    fun theEndOfAnAccount() {
        capture("settings-delete") {
            DeleteAccountContent(SettingsUiState(paired = true), sure = false, actions = DeleteAccountActions())
        }
        composeRule.onNodeWithText("This cannot be undone.").assertIsDisplayed()
        // The part nobody expects, said before the first tap.
        composeRule.onNodeWithText(
            "• The couple — and with it every night you played, every memory you kept, and everything " +
                "either of you sent the other.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("I understand what goes").assertIsDisplayed()
        // The button that actually deletes is behind that one.
        assertThat(composeRule.onAllNodesWithText("Delete everything, permanently").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun onceTheyHaveSaidTheyUnderstand() {
        capture("settings-delete-sure") {
            DeleteAccountContent(SettingsUiState(paired = true), sure = true, actions = DeleteAccountActions())
        }
        composeRule.onNodeWithText("Delete everything, permanently").assertIsDisplayed()
        composeRule.onNodeWithText("Keep my account").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
