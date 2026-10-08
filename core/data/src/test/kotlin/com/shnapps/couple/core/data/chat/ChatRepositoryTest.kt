package com.shnapps.couple.core.data.chat

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.firebase.chat.ChatRemoteDataSource
import com.shnapps.couple.core.firebase.chat.MessageDoc
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.model.UserProfile
import com.shnapps.couple.core.testing.FakeAuthRepository
import com.shnapps.couple.core.testing.FakeMediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

/**
 * The conversation (CHAT_PROMPT.md).
 *
 * Three rules carry the weight here. Media is confirmed before the message that points at it
 * exists, because the other way round draws a spinner that never resolves. A phone with no partner
 * has no chat at all — not an empty one, which would attach a listener and draw a composer for a
 * conversation with nobody. And a message carrying media is joined to that media's record, because
 * a screen cannot otherwise tell a once-only photo from an ordinary one, and finding out by
 * fetching it is what destroys it.
 */
class ChatRepositoryTest {

    private val auth = FakeAuthRepository()
    private val media = FakeMediaRepository()
    private val remote = RecordingChat()
    private val repository = DefaultChatRepository(auth, remote, media)

    private fun paired() {
        auth.profile.value = UserProfile(uid = ME, coupleId = COUPLE)
    }

    @Test
    fun `with no partner there is no chat, and nothing is listened to`() = runTest {
        val chat = repository.chat.first()

        assertThat(chat.paired).isFalse()
        assertThat(chat.messages).isEmpty()
        assertThat(remote.listeners).isEqualTo(0)
    }

    @Test
    fun `what was said comes back, theirs and mine told apart`() = runTest {
        paired()
        remote.documents.value = listOf(
            MessageDoc("m1", fromUid = THEM, kind = "TEXT", text = "did you do it", mediaId = null, atMillis = 10),
            MessageDoc("m2", fromUid = ME, kind = "TEXT", text = "doing it now", mediaId = null, atMillis = 20),
        )

        val chat = repository.chat.first()

        assertThat(chat.messages.map { it.mine }).containsExactly(false, true).inOrder()
        assertThat(chat.messages.map { it.text }).containsExactly("did you do it", "doing it now").inOrder()
    }

    @Test
    fun `unread is what they said since this phone last looked, and never my own`() = runTest {
        paired()
        remote.documents.value = listOf(
            MessageDoc("m1", fromUid = THEM, kind = "TEXT", text = "one", mediaId = null, atMillis = 10),
            MessageDoc("m2", fromUid = THEM, kind = "TEXT", text = "two", mediaId = null, atMillis = 30),
            MessageDoc("m3", fromUid = ME, kind = "TEXT", text = "mine", mediaId = null, atMillis = 40),
        )
        remote.read.value = mapOf(ME to 20L)

        val chat = repository.chat.first()

        assertThat(chat.unread).isEqualTo(1)
        // The screen needs the mark as well as the count: the count says how many, this says which.
        assertThat(chat.lastReadMillis).isEqualTo(20L)
    }

    @Test
    fun `a message still on its way has no time on it, and is not counted`() = runTest {
        paired()
        remote.documents.value = listOf(
            MessageDoc("m1", fromUid = ME, kind = "TEXT", text = "sending", mediaId = null, atMillis = null),
        )

        val chat = repository.chat.first()

        assertThat(chat.messages.single().pending).isTrue()
        assertThat(chat.unread).isEqualTo(0)
    }

    @Test
    fun `a message carrying media says what that media is`() = runTest {
        paired()
        media.theySend(MediaKind.VOICE, id = "v1", burnAfterView = true, seconds = 7, levels = List(40) { 30 })
        remote.documents.value = listOf(
            MessageDoc("m1", fromUid = THEM, kind = "VOICE", text = null, mediaId = "v1", atMillis = 10),
        )

        val record = repository.chat.first().messages.single().media

        // Everything needed to draw it without fetching it, which for a once-only one is the
        // difference between showing it and destroying it (D-083).
        assertThat(record?.burnAfterView).isTrue()
        assertThat(record?.opened).isFalse()
        assertThat(record?.seconds).isEqualTo(7)
        assertThat(record?.levels).hasSize(40)
    }

