package com.shnapps.couple.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.chat.ChatRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Why something could not be sent. Never a reason to lose what was typed. */
enum class ChatError { OFFLINE, OTHER }

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class ChatUiState(
    /** Drawn over whatever is underneath. Closed is the resting state. */
    val open: Boolean = false,
    /** No pair, no chat: the affordance is not drawn at all until there is somebody to talk to. */
    val paired: Boolean = false,
    val messages: List<Message> = emptyList(),
    /** Theirs, since this phone last looked: the dot on the bar. */
    val unread: Int = 0,
    val draft: String = "",
    /** A voice note being recorded right now. */
    val recording: Boolean = false,
    val sending: Boolean = false,
    val error: ChatError? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !sending
}

/**
 * The conversation (CHAT_PROMPT.md).
 *
 * It is one ViewModel for the whole app rather than one per screen, because the chat is a layer
 * over every screen: opening it must not disturb what is underneath, and closing it must not throw
 * away the draft. It is held by the activity, so a round being played keeps its own ViewModel and
 * never knows this one exists.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val nights: NightRepository,
) : ViewModel() {

    private val local = MutableStateFlow(ChatUiState())

    val uiState: StateFlow<ChatUiState> = combine(local, chat.chat) { state, conversation ->
        state.copy(
            paired = conversation.paired,
            messages = conversation.messages,
            unread = conversation.unread,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState())

    /**
     * Opens it, and marks what is already there as read.
     *
     * Read on open rather than per message scrolled past: one write instead of fifty, and the
     * only thing it is used for is the dot.
     */
    fun open() {
        local.update { it.copy(open = true, error = null) }
        viewModelScope.launch { chat.markRead() }
    }

    fun close() = local.update { it.copy(open = false) }

    fun draft(text: String) = local.update { it.copy(draft = text) }

    /**
     * Says it.
     *
     * The draft is cleared **before** the write, because the write is a local one that Firestore
     * retries for ever — waiting for the server would leave the composer full of a message that is
     * already on the screen above it.
     */
    fun say() {
        val words = local.value.draft.trim()
        if (words.isEmpty()) return
        local.update { it.copy(draft = "", error = null) }
        viewModelScope.launch { report(chat.say(words)) }
    }

    /** A photo or a voice note, with whatever was typed beside it. */
    fun send(kind: MediaKind, file: File, burnAfterView: Boolean = false) {
        val note = local.value.draft.trim().takeIf { it.isNotEmpty() }
        local.update { it.copy(draft = "", sending = true, error = null) }
        viewModelScope.launch {
            val result = chat.send(kind, file, note, burnAfterView)
            local.update { it.copy(sending = false) }
            report(result)
        }
    }

    fun recording(on: Boolean) = local.update { it.copy(recording = on) }

    fun unsay(messageId: String) {
        viewModelScope.launch { report(chat.unsay(messageId)) }
    }

    /**
     * The safe word, from inside the chat (M7).
     *
     * It covers a screen that may be playing something, so it carries the one control that is on
     * every such screen. It asks nothing and explains nothing: whatever is under way ends, and the
     * chat closes so they are looking at the thing that stopped.
     */
    fun stop() {
        local.update { it.copy(open = false) }
        viewModelScope.launch {
            nights.activeSession.first()?.let { nights.stop(it.id) }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    private fun report(result: Outcome<Unit>) {
        if (result !is Outcome.Failure) return
        val why = if (result.error is AppError.Network) ChatError.OFFLINE else ChatError.OTHER
        local.update { it.copy(error = why) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
