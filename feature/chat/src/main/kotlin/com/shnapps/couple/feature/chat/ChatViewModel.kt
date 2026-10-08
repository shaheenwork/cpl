package com.shnapps.couple.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.chat.ChatRepository
import com.shnapps.couple.core.data.night.NightRepository
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageMedia
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
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Why something could not be sent. Never a reason to lose what was typed. */
enum class ChatError { OFFLINE, MIC_REFUSED, OTHER }

/** Something this phone is uploading: not a message yet, and drawn as though it nearly is. */
data class Outgoing(
    val id: String,
    val kind: MediaKind,
    val text: String?,
    val seconds: Int?,
    val levels: List<Int>,
)

/** Immutable screen state (BUILD_PROMPT.md §4.1; enforced by :architecture). */
data class ChatUiState(
    /** Drawn over whatever is underneath. Closed is the resting state. */
    val open: Boolean = false,
    /** No pair, no chat: the affordance is not drawn at all until there is somebody to talk to. */
    val paired: Boolean = false,
    val messages: List<Message> = emptyList(),
    /** Theirs, since this phone last looked: the dot on the affordance. */
    val unread: Int = 0,
    val draft: String = "",
    /** A voice note being recorded right now, with how long and how loud. */
    val recording: Recording? = null,
    /**
     * Media going up right now, before any message points at it.
     *
     * It is drawn as a bubble of its own at the end of the conversation, because an upload takes
     * seconds and a photo that disappears into a tapped button for that long looks like one that
     * did not send.
     */
    val outgoing: List<Outgoing> = emptyList(),
    /** The note playing, and how far through it. */
    val playing: Playing? = null,
    /** What this phone has fetched, by media id. Absent means not fetched — see [ChatMedia]. */
    val shown: Map<String, Shown> = emptyMap(),
    /** A photo opened full-screen over the conversation. */
    val viewing: String? = null,
    /**
     * The first message this phone had not read when the chat was opened.
     *
     * Fixed at that moment on purpose: a divider that moved as things were read would slide away
     * from under the thing it is marking.
     */
    val newFrom: String? = null,
    val sending: Boolean = false,
    val error: ChatError? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !sending && recording == null
}

