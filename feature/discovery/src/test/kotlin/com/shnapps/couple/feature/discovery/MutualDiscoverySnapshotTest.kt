package com.shnapps.couple.feature.discovery

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MatchLevel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class MutualDiscoverySnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val massage = MatchCard(
        itemId = "sensory_massage",
        prompt = "Massage",
        description = "Slow hands and nowhere else to be.",
        categoryTitle = "Senses",
        level = MatchLevel.BOTH_YES,
    )
    private val strangers = MatchCard(
        itemId = "roleplay_strangers",
        prompt = "Meeting as strangers",
        description = "Two people who've never met. One of them makes the first move.",
        categoryTitle = "Roleplay",
        level = MatchLevel.BOTH_SECRET,
    )
    private val voice = massage.copy(
        itemId = "comm_voice",
        prompt = "Voice notes",
        categoryTitle = "Talk",
        level = MatchLevel.MIXED_POSITIVE,
    )

    @Test fun concealed() = capture(
        "mutual-concealed",
        MutualDiscoveryUiState(loading = false, current = massage, moreAfter = 2),
    )

    @Test fun revealed() = capture(
        "mutual-revealed",
        MutualDiscoveryUiState(loading = false, current = massage, step = RevealStep.Revealed, moreAfter = 2),
    )

    @Test fun secretRevealed() = capture(
        "mutual-secret-revealed",
        MutualDiscoveryUiState(loading = false, current = strangers, step = RevealStep.Revealed),
    )

    @Test fun shared() = capture(
        "mutual-shared",
        MutualDiscoveryUiState(loading = false, shared = listOf(strangers, voice, massage)),
    )

    @Test fun empty() = capture("mutual-empty", MutualDiscoveryUiState(loading = false))

    @Test
    fun `a concealed match keeps its content out of the tree entirely`() {
        show(MutualDiscoveryUiState(loading = false, current = massage))

        // Hiding must mean absent (§5.7): not blurred text a screen reader could still read.
        composeRule.onNodeWithText("Massage").assertDoesNotExist()
        composeRule.onNodeWithText("You both said yes.").assertDoesNotExist()
    }

    private fun capture(name: String, state: MutualDiscoveryUiState) {
        show(state)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun show(state: MutualDiscoveryUiState) {
        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface {
                    MutualDiscoveryContent(
                        state = state,
                        onReveal = {}, onRevealed = {}, onNext = {}, onBuildNight = null, onDone = {},
                    )
                }
            }
        }
    }
}
