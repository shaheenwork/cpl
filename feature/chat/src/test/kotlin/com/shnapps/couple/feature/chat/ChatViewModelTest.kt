package com.shnapps.couple.feature.chat

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.chat.Chat
import com.shnapps.couple.core.data.chat.ChatRepository
import com.shnapps.couple.core.model.ExperienceType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.model.MessageMedia
import com.shnapps.couple.core.model.NightSession
import com.shnapps.couple.core.model.SessionState
import com.shnapps.couple.core.testing.FakeNightRepository
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * The chat as a layer (CHAT_PROMPT.md, CHAT_PRODUCTION_PROMPT.md).
 *
 * Two kinds of promise are worth asserting here. The first is about not losing things: the draft
 * clears the moment it is said rather than when the server answers, because the write is local and
 * retried for ever, and the safe word works from in here because this screen covers one that may
 * be playing.
 *
 * The second is about not *destroying* things. Opening a once-only photo deletes it, so it must be
 * fetched when it is tapped and at no other moment — not when it arrives, not when it scrolls past.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chat = RecordingChat()
    private val nights = FakeNightRepository()
    private val media = FakeChatMedia()
    private val voice = FakeChatVoice()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = ChatViewModel(chat, nights, media, voice).also { vm ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        advanceUntilIdle()
    }

    private fun TestScope.act(action: () -> Unit) {
        action()
        advanceUntilIdle()
    }

    @Test
    fun `closed until it is opened, and opening marks what is there as read`() = test {
        chat.conversation.value = Chat(paired = true, messages = listOf(said("m1", mine = false)), unread = 1)
        val vm = screen()

        assertThat(vm.uiState.value.open).isFalse()
        assertThat(vm.uiState.value.unread).isEqualTo(1)

        act(vm::open)

        assertThat(vm.uiState.value.open).isTrue()
        assertThat(chat.readMarks).isEqualTo(1)
    }

    @Test
    fun `the unread mark sits on the first of theirs this phone has not seen`() = test {
        chat.conversation.value = Chat(
            paired = true,
            messages = listOf(
                said("m1", mine = false, at = 10),
                said("m2", mine = true, at = 20),
                said("m3", mine = false, at = 30),
                said("m4", mine = false, at = 40),
            ),
            unread = 2,
            lastReadMillis = 25,
        )
        val vm = screen()

        act(vm::open)

        // Not the first unread *message* — the first unread one of theirs. Mine are always read.
        assertThat(vm.uiState.value.newFrom).isEqualTo("m3")
    }

    @Test
    fun `the draft clears as it is said, not when the server answers`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act { vm.draft("doing it now") }
        assertThat(vm.uiState.value.canSend).isTrue()

        act(vm::say)

        // A Firestore write is queued offline and retried for ever: a composer that waited for it
        // would sit full of a message that is already on the screen above it.
        assertThat(vm.uiState.value.draft).isEmpty()
        assertThat(chat.said).containsExactly("doing it now")
    }

    @Test
    fun `nothing is sent for an empty draft`() = test {
        val vm = screen()

        act { vm.draft("   ") }
        act(vm::say)

        assertThat(chat.said).isEmpty()
        assertThat(vm.uiState.value.canSend).isFalse()
    }

    @Test
    fun `a failure says so and keeps the conversation open`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act(vm::open)
        chat.failNext = AppError.Network()

        act { vm.draft("hello") }
        act(vm::say)

        assertThat(vm.uiState.value.error).isEqualTo(ChatError.OFFLINE)
        assertThat(vm.uiState.value.open).isTrue()
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
    }

    @Test
    fun `a photo goes with whatever was typed beside it, and is kept for the sender to see`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act { vm.draft("proof") }

        act { vm.send(MediaKind.PHOTO, File("proof.jpg")) }

        assertThat(chat.sent).containsExactly(MediaKind.PHOTO to "proof")
        assertThat(vm.uiState.value.draft).isEmpty()
        // Already on this phone: drawing it must not spend a read, and must not go near open().
        assertThat(media.kept.keys).containsExactly("media_1")
    }

    @Test
    fun `an ordinary photo of theirs is fetched as it arrives`() = test {
        chat.conversation.value = Chat(
            paired = true,
            messages = listOf(photo("m1", burns = false)),
        )
        val vm = screen()
        advanceUntilIdle()

        assertThat(media.looked).containsExactly("media_m1")
        assertThat(vm.uiState.value.shown).containsKey("media_m1")
    }

    @Test
    fun `a once-only photo is not fetched until it is tapped`() = test {
        val message = photo("m1", burns = true)
        chat.conversation.value = Chat(paired = true, messages = listOf(message))
        val vm = screen()
        advanceUntilIdle()

        // It was offered and refused: opening it is what deletes it, so nothing happens until
        // somebody asks for it.
        assertThat(media.offered).containsExactly("media_m1")
        assertThat(media.looked).isEmpty()

        act { vm.look(message.media!!) }

        assertThat(media.looked).containsExactly("media_m1")
    }

    @Test
    fun `a fetch that failed is not retried by the listener, only by a tap`() = test {
        val message = photo("m1", burns = false)
        media.result = { Shown.Failed }
        chat.conversation.value = Chat(paired = true, messages = listOf(message))
        val vm = screen()
        advanceUntilIdle()

        // The conversation re-emits on every message; a failed photo must not become a retry loop.
        chat.conversation.value = chat.conversation.value.copy(unread = 1)
        advanceUntilIdle()

        assertThat(media.looked).containsExactly("media_m1")

        act { vm.look(message.media!!) }

        assertThat(media.looked).containsExactly("media_m1", "media_m1")
    }

    @Test
    fun `one that has already burned is never fetched at all`() = test {
        val message = photo("m1", burns = true, opened = true)
        chat.conversation.value = Chat(paired = true, messages = listOf(message))
        val vm = screen()
        advanceUntilIdle()

        assertThat(media.looked).isEmpty()
        assertThat(vm.uiState.value.shown["media_m1"]).isEqualTo(Shown.Gone)
    }

    @Test
    fun `the mic asks, and a refusal is said once rather than repeated`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act(vm::open)

        act { vm.record(granted = false) }

        assertThat(vm.uiState.value.error).isEqualTo(ChatError.MIC_REFUSED)
        assertThat(voice.starts).isEqualTo(0)
        // Dismissed, and not re-raised by itself.
        act(vm::dismissError)
        assertThat(vm.uiState.value.error).isNull()
        assertThat(vm.uiState.value.recording).isNull()
    }

    @Test
    fun `a granted mic records, and finishing sends the note with its own waveform`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act(vm::open)

        act { vm.record(granted = true) }
        assertThat(vm.uiState.value.recording).isNotNull()

        act(vm::finishRecording)

        assertThat(chat.sent).containsExactly(MediaKind.VOICE to null)
        assertThat(chat.lastSeconds).isEqualTo(7)
        assertThat(chat.lastLevels).hasSize(40)
        assertThat(vm.uiState.value.recording).isNull()
    }

    @Test
    fun `a recording that came to nothing sends nothing`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        voice.take = null

        act { vm.record(granted = true) }
        act(vm::finishRecording)

        assertThat(chat.sent).isEmpty()
    }

    @Test
    fun `playing one stops the one already playing`() = test {
        val message = voiceNote("m1")
        chat.conversation.value = Chat(paired = true, messages = listOf(message))
        val vm = screen()
        act(vm::open)
        advanceUntilIdle()

        act { vm.play(message.media!!) }
        assertThat(voice.played).containsExactly("media_m1")

        act { vm.play(message.media!!) }
        assertThat(voice.stops).isEqualTo(1)
    }

    @Test
    fun `closing throws away a recording rather than leaving the microphone on`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act(vm::open)
        act { vm.record(granted = true) }

        act(vm::close)

        assertThat(voice.cancels).isAtLeast(1)
        assertThat(vm.uiState.value.recording).isNull()
    }

    @Test
    fun `the safe word works from in here, and closes the chat as it goes`() = test {
        chat.conversation.value = Chat(paired = true)
        nights.session.value = underWay()
        val vm = screen()
        act(vm::open)

        act(vm::stop)

        // It covers a screen that may be playing something, so it carries the one control every
        // such screen has (M7).
        assertThat(vm.uiState.value.open).isFalse()
        assertThat(nights.calls).contains("stop")
    }

    @Test
    fun `no partner, no chat at all`() = test {
        val vm = screen()

        assertThat(vm.uiState.value.paired).isFalse()
    }

    /** Something is being played: the only state in which the safe word has anything to stop. */
    private fun underWay() = NightSession(
        id = "s",
        experienceId = "e",
        state = SessionState.CHAPTER_ACTIVE,
        pausedFrom = null,
        currentChapter = 0,
        chapterCount = 3,
        consentRequired = false,
        consented = true,
        partnerConsented = true,
        isHost = true,
        endReason = null,
        experienceType = ExperienceType.GAME,
    )

    private fun said(id: String, mine: Boolean, at: Long = 1) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.TEXT,
        text = "something",
        mediaId = null,
        atMillis = at,
    )

    private fun photo(id: String, burns: Boolean, opened: Boolean = false) = Message(
        id = id,
        mine = false,
        kind = MessageKind.PHOTO,
        text = null,
        mediaId = "media_$id",
        atMillis = 10,
        media = MessageMedia(
            id = "media_$id",
            kind = MediaKind.PHOTO,
            burnAfterView = burns,
            opened = opened,
            seconds = null,
            levels = emptyList(),
        ),
    )

    private fun voiceNote(id: String) = Message(
        id = id,
        mine = false,
        kind = MessageKind.VOICE,
        text = null,
        mediaId = "media_$id",
        atMillis = 10,
        media = MessageMedia(
            id = "media_$id",
            kind = MediaKind.VOICE,
            burnAfterView = false,
            opened = false,
            seconds = 7,
            levels = List(40) { 40 },
        ),
    )

    /** A real object with real state, not a mock: the fakes rule (D-013). */
    private class RecordingChat : ChatRepository {
        val conversation = MutableStateFlow(Chat())
        override val chat = conversation

        val said = mutableListOf<String>()
        val sent = mutableListOf<Pair<MediaKind, String?>>()
        var lastSeconds: Int? = null
            private set
        var lastLevels: List<Int> = emptyList()
            private set
        var failNext: AppError? = null
        var readMarks = 0
            private set

        override suspend fun say(text: String): Outcome<Unit> {
            failNext?.let {
                failNext = null
                return Outcome.Failure(it)
            }
            said += text
            return Outcome.Success(Unit)
        }

        override suspend fun send(
            kind: MediaKind,
            file: File,
            text: String?,
            burnAfterView: Boolean,
            seconds: Int?,
            levels: List<Int>,
        ): Outcome<String> {
            sent += kind to text
            lastSeconds = seconds
            lastLevels = levels
            return Outcome.Success("media_${sent.size}")
        }

        override suspend fun unsay(messageId: String): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun markRead(): Outcome<Unit> {
            readMarks += 1
            return Outcome.Success(Unit)
        }
    }
}
