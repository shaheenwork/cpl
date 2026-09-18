package com.shnapps.couple.feature.preferences

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The deck's controls, driven the way a finger drives them.
 *
 * The swipe is an accelerator over the buttons: left skips, right goes back, and neither
 * ever records an answer — only a deliberate tap does.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class DiscoveryDeckTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val answers = mutableListOf<PreferenceAnswer>()
    private var skips = 0
    private var backs = 0

    private fun show(state: PreferenceDiscoveryUiState) {
        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface {
                    PreferenceDiscoveryContent(
                        state = state,
                        onLevelChange = {}, onStart = {}, onAnswer = { answers += it }, onSkip = { skips++ },
                        onBack = { backs++ }, onFinishLater = {}, onReview = {}, onEdit = {},
                        onClearAnswer = {}, onDismissError = {}, onFinished = {},
                    )
                }
            }
        }
    }

    @Test
    fun `swiping the card left skips it without answering`() {
        show(DiscoveryFixtures.answering(position = 3))

        composeRule.onNodeWithTag(DISCOVERY_CARD_TAG).performTouchInput { swipeLeft() }

        composeRule.runOnIdle {
            assertThat(skips).isEqualTo(1)
            assertThat(answers).isEmpty()
        }
    }

    @Test
    fun `swiping right goes back a card`() {
        show(DiscoveryFixtures.answering(position = 3))

        composeRule.onNodeWithTag(DISCOVERY_CARD_TAG).performTouchInput { swipeRight() }

        composeRule.runOnIdle {
            assertThat(backs).isEqualTo(1)
            assertThat(answers).isEmpty()
        }
    }

    @Test
    fun `swiping right on the first card goes nowhere`() {
        show(DiscoveryFixtures.answering(position = 1))

        composeRule.onNodeWithTag(DISCOVERY_CARD_TAG).performTouchInput { swipeRight() }

        composeRule.runOnIdle { assertThat(backs).isEqualTo(0) }
    }

    @Test
    fun `a short drag springs back instead of skipping`() {
        show(DiscoveryFixtures.answering(position = 3))

        composeRule.onNodeWithTag(DISCOVERY_CARD_TAG).performTouchInput {
            swipeLeft(startX = centerX, endX = centerX - width * 0.1f)
        }

        composeRule.runOnIdle { assertThat(skips).isEqualTo(0) }
    }

    @Test
    fun `answering takes a tap, and secretly curious is its own answer`() {
        show(DiscoveryFixtures.answering())

        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithText("Secretly curious").performClick()

        composeRule.runOnIdle {
            assertThat(answers).containsExactly(
                PreferenceAnswer(PreferenceValue.YES),
                PreferenceAnswer.SECRETLY_CURIOUS,
            ).inOrder()
        }
    }
}
