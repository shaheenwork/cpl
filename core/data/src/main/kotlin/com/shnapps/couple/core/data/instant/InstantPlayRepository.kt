package com.shnapps.couple.core.data.instant

import com.shnapps.couple.core.data.content.ContentRepository
import com.shnapps.couple.core.data.limits.LimitsRepository
import com.shnapps.couple.core.datastore.AppPreferencesStore
import com.shnapps.couple.core.datastore.PlaySettingsStore
import com.shnapps.couple.core.engine.GameNight
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
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
    /**
     * The deck as it stands: the shipped content, this phone's heat and lane, minus what was
     * refused. The pool, not the running order — which card comes next is `InstantDeck.next`.
     */
    val deck: Flow<InstantDeck>

    val tier: Flow<Intensity>

    suspend fun setTier(tier: Intensity)

    /** The packs the app ships, as a couple would name them: the lanes they can ask for (D-125). */
    val packs: Flow<List<ContentPack>>

    /**
     * Together in a room, or apart on two phones (M6).
     *
     * Here rather than read from DataStore by the screen, because a ViewModel reaching the store
     * directly skips the layer that exists to keep device state in one place — the tier, the lane,
     * the refusals and this are all the same kind of thing.
     */
    val mode: Flow<Mode>

    suspend fun setMode(mode: Mode)

    /** The pack being dealt from, or null for all of them. */
    val lane: Flow<String?>

    suspend fun setLane(pack: String?)

    /** What this phone has already been dealt, so a deck of 790 stops behaving like a deck of 6. */
    val played: Flow<Set<String>>

    /** This card has been seen. [deckSize] is what the record is reset against when it is full. */
    suspend fun markPlayed(card: InstantCard, deckSize: Int)

    /** "Not this kind of thing." Silent, immediate, and never sent anywhere. */
    suspend fun refuse(card: InstantCard, alsoItsKind: Boolean)

    /** Every game the app ships, whether or not anyone has signed in. */
    val games: Flow<List<Game>>

    /**
     * The two people passing this phone, if they have said who they are.
     *
     * Kept here with the heat and the refusals, because it is the same kind of thing: something
     * this phone knows about the couple holding it and never sends anywhere. Unnamed until they
     * say otherwise (D-120), which is a real state rather than a placeholder.
     */
    val players: Flow<GamePlayers>

    suspend fun rememberPlayers(players: GamePlayers)

    /**
     * A game dealt on this phone, inside its heat and its refusals (D-100), or null when the
     * content has nothing for it. Nothing is sent and nothing is stored: the game exists for as
     * long as it is being played.
     */
    /**
     * A guided evening planned on this phone: four phases over the whole catalogue (M5).
     *
     * The same pool, tier, limits and no-repeats record as a single card — it is the deck with a
     * shape over it, not a second source of content.
     */
    suspend fun planNight(length: NightLength): GameNight

    suspend fun startGame(gameId: String, autoHeat: Boolean = true): ReleasedGame?
}

@Singleton
class DefaultInstantPlayRepository @Inject constructor(
    private val content: ContentRepository,
    private val preferences: AppPreferencesStore,
    private val play: PlaySettingsStore,
    private val limits: LimitsRepository,
) : InstantPlayRepository {

    override val deck: Flow<InstantDeck> =
        combine(content.items, preferences.preferences, limits.refusedThemes) { items, prefs, refused ->
            InstantDeck.of(
                items = items,
                tier = prefs.tier,
                mode = Mode.TOGETHER,
                skippedIds = prefs.refusedCardIds,
                // The profile's noes and the in-the-moment refusals are the same kind of
                // statement, so the deck takes both from one place (M3).
                refusedThemes = refused,
                pack = prefs.lane,
            )
        }

    override val tier: Flow<Intensity> = preferences.preferences.map { it.tier }

    /**
     * Only the packs that have something to deal at this tier, in this mode.
     *
     * A lane offered and then empty is worse than one not offered: "After dark" starts at level
     * three, so at Mild and Flirty it has nothing, and Long distance is written for two people who
     * are not in the same room. Both would be a dead end, which §6 of the rebuild does not allow —
     * and so is a lane the limits profile has just emptied, which is why it narrows on the same
     * refusals the deck does.
     */
    override val packs: Flow<List<ContentPack>> =
        combine(content.packs, content.items, preferences.preferences, limits.refusedThemes) {
                packs, items, prefs, refused ->
            val dealable = InstantDeck.of(
                items = items,
                tier = prefs.tier,
                mode = Mode.TOGETHER,
                skippedIds = prefs.refusedCardIds,
                refusedThemes = refused,
            ).packs
            packs.filter { it.id in dealable }
        }

    override val mode: Flow<Mode> = preferences.preferences.map { it.mode }

    override suspend fun setMode(mode: Mode) = preferences.setMode(mode)

    override val lane: Flow<String?> = preferences.preferences.map { it.lane }

    override suspend fun setLane(pack: String?) = play.setLane(pack)

    override val played: Flow<Set<String>> = preferences.preferences.map { it.playedCardIds }

    override suspend fun markPlayed(card: InstantCard, deckSize: Int) = play.markPlayed(card.id, deckSize)

    override val games: Flow<List<Game>> = content.games

    override val players: Flow<GamePlayers> =
        preferences.preferences.map { it.gamePlayers ?: GamePlayers.UNNAMED }

    override suspend fun rememberPlayers(players: GamePlayers) = preferences.setGamePlayers(players)

    override suspend fun planNight(length: NightLength): GameNight {
        val prefs = preferences.preferences.first()
        return GameNight.plan(
            deck = deck.first(),
            tier = prefs.tier,
            length = length,
            // A new evening each time it is planned, and the same one for as long as it is played.
            seed = System.currentTimeMillis(),
            exclude = prefs.playedCardIds,
        )
    }

    override suspend fun startGame(gameId: String, autoHeat: Boolean): ReleasedGame? {
        val game = content.games.first().firstOrNull { it.id == gameId } ?: return null
        val prefs = preferences.preferences.first()
        return ReleasedGame.forHeat(
            game = game,
            prompts = content.prompts.first(),
            ceiling = prefs.tier.level,
            refusedThemes = limits.refusedThemes.first(),
            // A new deal each time it is started, and the same one for as long as it is played.
            seed = System.currentTimeMillis(),
            autoHeat = autoHeat,
        )
    }

    override suspend fun setTier(tier: Intensity) = play.setTier(tier)

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
