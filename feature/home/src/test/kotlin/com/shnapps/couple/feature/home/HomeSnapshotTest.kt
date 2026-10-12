package com.shnapps.couple.feature.home

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
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
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
    fun `with no account it is the whole app, and every way to start is a named row`() {
        capture("home-alone") { HomeContent(state(Company.ALONE), HomeActions()) }

        composeRule.onNodeWithText("One phone, both of you.").assertIsDisplayed()
        // The first card is two taps away: this row, and whatever the card says (D-150).
        composeRule.onNodeWithText("Cards").assertIsDisplayed()
        // There is no button that starts something the list does not contain.
        assertThat(composeRule.onAllNodesWithText("Play Now").fetchSemanticsNodes()).isEmpty()
        // Game Night is first and is not one of the catalogue's games: it is a plan over all of it.
        composeRule.onNodeWithText("Game Night").performScrollTo().assertIsDisplayed()
        // Nothing is hidden until later: the other half of the app is a toggle away, saying so.
        composeRule.onNodeWithContentDescription("Apart").assertExists()
    }

    @Test
    fun `paired is the same screen with more of it working`() {
        capture("home-paired") { HomeContent(state(Company.PAIRED), HomeActions()) }

        // The same heading and the same first row: signing in adds, it does not replace (§6).
        composeRule.onNodeWithText("One phone, both of you.").assertIsDisplayed()
        composeRule.onNodeWithText("Cards").assertIsDisplayed()
        composeRule.onNodeWithText("Truth or Dare").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the toggle changes the heading, and nothing else`() {
        val apart = state(Company.PAIRED).copy(mode = Mode.APART)
        capture("home-apart-mode") { HomeContent(apart, HomeActions()) }

        // It used to replace the list below it with a second surface. V2 has no second surface:
        // apart is the same games played a phone each, so the heading changes and the list loses
        // the two things that are played on one phone (D-150).
        composeRule.onNodeWithText("Two phones, one evening.").assertIsDisplayed()
        composeRule.onNodeWithText("Truth or Dare").performScrollTo().assertIsDisplayed()
        for (word in listOf("Cards", "Game Night")) {
            assertThat(composeRule.onAllNodesWithText(word).fetchSemanticsNodes()).isEmpty()
        }
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
        // Said under the toggle that asks it, in one line, and only in this mode.
        composeRule.onNodeWithText("Paired with their phone.").assertIsDisplayed()
        composeRule.onNodeWithText("Truth or Dare").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `apart with nobody paired asks for the one thing that is missing`() {
        val state = state(Company.ALONE).copy(mode = Mode.APART)

        capture("home-apart-unpaired") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("Apart needs both phones").assertIsDisplayed()
        // The one thing on offer is the thing that fixes it.
        composeRule.onNodeWithText("Pair your phones").assertIsDisplayed()
        // Nothing is dealt on one phone under a toggle that says two (D-146): no way to start, and
        // none of the games or the heat that would have started it.
        for (word in listOf("Cards", "Game Night", "Truth or Dare", "Play")) {
            assertThat(
                composeRule.onAllNodesWithText(word, ignoreCase = true).fetchSemanticsNodes(),
            ).isEmpty()
        }
    }

    @Test
    fun `the cards row says how far it goes, in the heat's own word`() {
        val hot = state(Company.ALONE).copy(tier = Intensity.HOT)
        capture("home-cards-hot") { HomeContent(hot, HomeActions()) }

        // What the row will do is what the picker above it says: a ceiling, not a promise.
        composeRule.onNodeWithText("One dare at a time. Six a round, up to Hot.").assertIsDisplayed()
    }

    @Test
    fun `a game being made says so where it was tapped`() {
        val making = state(Company.PAIRED).copy(making = true)
        capture("home-getting-ready") { HomeContent(making, HomeActions()) }

        // The button that used to say it is gone: a tap that gets no answer looks like no tap.
        composeRule.onNodeWithText("Getting it ready…").assertIsDisplayed()
    }

    @Test
    fun `the free period says when it ends, and never counts down`() {
        val free = state(Company.ALONE).copy(
            access = LevelAccess(
                ceiling = Intensity.EXTREME,
                owned = emptySet(),
                freeForNow = true,
                freeUntilMillis = NOVEMBER_14,
            ),
        )
        capture("home-free-month") { HomeContent(free, HomeActions()) }

        composeRule.onNodeWithText("Everything is open until 14 November").assertIsDisplayed()
        composeRule.onNodeWithText("Then Mild and Flirty stay free, and the rest are one-time packs.")
            .assertIsDisplayed()
    }

    @Test
    fun `when the free period is over the dial says which levels are not theirs`() {
        val locked = state(Company.ALONE).copy(access = LevelAccess.FREE, tier = Intensity.FLIRTY)
        capture("home-locked") { HomeContent(locked, HomeActions()) }

        // Locked, not hidden: the ramp is the same length for every couple.
        composeRule.onNodeWithContentDescription("Level 3 of 5, Spicy, locked").assertExists()
        composeRule.onNodeWithContentDescription("Level 5 of 5, Extreme, locked").assertExists()
    }

    private fun state(company: Company) = HomeUiState(
        company = company,
        games = games,
        tier = Intensity.HOT,
        // Open, as it is while the free period is on (V2 rule 5, D-151).
        access = LevelAccess.OPEN,
    )

    private companion object {
        const val SETTLE_MS = 2_000L

        /** 14 November 2026, midday UTC: the date the design's banner says. */
        const val NOVEMBER_14 = 1_794_657_600_000L

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
