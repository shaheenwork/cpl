package com.shnapps.couple.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.gallery.AnticipationSpecimen
import com.shnapps.couple.core.designsystem.gallery.ButtonsSpecimen
import com.shnapps.couple.core.designsystem.gallery.CardsSpecimen
import com.shnapps.couple.core.designsystem.gallery.EmptySpecimen
import com.shnapps.couple.core.designsystem.gallery.NightControlsSpecimen
import com.shnapps.couple.core.designsystem.gallery.PrivateAnswersSpecimen
import com.shnapps.couple.core.designsystem.gallery.SafetySpecimen
import com.shnapps.couple.core.designsystem.gallery.TypeSpecimen
import com.shnapps.couple.core.designsystem.gallery.VoiceSpecimen
import com.shnapps.couple.core.designsystem.gallery.WaitingSpecimen
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot tests for the component inventory (BUILD_PROMPT.md §15.3, and the Phase 2
 * exit criterion "screenshot tests pass").
 *
 * These run on the JVM through Robolectric, so the design system is verifiable with no
 * device at all — which is the difference between Phase 2 being provable today and being
 * blocked behind the hypervisor install in HUMAN_SETUP.md section 1.3.
 *
 * Record goldens:  `./gradlew :core:designsystem:recordRoborazziDebug`
 * Verify goldens:  `./gradlew :core:designsystem:verifyRoborazziDebug`
 *
 * Sections are captured separately rather than as one tall image, so a diff points at the
 * component that changed instead of at "the gallery".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GallerySnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test fun typography() = capture("typography") { TypeSpecimen() }

    @Test fun cards() = capture("cards") { CardsSpecimen() }

    @Test fun anticipation() = capture("anticipation") { AnticipationSpecimen() }

    @Test fun buttons() = capture("buttons") { ButtonsSpecimen() }

    @Test fun nightControls() = capture("night-controls") { NightControlsSpecimen() }

    @Test fun privateAnswers() = capture("private-answers") { PrivateAnswersSpecimen() }

    @Test fun waitingAndTime() = capture("waiting-and-time") { WaitingSpecimen() }

    @Test fun voice() = capture("voice") { VoiceSpecimen() }

    @Test fun safety() = capture("safety") { SafetySpecimen() }

    @Test fun empty() = capture("empty") { EmptySpecimen() }

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            AfterhoursTheme {
                Column(
                    modifier = Modifier
                        .width(PHONE_WIDTH.dp)
                        .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                        .fillMaxWidth(),
                ) {
                    content()
                }
            }
        }
        composeRule.onRoot().captureRoboImage("$GOLDEN_DIR/$name.png")
    }

    private companion object {
        const val PHONE_WIDTH = 392
        const val GOLDEN_DIR = "src/test/screenshots"
    }
}
