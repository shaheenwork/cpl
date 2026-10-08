package com.shnapps.couple.core.data.chat

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.firebase.chat.ChatRemoteDataSource
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Message
import com.shnapps.couple.core.model.MessageKind
import com.shnapps.couple.core.model.MessageMedia
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The conversation as a screen needs it: what was said, and what this phone has not read. */
data class Chat(
    val messages: List<Message> = emptyList(),
    val unread: Int = 0,
    /** Whether there is anybody to talk to. False is not an empty chat — it is no chat at all. */
    val paired: Boolean = false,
    /**
     * When this phone last looked. The screen needs it as well as the count: the count says how
     * many are new, and this says which one is the first of them.
     */
    val lastReadMillis: Long = 0L,
)

/**
 * What the two of them say to each other (CHAT_PROMPT.md).
 *
 * A phone with no partner has no chat at all — nothing to read, nothing to send, and no listener
 * attached. That is the same shape as every other paired feature here: the flow is empty until
 * there is a couple, so the signed-out half of the app costs nothing.
 */
interface ChatRepository {
    /** The newest page, oldest first, with this phone's unread count. Empty when not paired. */
    val chat: Flow<Chat>

    suspend fun say(text: String): Outcome<Unit>

    /**
     * Sends a photo or a voice note with an optional line.
     *
     * The media is confirmed **before** the message that points at it exists. The other order
     * would leave a message pointing at an unconfirmed record, which draws as a spinner that never
     * resolves; this order can only ever leave a confirmed record nobody references, which the
     * hourly sweep takes away (D-083).
     */
    suspend fun send(
        kind: MediaKind,
        file: File,
        text: String?,
        burnAfterView: Boolean,
        seconds: Int? = null,
        levels: List<Int> = emptyList(),
    ): Outcome<String>

    /** Takes a message back, for both of them. Only its author may. */
    suspend fun unsay(messageId: String): Outcome<Unit>

    /** Everything up to now has been read on this phone. */
    suspend fun markRead(): Outcome<Unit>
}

@Singleton
class DefaultChatRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: ChatRemoteDataSource,
    private val media: MediaRepository,
) : ChatRepository {

    /** This phone's uid and couple, or null while there is no pair to talk to. */
    private val pair: Flow<Pair<String, String>?> = auth.currentProfile
        .map { profile -> profile?.coupleId?.let { profile.uid to it } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val chat: Flow<Chat> = pair.flatMapLatest { ids ->
        if (ids == null) {
            flowOf(Chat())
        } else {
            val (uid, coupleId) = ids
            combine(
                remote.observe(coupleId, PAGE),
                remote.observeRead(coupleId),
                // What a message's media *is*, which the message itself does not say. Without this
                // a screen cannot tell a once-only photo from an ordinary one, and fetching one to
                // find out is what destroys it (D-083).
                media.inbox,
            ) { documents, read, inbox ->
                val records = inbox.associateBy { it.id }
                val messages = documents.mapNotNull { document ->
                    MessageKind.of(document.kind)?.let { kind ->
                        Message(
                            id = document.id,
                            mine = document.fromUid == uid,
                            kind = kind,
                            text = document.text,
                            mediaId = document.mediaId,
                            atMillis = document.atMillis ?: 0L,
                            media = document.mediaId?.let { records[it]?.describe() },
                        )
                    }
                }
                val lastRead = read[uid] ?: 0L
                Chat(
                    paired = true,
                    messages = messages,
                    lastReadMillis = lastRead,
                    // Theirs, since this phone last looked. A message still on its way has no
                    // timestamp, and it is this phone's own, so it never counts.
                    unread = messages.count { !it.mine && it.atMillis > lastRead },
                )
            }
        }
    }

    override suspend fun say(text: String): Outcome<Unit> {
        val words = text.trim()
        if (words.isEmpty()) return Outcome.Failure(AppError.Validation(EMPTY))
        return write(MessageKind.TEXT, words, mediaId = null)
    }

    override suspend fun send(
        kind: MediaKind,
        file: File,
        text: String?,
        burnAfterView: Boolean,
        seconds: Int?,
        levels: List<Int>,
    ): Outcome<String> {
        // quiet: the message written next is what tells them, and the chat's own trigger holds a
        // conversation to one push every few minutes. Letting confirmMedia speak too means two
        // notifications for one photo.
        val sent = media.send(
            kind = kind,
            file = file,
            note = text?.trim()?.takeIf { it.isNotEmpty() },
            burnAfterView = burnAfterView,
            seconds = seconds,
            levels = levels,
            quiet = true,
        )
        return when (sent) {
            is Outcome.Failure -> sent
            is Outcome.Success -> write(
                kind = if (kind == MediaKind.VOICE) MessageKind.VOICE else MessageKind.PHOTO,
                text = text?.trim()?.takeIf { it.isNotEmpty() },
                mediaId = sent.value,
            ).map { sent.value }
        }
    }

    override suspend fun unsay(messageId: String): Outcome<Unit> {
        val (_, coupleId) = pair.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.delete(coupleId, messageId)
    }

    override suspend fun markRead(): Outcome<Unit> {
        val (uid, coupleId) = pair.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.markRead(coupleId, uid)
    }

    private suspend fun write(kind: MessageKind, text: String?, mediaId: String?): Outcome<Unit> {
        val (uid, coupleId) = pair.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.send(coupleId, uid, kind.name, text, mediaId).map { }
    }

    private companion object {
        /** Enough for an evening; "load older" is the screen's job, not the listener's. */
        const val PAGE = 50

        const val EMPTY = "empty-message"
    }
}

/**
 * What a screen may know about a message's media before it is fetched.
 *
 * `mine` is left out on purpose: whose it is, is the message's business, and a record and its
 * message always agree about it.
 */
private fun MediaItem.describe(): MessageMedia = MessageMedia(
    id = id,
    kind = kind,
    burnAfterView = burnAfterView,
    opened = viewedAtMillis != null,
    seconds = seconds,
    levels = levels,
)
