package com.shnapps.couple.feature.memories

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.testing.FakeMediaRepository
import com.shnapps.couple.core.testing.FakeMemoryRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** "Save to Our Memories" (BUILD_PROMPT.md §14.8): their words, and what they attached. */
@OptIn(ExperimentalCoroutinesApi::class)
class SaveMemoryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val memories = FakeMemoryRepository()
    private val media = FakeMediaRepository()

    private fun item(id: String, sent: Boolean = true) = MediaItem(
        id = id,
        kind = MediaKind.VOICE,
        mine = true,
        note = null,
        burnAfterView = false,
        sentAtMillis = if (sent) SENT_AT else null,
        viewedAtMillis = null,
        expiresAtMillis = null,
    )

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = SaveMemoryViewModel(
        memories = memories,
        media = media,
        savedStateHandle = SavedStateHandle(mapOf("experienceId" to EXPERIENCE)),
    ).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `keeps a night in their words`() = test {
        val vm = screen()

        act { vm.setTitle("The Tuesday one") }
        act { vm.setMoment("the bit where you laughed") }
        act { vm.setNote("we talked until two") }
        act { vm.setRating(5) }
        act(vm::save)

        val kept = memories.kept.single()
        assertThat(kept.experienceId).isEqualTo(EXPERIENCE)
        assertThat(kept.title).isEqualTo("The Tuesday one")
        assertThat(kept.favoriteMoment).isEqualTo("the bit where you laughed")
        assertThat(kept.note).isEqualTo("we talked until two")
        assertThat(kept.rating).isEqualTo(5)
        assertThat(vm.uiState.value.saved).isTrue()
    }

    @Test
    fun `will not keep one without a name for it`() = test {
        val vm = screen()

        act(vm::save)

        assertThat(memories.kept).isEmpty()
        assertThat(vm.uiState.value.canSave).isFalse()
    }

    @Test
    fun `attaches up to four of the things they sent each other, and only what was sent`() = test {
        media.items.value = listOf(item("a"), item("b"), item("c"), item("d"), item("e"), item("f", sent = false))
        val vm = screen()
        act { vm.setTitle("With a voice note") }

        assertThat(vm.uiState.value.media.map { it.id }).containsExactly("a", "b", "c", "d", "e")
        for (id in listOf("a", "b", "c", "d", "e")) {
            act { vm.toggleAttachment(item(id)) }
        }

        assertThat(vm.uiState.value.attached).hasSize(4)
        act(vm::save)
        assertThat(memories.kept.single().mediaIds).hasSize(4)
    }

    @Test
    fun `a night nobody played cannot be kept, and the screen says why`() = test {
        val vm = screen()
        act { vm.setTitle("Too soon") }
        memories.failNext = AppError.Validation("not-played")

        act(vm::save)

        assertThat(vm.uiState.value.error).isEqualTo(SaveMemoryError.NOT_PLAYED)
        assertThat(vm.uiState.value.saved).isFalse()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    private companion object {
        const val EXPERIENCE = "night_1"
        const val SENT_AT = 1_790_000_000_000L
    }
}
