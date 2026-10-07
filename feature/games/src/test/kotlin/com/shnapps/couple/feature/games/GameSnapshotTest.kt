package com.shnapps.couple.feature.games

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.game.StartedGame
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.engine.game.ServerGame
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GameStep
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.SessionEndReason
import com.shnapps.couple.core.model.SessionKind
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.StepSeconds
import com.shnapps.couple.core.model.StepText
import com.shnapps.couple.core.model.TurnOrder
import com.shnapps.couple.core.testing.TestGames
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The games and the player, screen by screen (BUILD_PROMPT.md §11) — and the promises they
 * keep on screen: a hand-off shows nothing of what comes next, the partner's phone never shows
 * the game, and STOP is on every screen of a game under way.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class GameSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val players = GamePlayers("Alex", "Sam")

    private val choicePrompts = TestGames.choicePrompts.mapIndexed { index, prompt ->
        prompt.copy(
            text = listOf("Would you rather…", "Which sounds better tonight?", "Pick one, quickly.")[index % 3],
            options = listOf(
                listOf("A slow dance in the kitchen", "A long bath with the lights low"),
                listOf("Breakfast in bed", "A midnight walk"),
                listOf("Whispered secrets", "A love letter"),
            )[index % 3],
        )
    }

    private fun session(
        state: SessionState,
        isHost: Boolean = true,
        round: Int = 0,
        consented: Boolean = false,
        kind: SessionKind = SessionKind.ONE_DEVICE,
    ) = NightSession(
        id = "s",
        experienceId = "e",
        state = state,
        pausedFrom = null,
        currentChapter = round,
        chapterCount = 3,
        consentRequired = state == SessionState.WAITING_FOR_PARTNER,
        consented = consented,
        partnerConsented = false,
        isHost = isHost,
        endReason = if (state == SessionState.ABANDONED) SessionEndReason.STOPPED else null,
        experienceType = ExperienceType.GAME,
        kind = kind,
    )

    private fun released(game: Game, prompts: List<com.shnapps.couple.core.model.GamePrompt>): ReleasedGame {
        val byId = prompts.associate { it.id to it.copy(game = game.id) }
        val kinds = game.promptKinds.ifEmpty { listOf(null) }
        val rounds = (0 until 3).map { round -> kinds.map { kind -> prompts.filter { it.kind == kind }[round].id } }
        return ReleasedGame.fromServer(
            ServerGame(game.id, rounds, emptyList(), effectiveIntensity = 5),
            game,
        ) { byId[it] }
    }

    private fun playing(
        game: Game,
        prompts: List<com.shnapps.couple.core.model.GamePrompt>,
        moves: (GameRun) -> GameRun,
    ): GameUiState {
        val released = released(game, prompts)
        val run = moves(GameRun.start(released, players))
        val state = GameUiState(
            started = StartedGame("e", released),
            players = players,
            session = session(SessionState.CHAPTER_ACTIVE, round = run.round),
            run = run,
        )
        return state.copy(stage = GameStages.stageOf(state, awaitingSession = false))
    }

    /** The same game, a phone each: this phone plays the first seat, and nothing is handed over. */
    private fun playingApart(moves: (GameRun) -> GameRun): GameUiState {
        val released = released(TestGames.choices, choicePrompts)
        val run = moves(GameRun.start(released, GamePlayers("You", "Your partner"), phone = 0))
        val state = GameUiState(
            started = StartedGame("e", released),
            players = GamePlayers("You", "Your partner"),
            session = session(SessionState.AWAITING_SUBMISSIONS, round = run.round, kind = SessionKind.TWO_DEVICE),
            run = run,
            twoPhones = true,
        )
        return state.copy(stage = GameStages.stageOf(state, awaitingSession = false))
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun handOffShowsNothingOfWhatComesNext() {
        val state = playing(TestGames.choices, choicePrompts) { it }
        capture("game-hand-off") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("Over to Alex.").assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Would you rather…").fetchSemanticsNodes()).isEmpty()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun privateChoice() {
        val state = playing(TestGames.choices, choicePrompts) { it.proceed() }
        capture("game-choice") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("Not this one").assertIsDisplayed()
    }

    @Test
    fun revealMatched() {
        val state = playing(TestGames.choices, choicePrompts) { run ->
            run.proceed().submit(Answer.Picked("0"))!!.proceed().submit(Answer.Picked("0"))!!.proceed()
        }
        capture("game-reveal-matched") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("You matched.").assertIsDisplayed()
    }

    @Test
    fun statementsQuestion() {
        val prompts = (1..3).map { TestGames.prompt("tt_$it", "TWO", "Three things about your first job.") }
        val state = playing(twoTruths, prompts) { it.proceed() }
        capture("game-statements") { GameContent(state, GameActions()) }
    }

    @Test
    fun battleTimer() {
        val prompts = (1..3).map {
            TestGames.prompt("battle_$it", "BATTLE", "Staring contest. First to blink loses.").copy(seconds = 60)
        }
        val state = playing(battle, prompts) { it.submit(Answer.Done)!! }
        capture("game-timer") { GameContent(state, GameActions()) }
    }

    @Test
    fun truthOrDareChallenge() {
        val prompts = TestGames.truths.map { it.copy(text = "What's the first thing you noticed about me?") } +
            TestGames.dares.map { it.copy(text = "Slow dance with me for one whole song, no talking.") }
        // Past the hand-off a game of turns opens each round with (D-130).
        val state = playing(TestGames.truthOrDare, prompts) { it.proceed().submit(Answer.Picked("DARE"))!! }
        capture("game-dare") { GameContent(state, GameActions()) }
    }

    @Test
    fun waitingForTheOtherPhone() {
        val state = playingApart { it.submit(Answer.Picked("0"))!! }
        capture("game-waiting") { GameContent(state, GameActions()) }
        composeRule.onNodeWithContentDescription("Over to them.", substring = true).assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Would you rather…").fetchSemanticsNodes()).isEmpty()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun cutOffFromTheServer() {
        val state = playingApart { it.submit(Answer.Picked("0"))!! }.copy(reconnecting = true)
        capture("game-reconnecting") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("Reconnecting…").assertIsDisplayed()
    }

    @Test
    fun theRoundWaitsForTheOtherPhoneToLook() {
        val played = playingApart { run ->
            run.submit(Answer.Picked("0"))!!
                .withPartnerAnswer(step = 0, seat = 1, answer = Answer.Picked("0"))
                .proceed()
                .submit(Answer.Done)!!
        }
        val state = played.copy(
            session = session(SessionState.REVEAL, kind = SessionKind.TWO_DEVICE).copy(seen = true),
        )
        capture("game-round-waiting") { GameContent(state, GameActions()) }
        composeRule.onNodeWithContentDescription("Still with them.", substring = true).assertIsDisplayed()
        assertThat(composeRule.onAllNodesWithText("Next round").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun aPartnerWhoDroppedOut() {
        val state = playingApart { it.submit(Answer.Picked("0"))!! }.copy(partnerAway = true)
        capture("game-partner-away") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("Your partner dropped out — waiting…").assertIsDisplayed()
    }

    @Test
    fun partnerConsent() {
        val released = released(TestGames.choices, choicePrompts)
        val state = GameUiState(
            started = StartedGame("e", released),
            session = session(SessionState.WAITING_FOR_PARTNER, isHost = false),
        ).let { it.copy(stage = GameStages.stageOf(it, awaitingSession = false)) }
        capture("game-consent-partner") { GameContent(state, GameActions()) }
    }

    @Test
    fun partnerPhoneNeverShowsTheGame() {
        val released = released(TestGames.choices, choicePrompts)
        val state = GameUiState(
            started = StartedGame("e", released),
            session = session(SessionState.CHAPTER_ACTIVE, isHost = false),
        ).let { it.copy(stage = GameStages.stageOf(it, awaitingSession = false)) }
        capture("game-elsewhere") { GameContent(state, GameActions()) }
        assertThat(composeRule.onAllNodesWithText("Would you rather…").fetchSemanticsNodes()).isEmpty()
        // Why, not just that: this screen is where a couple who paired two phones ends up when the
        // game asks one of them per round, and "the game is on the other phone" alone reads like a
        // fault (§12).
        composeRule.onNodeWithText("This one's on the other phone.").assertIsDisplayed()
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun results() {
        val finished = playing(TestGames.choices, choicePrompts) { run ->
            var next = run
            repeat(3) { round ->
                val theirs = if (round == 1) "1" else "0"
                next = next.proceed().submit(Answer.Picked("0"))!!.proceed().submit(Answer.Picked(theirs))!!
                    .proceed().submit(Answer.Done)!!.nextRound()
            }
            next
        }
        val state = finished.copy(session = session(SessionState.COMPLETED), closed = true)
            .let { it.copy(stage = GameStages.stageOf(it, awaitingSession = false)) }
        capture("game-results") { GameContent(state, GameActions()) }
        composeRule.onNodeWithText("You've matched on 2 of 3.").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L

        @Suppress("LongParameterList") // A step, with only what the test sets named.
        fun step(
            primitive: GamePrimitive,
            who: StepAudience,
            isPrivate: Boolean = false,
            text: StepText? = null,
            answer: AnswerKind? = null,
            of: List<Int> = emptyList(),
            outcome: RevealOutcome? = null,
            seconds: StepSeconds? = null,
        ) = GameStep(primitive, who, isPrivate, text, options = null, answer, of, outcome, seconds, kindFrom = null)

        val twoTruths = Game(
            id = "TWO",
            title = "Two truths and a lie",
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 3,
            turns = TurnOrder.ALTERNATE,
            scoring = Scoring.POINTS,
            promptKinds = emptyList(),
            steps = listOf(
                step(
                    GamePrimitive.QUESTION,
                    StepAudience.ACTIVE,
                    isPrivate = true,
                    text = StepText.Prompt,
                    answer = AnswerKind.STATEMENTS,
                ),
            ),
        )

        val battle = Game(
            id = "BATTLE",
            title = "Couple battle",
            tagline = "",
            description = "",
            modes = setOf(Mode.TOGETHER),
            rounds = 3,
            turns = TurnOrder.SHARED,
            scoring = Scoring.POINTS,
            promptKinds = emptyList(),
            steps = listOf(
                step(GamePrimitive.CHALLENGE, StepAudience.TOGETHER, text = StepText.Prompt),
                step(GamePrimitive.TIMER, StepAudience.TOGETHER, seconds = StepSeconds.FromPrompt),
            ),
        )
    }
}
