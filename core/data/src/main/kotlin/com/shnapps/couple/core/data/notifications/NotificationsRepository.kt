package com.shnapps.couple.core.data.notifications

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.firebase.notifications.PushDataSource
import com.shnapps.couple.core.firebase.user.UserProfileDataSource
import com.shnapps.couple.core.model.NotificationSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications, from this phone's side (BUILD_PROMPT.md §16).
 *
 * The settings live in the user's own profile because the **server** reads them: whether to send
 * at all, and whether it is the middle of this user's night. What a notification may say is kept
 * on the phone as well, because a message can arrive long before anything has loaded.
 */
interface NotificationsRepository {
    val settings: Flow<NotificationSettings>

    /** Tells the server where this phone is. Called once signed in. */
    suspend fun registerThisPhone(): Outcome<Unit>

    /** Stops the server reaching this phone. Called before signing out. */
    suspend fun forgetThisPhone(): Outcome<Unit>

    suspend fun update(settings: NotificationSettings): Outcome<Unit>

    /**
     * Whether it is time to ask this phone for permission to notify (D-151): it is paired, and it has
     * not been asked. Never before pairing — until then there is nobody to send a notification — and
     * never twice, because the system shows the question at most twice in a phone's life and a "no"
     * costs only the notifications.
     */
    val shouldAskPermission: Flow<Boolean>

    /** The question has been put, or its answer was already known: do not put it again. */
    suspend fun permissionAsked()
}

@Singleton
class DefaultNotificationsRepository @Inject constructor(
    private val auth: AuthRepository,
    private val profiles: UserProfileDataSource,
    private val push: PushDataSource,
    private val preferences: AppPreferencesStore,
) : NotificationsRepository {

    override val settings: Flow<NotificationSettings> =
        auth.currentProfile.map { it?.notificationSettings ?: NotificationSettings() }

    override val shouldAskPermission: Flow<Boolean> = combine(
        auth.currentProfile.map { it?.coupleId != null },
        preferences.preferences.map { it.notificationPermissionAsked },
    ) { paired, asked -> paired && !asked }.distinctUntilChanged()

    override suspend fun permissionAsked() = preferences.setNotificationPermissionAsked()

    override suspend fun registerThisPhone(): Outcome<Unit> = withPhone { uid, token ->
        push.register(uid, token)
    }

    override suspend fun forgetThisPhone(): Outcome<Unit> = withPhone { uid, token ->
        push.unregister(uid, token)
    }

    override suspend fun update(settings: NotificationSettings): Outcome<Unit> {
        val uid = uid() ?: return Outcome.Failure(AppError.Unauthenticated())
        // The phone keeps its own copy of the one answer a notification needs at three in the
        // morning, when nothing else has loaded yet (§16.2).
        preferences.setShowNotificationContent(settings.showContent)
        return profiles.updateNotificationSettings(uid, settings)
    }

    private suspend fun uid(): String? = auth.authState.first()?.uid

    /** A phone with no push token at all — no Play services, say — is simply not registered. */
    private suspend fun withPhone(work: suspend (uid: String, token: String) -> Outcome<Unit>): Outcome<Unit> {
        val uid = uid() ?: return Outcome.Failure(AppError.Unauthenticated())
        val token = push.currentToken() ?: return Outcome.Success(Unit)
        return work(uid, token)
    }
}
