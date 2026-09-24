package com.shnapps.couple.feature.memories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.data.memories.MemoryRepository
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Why a night could not be kept, in words the screen can say. */
enum class SaveMemoryError { OFFLINE, NOT_PLAYED, OTHER }

data class SaveMemoryUiState(
    val title: String = "",
    val note: String = "",
    val favoriteMoment: String = "",
    val rating: Int? = null,
    /** What they have sent each other, to attach a voice note or a photo to the night. */
    val media: List<MediaItem> = emptyList(),
    val attached: Set<String> = emptySet(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: SaveMemoryError? = null,
) {
    val canSave: Boolean get() = title.isNotBlank() && !saving
    val canAttachMore: Boolean get() = attached.size < MAX_MEDIA

    companion object {
        const val MAX_TITLE = 60
        const val MAX_NOTE = 2000
        const val MAX_MOMENT = 280
        const val MAX_MEDIA = 4
        const val MAX_RATING = 5
    }
}

/**
 * "Save to Our Memories" (BUILD_PROMPT.md §14.8), at the close of a night.
 *
 * The phone sends their own words and the ids of anything they attached. Everything else — the
 * date, the length, the moods, that the night was played at all — the server reads from the night
 * itself, so a memory says what happened rather than what a phone claimed (D-090).
 */
@HiltViewModel
class SaveMemoryViewModel @Inject constructor(
    private val memories: MemoryRepository,
    media: MediaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // By property name: toRoute() goes through Android's Bundle, which unit tests stub (D-067).
    private val experienceId: String =
        savedStateHandle.get<String>(Route.SaveMemory::experienceId.name).orEmpty()

    private val local = MutableStateFlow(SaveMemoryUiState())

    val uiState: StateFlow<SaveMemoryUiState> = combine(local, media.inbox) { state, inbox ->
        state.copy(media = inbox.filter { it.sentAtMillis != null })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SaveMemoryUiState())

    fun setTitle(title: String) = local.update { it.copy(title = title.take(SaveMemoryUiState.MAX_TITLE)) }

    fun setNote(note: String) = local.update { it.copy(note = note.take(SaveMemoryUiState.MAX_NOTE)) }

    fun setMoment(moment: String) =
        local.update { it.copy(favoriteMoment = moment.take(SaveMemoryUiState.MAX_MOMENT)) }

    fun setRating(rating: Int) = local.update { it.copy(rating = if (it.rating == rating) null else rating) }

    fun toggleAttachment(item: MediaItem) = local.update { state ->
        when {
            item.id in state.attached -> state.copy(attached = state.attached - item.id)
            state.attached.size < SaveMemoryUiState.MAX_MEDIA -> state.copy(attached = state.attached + item.id)
            else -> state
        }
    }

    fun save() {
        val state = uiState.value
        if (!state.canSave) return
        local.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = memories.save(
                experienceId = experienceId,
                title = state.title.trim(),
                note = state.note.trim().ifBlank { null },
                favoriteMoment = state.favoriteMoment.trim().ifBlank { null },
                rating = state.rating,
                mediaIds = state.attached.toList(),
            )
            local.update { current ->
                when (result) {
                    is Outcome.Success -> current.copy(saving = false, saved = true)
                    is Outcome.Failure -> current.copy(saving = false, error = result.error.toSaveError())
                }
            }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toSaveError(): SaveMemoryError = when {
    this is AppError.Network -> SaveMemoryError.OFFLINE
    this is AppError.Validation && code == "not-played" -> SaveMemoryError.NOT_PLAYED
    else -> SaveMemoryError.OTHER
}
