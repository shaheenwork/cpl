package com.shnapps.couple.core.testing

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.notifications.NotificationsRepository
import com.shnapps.couple.core.model.NotificationSettings
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [NotificationsRepository]: the settings really change, and the phone is really registered. */
class FakeNotificationsRepository(
    initial: NotificationSettings = NotificationSettings(),
) : NotificationsRepository {

    val current = MutableStateFlow(initial)
    override val settings = current

    var registered = false
        private set
    var failNext: AppError? = null

    override suspend fun registerThisPhone(): Outcome<Unit> {
        registered = true
        return Outcome.Success(Unit)
    }

    override suspend fun forgetThisPhone(): Outcome<Unit> {
        registered = false
        return Outcome.Success(Unit)
    }

    override suspend fun update(settings: NotificationSettings): Outcome<Unit> {
        failNext?.let {
            failNext = null
            return Outcome.Failure(it)
        }
        current.value = settings
        return Outcome.Success(Unit)
    }
}
