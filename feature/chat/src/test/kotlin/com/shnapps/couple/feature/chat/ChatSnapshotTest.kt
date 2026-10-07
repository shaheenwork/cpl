package com.shnapps.couple.feature.chat

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
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The conversation, drawn (CHAT_PROMPT.md §4).
 *
 * The promises in the picture: whose message is whose is told by which side it is on and nothing
 * else, a message still on its way says so, and the safe word is on the bar — because this screen
 * covers one that may be playing something (M7).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class ChatSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun aConversation() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(
                said("m1", mine = false, text = "did you do it"),
                said("m2", mine = true, text = "doing it now"),
                said("m3", mine = true, kind = MessageKind.PHOTO, text = "proof"),
                said("m4", mine = false, kind = MessageKind.VOICE, text = null),
                said("m5", mine = true, text = "one second", at = 0L),
            ),
        )

        capture("chat-conversation") { ChatContent(state, ChatActions()) }

        composeRule.onNodeWithText("did you do it").assertIsDisplayed()
        composeRule.onNodeWithText("A voice note").assertIsDisplayed()
        // Still on its way, and said so rather than silently missing.
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
        // It covers a screen that may be playing something.
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun nothingSaidYet() {
        capture("chat-empty") { ChatContent(ChatUiState(open = true, paired = true), ChatActions()) }

        composeRule.onNodeWithText("Nothing here yet.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send a photo").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Record a voice note").assertIsDisplayed()
    }

    private fun said(
        id: String,
        mine: Boolean,
        kind: MessageKind = MessageKind.TEXT,
        text: String? = null,
        at: Long = 1_800_000_000_000L,
    ) = Message(id = id, mine = mine, kind = kind, text = text, mediaId = null, atMillis = at)

    private companion object {
        const val SETTLE_MS = 1_000L
    }
}
