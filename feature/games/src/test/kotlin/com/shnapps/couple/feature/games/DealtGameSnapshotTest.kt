package com.shnapps.couple.feature.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.Option
import com.shnapps.couple.core.engine.game.RolledDie
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.Score
import com.shnapps.couple.core.model.GamePlayers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A game dealt on this phone (REDESIGN_PROMPT.md §5.2), drawn by the one player.
 *
 * It lives beside the session player's goldens on purpose: the two are meant to be
 * indistinguishable to a person, and a diff here is how anyone would notice they had drifted apart.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class DealtGameSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a game dealt on this phone`() {
        capture("dealt-game") {
            content(
                DealtGameUiState(
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
            )
        }

        // The game's name is the bar's; where the round is, is the rail's (DESIGN_PROMPT.md §5.6).
        composeRule.onNodeWithText("DIRTY DICE").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Card 1 of 6").assertExists()
        composeRule.onNodeWithText("Not this one").assertIsDisplayed()
    }

    @Test
    fun `three dice, landed, with the clock they landed on`() {
        capture("dealt-game-dice") {
            content(
                DealtGameUiState(
                    title = "Heat Dice",
                    players = GamePlayers("Alex", "Sam"),
                    moment = GameMoment.Turn(
                        round = 1,
                        primitive = RoundPrimitive.Dice(
                            step = 0,
                            seat = 0,
                            caption = "They stay completely still. You don't.",
                            dice = listOf(
                                die("What", listOf("Kiss", "Use your hands", "Use your mouth"), landed = 2),
                                die("Where", listOf("Their neck", "Their back", "Their thighs"), landed = 0),
                                die("How long", listOf("30 seconds", "2 minutes"), landed = 1, seconds = 120),
                            ),
                        ),
                        canSkip = true,
                    ),
                    round = 1,
                    rounds = 6,
                    loading = false,
                ),
            )
        }

        // Three dice, each saying what it is for and what it landed on — and whose turn it is, which
        // a game of turns could not say at all before names were asked for (D-130).
        composeRule.onNodeWithText("ALEX").assertIsDisplayed()
        composeRule.onNodeWithText("Use your mouth").assertIsDisplayed()
        composeRule.onNodeWithText("Their neck").assertIsDisplayed()
        composeRule.onNodeWithText("Start the clock").assertIsDisplayed()
    }

    @Test
    fun `a finished game does not count past its last round`() {
        capture("dealt-game-finished") {
            content(
                DealtGameUiState(
                    title = "Dirty Dice",
                    moment = GameMoment.Finished(round = 6, score = Score()),
                    round = 6,
                    rounds = 6,
                    loading = false,
                ),
            )
        }

        // Found on the device: the eyebrow read "ROUND 7 OF 6" once the game was over. A finished
        // game has no round, so it has no rail and no title either — only what it says.
        composeRule.onNodeWithText("THAT'S THE GAME").assertIsDisplayed()
        composeRule.onNodeWithText("Nicely played, both of you.").assertIsDisplayed()
    }

    @Test
    fun `a game the content cannot fill says so, and offers a way on`() {
        capture("dealt-game-nothing") { content(DealtGameUiState(loading = false, nothingToPlay = true)) }

        // Never a blank screen: what happened, and the two things that would fix it (§6, U6).
        composeRule.onNodeWithText("Nothing to deal at this tier.").assertIsDisplayed()
        composeRule.onNodeWithText("Pick something else").assertIsDisplayed()
    }

    @Composable
    private fun content(state: DealtGameUiState) {
        DealtGameContent(
            state = state,
            onBack = {},
            onProceed = {},
            onAnswer = {},
            onSkipPrompt = {},
            onPlayAgain = {},
        )
    }

    /** One die as the screen gets it: already landed, because the interpreter decided (D-047). */
    private fun die(label: String, faces: List<String>, landed: Int, seconds: Int? = null) = RolledDie(
        label = label,
        faces = faces.mapIndexed { index, face -> Option("$index", face) },
        landed = landed,
        seconds = seconds,
    )

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
