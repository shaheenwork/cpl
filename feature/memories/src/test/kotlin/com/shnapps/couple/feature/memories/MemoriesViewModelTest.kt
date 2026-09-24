package com.shnapps.couple.feature.memories

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.model.Memory
import com.shnapps.couple.core.model.MemoryKind
import com.shnapps.couple.core.testing.FakeMemoryRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/** Our nights (BUILD_PROMPT.md §14.8): a page at a time, favourites, and forgetting one. */
@OptIn(ExperimentalCoroutinesApi::class)
class MemoriesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun memory(index: Int, favorite: Boolean = false) = Memory(
        id = "m$index",
        experienceId = "n$index",
        title = "Night $index",
        note = null,
        favoriteMoment = null,
        rating = null,
        mood = emptyList(),
        durationMin = 60,
        kind = MemoryKind.NIGHT,
        chapterCount = 4,
        playedThrough = 4,
        mediaIds = emptyList(),
        favorite = favorite,
        dateMillis = NOW - index * DAY_MS,
    )

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen(memories: FakeMemoryRepository) =
        MemoriesViewModel(memories).also { advanceUntilIdle() }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `shows what they kept, newest first`() = test {
        val memories = FakeMemoryRepository(listOf(memory(2), memory(0), memory(1)))

        val vm = screen(memories)

        assertThat(vm.uiState.value.shown.map { it.title })
            .containsExactly("Night 0", "Night 1", "Night 2").inOrder()
        assertThat(vm.uiState.value.loading).isFalse()
        assertThat(vm.uiState.value.hasMore).isFalse()
    }

    @Test
    fun `asks for the next twenty, and only when there are more`() = test {
        val memories = FakeMemoryRepository((0..24).map { memory(it) })
        val vm = screen(memories)

        assertThat(vm.uiState.value.shown).hasSize(20)
        assertThat(vm.uiState.value.hasMore).isTrue()

        act(vm::loadMore)

        assertThat(vm.uiState.value.shown).hasSize(25)
        assertThat(vm.uiState.value.hasMore).isFalse()
        // Nothing more to ask for: a second tap costs no read.
        val asked = memories.pagesAsked
        act(vm::loadMore)
        assertThat(memories.pagesAsked).isEqualTo(asked)
    }

    @Test
    fun `marks one, and shows only those when asked`() = test {
        val memories = FakeMemoryRepository(listOf(memory(0), memory(1, favorite = true)))
        val vm = screen(memories)

        act { vm.setFilter(MemoryFilter.FAVORITES) }
        assertThat(vm.uiState.value.shown.map { it.title }).containsExactly("Night 1")

        act { vm.toggleFavorite(memories.kept.first { it.id == "m0" }) }

        assertThat(memories.kept.first { it.id == "m0" }.favorite).isTrue()
        assertThat(vm.uiState.value.shown.map { it.title }).containsExactly("Night 0", "Night 1").inOrder()
    }

    @Test
    fun `a mark the server refuses goes back where it was`() = test {
        val memories = FakeMemoryRepository(listOf(memory(0)))
        val vm = screen(memories)
        memories.failNext = AppError.Network()

        act { vm.toggleFavorite(vm.uiState.value.shown.first()) }

        assertThat(vm.uiState.value.shown.first().favorite).isFalse()
        assertThat(vm.uiState.value.error).isEqualTo(MemoriesError.OFFLINE)
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `forgetting one takes it off the screen and out of the library`() = test {
        val memories = FakeMemoryRepository(listOf(memory(0), memory(1)))
        val vm = screen(memories)

        act { vm.forget(vm.uiState.value.shown.first()) }

        assertThat(vm.uiState.value.shown.map { it.title }).containsExactly("Night 1")
        assertThat(memories.kept.map { it.id }).containsExactly("m1")
    }

    @Test
    fun `an empty library says so, rather than showing nothing at all`() = test {
        val vm = screen(FakeMemoryRepository())

        assertThat(vm.uiState.value.empty).isTrue()
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
