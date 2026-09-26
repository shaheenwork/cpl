package com.shnapps.couple.core.data.instant

import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.Heat
import com.shnapps.couple.core.model.Mode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playing with no account and no partner (REDESIGN_PROMPT.md §4.1).
 *
 * Everything here is on the phone: the content that shipped with the app, the heat this person
 * picked, and the cards they have refused. Nothing is fetched, so the first card arrives as fast as
 * Room can read, and nothing is written anywhere a second person could see.
 *
 * This is deliberately not a night. A night is built on the server from both partners' limits and
 * cannot exist before there are two accounts. What this offers instead is the half that needs
 * nobody: something to do, now, inside a ceiling this phone set for itself.
 */
interface InstantPlayRepository {
    /** The deck as it stands: the shipped content, this phone's heat, minus what was refused. */
    val deck: Flow<InstantDeck>

    val heat: Flow<Heat>

    suspend fun setHeat(heat: Heat)

    /** "Not this kind of thing." Silent, immediate, and never sent anywhere. */
    suspend fun refuse(card: InstantCard, alsoItsKind: Boolean)

    /** Every game the app ships, whether or not anyone has signed in. */
    val games: Flow<List<Game>>

    /**
     * A game dealt on this phone, inside its heat and its refusals (D-100), or null when the
     * content has nothing for it. Nothing is sent and nothing is stored: the game exists for as
     * long as it is being played.
     */
    suspend fun startGame(gameId: String): ReleasedGame?
}

@Singleton
class DefaultInstantPlayRepository @Inject constructor(
    private val content: ContentRepository,
    private val preferences: AppPreferencesStore,
    private val play: PlaySettingsStore,
) : InstantPlayRepository {

    override val deck: Flow<InstantDeck> =
        combine(content.items, preferences.preferences) { items, prefs ->
            InstantDeck.of(
                items = items,
                heat = prefs.heat,
                mode = Mode.TOGETHER,
                skippedIds = prefs.refusedCardIds,
                refusedThemes = prefs.refusedThemes,
            )
        }

    override val heat: Flow<Heat> = preferences.preferences.map { it.heat }

    override val games: Flow<List<Game>> = content.games

    override suspend fun startGame(gameId: String): ReleasedGame? {
        val game = content.games.first().firstOrNull { it.id == gameId } ?: return null
        val prefs = preferences.preferences.first()
        return ReleasedGame.forHeat(
            game = game,
            prompts = content.prompts.first(),
            ceiling = prefs.heat.ceiling.level,
            refusedThemes = prefs.refusedThemes,
            // A new deal each time it is started, and the same one for as long as it is played.
            seed = System.currentTimeMillis(),
        )
    }

    override suspend fun setHeat(heat: Heat) = play.setHeat(heat)

    /**
     * A refusal of one card, or of everything like it.
     *
     * "Just this one" records the card so it does not come back tonight. "Never show me these
     * again" records the themes it belongs to, which is the same thing the boundaries screen
     * writes — captured in one tap, during play, instead of in a wizard beforehand.
     */
    override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) =
        play.refuse(card.id, if (alsoItsKind) card.themes else emptySet())
}
