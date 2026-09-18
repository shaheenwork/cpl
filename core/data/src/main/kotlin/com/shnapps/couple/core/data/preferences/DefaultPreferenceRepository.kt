package com.shnapps.couple.core.data.preferences

import com.shnapps.couple.core.common.AppError
import com.shnapps.couple.core.common.ApplicationScope
import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.taxonomy.TaxonomyRepository
import com.shnapps.couple.core.firebase.preferences.PreferenceDataSource
import com.shnapps.couple.core.model.PreferenceAnswer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPreferenceRepository @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferences: PreferenceDataSource,
    private val taxonomyRepository: TaxonomyRepository,
    @ApplicationScope appScope: CoroutineScope,
) : PreferenceRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val answers: Flow<Map<String, PreferenceAnswer>> = authRepository.authState
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(emptyMap())
            } else {
                preferences.observeAnswers(user.uid)
                    // A listener can be refused mid-flight — most often during sign-out,
                    // when the rules see the old listener without its credentials. That
                    // ends this stream quietly; the auth change that follows starts the
                    // next one. Left uncaught, it would crash the app from the shared scope.
                    .catch { }
            }
        }
        // One listener however many screens collect, closed when none do (section 75).
        .shareIn(appScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    override suspend fun setAnswer(itemId: String, answer: PreferenceAnswer): Outcome<Unit> {
        val uid = authRepository.authState.first()?.uid
            ?: return Outcome.Failure(AppError.Unauthenticated())
        // Stored with the version it was given against, so a reworded item's old answers can
        // be recognised later rather than silently reinterpreted.
        val version = taxonomyRepository.taxonomy.first().version
        return preferences.setAnswer(uid, itemId, answer, version)
    }

    override suspend fun clearAnswer(itemId: String): Outcome<Unit> {
        val uid = authRepository.authState.first()?.uid
            ?: return Outcome.Failure(AppError.Unauthenticated())
        return preferences.clearAnswer(uid, itemId)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
