package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.engine.Rng
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PromptKind

/** A game as the server started it (BUILD_PROMPT.md §11): ids only. */
data class ServerGame(
    val gameId: String,
    /** Each round's prompt ids: one of each of the game's kinds, in the game's order. */
    val rounds: List<List<String>>,
    /** Released for "Not this one", best first. Never the whole permitted set (§5.5). */
    val alternates: List<String>,
    val effectiveIntensity: Int,
)

/** One round as released: its prompts, or none when this phone cannot play it. */
data class ReleasedRound(val index: Int, val prompts: List<GamePrompt>) {
    val playable: Boolean get() = prompts.isNotEmpty()
}

/**
 * The only prompts a game on this phone may ever show: exactly what the server released for
 * it — its rounds and its alternates (BUILD_PROMPT.md §3.2, §5.5).
 *
 * The constructor is internal, so app code cannot hand the interpreter prompts of its own
 * choosing; the ways in are [fromServer] and [forHeat]. Both drop, as defence in depth, anything
 * that could not have been permitted — another game, not playable together, above the ceiling, not
 * published — even though the server's chokepoint should never release such a thing.
 */
class ReleasedGame internal constructor(
    val game: Game,
    val rounds: List<ReleasedRound>,
    val alternates: List<GamePrompt>,
    val effectiveIntensity: Int,
) {
    companion object {
        /**
         * The game [server] started, its ids resolved through [resolve]. A round missing any of
         * its prompts is kept, empty, so the rounds still line up with the server's: the player
         * passes over it.
         */
        fun fromServer(server: ServerGame, game: Game, resolve: (String) -> GamePrompt?): ReleasedGame {
            require(server.gameId == game.id) { "Rounds of ${server.gameId} cannot play ${game.id}" }
            val kinds = game.promptKinds
            fun usable(prompt: GamePrompt?): GamePrompt? = prompt?.takeIf {
                it.status == ContentStatus.PUBLISHED &&
                    it.game == game.id &&
                    Mode.TOGETHER in it.modes &&
                    it.intensity.level <= server.effectiveIntensity
            }
            val rounds = server.rounds.mapIndexed { index, ids ->
                val prompts = ids.map { usable(resolve(it)) }
                val complete = prompts.all { it != null } &&
                    prompts.size == maxOf(1, kinds.size) &&
                    (kinds.isEmpty() || prompts.map { it?.kind } == kinds)
                ReleasedRound(index, if (complete) prompts.filterNotNull() else emptyList())
            }
            val alternates = server.alternates.distinct().mapNotNull { usable(resolve(it)) }
            return ReleasedGame(game, rounds, alternates, server.effectiveIntensity)
        }

        /**
         * A game on a phone with no account (REDESIGN_PROMPT.md §4.1, D-100).
         *
         * The same reasoning as `InstantDeck`: there is no partner yet, so there is no private
         * document to filter against and nobody whose limits could leak. What bounds this instead
         * is the heat on this phone, the refusals made on it, and the game's own prompts.
         *
         * This is **not** the server's pipeline and does not pretend to be. There is no scoring
         * here, because scoring reads an affinity that only exists once a couple has played
         * together; rounds are filled in the content's own order at each intensity, climbing
         * towards the ceiling the way `engine/SPEC.md` §10 describes, and the draw is seeded from
         * the game and the round so a reopened phone deals the same game (D-047).
         *
         * The moment a couple signs in, `fromServer` is the only way a game starts again.
         */
        fun forHeat(
            game: Game,
            prompts: List<GamePrompt>,
            ceiling: Int,
            refusedThemes: Set<String> = emptySet(),
            seed: Long = 0,
        ): ReleasedGame {
            val kinds = game.promptKinds.ifEmpty { listOf(null) }
            val usable = prompts
                .filter { it.game == game.id && it.status == ContentStatus.PUBLISHED }
                .filter { Mode.TOGETHER in it.modes && it.intensity.level <= ceiling }
                .filterNot { prompt -> prompt.excludedByBoundaries.any { it in refusedThemes } }
                .sortedBy { it.id }

            val used = mutableSetOf<String>()
            val rng = Rng(seed)
            val start = maxOf(1, ceiling - CLIMB_FROM)
            val rounds = (0 until game.rounds).map { index ->
                val target = targetFor(index, game.rounds, start, ceiling)
                val picked = kinds.mapNotNull { kind ->
                    pick(usable, kind, target, used, rng)?.also { used += it.id }
                }
                ReleasedRound(index, if (picked.size == kinds.size) picked else emptyList())
            }
            val alternates = usable.filterNot { it.id in used }.take(ALTERNATES)
            return ReleasedGame(game, rounds, alternates, ceiling)
        }

        /** A climb towards the ceiling rather than a flat evening (SPEC §10, step 3). */
        private fun targetFor(round: Int, rounds: Int, start: Int, ceiling: Int): Int =
            if (rounds <= 1) ceiling else start + ((ceiling - start) * round) / (rounds - 1)

        /**
         * One unused prompt of [kind] at or below [target], preferring the highest intensity
         * available, with the choice among equals drawn from the run's own seed.
         */
        private fun pick(
            prompts: List<GamePrompt>,
            kind: PromptKind?,
            target: Int,
            used: Set<String>,
            rng: Rng,
        ): GamePrompt? {
            val candidates = prompts
                .filter { it.id !in used && (kind == null || it.kind == kind) && it.intensity.level <= target }
            if (candidates.isEmpty()) return null
            val top = candidates.maxOf { it.intensity.level }
            val best = candidates.filter { it.intensity.level == top }
            return best[rng.nextInt(best.size)]
        }

        /** Enough for "Not this one" a few times, never the whole permitted set (§5.5). */
        private const val ALTERNATES = 3

        /** How far below the ceiling a game starts before climbing to it. */
        private const val CLIMB_FROM = 2
    }
}
