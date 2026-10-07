package com.shnapps.couple.feature.send

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.Surprise
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.SurpriseType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Sending something, screen by screen (BUILD_PROMPT.md §13) — and the promises on it: what is
 * recorded is not sent until it is sent, an item that burns says so before it is opened, and an
 * opened one says plainly that it has gone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class SendSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun item(
        kind: MediaKind,
        mine: Boolean,
        note: String? = null,
        burn: Boolean = false,
        viewed: Boolean = false,
    ) = MediaItem(
        id = "m_${kind.name}_$mine",
        kind = kind,
        mine = mine,
        note = note,
        burnAfterView = burn,
        sentAtMillis = SENT_AT,
        viewedAtMillis = SENT_AT.takeIf { viewed },
        expiresAtMillis = null,
    )

    @Test
    fun nothingMadeYet() {
        capture("send-empty") { SendContent(SendUiState(), SendActions()) }
        composeRule.onNodeWithText("Record a voice note").assertIsDisplayed()
        composeRule.onNodeWithText("Send a photo").assertIsDisplayed()
        // Nothing to send until something is made.
        assertThat(composeRule.onAllNodesWithText("Send it").fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun recordedAndWaitingToBeSent() {
        val state = SendUiState(
            ready = File("voice.m4a"),
            readyKind = MediaKind.VOICE,
            note = "listen to this",
            burnAfterView = true,
        )
        capture("send-ready") { SendContent(state, SendActions()) }
        composeRule.onNodeWithText("Send it").assertIsDisplayed()
        composeRule.onNodeWithText("Gone after they open it").assertIsDisplayed()
        composeRule.onNodeWithText("Deleted for good — not hidden.").assertIsDisplayed()
    }

    @Test
    fun whatIsBetweenThem() {
        val state = SendUiState(
            inbox = listOf(
                item(MediaKind.VOICE, mine = false, note = "just this once", burn = true),
                item(MediaKind.PHOTO, mine = true, viewed = true),
            ),
        )
        capture("send-inbox") { SendContent(state, SendActions()) }
        composeRule.onNodeWithText("A voice note from them. Not opened yet.").assertIsDisplayed()
        composeRule.onNodeWithText("A photo you sent. They've opened it.").assertIsDisplayed()
        composeRule.onNodeWithText("ONCE ONLY").assertIsDisplayed()
    }

    @Test
    fun openedAndGone() {
        val state = SendUiState(
            open = OpenItem(
                item = item(MediaKind.VOICE, mine = false, note = "just this once", burn = true),
                file = File("voice.m4a"),
                burned = true,
            ),
        )
        capture("send-open-burned") { SendContent(state, SendActions()) }
        composeRule.onNodeWithText("This one is gone now — for both of you.").assertIsDisplayed()
        composeRule.onNodeWithText("Play it").assertIsDisplayed()
    }

    @Test
    fun leavingSomethingForLater() {
        capture("later-leave") {
            LaterContent(
                LaterUiState(title = "For Friday", text = "something to think about", leaveWhen = LeaveWhen.TOMORROW),
                LaterActions(),
            )
        }
        composeRule.onNodeWithText("Leave it").assertIsDisplayed()
        composeRule.onNodeWithText("Tomorrow").assertIsDisplayed()
    }

    @Test
    fun somethingHasArrived() {
        val state = LaterUiState(
            surprises = listOf(
                Surprise(
                    id = "s1",
                    type = SurpriseType.SCHEDULED,
                    title = "For Friday",
                    mine = false,
                    state = SurpriseState.DELIVERED,
                    scheduledForMillis = SENT_AT,
                    trigger = null,
                    hasMedia = false,
                ),
                Surprise(
                    id = "s2",
                    type = SurpriseType.OPEN_WHEN,
                    title = "When you can't sleep",
                    mine = false,
                    state = SurpriseState.SCHEDULED,
                    scheduledForMillis = null,
                    trigger = OpenWhenTrigger.CANT_SLEEP,
                    hasMedia = false,
                ),
            ),
        )
        capture("later-arrived") { LaterContent(state, LaterActions()) }
        composeRule.onNodeWithText("IT'S HERE").assertIsDisplayed()
        composeRule.onNodeWithText("OPEN WHEN…").assertIsDisplayed()
        // What is still kept for a moment says only that, never what is in it (§5.7).
        assertThat(composeRule.onAllNodesWithText("read this").fetchSemanticsNodes()).isEmpty()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val SENT_AT = 1_790_000_000_000L
    }
}
