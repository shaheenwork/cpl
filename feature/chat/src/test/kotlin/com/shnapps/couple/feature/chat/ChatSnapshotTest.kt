package com.shnapps.couple.feature.chat

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.model.MessageMedia
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The conversation, drawn (CHAT_PROMPT.md §4, CHAT_PRODUCTION_PROMPT.md §4).
 *
 * The promises in the picture: whose message is whose is told by which side it is on and nothing
 * else; a photo is a photo rather than the word "photo"; a once-only one says so **without having
 * been fetched**, because fetching it is what destroys it; a voice note has a shape and a length;
 * and the safe word is on the bar, because this screen covers one that may be playing something
 * (M7).
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
            newFrom = "m4",
            messages = listOf(
                said("m1", mine = false, text = "did you do it", at = at(21, 2)),
                said("m2", mine = true, text = "doing it now", at = at(21, 3)),
                said("m3", mine = true, text = "hang on", at = at(21, 3)),
                photo("m4", mine = false, text = "your turn"),
                voice("m5", mine = false),
                said("m6", mine = true, text = "one second", at = 0L),
            ),
            shown = mapOf(
                "media_m4" to Shown.Photo(swatch()),
                "media_m5" to Shown.Sound(java.io.File("note.m4a")),
            ),
        )

        capture("chat-conversation") { ChatContent(state, ChatActions()) }

        composeRule.onNodeWithText("did you do it").assertIsDisplayed()
        composeRule.onNodeWithText("NEW").assertIsDisplayed()
        // Still on its way, and said so rather than silently missing.
        composeRule.onNodeWithText("Sending…").assertIsDisplayed()
        // It covers a screen that may be playing something.
        composeRule.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun aPhotoOnItsWay() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(photo("m1", mine = false, text = null)),
            shown = mapOf("media_m1" to Shown.Loading),
        )

        capture("chat-photo-loading") { ChatContent(state, ChatActions()) }
    }

    @Test
    fun aPhotoMeantToBeSeenOnce() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(photo("m1", mine = false, text = null, burns = true)),
        )

        capture("chat-once-only") { ChatContent(state, ChatActions()) }

        // Nothing was fetched to draw this: the record said enough, and fetching is what deletes it.
        composeRule.onNodeWithText("Once only. Tap to look.").assertIsDisplayed()
    }

    @Test
    fun aPhotoThatHasGone() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(photo("m1", mine = false, text = null, burns = true, opened = true)),
            shown = mapOf("media_m1" to Shown.Gone),
        )

        capture("chat-once-only-gone") { ChatContent(state, ChatActions()) }

        composeRule.onNodeWithText("Seen. It's gone now.").assertIsDisplayed()
    }

    @Test
    fun aVoiceNotePlaying() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(voice("m1", mine = false)),
            shown = mapOf("media_m1" to Shown.Sound(java.io.File("note.m4a"))),
            playing = Playing(id = "media_m1", fraction = 0.4f, seconds = 3),
        )

        capture("chat-voice-playing") { ChatContent(state, ChatActions()) }

        composeRule.onNodeWithText("0:03").assertIsDisplayed()
    }

    @Test
    fun somethingOnItsWayUp() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(said("m1", mine = false, text = "send me one", at = at(21, 2))),
            outgoing = listOf(
                Outgoing(id = "going_1", kind = MediaKind.PHOTO, text = "here", seconds = null, levels = emptyList()),
                Outgoing(id = "going_2", kind = MediaKind.VOICE, text = null, seconds = 7, levels = WAVE),
            ),
            shown = mapOf("going_1" to Shown.Photo(swatch())),
        )

        capture("chat-sending") { ChatContent(state, ChatActions()) }

        // The upload is the slow part. Before this, a tap on send looked like a tap on nothing.
        composeRule.onAllNodesWithText("Sending…").onFirst().assertIsDisplayed()
    }

    @Test
    fun aPhotoIsTheSameSizeWhateverShapeItIs() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(photo("m1", mine = false, text = null), photo("m2", mine = true, text = null)),
            shown = mapOf(
                // A panorama and a portrait: both bubbles come out the same.
                "media_m1" to Shown.Photo(swatch(WIDE_PHOTO, SHORT_PHOTO)),
                "media_m2" to Shown.Photo(swatch(SHORT_PHOTO, WIDE_PHOTO)),
            ),
        )

        capture("chat-photo-sizes") { ChatContent(state, ChatActions()) }
    }

    @Test
    fun recording() {
        val state = ChatUiState(
            open = true,
            paired = true,
            recording = Recording(seconds = 7, level = 0.6f),
        )

        capture("chat-recording") { ChatContent(state, ChatActions()) }

        composeRule.onNodeWithText("0:07").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send the voice note").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Throw the recording away").assertIsDisplayed()
    }

    @Test
    fun theMicrophoneWasRefused() {
        val state = ChatUiState(open = true, paired = true, error = ChatError.MIC_REFUSED)

        capture("chat-mic-refused") { ChatContent(state, ChatActions()) }
    }

    @Test
    fun nothingSaidYet() {
        capture("chat-empty") { ChatContent(ChatUiState(open = true, paired = true), ChatActions()) }

        composeRule.onNodeWithText("Say something to them.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Attach something").assertIsDisplayed()
    }

    @Test
    fun atTwiceTheTypeSize() {
        val state = ChatUiState(
            open = true,
            paired = true,
            messages = listOf(
                said("m1", mine = false, text = "did you do it", at = at(21, 2)),
                voice("m2", mine = true),
            ),
            shown = mapOf("media_m2" to Shown.Sound(java.io.File("note.m4a"))),
        )

        composeRule.setContent {
            AfterhoursTheme {
                AfterhoursSurface {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.ui.platform.LocalDensity provides
                            androidx.compose.ui.unit.Density(
                                density = androidx.compose.ui.platform.LocalDensity.current.density,
                                fontScale = BIG_TYPE,
                            ),
                    ) {
                        ChatContent(state, ChatActions())
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/chat-big-type.png")
    }

    /** A plain bitmap: this is about layout, not about what the photo is of. */
    private fun swatch(
        wide: Int = SWATCH_WIDE,
        tall: Int = SWATCH_TALL,
    ): androidx.compose.ui.graphics.ImageBitmap =
        Bitmap.createBitmap(wide, tall, Bitmap.Config.ARGB_8888).asImageBitmap()

    private fun at(hour: Int, minute: Int): Long = LocalDateTime
        .of(2026, 10, 8, hour, minute)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

    private fun said(id: String, mine: Boolean, text: String?, at: Long) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.TEXT,
        text = text,
        mediaId = null,
        atMillis = at,
    )

    private fun photo(
        id: String,
        mine: Boolean,
        text: String?,
        burns: Boolean = false,
        opened: Boolean = false,
    ) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.PHOTO,
        text = text,
        mediaId = "media_$id",
        atMillis = at(21, 4),
        media = MessageMedia(
            id = "media_$id",
            kind = MediaKind.PHOTO,
            burnAfterView = burns,
            opened = opened,
            seconds = null,
            levels = emptyList(),
        ),
    )

    private fun voice(id: String, mine: Boolean) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.VOICE,
        text = null,
        mediaId = "media_$id",
        atMillis = at(21, 5),
        media = MessageMedia(
            id = "media_$id",
            kind = MediaKind.VOICE,
            burnAfterView = false,
            opened = false,
            seconds = 7,
            levels = WAVE,
        ),
    )

    private companion object {
        const val SETTLE_MS = 1_000L
        const val SWATCH_WIDE = 480
        const val SWATCH_TALL = 320
        const val BIG_TYPE = 2f
        const val WIDE_PHOTO = 1_200
        const val SHORT_PHOTO = 400

        /** A plausible waveform rather than a flat line, so the golden shows the real thing. */
        val WAVE = List(40) { index -> 20 + (index * 7) % 70 }
    }
}
