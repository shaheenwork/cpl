package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Clock
import com.shnapps.couple.core.common.FakeClock
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.surprise.ArcRepository
import com.shnapps.couple.core.model.Arc
import com.shnapps.couple.core.model.ArcDay
import com.shnapps.couple.core.model.ArcDayState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ArcRepository] that plays the server's part: a day opens only when it is due and
 * the day before it has been played, and a night appears the moment it is opened.
 */
class FakeArcRepository(private val clock: Clock = FakeClock()) : ArcRepository {

    val current = MutableStateFlow<Arc?>(null)
    override val arc = current

    val opened = mutableListOf<Int>()
    var failNext: AppError? = null

    override suspend fun start(lengthDays: Int): Outcome<String> {
        forced()?.let { return Outcome.Failure(it) }
        current.value = Arc(
            id = "arc_1",
            title = if (lengthDays == 1) "One night" else "$lengthDays days",
            lengthDays = lengthDays,
            active = true,
            days = (0 until lengthDays).map { index ->
                ArcDay(
                    index = index,
                    title = if (index == lengthDays - 1) "The last night" else "Day ${index + 1}",
                    state = if (index == 0) ArcDayState.READY else ArcDayState.LOCKED,
                    unlockAtMillis = clock.nowMillis() + index * DAY_MS,
                    experienceId = null,
                )
            },
        )
        return Outcome.Success("arc_1")
    }

    override suspend fun openDay(arcId: String, dayIndex: Int): Outcome<String> {
        val arc = current.value ?: return Outcome.Failure(forced() ?: AppError.NotFound())
        val day = arc.days.getOrNull(dayIndex)
        val before = arc.days.getOrNull(dayIndex - 1)
        val refusal = forced() ?: when {
            day == null -> AppError.NotFound()
            day.unlockAtMillis > clock.nowMillis() -> AppError.Validation("not-yet")
            before != null && before.state != ArcDayState.DONE -> AppError.Validation("finish-the-day-before")
            else -> null
        }
        if (refusal != null) return Outcome.Failure(refusal)
        opened += dayIndex
        val experienceId = "night_$dayIndex"
        current.value = arc.copy(
            days = arc.days.map {
                when (it.index) {
                    dayIndex -> it.copy(state = ArcDayState.READY, experienceId = experienceId)
                    dayIndex - 1 -> it.copy(state = ArcDayState.DONE)
                    else -> it
                }
            },
        )
        return Outcome.Success(experienceId)
    }

    override suspend fun end(arcId: String): Outcome<Unit> {
        current.value = null
        return Outcome.Success(Unit)
    }

    /** The failure a test asked for, if any: taken once. */
    private fun forced(): AppError? = failNext?.also { failNext = null }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
