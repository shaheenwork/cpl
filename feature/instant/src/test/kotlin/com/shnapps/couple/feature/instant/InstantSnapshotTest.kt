package com.shnapps.couple.feature.instant

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.model.Intensity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two screens the rebuild is judged on (REDESIGN_PROMPT.md §5.1, §5.3): first launch, and the
 * card.
 *
 * The goldens are the point: this is the first thing anyone sees of the app, and a change that
 * makes the first screen longer, or buries the card under controls, should show up as an image
 * diff rather than as a review comment.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class InstantSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `first launch is one screen`() {
        capture("instant-start") {
            StartContent(
                state = StartUiState(tier = Intensity.HOT),
                onTier = {},
                onConfirm = {},
                onNotOldEnough = {},
            )
        }

        // Everything the first screen asks for, and nothing else: an age, a tier, a way in.
        composeRule.onNodeWithText("One question, then we start.").assertIsDisplayed()
        composeRule.onNodeWithText("We're both 18 or over — start").assertIsDisplayed()
        composeRule.onNodeWithText("I'm not 18").assertIsDisplayed()
    }

    @Test
    fun `a card arrives face down, and nothing about it is on the screen yet`() {
        capture("instant-face-down") { play(state(revealed = false)) }

        // Face down means absent: the card's words are not in the tree until it is turned (D-143).
        composeRule.onNodeWithContentDescription("A card, face down. Tap to reveal it.").assertIsDisplayed()
        composeRule.onNodeWithText(card.title).assertDoesNotExist()
        // Nobody can have done a card nobody has read.
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun `a card, and the three things you can do about it`() {
        capture("instant-card") { play(state()) }

        // Done and Skip dock within thumb reach; Stop and "Too far" are on the safety rail under
        // them, where a thumb already is and where neither can be mistaken for a third choice.
        composeRule.onNodeWithText("Skip").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsEnabled()
        composeRule.onNodeWithText("Too far").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun `the rail says which card this is`() {
        capture("instant-card-later") { play(state(played = 4)) }

        // Found on the device: the rail was pinned to the first segment for the whole run,
        // because the screen subtracted one from an index that was already 0-based.
        composeRule.onNodeWithContentDescription("Card 5 of 6").assertExists()
    }

    @Test
    fun `a clock, once someone starts one`() {
        capture("instant-timer") { play(state(timer = CardTimer(totalSeconds = 300, remainingSeconds = 182))) }

        composeRule.onNodeWithText("PAUSE").assertIsDisplayed()
    }

    @Test
    fun `saying not this kind of thing`() {
        capture("instant-refusal") { play(state(refusing = card)) }

        composeRule.onNodeWithText("Nobody is told. Not tonight, not ever — your partner never sees this.")
            .assertIsDisplayed()
    }

    @Test
    fun `offering to turn it up`() {
        capture("instant-hotter") { play(state(offeringHotter = Intensity.EXTREME)) }

        composeRule.onNodeWithText("Turn it up?").assertIsDisplayed()
    }

    @Test
    fun `the end of a run asks rather than ends`() {
        capture("instant-run-complete") { play(state(runComplete = true, played = 6)) }

        composeRule.onNodeWithText("Keep going").assertIsDisplayed()
        composeRule.onNodeWithText("That's us for tonight").assertIsDisplayed()
    }

    @Test
    fun `the dial says which levels are not theirs yet`() {
        // Locked, not hidden: a couple cannot decide whether they want Spicy if the app will not
        // admit it exists, and hiding it makes the ramp a different length for different couples.
        capture("instant-locked-dial") {
            // Flirty, because that is the only heat such a phone can be playing at: the deck is
            // clamped to the ceiling, so a locked dial never sits under a Hot card. Opened from
            // the heat chip in the bar, which is where the dial lives now (D-143).
            play(state(access = LevelAccess.FREE).copy(tier = Intensity.FLIRTY, sheet = DeckSheet.HEAT))
        }
    }

    @Test
    fun `a locked level tapped is a card, not a wall`() {
        capture("instant-locked-level") {
            play(
                state(access = LevelAccess.FREE).copy(
                    tier = Intensity.FLIRTY,
                    lockedLevel = Intensity.SPICY,
                    lockedPrice = "£3.99",
                ),
            )
        }
    }

    @Composable
    private fun play(state: InstantPlayUiState) {
        InstantPlayContent(
            state = state,
            actions = DeckActions(),
        )
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun state(
        timer: CardTimer? = null,
        refusing: InstantCard? = null,
        offeringHotter: Intensity? = null,
        runComplete: Boolean = false,
        played: Int = 2,
        access: LevelAccess = LevelAccess.OPEN,
        revealed: Boolean = true,
    ) = InstantPlayUiState(
        card = card,
        tier = Intensity.HOT,
        played = played,
        loading = false,
        position = played,
        timer = timer,
        refusing = refusing,
        offeringHotter = offeringHotter,
        runComplete = runComplete,
        // Open: the first month after install, which is what most of these shots are of.
        access = access,
        // Turned over, which is what most of these shots are of: the card a couple reads.
        revealedId = if (revealed) card.id else null,
    )

    private companion object {
        const val SETTLE_MS = 1_000L

        val card = InstantCard(
            id = "tease_opposite_corners",
            title = "Opposite Corners",
            body = "Sit at opposite sides of the room and talk only by message. " +
                "Describe what you would do if they came closer. Nobody moves until someone gives in.",
            minutes = 5,
            intensity = Intensity.SPICY,
            pack = "TEASE",
            aftercare = false,
            themes = setOf("teasing_anticipation"),
        )
    }
}
