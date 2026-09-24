package com.shnapps.couple.core.data.surprise

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.firebase.surprise.SurpriseDoc
import com.shnapps.couple.core.firebase.surprise.SurpriseRemoteDataSource
import com.shnapps.couple.core.model.Countdown
import com.shnapps.couple.core.model.OpenWhenTrigger
import com.shnapps.couple.core.model.Surprise
import com.shnapps.couple.core.model.SurpriseContent
import com.shnapps.couple.core.model.SurpriseState
import com.shnapps.couple.core.model.SurpriseType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Things left for later (BUILD_PROMPT.md §5.7, §13): a scheduled surprise, an "Open when…", a
 * capsule — and the countdowns they are both waiting through.
 *
 * The phone can see that something exists and cannot see what is in it. Asking for the payload
 * before the server delivers it is refused by the rules, not by this class: [open] fetches it and
 * lets the refusal come back as an error, because a client that decided for itself would be the
 * "hidden in the UI" the brief forbids.
 */
interface SurpriseRepository {
    /** What either of them has left for the other, and where each one stands. */
    val surprises: Flow<List<Surprise>>

    val countdowns: Flow<List<Countdown>>

    /** Leaves something for the partner at a time, or for a moment ([trigger]). */
    suspend fun leave(
        type: SurpriseType,
        title: String,
        text: String?,
        mediaId: String? = null,
        scheduledForMillis: Long? = null,
        trigger: OpenWhenTrigger? = null,
    ): Outcome<String>

    /** "Open when you can't sleep": asks the server for one kept for that moment. */
    suspend fun pull(trigger: OpenWhenTrigger): Outcome<String>

    /** Opens what was delivered, and reads what is in it. */
    suspend fun open(surpriseId: String): Outcome<SurpriseContent>

    suspend fun addCountdown(title: String, targetAtMillis: Long): Outcome<Unit>

    suspend fun removeCountdown(countdownId: String): Outcome<Unit>
}

@Singleton
class DefaultSurpriseRepository @Inject constructor(
    private val auth: AuthRepository,
    private val remote: SurpriseRemoteDataSource,
) : SurpriseRepository {

    private val ids: Flow<Pair<String, String>?> = auth.currentProfile
        .map { profile -> profile?.coupleId?.let { profile.uid to it } }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val surprises: Flow<List<Surprise>> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(emptyList())
        } else {
            val (uid, coupleId) = pair
            remote.observe(coupleId)
                .map { docs -> docs.map { it.toSurprise(uid) }.sortedBy { it.scheduledForMillis ?: Long.MAX_VALUE } }
                .catch { emit(emptyList()) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val countdowns: Flow<List<Countdown>> = ids.flatMapLatest { pair ->
        if (pair == null) {
            flowOf(emptyList())
        } else {
            remote.observeCountdowns(pair.second)
                .map { docs ->
                    docs.map { Countdown(it.id, it.title, it.targetAtMillis) }.sortedBy { it.targetAtMillis }
                }
                .catch { emit(emptyList()) }
        }
    }

    override suspend fun leave(
        type: SurpriseType,
        title: String,
        text: String?,
        mediaId: String?,
        scheduledForMillis: Long?,
        trigger: OpenWhenTrigger?,
    ): Outcome<String> = remote.create(
        type = type.name,
        title = title,
        text = text,
        mediaId = mediaId,
        scheduledForMillis = scheduledForMillis,
        trigger = trigger?.name,
    )

    override suspend fun pull(trigger: OpenWhenTrigger): Outcome<String> = remote.openWhen(trigger.name)

    override suspend fun open(surpriseId: String): Outcome<SurpriseContent> {
        val (_, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        // Opening it first: the server refuses one that has not been delivered, so what comes
        // back after that is something this phone is allowed to read.
        return when (val opened = remote.open(surpriseId)) {
            is Outcome.Failure -> opened
            is Outcome.Success -> remote.payload(coupleId, surpriseId)
                .map { SurpriseContent(text = it.text, mediaId = it.mediaId) }
        }
    }

    override suspend fun addCountdown(title: String, targetAtMillis: Long): Outcome<Unit> {
        val (uid, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.addCountdown(coupleId, uid, title, targetAtMillis)
    }

    override suspend fun removeCountdown(countdownId: String): Outcome<Unit> {
        val (_, coupleId) = ids.first() ?: return Outcome.Failure(AppError.Unauthenticated())
        return remote.removeCountdown(coupleId, countdownId)
    }
}

private fun SurpriseDoc.toSurprise(uid: String): Surprise = Surprise(
    id = id,
    type = SurpriseType.entries.firstOrNull { it.name == type } ?: SurpriseType.SCHEDULED,
    title = title,
    mine = createdBy == uid,
    state = SurpriseState.entries.firstOrNull { it.name == state } ?: SurpriseState.SCHEDULED,
    scheduledForMillis = scheduledForMillis,
    trigger = OpenWhenTrigger.entries.firstOrNull { it.name == trigger },
    hasMedia = hasMedia,
)
