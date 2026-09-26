package com.shnapps.couple.feature.instant

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.Score
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.TurnOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two screens the rebuild is judged on (REDESIGN_PROMPT.md §5.1, §5.3).
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
                state = StartUiState(heat = Heat.HOT),
                onHeat = {},
                onConfirm = {},
                onNotOldEnough = {},
                onPairInstead = {},
            )
        }

        // Everything the first screen asks for, and nothing else: an age, a heat, a way in.
        composeRule.onNodeWithText("Two questions, then we start.").assertIsDisplayed()
        composeRule.onNodeWithText("We're both 18 or over — start").assertIsDisplayed()
    }

    @Test
    fun `a card, and the three things you can do about it`() {
        capture("instant-card") { play(state()) }

        composeRule.onNodeWithText("Skip").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Too far — not this kind of thing").assertIsDisplayed()
    }

    @Test
    fun `a clock, once someone starts one`() {
        capture("instant-timer") { play(state(timer = CardTimer(totalSeconds = 300, remainingSeconds = 182))) }

        composeRule.onNodeWithText("Stop the clock").assertIsDisplayed()
    }

    @Test
    fun `saying not this kind of thing`() {
        capture("instant-refusal") { play(state(refusing = card)) }

        composeRule.onNodeWithText("Nobody is told. Not tonight, not ever — your partner never sees this.")
            .assertIsDisplayed()
    }

    @Test
    fun `offering to turn it up`() {
        capture("instant-hotter") { play(state(offeringHotter = Heat.WILD)) }

        composeRule.onNodeWithText("Turn it up?").assertIsDisplayed()
    }

    @Test
    fun `the end of a run asks rather than ends`() {
        capture("instant-run-complete") { play(state(runComplete = true, played = 6)) }

        composeRule.onNodeWithText("Keep going").assertIsDisplayed()
        composeRule.onNodeWithText("That's us for tonight").assertIsDisplayed()
    }

    @Test
    fun `home is games, not a form`() {
        capture("instant-home") {
            InstantHomeContent(
                state = InstantHomeUiState(games = shippedGames, heat = Heat.HOT),
                onStartNow = {},
                onGame = {},
                onApart = {},
                onPlanEvening = {},
                onHeat = {},
            )
        }

        // What replaced the six-field build form: one Start, and the games by name.
        composeRule.onNodeWithText("Start now").assertIsDisplayed()
        composeRule.onNodeWithText("Spin").assertIsDisplayed()
    }

    @Test
    fun `a game dealt on this phone`() {
        capture("instant-game") {
            InstantGameContent(
                state = InstantGameUiState(
                    title = "Dirty Dice",
                    moment = GameMoment.Turn(
                        round = 0,
                        primitive = RoundPrimitive.Challenge(
                            step = 1,
                            seat = 0,
                            text = "Do it without using your hands at all.",
                            kind = null,
                        ),
                        canSkip = true,
                    ),
                    round = 0,
                    rounds = 6,
                    loading = false,
                ),
                onBack = {},
                onProceed = {},
                onAnswer = {},
                onSkipPrompt = {},
                onNextRound = {},
                onPlayAgain = {},
            )
        }

        composeRule.onNodeWithText("DIRTY DICE · ROUND 1 OF 6").assertIsDisplayed()
        composeRule.onNodeWithText("Not this one").assertIsDisplayed()
    }

    @Test
    fun `a finished game does not count past its last round`() {
        capture("instant-game-finished") {
            InstantGameContent(
                state = InstantGameUiState(
                    title = "Dirty Dice",
                    moment = GameMoment.Finished(round = 6, score = Score()),
                    round = 6,
                    rounds = 6,
                    loading = false,
                ),
                onBack = {},
                onProceed = {},
                onAnswer = {},
                onSkipPrompt = {},
                onNextRound = {},
                onPlayAgain = {},
            )
        }

        // Found on the device: the eyebrow read "ROUND 7 OF 6" once the game was over.
        composeRule.onNodeWithText("DIRTY DICE").assertIsDisplayed()
        composeRule.onNodeWithText("That's the game.").assertIsDisplayed()
    }

    @Composable
    private fun play(state: InstantPlayUiState) {
        InstantPlayContent(
            state = state,
            onBack = {},
            onDone = {},
            onSkip = {},
            onTooFar = {},
            onRefuse = {},
            onTurnUp = {},
            onKeepHeat = {},
            onHeat = {},
            onStartTimer = {},
            onStopTimer = {},
            onKeepGoing = {},
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
        offeringHotter: Heat? = null,
        runComplete: Boolean = false,
        played: Int = 2,
    ) = InstantPlayUiState(
        card = card,
        heat = Heat.HOT,
        played = played,
        loading = false,
        position = played,
        timer = timer,
        refusing = refusing,
        offeringHotter = offeringHotter,
        runComplete = runComplete,
    )

    private companion object {
        const val SETTLE_MS = 1_000L

        val shippedGames = listOf(
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

        val card = InstantCard(
            id = "tease_opposite_corners",
            title = "Opposite Corners",
            body = "Sit at opposite sides of the room and talk only by message. " +
                "Describe what you would do if they came closer. Nobody moves until someone gives in.",
            minutes = 5,
            intensity = Intensity.NAUGHTY,
            themes = setOf("teasing_anticipation"),
        )
    }
}
