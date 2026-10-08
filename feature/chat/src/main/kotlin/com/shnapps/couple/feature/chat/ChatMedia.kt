package com.shnapps.couple.feature.chat

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.shnapps.couple.core.common.DispatcherProvider
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.MessageMedia
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What a bubble's photo or voice note looks like on this phone, right now. */
sealed interface Shown {

    /** Being fetched. An ordinary photo goes through this on its way to the screen. */
    data object Loading : Shown

    data class Photo(val image: ImageBitmap) : Shown

    /** A voice note's bytes, which the bar needs in order to play them. */
    data class Sound(val file: File) : Shown

    /** It burned as it was read. There is nothing left to show and nothing left to tap. */
    data object Gone : Shown

    data object Failed : Shown
}

/**
 * The bytes behind a message, and the one rule that governs fetching them.
 *
 * `MediaRepository.open` is not a read — it tells the server the item was opened, and a once-only
 * item is **deleted as it is read** (D-083). So a photo cannot simply be loaded because it
 * scrolled into view: doing that would burn every burn-after-view photo the moment it appeared,
 * which is the opposite of what once-only means.
 *
 * Hence [wanted], which loads only what is safe to load, and [look], which is the tap.
 *
 * Nothing here mints a URL. There is none to mint: the bytes are fetched with the signed-in user's
 * own credentials into this app's cache, and a decoded bitmap lives no longer than the process.
 */
interface ChatMedia {

    /** What this phone has fetched or is fetching, by media id. Absent means neither. */
    val shown: StateFlow<Map<String, Shown>>

    /** Remembers the file that was just sent, so the sender sees their own photo at once. */
    fun keep(id: String, file: File)

    /**
     * Fetches [record] if that is safe without being asked.
     *
     * Safe means: not a once-only item. Everything else — ours, theirs, read or unread — may be
     * fetched as it appears, because opening it only marks it read.
     */
    suspend fun wanted(record: MessageMedia)

    /**
     * Fetches [record] because it was asked for.
     *
     * For a once-only item this tap is the only one there will be: the server deletes it as it is
     * read, so there is nothing to come back to.
     */
    suspend fun look(record: MessageMedia)

    /**
     * Decodes a file this phone is about to send, under [key], so it can be shown while it goes up.
     *
     * An upload takes seconds on a bad connection, and a photo that vanishes into a tapped button
     * for that long looks like a photo that did not send.
     */
    suspend fun preview(key: String, file: File)

    /** Drops one preview: its upload finished, or failed. */
    fun forget(key: String)

    /** Everything decoded is dropped: the chat closed, or the account did. */
    fun clear()
}

@Singleton
class DefaultChatMedia @Inject constructor(
    private val media: MediaRepository,
    private val dispatchers: DispatcherProvider,
    @ApplicationContext private val context: Context,
) : ChatMedia {

    private val state = MutableStateFlow<Map<String, Shown>>(emptyMap())
    override val shown: StateFlow<Map<String, Shown>> = state.asStateFlow()

    /** Decoded photos, oldest first, so the oldest is the one dropped when there are too many. */
    private val decoded = ArrayDeque<String>()

    /** What this phone uploaded, by media id: its own photo needs no round trip to be seen. */
    private val ours = mutableMapOf<String, File>()

    override fun keep(id: String, file: File) {
        ours[id] = file
    }

    override suspend fun wanted(record: MessageMedia) {
        // Once per id, whatever became of it. The conversation re-emits on every message, and a
        // fetch that failed must not be retried forty times a minute by the listener — that is
        // what the tap on "Didn't load" is for.
        if (state.value.containsKey(record.id)) return
        if (!record.burnAfterView) {
            look(record)
            return
        }
        // A once-only item waits for the tap. What it already is, is known from the record
        // itself — and finding out by fetching it is exactly what destroys it.
        if (record.gone) state.update { it + (record.id to Shown.Gone) }
    }

    override suspend fun look(record: MessageMedia) {
        if (settled(state.value[record.id])) return
        if (record.gone) {
            state.update { it + (record.id to Shown.Gone) }
            return
        }
        state.update { it + (record.id to Shown.Loading) }

        val file = fetch(record) ?: run {
            state.update { it + (record.id to Shown.Failed) }
            return
        }
        val result = when (record.kind) {
            MediaKind.VOICE -> Shown.Sound(file)
            MediaKind.PHOTO -> decode(file)?.let(Shown::Photo) ?: Shown.Failed
        }
        if (result is Shown.Photo) remember(record.id)
        state.update { it + (record.id to result) }
    }

    override suspend fun preview(key: String, file: File) {
        state.update { it + (key to Shown.Loading) }
        val drawn = decode(file)?.let(Shown::Photo) ?: Shown.Failed
        state.update { it + (key to drawn) }
    }

    override fun forget(key: String) {
        decoded.remove(key)
        state.update { it - key }
    }

    override fun clear() {
        decoded.clear()
        ours.clear()
        state.value = emptyMap()
    }

    /** Loaded, or already on its way: either way, asking again would spend a read for nothing. */
    private fun settled(shown: Shown?): Boolean =
        shown is Shown.Photo || shown is Shown.Sound || shown == Shown.Loading

    /** Ours from this session's own upload, or the server's. Never `open` on our own. */
    private suspend fun fetch(record: MessageMedia): File? {
        ours[record.id]?.takeIf { it.exists() }?.let { return it }
        val opened = media.open(record.id, into = cacheFor(record.id))
        return (opened as? Outcome.Success)?.value?.file
    }

    /** A bitmap is held only while it is one of the last few looked at. */
    private fun remember(id: String) {
        decoded.remove(id)
        decoded.addLast(id)
        while (decoded.size > KEPT) {
            val dropped = decoded.removeFirst()
            state.update { it - dropped }
        }
    }

    private fun cacheFor(id: String): File = File(context.cacheDir, "chat_$id")

    /**
     * The bytes as something drawable, scaled down on the way.
     *
     * Two passes, because the first has to measure the photo without allocating it: a modern
     * phone's camera produces an image several times larger than any screen, and decoding that at
     * full size on the main thread is the one reliable way to make a chat stutter.
     */
    private suspend fun decode(file: File): ImageBitmap? = withContext(dispatchers.io) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > WIDTH) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        }.getOrNull()
    }

    private companion object {
        /** How many photos stay decoded. Enough for a screenful and the scroll either side of it. */
        const val KEPT = 12

        /** Wider than any bubble on any phone this runs on; anything larger is wasted memory. */
        const val WIDTH = 1_080
    }
}
