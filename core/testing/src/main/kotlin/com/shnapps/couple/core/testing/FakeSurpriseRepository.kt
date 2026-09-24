package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.surprise.SurpriseRepository
import com.shnapps.couple.core.model.Countdown
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.Surprise
import com.shnapps.couple.core.model.SurpriseContent
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.SurpriseType
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SurpriseRepository] that plays the server's part: what is left is waiting until it
 * is delivered, "Open when…" hands over the oldest one kept for that moment, and what is in one
 * cannot be read before then — the way the rules refuse it.
 */
class FakeSurpriseRepository : SurpriseRepository {

    val items = MutableStateFlow<List<Surprise>>(emptyList())
    override val surprises = items

    val timers = MutableStateFlow<List<Countdown>>(emptyList())
    override val countdowns = timers

    /** What is in each one, by id. */
    val contents = mutableMapOf<String, SurpriseContent>()

    val left = mutableListOf<Surprise>()
    val pulled = mutableListOf<OpenWhenTrigger>()
    val opened = mutableListOf<String>()

    var failNext: AppError? = null

    override suspend fun leave(
        type: SurpriseType,
        title: String,
        text: String?,
        mediaId: String?,
        scheduledForMillis: Long?,
        trigger: OpenWhenTrigger?,
    ): Outcome<String> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        val surprise = Surprise(
            id = "left_${left.size}",
            type = type,
            title = title,
            mine = true,
            state = SurpriseState.SCHEDULED,
            scheduledForMillis = scheduledForMillis,
            trigger = trigger,
            hasMedia = mediaId != null,
        )
        left += surprise
        items.value = items.value + surprise
        contents[surprise.id] = SurpriseContent(text, mediaId)
        return Outcome.Success(surprise.id)
    }

    override suspend fun pull(trigger: OpenWhenTrigger): Outcome<String> {
        pulled += trigger
        val waiting = items.value.firstOrNull {
            !it.mine && it.trigger == trigger && it.state == SurpriseState.SCHEDULED
        } ?: return Outcome.Failure(AppError.NotFound())
        deliver(waiting.id)
        return Outcome.Success(waiting.id)
    }

    override suspend fun open(surpriseId: String): Outcome<SurpriseContent> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        opened += surpriseId
        val surprise = items.value.firstOrNull { it.id == surpriseId } ?: return Outcome.Failure(AppError.NotFound())
        // The server refuses one that has not been delivered; so does this.
        if (surprise.state == SurpriseState.SCHEDULED) return Outcome.Failure(AppError.Validation("not-yet"))
        items.value = items.value.map { if (it.id == surpriseId) it.copy(state = SurpriseState.OPENED) else it }
        return Outcome.Success(contents[surpriseId] ?: SurpriseContent(null, null))
    }

    override suspend fun addCountdown(title: String, targetAtMillis: Long): Outcome<Unit> {
        timers.value = timers.value + Countdown("cd_${timers.value.size}", title, targetAtMillis)
        return Outcome.Success(Unit)
    }

    override suspend fun removeCountdown(countdownId: String): Outcome<Unit> {
        timers.value = timers.value.filterNot { it.id == countdownId }
        return Outcome.Success(Unit)
    }

    /** The partner, on their own phone: leaves something for this one. */
    fun theyLeave(
        type: SurpriseType = SurpriseType.SCHEDULED,
        title: String = "For you",
        text: String = "something",
        trigger: OpenWhenTrigger? = null,
        state: SurpriseState = SurpriseState.SCHEDULED,
    ): Surprise {
        val surprise = Surprise(
            id = "theirs_${items.value.size}",
            type = type,
            title = title,
            mine = false,
            state = state,
            scheduledForMillis = null,
            trigger = trigger,
            hasMedia = false,
        )
        items.value = items.value + surprise
        contents[surprise.id] = SurpriseContent(text, null)
        return surprise
    }

    /** The clock, or the server: this one has arrived. */
    fun deliver(surpriseId: String) {
        items.value = items.value.map {
            if (it.id == surpriseId) it.copy(state = SurpriseState.DELIVERED) else it
        }
    }
}
