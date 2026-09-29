package com.shnapps.couple.feature.memories

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
import com.shnapps.couple.core.designsystem.theme.AfterhoursSurface
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind
import com.shnapps.couple.core.model.Mood
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The library, screen by screen (BUILD_PROMPT.md §14.8) — and the promise on it: a memory shows
 * the shape of an evening and their own words about it, never what was in it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w392dp-h840dp-xhdpi")
class MemoriesSnapshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        composeRule.setContent { AfterhoursTheme { AfterhoursSurface { content() } } }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun memory(
        id: String,
        title: String,
        favorite: Boolean = false,
        media: List<String> = emptyList(),
    ) = Memory(
        id = id,
        experienceId = id,
        title = title,
        note = "we talked until two",
        favoriteMoment = "the bit where you laughed",
        rating = 5,
        mood = listOf(Mood.NAUGHTY, Mood.TALK),
        durationMin = 60,
        kind = MemoryKind.NIGHT,
        chapterCount = 4,
        playedThrough = 4,
        mediaIds = media,
        favorite = favorite,
        dateMillis = DATE,
    )

    @Test
    fun nothingKeptYet() {
        capture("memories-empty") { MemoriesContent(MemoriesUiState(loading = false), MemoriesActions()) }
        composeRule.onNodeWithText("Nothing kept yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Our evenings").assertIsDisplayed()
    }

    @Test
    fun theOnesTheyKept() {
        val state = MemoriesUiState(
            memories = listOf(
                memory("m1", "The Tuesday one", favorite = true, media = listOf("a")),
                memory("m2", "The long one"),
            ),
            loading = false,
            hasMore = true,
        )
        capture("memories-list") { MemoriesContent(state, MemoriesActions()) }
        composeRule.onNodeWithText("The Tuesday one").assertIsDisplayed()
        // The shape of the evening is the card's description now, not a line of capitals across
        // it: a grid only fits twelve evenings if each one stops being a paragraph (§6.6).
        composeRule.onNodeWithContentDescription(
            "The Tuesday one. NAUGHTY · TALK · 60 MIN · ALL 4 · 1 ATTACHED",
        ).assertExists()
        composeRule.onNodeWithText("Show more").assertIsDisplayed()
        // The shape of the evening, and never a line of what was in it.
        assertThat(composeRule.onAllNodesWithText("item_", substring = true).fetchSemanticsNodes()).isEmpty()
    }

    @Test
    fun keepingTheNightJustPlayed() {
        val state = SaveMemoryUiState(
            title = "The Tuesday one",
            favoriteMoment = "the bit where you laughed",
            rating = 4,
            media = listOf(
                MediaItem(
                    id = "a",
                    kind = MediaKind.VOICE,
                    mine = true,
                    note = null,
                    burnAfterView = false,
                    sentAtMillis = DATE,
                    viewedAtMillis = null,
                    expiresAtMillis = null,
                ),
            ),
            attached = setOf("a"),
        )
        capture("memories-save") { SaveMemoryContent(state, SaveMemoryActions()) }
        composeRule.onNodeWithText("Save to Our Memories").assertIsDisplayed()
        composeRule.onNodeWithText("Voice note you sent").assertIsDisplayed()
        composeRule.onNodeWithText("Keep this one?").assertIsDisplayed()
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val DATE = 1_790_000_000_000L
    }
}
