package com.shnapps.couple.core.data.limits

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.auth.AuthRepository
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.LimitAnswer
import com.shnapps.couple.core.model.LimitTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The limits profile: eight questions over the twenty-seven themes the content is authored on (M3).
 *
 * Two places hold the answers, and which one is authoritative depends on whether there is an
 * account yet:
 *
 * - **On the phone**, always. The profile is offered after the first session and the brief is
 *   explicit that nothing may require signing up first, so DataStore is where a profile starts and
 *   where it stays for anyone playing in the same room.
 * - **In the account**, once there is one, as the per-theme `Boundary` documents the server already
 *   reads. Those documents are the input to the chokepoint (§3.2, D-032): the server intersects
 *   both partners' limits into `engineFilters`, which no client can read, so neither partner ever
 *   learns the other's answers. That is why the profile expands into themes rather than being kept
 *   as eight tags — the filtering unit does not change, only the question does.
 *
 * Writing sets every theme the tag owns. Reading takes the strictest answer any of its themes
 * holds ([LimitAnswer.of]), so a tag reads as a no the moment one theme under it is one.
 *
 * **A no is the only answer that removes anything.** Maybe is a real answer and it keeps the card:
 * the brief asks for yes/maybe/no so a couple can say "not sure" without losing the content, and
 * treating maybe as a refusal would make the middle answer the expensive one to pick.
 */
interface LimitsRepository {
    /** What each tag reads as now. A tag with no answer is absent rather than a default. */
    val limits: Flow<Map<LimitTag, LimitAnswer>>

    /** Themes that must not be shown: every theme under a tag answered no. */
    val refusedThemes: Flow<Set<String>>

    /** Sets one tag, on this phone and — when there is an account — on all of its themes. */
    suspend fun set(tag: LimitTag, answer: LimitAnswer): Outcome<Unit>
}

@Singleton
class DefaultLimitsRepository @Inject constructor(
    private val auth: AuthRepository,
    private val boundaries: BoundaryRepository,
    private val preferences: AppPreferencesStore,
    private val play: PlaySettingsStore,
) : LimitsRepository {

    /**
     * The phone's answers, and the account's on top of them.
     *
     * The account wins where it has anything to say, because it is what the server filters on. The
     * phone's copy stays the floor: a profile filled in before signing up keeps working, and
     * `LocalPlayReconciler` is what carries it up when an account appears.
     */
    override val limits: Flow<Map<LimitTag, LimitAnswer>> =
        combine(preferences.preferences, boundaries.boundaries) { prefs, stored ->
            val fromAccount = LimitTag.entries.mapNotNull { tag ->
                val levels = tag.themes.mapNotNull { stored[it]?.level }
                LimitAnswer.of(levels)?.let { tag to it }
            }.toMap()
            prefs.limits + fromAccount
        }

    override val refusedThemes: Flow<Set<String>> =
        combine(limits, preferences.preferences) { answers, prefs ->
            val fromProfile = answers
                .filterValues { it == LimitAnswer.NO }
                .keys
                .flatMapTo(mutableSetOf()) { it.themes }
            // Whatever "never show me these again" wrote mid-card still counts: it is the same
            // kind of statement, made in the moment rather than in a form (D-100).
            fromProfile + prefs.refusedThemes
        }

    override suspend fun set(tag: LimitTag, answer: LimitAnswer): Outcome<Unit> {
        // The phone first, so the answer holds even if there is no account or the write fails.
        play.setLimit(tag, answer)

        if (auth.authState.first() == null) return Outcome.Success(Unit)

        // Then every theme it owns, which is what the server's filter actually reads.
        val level = answer.asBoundary()
        for (theme in tag.themes) {
            when (val result = boundaries.setBoundary(theme, Boundary(level = level))) {
                is Outcome.Failure -> return result
                is Outcome.Success -> Unit
            }
        }
        return Outcome.Success(Unit)
    }
}
