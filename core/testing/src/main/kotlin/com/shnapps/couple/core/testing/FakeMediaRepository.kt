package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.media.MediaRepository
import com.shnapps.couple.core.data.media.OpenedMedia
import com.shnapps.couple.core.model.MediaItem
import com.shnapps.couple.core.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * In-memory [MediaRepository] that plays the server's part: what is sent lands in the inbox, and
 * opening a burn-after-view item takes it out again — the way the server's deletion does.
 */
class FakeMediaRepository : MediaRepository {

    val items = MutableStateFlow<List<MediaItem>>(emptyList())
    override val inbox = items

    /** Every send, in order: kind, note and whether it burns. */
    val sent = mutableListOf<Triple<MediaKind, String?, Boolean>>()

    /** Whether each send asked for the notification to be held back. */
    val quietly = mutableListOf<Boolean>()
    val opened = mutableListOf<String>()

    var failNext: AppError? = null

    override suspend fun send(
        kind: MediaKind,
        file: File,
        note: String?,
        burnAfterView: Boolean,
        seconds: Int?,
        levels: List<Int>,
        quiet: Boolean,
    ): Outcome<String> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        sent += Triple(kind, note, burnAfterView)
        quietly += quiet
        val id = "media_${sent.size}"
        items.value = items.value + MediaItem(
            id = id,
            kind = kind,
            mine = true,
            note = note,
            burnAfterView = burnAfterView,
            sentAtMillis = SENT_AT,
            viewedAtMillis = null,
            expiresAtMillis = null,
            seconds = seconds,
            levels = levels,
        )
        return Outcome.Success(id)
    }

    override suspend fun open(itemId: String, into: File): Outcome<OpenedMedia> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        opened += itemId
        val item = items.value.firstOrNull { it.id == itemId } ?: return Outcome.Failure(AppError.NotFound())
        val burned = item.burnAfterView && !item.mine
        items.value = if (burned) {
            items.value.filterNot { it.id == itemId }
        } else {
            items.value.map { if (it.id == itemId) it.copy(viewedAtMillis = SENT_AT) else it }
        }
        return Outcome.Success(OpenedMedia(into, burned))
    }

    /** The partner, on their own phone: sends something. */
    fun theySend(
        kind: MediaKind,
        note: String? = null,
        burnAfterView: Boolean = false,
        id: String = "from_them_${items.value.size}",
        seconds: Int? = null,
        levels: List<Int> = emptyList(),
    ): MediaItem {
        val item = MediaItem(
            id = id,
            kind = kind,
            mine = false,
            note = note,
            burnAfterView = burnAfterView,
            sentAtMillis = SENT_AT,
            viewedAtMillis = null,
            expiresAtMillis = null,
            seconds = seconds,
            levels = levels,
        )
        items.value = items.value + item
        return item
    }

    private companion object {
        const val SENT_AT = 1_790_000_000_000L
    }
}
