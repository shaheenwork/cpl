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
 * The chat as a layer (CHAT_PROMPT.md).
 *
 * What is worth asserting is mostly about *not* losing things: the draft clears the moment it is
 * said rather than when the server answers, because the write is local and retried for ever; the
 * conversation is marked read when it is actually opened; and the safe word works from in here,
 * because this screen covers one that may be playing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chat = RecordingChat()
    private val nights = FakeNightRepository()

    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.testDispatcher) { body() }

    private fun TestScope.screen() = ChatViewModel(chat, nights).also { vm ->
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
    fun `a photo goes with whatever was typed beside it`() = test {
        chat.conversation.value = Chat(paired = true)
        val vm = screen()
        act { vm.draft("proof") }

        act { vm.send(MediaKind.PHOTO, File("proof.jpg")) }

        assertThat(chat.sent).containsExactly(MediaKind.PHOTO to "proof")
        assertThat(vm.uiState.value.draft).isEmpty()
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

    private fun said(id: String, mine: Boolean) = Message(
        id = id,
        mine = mine,
        kind = MessageKind.TEXT,
        text = "something",
        mediaId = null,
        atMillis = 1,
    )

    /** A real object with real state, not a mock: the fakes rule (D-013). */
    private class RecordingChat : ChatRepository {
        val conversation = MutableStateFlow(Chat())
        override val chat = conversation

        val said = mutableListOf<String>()
        val sent = mutableListOf<Pair<MediaKind, String?>>()
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
        ): Outcome<Unit> {
            sent += kind to text
            return Outcome.Success(Unit)
        }

        override suspend fun unsay(messageId: String): Outcome<Unit> = Outcome.Success(Unit)

        override suspend fun markRead(): Outcome<Unit> {
            readMarks += 1
            return Outcome.Success(Unit)
        }
    }
}
