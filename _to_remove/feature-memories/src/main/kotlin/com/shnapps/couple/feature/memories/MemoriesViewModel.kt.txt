package com.shnapps.couple.feature.memories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.memories.MemoryRepository
import com.shnapps.couple.core.model.Memory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why the library could not do something, in words the screen can say. */
enum class MemoriesError { OFFLINE, OTHER }

/** What the library is showing (§14.8). */
enum class MemoryFilter(val label: String) {
    ALL("Our evenings"),
    FAVORITES("We love"),
}

data class MemoriesUiState(
    val memories: List<Memory> = emptyList(),
    val filter: MemoryFilter = MemoryFilter.ALL,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: MemoriesError? = null,
) {
    /** What the list shows: everything they kept, or only what they marked. */
    val shown: List<Memory>
        get() = if (filter == MemoryFilter.FAVORITES) memories.filter { it.favorite } else memories

    val empty: Boolean get() = !loading && shown.isEmpty()
}

/**
 * Our nights (BUILD_PROMPT.md §14.8): what they kept, newest first, a page at a time.
 *
 * Nothing in here is content — a memory holds their own words and the shape of the evening, never
 * what was in it (D-090) — so this screen can list freely what no other screen may.
 */
@HiltViewModel
class MemoriesViewModel @Inject constructor(
    private val memories: MemoryRepository,
) : ViewModel() {

    private val state = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = state.asStateFlow()

    init {
        refresh()
    }

    /** The first page again: after keeping one, or after coming back to the screen. */
    fun refresh() {
        state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val page = memories.page()) {
                is Outcome.Success -> state.update {
                    it.copy(memories = page.value.memories, hasMore = page.value.hasMore, loading = false)
                }
                is Outcome.Failure -> state.update {
                    it.copy(loading = false, error = page.error.toMemoriesError())
                }
            }
        }
    }

    /** The next twenty. The library only grows, so it is never read whole (§18). */
    fun loadMore() {
        val current = state.value
        if (current.loadingMore || !current.hasMore) return
        val last = current.memories.lastOrNull()?.dateMillis ?: return
        state.update { it.copy(loadingMore = true, error = null) }
        viewModelScope.launch {
            when (val page = memories.page(beforeMillis = last)) {
                is Outcome.Success -> state.update {
                    it.copy(
                        memories = it.memories + page.value.memories,
                        hasMore = page.value.hasMore,
                        loadingMore = false,
                    )
                }
                is Outcome.Failure -> state.update {
                    it.copy(loadingMore = false, error = page.error.toMemoriesError())
                }
            }
        }
    }

    fun setFilter(filter: MemoryFilter) = state.update { it.copy(filter = filter) }

    /** Marking one. The page shows it at once; the server is told, and disagrees by refusing. */
    fun toggleFavorite(memory: Memory) {
        val wanted = !memory.favorite
        state.update { current -> current.copy(memories = current.memories.mark(memory.id, wanted)) }
        viewModelScope.launch {
            val result = memories.setFavorite(memory.id, wanted)
            if (result is Outcome.Failure) {
                state.update { current ->
                    current.copy(
                        memories = current.memories.mark(memory.id, memory.favorite),
                        error = result.error.toMemoriesError(),
                    )
                }
            }
        }
    }

    fun forget(memory: Memory) {
        state.update { current -> current.copy(memories = current.memories.filterNot { it.id == memory.id }) }
        viewModelScope.launch {
            if (memories.forget(memory.id) is Outcome.Failure) refresh()
        }
    }

    fun dismissError() = state.update { it.copy(error = null) }
}

private fun List<Memory>.mark(id: String, favorite: Boolean): List<Memory> =
    map { if (it.id == id) it.copy(favorite = favorite) else it }

private fun AppError.toMemoriesError(): MemoriesError =
    if (this is AppError.Network) MemoriesError.OFFLINE else MemoriesError.OTHER
