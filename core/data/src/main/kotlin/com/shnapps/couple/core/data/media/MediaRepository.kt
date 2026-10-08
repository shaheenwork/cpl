package com.shnapps.couple.core.data.media

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.media.MediaDoc
import com.shnapps.couple.core.firebase.media.MediaRemoteDataSource
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** An opened item: the bytes on this phone, and whether opening it burned it. */
data class OpenedMedia(val file: File, val burned: Boolean)

/**
 * Voice notes and photos between the two of them (BUILD_PROMPT.md §5.8, §13).
 *
 * Sending is three steps, in this order, because the rules are written for exactly this order:
 * the server reserves a path, the phone uploads to it, the server confirms what landed. A phone
 * that dies between the second and third leaves an unconfirmed upload the server sweeps away.
 *
 * Opening one goes through the server too, because a burn-after-view item has to be *deleted*
 * rather than hidden: the server removes the object and its record, and the Storage rules stop
 * serving the path the moment the record is gone (D-083).
 */
interface MediaRepository {
    /** What the two of them have sent each other, newest first. */
    val inbox: Flow<List<MediaItem>>

    /**
     * Sends [file] to the partner. [note] is a short line; [burnAfterView] deletes it on opening.
     *
     * [seconds] and [levels] describe a voice note so it can be drawn without being decoded, and
     * [quiet] holds back the notification for media something else is about to announce.
     */
    suspend fun send(
        kind: MediaKind,
        file: File,
        note: String?,
        burnAfterView: Boolean,
        seconds: Int? = null,
        levels: List<Int> = emptyList(),
        quiet: Boolean = false,
    ): Outcome<String>

    /**
     * Opens [itemId]: fetches the bytes into the app's own cache with this user's credentials,
     * and tells the server it was opened — which burns it, if it was sent to burn.
     */
    suspend fun open(itemId: String, into: File): Outcome<OpenedMedia>
}

@Singleton
class DefaultMediaRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: MediaRemoteDataSource,
) : MediaRepository {

    private val ids: Flow<Pair<String, String>?> = auth.currentProfile
        .map { profile -> profile?.coupleId?.let { profile.uid to it } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val inbox: Flow<List<MediaItem>> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(emptyList())
        } else {
            val (uid, coupleId) = pair
            remote.observe(coupleId)
                .map { docs ->
                    docs.filter { it.state == SENT }
                        .sortedByDescending { it.sentAtMillis ?: 0L }
                        .map { it.toItem(uid) }
                }
                .catch { emit(emptyList()) }
        }
    }

    override suspend fun send(
        kind: MediaKind,
        file: File,
        note: String?,
        burnAfterView: Boolean,
        seconds: Int?,
        levels: List<Int>,
        quiet: Boolean,
    ): Outcome<String> {
        val reserved = when (
            val result = remote.reserve(kind.name, note, burnAfterView, ttlMs = null, seconds, levels)
        ) {
            is Outcome.Failure -> return result
            is Outcome.Success -> result.value
        }
        val contentType = if (kind == MediaKind.VOICE) VOICE_TYPE else PHOTO_TYPE
        return when (val upload = remote.upload(reserved.path, file, contentType)) {
            is Outcome.Failure -> upload
            is Outcome.Success -> remote.confirm(reserved.itemId, quiet).map { reserved.itemId }
        }
    }

    override suspend fun open(itemId: String, into: File): Outcome<OpenedMedia> {
        val (uid, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        val item = remote.observe(coupleId).first().firstOrNull { it.id == itemId }
            ?: return Outcome.Failure(AppError.NotFound())
        // The bytes first: a burn deletes the object, so there is one moment to read it in.
        val file = when (val download = remote.download(item.path, into)) {
            is Outcome.Failure -> return download
            is Outcome.Success -> download.value
        }
        // The author re-reading their own does not burn it; the server decides, not this phone.
        return remote.view(itemId).map { burned -> OpenedMedia(file, burned && item.ownerUid != uid) }
    }

    private companion object {
        const val SENT = "SENT"
        const val VOICE_TYPE = "audio/mp4"
        const val PHOTO_TYPE = "image/jpeg"
    }
}

private fun MediaDoc.toItem(uid: String): MediaItem = MediaItem(
    id = id,
    kind = MediaKind.entries.firstOrNull { it.name == kind } ?: MediaKind.PHOTO,
    mine = ownerUid == uid,
    note = note,
    burnAfterView = burnAfterView,
    sentAtMillis = sentAtMillis,
    viewedAtMillis = viewedAtMillis,
    expiresAtMillis = expiresAtMillis,
    seconds = seconds,
    levels = levels,
)