/**
 * The conversation (CHAT_PROMPT.md, CHAT_PRODUCTION_PROMPT.md).
 *
 * One ViewModel for the whole app rather than one per screen, because the chat is a layer over
 * every screen: opening it must not disturb what is underneath, and closing it must not throw away
 * the draft. It is held by the activity, so a round being played keeps its own ViewModel and never
 * knows this one exists.
 *
 * The microphone, the speaker and the bytes behind a message are three collaborators rather than
 * three bits of this class — not for tidiness, but because each of them ticks, and a ViewModel
 * that ticks on its own is one no test can run to completion (D-076).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val nights: NightRepository,
    private val media: ChatMedia,
    private val voice: ChatVoice,
) : ViewModel() {

    private val local = MutableStateFlow(ChatUiState())

    val uiState: StateFlow<ChatUiState> = combine(
        local,
        chat.chat,
        media.shown,
        voice.recording,
        voice.playing,
    ) { state, conversation, shown, recording, playing ->
        state.copy(
            paired = conversation.paired,
            messages = conversation.messages,
            unread = conversation.unread,
            shown = shown,
            recording = recording,
            playing = playing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState())

    init {
        // Everything safe to fetch, fetched as it arrives. Safe excludes a once-only item, which
        // is destroyed by being read and so waits for the tap (D-083).
        viewModelScope.launch {
            chat.chat.collect { conversation ->
                conversation.messages.forEach { message ->
                    message.media?.let { record -> media.wanted(record) }
                }
            }
        }
    }

    /**
     * Opens it, and marks what is already there as read.
     *
     * Read on open rather than per message scrolled past: one write instead of fifty, and the only
     * thing it is used for is the dot.
     */
    fun open() {
        local.update { it.copy(open = true, error = null) }
        viewModelScope.launch {
            val conversation = chat.chat.first()
            val first = conversation.messages
                .firstOrNull { !it.mine && it.atMillis > conversation.lastReadMillis }
            local.update { it.copy(newFrom = first?.id) }
            chat.markRead()
        }
    }

    fun close() {
        voice.cancel()
        voice.stopPlaying()
        local.update { it.copy(open = false, viewing = null, newFrom = null) }
    }

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

    /**
     * A photo or a voice note, with whatever was typed beside it.
     *
     * It appears on the screen before it leaves the phone. The upload is the slow part — seconds
     * on a bad connection — and until it finishes there is no message and nothing to draw, so the
     * tap looked like it had done nothing at all.
     */
    fun send(
        kind: MediaKind,
        file: File,
        burnAfterView: Boolean = false,
        seconds: Int? = null,
        levels: List<Int> = emptyList(),
    ) {
        val note = local.value.draft.trim().takeIf { it.isNotEmpty() }
        val going = Outgoing(
            id = "going_${uploads.incrementAndGet()}",
            kind = kind,
            text = note,
            seconds = seconds,
            levels = levels,
        )
        local.update {
            it.copy(draft = "", sending = true, error = null, outgoing = it.outgoing + going)
        }
        // Decoded beside the upload rather than before it, so the bytes start moving at once.
        viewModelScope.launch { if (kind == MediaKind.PHOTO) media.preview(going.id, file) }
        viewModelScope.launch {
            val result = chat.send(kind, file, note, burnAfterView, seconds, levels)
            // Our own: already on this phone, so it draws without a round trip — and without
            // spending a read on a photo we took ourselves.
            if (result is Outcome.Success) media.keep(result.value, file)
            media.forget(going.id)
            local.update { it.copy(sending = false, outgoing = it.outgoing - going) }
            report(result.map { })
        }
    }

    /**
     * Starts recording.
     *
     * [granted] is the answer to the runtime permission request — the composer asks, because a
     * launcher belongs to the composition, and a refusal is said once here rather than in a dialog.
     */
    fun record(granted: Boolean) {
        if (granted && voice.start()) {
            local.update { it.copy(error = null) }
            return
        }
        local.update { it.copy(error = ChatError.MIC_REFUSED) }
    }

    /** Stops recording and sends what there is. Nothing usable means nothing sent. */
    fun finishRecording() {
        val take = voice.finish() ?: return
        send(MediaKind.VOICE, take.file, seconds = take.seconds, levels = take.levels)
    }

    /** Throws the recording away. */
    fun cancelRecording() = voice.cancel()

    /**
     * Fetches one, because it was tapped.
     *
     * For a once-only item this tap is the only one there will ever be: the server deletes it as
     * it is read, so there is nothing to come back to.
     */
    fun look(record: MessageMedia) {
        viewModelScope.launch { media.look(record) }
    }

    /** Plays a note, or stops it if it is the one already playing. */
    fun play(record: MessageMedia) {
        if (!local.value.open) return
        if (voice.playing.value?.id == record.id) {
            voice.stopPlaying()
            return
        }
        val sound = uiState.value.shown[record.id] as? Shown.Sound
        if (sound == null) {
            look(record)
            return
        }
        voice.play(record.id, sound.file)
    }

    /** Opens a photo over the conversation, or closes the one that is open. */
    fun view(mediaId: String?) = local.update { it.copy(viewing = mediaId) }

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
        voice.cancel()
        voice.stopPlaying()
        local.update { it.copy(open = false, viewing = null) }
        viewModelScope.launch {
            nights.activeSession.first()?.let { nights.stop(it.id) }
        }
    }

    fun dismissError() = local.update { it.copy(error = null) }

    override fun onCleared() {
        // A recorder left running is a microphone left on.
        voice.cancel()
        voice.stopPlaying()
    }

    private fun report(result: Outcome<Unit>) {
        if (result !is Outcome.Failure) return
        val why = if (result.error is AppError.Network) ChatError.OFFLINE else ChatError.OTHER
        local.update { it.copy(error = why) }
    }

    /** Tells one upload from the next while neither has a message id yet. */
    private val uploads = AtomicInteger()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
