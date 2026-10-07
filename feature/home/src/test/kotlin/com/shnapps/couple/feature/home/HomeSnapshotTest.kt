package com.shnapps.couple.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home, the only one (REDESIGN_PROMPT.md §6).
 *
 * The first two goldens are the claim the whole rebuild rests on: a phone with no account and a
 * paired couple are looking at the same screen. If a change ever makes those two diverge into two
 * different layouts again, it shows up here as an image diff rather than as a review comment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class HomeSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun `with no account it is the whole app, and Play Now is one tap`() {
        capture("home-alone") { HomeContent(state(Company.ALONE), HomeActions()) }

        composeRule.onNodeWithText("One phone, both of you.").assertIsDisplayed()
        // The brief's first card within two taps: this, and whatever the card says.
        composeRule.onNodeWithText("Play Now").assertIsDisplayed()
        // Game Night is first and is not one of the catalogue's games: it is a plan over all of it.
        composeRule.onNodeWithText("Game Night").performScrollTo().assertIsDisplayed()
        // Nothing is hidden until later: the other half of the app is a toggle away, saying so.
        composeRule.onNodeWithContentDescription("Apart").assertExists()
    }

    @Test
    fun `paired is the same screen with more of it working`() {
        capture("home-paired") { HomeContent(state(Company.PAIRED), HomeActions()) }

        // The same heading and the same one button: signing in adds, it does not replace (§6).
        composeRule.onNodeWithText("One phone, both of you.").assertIsDisplayed()
        composeRule.onNodeWithText("Play Now").assertIsDisplayed()
        composeRule.onNodeWithText("Truth or Dare").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the toggle changes the heading, and nothing else`() {
        val apart = state(Company.PAIRED).copy(mode = Mode.APART)
        capture("home-apart-mode") { HomeContent(apart, HomeActions()) }

        // It used to replace the list below it with a second surface. V2 has no second surface:
        // apart is the same games played a phone each, so only the heading changes.
        composeRule.onNodeWithText("Two phones, one evening.").assertIsDisplayed()
        composeRule.onNodeWithText("Play Now").assertIsDisplayed()
        composeRule.onNodeWithText("Game Night").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `something waiting has both answers on the screen`() {
        val waiting = state(Company.PAIRED).copy(
            waitingForMe = NightPlan(experienceId = "planned", mine = false, chapterCount = 5, opened = false),
        )
        capture("home-waiting") { HomeContent(waiting, HomeActions()) }

        // The card used to be a question above a void: no button, and nothing saying what to do.
        composeRule.onNodeWithText("They planned an evening for you.").assertIsDisplayed()
        composeRule.onNodeWithText("Open it").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing happens to it if you leave it. It waits.").assertIsDisplayed()
    }

    @Test
    fun `something under way has the way back into it`() {
        val state = state(Company.PAIRED).copy(
            active = NightSession(
                id = "s",
                experienceId = "e",
                state = SessionState.CHAPTER_ACTIVE,
                pausedFrom = null,
                currentChapter = 1,
                chapterCount = 6,
                consentRequired = false,
                consented = false,
                partnerConsented = false,
                isHost = true,
                endReason = null,
                experienceType = ExperienceType.NIGHT,
            ),
        )
        capture("home-under-way") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("Your evening is waiting. Pick up where you left off.").assertIsDisplayed()
        composeRule.onNodeWithText("Pick it up").assertIsDisplayed()
    }

    @Test
    fun `apart is the same games, a phone each`() {
        // V2 rule 3: there is no second surface any more. The toggle says how a game is played,
        // not which half of an app this is, so the grid below it is the same grid.
        val state = state(Company.PAIRED).copy(mode = Mode.APART)

        capture("home-apart") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("Two phones, one evening.").assertIsDisplayed()
        composeRule.onNodeWithText("Truth or Dare").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `apart with nobody paired asks for the one thing that is missing`() {
        val state = state(Company.ALONE).copy(mode = Mode.APART)

        capture("home-apart-unpaired") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("Pair with their phone").performScrollTo().assertIsDisplayed()
        // Everything still works on this phone alone, so it is a row and not a wall.
        composeRule.onNodeWithText("Game Night").performScrollTo().assertIsDisplayed()
    }

    private fun state(company: Company) = HomeUiState(
        company = company,
        games = games,
        tier = Intensity.HOT,
        // Open, as it is for the first month after install (V2 rule 5).
        access = LevelAccess.OPEN,
    )

    private companion object {
        const val SETTLE_MS = 2_000L

        val games = listOf(
            game("TRUTH_OR_DARE", "Truth or Dare", "The classic, with better questions."),
            game("HEAT_DICE", "Heat Dice", "Three dice. Do what they say."),
            game("FANTASY_MATCH", "Fantasy Match", "Only what you both said yes to."),
        )

        fun game(id: String, title: String, tagline: String) = Game(
            id = id,
            title = title,
            tagline = tagline,
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 6,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.NONE,
            promptKinds = emptyList(),
            steps = emptyList(),
        )
    }
}