    @Test
    fun `a message whose record has not arrived yet says nothing about it, rather than wrongly`() = runTest {
        paired()
        remote.documents.value = listOf(
            MessageDoc("m1", fromUid = THEM, kind = "PHOTO", text = null, mediaId = "not_here_yet", atMillis = 10),
        )

        val message = repository.chat.first().messages.single()

        // Two listeners, either may be first. Null means "not yet" and draws as loading; guessing
        // `burnAfterView = false` here would burn a once-only photo on arrival.
        assertThat(message.mediaId).isEqualTo("not_here_yet")
        assertThat(message.media).isNull()
    }

    @Test
    fun `a photo is confirmed before the message that points at it exists, and quietly`() = runTest {
        paired()

        val result = repository.send(MediaKind.PHOTO, File("proof.jpg"), text = "proof", burnAfterView = false)

        // The order is the whole of it: a message pointing at an unconfirmed record draws a
        // spinner that never resolves, while a confirmed record nobody references is swept.
        assertThat(media.sent).hasSize(1)
        assertThat(remote.sent.single().third).isEqualTo("media_1")
        assertThat(remote.sent.single().first).isEqualTo(MessageKind.PHOTO.name)
        // The message is what tells them. Letting confirmMedia notify as well is two pushes for
        // one photo, which is what the chat shipped with.
        assertThat(media.quietly).containsExactly(true)
        // The id comes back, so the sender's own phone can draw the photo it already has.
        assertThat((result as Outcome.Success).value).isEqualTo("media_1")
    }

    @Test
    fun `a voice note's length and shape travel with it`() = runTest {
        paired()

        repository.send(
            kind = MediaKind.VOICE,
            file = File("note.m4a"),
            text = null,
            burnAfterView = false,
            seconds = 7,
            levels = List(40) { 50 },
        )

        val item = media.items.value.single()
        assertThat(item.seconds).isEqualTo(7)
        assertThat(item.levels).hasSize(40)
    }

    @Test
    fun `media that would not send leaves no message behind`() = runTest {
        paired()
        media.failNext = AppError.Network()

        val result = repository.send(MediaKind.VOICE, File("note.m4a"), text = null, burnAfterView = false)

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(remote.sent).isEmpty()
    }

    @Test
    fun `an empty message is not a message`() = runTest {
        paired()

        val result = repository.say("   ")

        assertThat(result).isInstanceOf(Outcome.Failure::class.java)
        assertThat(remote.sent).isEmpty()
    }

    /** A real object with real state, not a mock: the fakes rule (D-013). */
    private class RecordingChat : ChatRemoteDataSource {
        val documents = MutableStateFlow<List<MessageDoc>>(emptyList())
        val read = MutableStateFlow<Map<String, Long>>(emptyMap())

        /** How many times a listener was asked for: an unpaired phone must ask for none. */
        var listeners = 0
            private set

        /** Every message written: kind, text, media id. */
        val sent = mutableListOf<Triple<String, String?, String?>>()
        val deleted = mutableListOf<String>()
        var readMarks = 0
            private set

        override fun observe(coupleId: String, limit: Int): Flow<List<MessageDoc>> {
            listeners += 1
            return documents
        }

        override suspend fun send(
            coupleId: String,
            uid: String,
            kind: String,
            text: String?,
            mediaId: String?,
        ): Outcome<String> {
            sent += Triple(kind, text, mediaId)
            return Outcome.Success("m${sent.size}")
        }

        override suspend fun delete(coupleId: String, messageId: String): Outcome<Unit> {
            deleted += messageId
            return Outcome.Success(Unit)
        }

        override suspend fun markRead(coupleId: String, uid: String): Outcome<Unit> {
            readMarks += 1
            return Outcome.Success(Unit)
        }

        override fun observeRead(coupleId: String): Flow<Map<String, Long>> = read
    }

    private companion object {
        const val ME = "uid_me"
        const val THEM = "uid_them"
        const val COUPLE = "c1"
    }
}
