package com.shnapps.couple.feature.preferences

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class PreferenceDiscoverySnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun intro() = capture("discovery-intro", DiscoveryFixtures.intro)

    @Test fun card() = capture("discovery-card", DiscoveryFixtures.answering())

    @Test fun cardAnswered() = capture(
        "discovery-card-answered",
        DiscoveryFixtures.answering(position = 6, answer = PreferenceAnswer(PreferenceValue.CURIOUS)),
    )

    @Test fun done() = capture("discovery-done", DiscoveryFixtures.done)

    @Test fun review() = capture("discovery-review", DiscoveryFixtures.review)

    @Test fun editing() = capture("discovery-editing", DiscoveryFixtures.editing)

    @Test fun error() = capture(
        "discovery-error",
        DiscoveryFixtures.answering().copy(errorMessage = "That answer didn't save. Try it again in a moment."),
    )

    private fun capture(name: String, state: PreferenceDiscoveryUiState) {
        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface {
                    PreferenceDiscoveryContent(
                        state = state,
                        onLevelChange = {}, onStart = {}, onAnswer = {}, onSkip = {}, onBack = {},
                        onFinishLater = {}, onReview = {}, onEdit = {}, onClearAnswer = {},
                        onDismissError = {}, onFinished = {},
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
