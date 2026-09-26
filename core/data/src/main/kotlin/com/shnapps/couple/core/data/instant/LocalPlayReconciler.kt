package com.shnapps.couple.core.data.instant

import com.shnapps.couple.core.common.Outcome
import com.shnapps.couple.core.data.boundaries.BoundaryRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.model.Boundary
import com.shnapps.couple.core.model.BoundaryLevel
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What a phone learned before anyone signed in, carried into the account (REDESIGN_PROMPT.md §4.2).
 *
 * Someone can play for a week before they ever make an account, and in that week they will have
 * said "never show me these again" to things they meant. Those refusals live on the phone because
 * there is nowhere else to put them (D-100). The moment there *is* somewhere — an account with real
 * limits behind it — they belong there, or the app forgets what it was told the first time the
 * couple pairs, which is the worst possible moment to forget it.
 *
 * Two rules keep this honest:
 *
 * - **It only ever tightens.** A local refusal becomes NEVER; a theme the person has already set
 *   themselves is left exactly as it is, even when they set it to something more permissive. What
 *   they chose deliberately on a limits screen beats what a tap during play implied.
 * - **It carries no content.** A refusal is a theme id, never the card that prompted it. Nothing
 *   about what they were shown reaches the server.
 *
 * The heat comes across as their own content level, which is the same thing in the engine's units.
 * That is a ceiling for themselves alone: the couple's is still the lower of the two, computed on
 * the server (§3.2).
 */
@Singleton
class LocalPlayReconciler @Inject constructor(
    private val preferences: AppPreferencesStore,
    private val boundaries: BoundaryRepository,
    private val contentLevel: ContentLevelWriter,
) {

    /**
     * Writes what this phone learned into the account that now exists, and returns how much was
     * carried. Safe to call again: a theme already set is left alone, so a second run writes
     * nothing.
     */
    suspend fun reconcile(): Carried {
        val local = preferences.preferences.first()
        val existing = boundaries.boundaries.first()

        var themes = 0
        for (theme in local.refusedThemes) {
            if (existing.containsKey(theme)) continue
            val written = boundaries.setBoundary(theme, Boundary(BoundaryLevel.NEVER))
            if (written is Outcome.Success) themes += 1
        }

        val level = local.heat.asContentLevel()
        val levelCarried = contentLevel.setContentLevel(level) is Outcome.Success

        return Carried(themes = themes, contentLevel = level.takeIf { levelCarried })
    }
}

/** What a sign-up carried over from the phone. */
data class Carried(val themes: Int, val contentLevel: Intensity?)

/**
 * Setting the person's own content level, as [LocalPlayReconciler] needs it.
 *
 * A narrow seam rather than the whole of auth: this needs exactly one method, and the reconciler
 * has no business being able to sign anyone in or out. `AuthRepository` implements it.
 */
interface ContentLevelWriter {
    suspend fun setContentLevel(level: Intensity): Outcome<Unit>
}

/** The heat this phone was set to, in the engine's own units. */
fun Heat.asContentLevel(): Intensity = ceiling
