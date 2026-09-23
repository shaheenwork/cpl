package com.shnapps.couple.feature.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Why something could not be sent or opened, in words the screen can say. */
enum class SendError { OFFLINE, TOO_BIG, GONE, OTHER }

data class SendUiState(
    /** What the two of them have sent each other, newest first. */
    val inbox: List<MediaItem> = emptyList(),
    /** Recorded or picked on this phone, waiting to be sent. */
    val ready: File? = null,
    val readyKind: MediaKind = MediaKind.VOICE,
    val note: String = "",
    val burnAfterView: Boolean = false,
    val recording: Boolean = false,
    val sending: Boolean = false,
    /** Set once something has been sent: the screen says so and clears. */
    val sent: Boolean = false,
    /** What is open right now: the file on this phone, and whether opening it burned it. */
    val open: OpenItem? = null,
    val error: SendError? = null,
) {
    val canSend: Boolean get() = ready != null && !sending && !recording

    /** Something the partner sent and nobody has opened (§13). */
    val waiting: MediaItem? get() = inbox.firstOrNull { it.unopened }
}

/** An item open on this phone: the bytes, and what happened to the original. */
data class OpenItem(val item: MediaItem, val file: File, val burned: Boolean)

/**
 * Sending a voice note or a photo, and opening one (BUILD_PROMPT.md §5.8, §13).
 *
 * Nothing here holds a URL. What is recorded or picked stays in this app's own cache until it is
 * sent, what is opened is fetched with the user's own credentials, and a burn-after-view item is
 * deleted by the server as it is opened — so the copy on this phone is the only one left, and it
 * goes when the cache does.
 */
@HiltViewModel
class SendViewModel @Inject constructor(
    private val media: MediaRepository,
    private val recorder: VoiceRecorder,
) : ViewModel() {

    private val local = MutableStateFlow(SendUiState())

    val uiState: StateFlow<SendUiState> = combine(local, media.inbox) { state, inbox ->
        state.copy(inbox = inbox)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SendUiState())

    fun startRecording() {
        if (local.value.recording) return
        when (val started = recorder.start()) {
            is Outcome.Success -> local.update { it.copy(recording = true, ready = null, error = null) }
            is Outcome.Failure -> local.update { it.copy(error = started.error.toSendError()) }
        }
    }

    fun stopRecording() {
        if (!local.value.recording) return
        when (val file = recorder.stop()) {
            is Outcome.Success -> local.update {
                it.copy(recording = false, ready = file.value, readyKind = MediaKind.VOICE)
            }
            is Outcome.Failure -> local.update { it.copy(recording = false, error = file.error.toSendError()) }
        }
    }

    /** A photo the system picker gave back, already copied into this app's cache. */
    fun photoPicked(file: File) = local.update { it.copy(ready = file, readyKind = MediaKind.PHOTO, error = null) }

    fun setNote(note: String) = local.update { it.copy(note = note.take(MAX_NOTE)) }

    fun setBurnAfterView(burn: Boolean) = local.update { it.copy(burnAfterView = burn) }

    fun send() {
        val state = local.value
        val file = state.ready ?: return
        if (!state.canSend) return
        local.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = media.send(state.readyKind, file, state.note.trim().ifEmpty { null }, state.burnAfterView)
            local.update { current ->
                when (result) {
                    is Outcome.Success -> SendUiState(sent = true)
                    is Outcome.Failure -> current.copy(sending = false, error = result.error.toSendError())
                }
            }
        }
    }

    /** Opening what they sent. A burn-after-view item is gone from the server as this returns. */
    fun open(item: MediaItem, into: File) {
        if (local.value.sending) return
        local.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            val result = media.open(item.id, into)
            local.update { current ->
                when (result) {
                    is Outcome.Success -> current.copy(
                        sending = false,
                        open = OpenItem(item, result.value.file, result.value.burned),
                    )
                    is Outcome.Failure -> current.copy(sending = false, error = result.error.toSendError())
                }
            }
        }
    }

    fun close() = local.update { it.copy(open = null) }

    fun dismissSent() = local.update { it.copy(sent = false) }

    fun dismissError() = local.update { it.copy(error = null) }

    override fun onCleared() {
        recorder.cancel()
        super.onCleared()
    }

    companion object {
        /** A line, not a letter: the note beside a voice note or a photo. */
        const val MAX_NOTE = 140
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun AppError.toSendError(): SendError = when {
    this is AppError.Network -> SendError.OFFLINE
    this is AppError.NotFound -> SendError.GONE
    this is AppError.PermissionDenied -> SendError.GONE
    this is AppError.Validation -> SendError.TOO_BIG
    else -> SendError.OTHER
}
