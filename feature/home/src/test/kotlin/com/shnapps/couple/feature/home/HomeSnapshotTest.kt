package com.shnapps.couple.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightPlan
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.ThreadEntry
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
    fun `with no account it is the whole app, with things marked as needing one`() {
        capture("home-alone") { HomeContent(state(Company.ALONE), HomeActions()) }

        composeRule.onNodeWithText("Pick something. Or let us pick.").assertIsDisplayed()
        composeRule.onNodeWithText("Start now").assertIsDisplayed()
        // The games are the same list, in the same order, before anyone signs in.
        composeRule.onNodeWithText("Spin").assertIsDisplayed()
        // Nothing is hidden until later: what needs an account is here, saying so.
        composeRule.onNodeWithText("Apart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `paired is the same screen with more of it working`() {
        capture("home-paired") { HomeContent(state(Company.PAIRED), HomeActions()) }

        // The same heading and the same one button: signing in adds, it does not replace (§6).
        composeRule.onNodeWithText("Pick something. Or let us pick.").assertIsDisplayed()
        composeRule.onNodeWithText("Start now").assertIsDisplayed()
        composeRule.onNodeWithText("Plan one for them").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Spin").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `every shape of an evening is behind one button`() {
        composeRule.setContent {
            AfterhoursTheme { AfterhoursSurface { HomeContent(state(Company.PAIRED), HomeActions()) } }
        }

        // Five buttons used to compete on this screen. One opens the five now.
        assertThat(composeRule.onAllNodesWithText("Surprise us").fetchSemanticsNodes()).isEmpty()
        composeRule.onNodeWithText("Start now").performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home-start.png")

        composeRule.onNodeWithText("Just deal us cards").assertIsDisplayed()
        composeRule.onNodeWithText("Surprise us").assertIsDisplayed()
        composeRule.onNodeWithText("A mystery").assertIsDisplayed()
        composeRule.onNodeWithText("We have 15 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("Plan the evening").assertIsDisplayed()
    }

    @Test
    fun `with no account the cards are still dealt, and the rest says what it needs`() {
        composeRule.setContent {
            AfterhoursTheme { AfterhoursSurface { HomeContent(state(Company.ALONE), HomeActions()) } }
        }
        composeRule.onNodeWithText("Start now").performClick()
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/home-start-alone.png")

        // The fastest way from opening the app to doing something needs nothing at all.
        composeRule.onNodeWithText("One at a time. Do it, skip it, or say it went too far.").assertIsDisplayed()
        // The others are on the screen, greyed, saying what they need — never hidden.
        composeRule.onNodeWithText("We choose everything — the shape, the length, all of it.").assertIsDisplayed()
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
    fun `apart is a thread`() {
        // What they left, what was sent, what is still sealed (REDESIGN_PROMPT.md §5.4).
        val state = state(Company.PAIRED).copy(
            thread = listOf(
                ThreadEntry.Media(
                    id = "m1",
                    atMillis = NOW,
                    mine = false,
                    kind = MediaKind.VOICE,
                    note = "listen when you're alone",
                    burnAfterView = true,
                    opened = false,
                ),
                ThreadEntry.Left(
                    id = "s1",
                    atMillis = NOW - 1,
                    mine = false,
                    title = null,
                    state = SurpriseState.SCHEDULED,
                    forMoment = null,
                ),
                ThreadEntry.Counting(id = "c1", atMillis = NOW + 1, title = "Friday"),
            ),
        )

        capture("home-apart-thread") { HomeContent(state, HomeActions()) }

        composeRule.onNodeWithText("A voice note, waiting. It goes when you open it.")
            .performScrollTo().assertIsDisplayed()
        // The one that matters: a surprise before its moment says only that it exists.
        composeRule.onNodeWithText("Something is coming.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `curiosities are offered rather than demanded`() {
        // The deck used to run straight after pairing (REDESIGN_PROMPT.md §4.2). Now it is one row
        // on home, and nothing makes anyone finish it.
        capture("home-curiosities") { HomeContent(state(Company.PAIRED).copy(answered = 12), HomeActions()) }

        // Where they are, never how far they have to go: there is no end anyone has to reach.
        composeRule.onNodeWithText("12 answered so far. Every one makes tonight a little more yours.")
            .performScrollTo().assertIsDisplayed()
    }

    private fun state(company: Company) = HomeUiState(company = company, games = games, tier = Intensity.HOT)

    private companion object {
        const val SETTLE_MS = 2_000L

        val games = listOf(
            game("SPIN", "Spin", "It decides. You just do it."),
            game("DIRTY_DICE", "Dirty Dice", "Roll, then run the clock."),
            game("RATE_IT", "Rate It", "How much, without saying a number."),
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

/** A fixed moment: a golden must not change with the clock. */
private const val NOW = 1_800_000_000_000L
