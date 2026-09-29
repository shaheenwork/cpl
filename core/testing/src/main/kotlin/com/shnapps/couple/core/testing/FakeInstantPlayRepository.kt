package com.shnapps.couple.core.testing

import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.Intensity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * In-memory [InstantPlayRepository]: what a phone with no account can play on its own.
 *
 * A real deck over real content rather than a mock, for the same reason as every other fake here
 * (DECISIONS.md D-013) — and built the only way a deck may be built, through the factory. [refused]
 * records what was said no to, since nothing else can: a refusal never leaves the phone.
 */
class FakeInstantPlayRepository(
    items: List<ContentItem> = emptyList(),
    games: List<Game> = TestGames.games,
) : InstantPlayRepository {

    val items = MutableStateFlow(items)
    override val games = MutableStateFlow(games)
    override val tier = MutableStateFlow(Intensity.DEFAULT)

    /** What this phone has said no to: the card, and whether its whole kind went with it. */
    val refused = mutableListOf<Pair<String, Boolean>>()

    /** Every game this phone dealt itself, in order. */
    val dealt = mutableListOf<String>()

    /** Whether each deal climbed to the tier or opened at it (M4). */
    val dealtWithAutoHeat = mutableListOf<Boolean>()

    /** What [startGame] hands back. Null stands for content that has nothing at this tier. */
    var released: ReleasedGame? = null

    /** The lanes a test offers, and the one it is dealing from. */
    override val packs = MutableStateFlow(TestContent.packs)
    override val lane = MutableStateFlow<String?>(null)

    /** What this fake has been told was seen, and by which deck size. */
    override val played = MutableStateFlow(emptySet<String>())
    val playedAgainst = mutableListOf<Pair<String, Int>>()

    override val deck: Flow<InstantDeck> =
        combine(this.items, tier, lane) { available, level, pack ->
            InstantDeck.of(available, level, pack = pack)
        }

    override suspend fun setLane(pack: String?) {
        this.lane.value = pack
    }

    override suspend fun markPlayed(card: InstantCard, deckSize: Int) {
        playedAgainst += card.id to deckSize
        played.value = played.value + card.id
    }

    /** Who is passing this phone, if they have said. Unnamed until a test says otherwise. */
    override val players = MutableStateFlow(GamePlayers.UNNAMED)

    override suspend fun rememberPlayers(players: GamePlayers) {
        this.players.value = players
    }

    override suspend fun setTier(tier: Intensity) {
        this.tier.value = tier
    }

    override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) {
        refused += card.id to alsoItsKind
    }

    override suspend fun startGame(gameId: String, autoHeat: Boolean): ReleasedGame? {
        dealt += gameId
        dealtWithAutoHeat += autoHeat
        return released
    }
}
