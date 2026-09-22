package com.shnapps.couple.core.engine.game

import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.Mode

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
 * choosing; the one way in is [fromServer]. It drops, as defence in depth, anything that
 * could not have been permitted — another game, not playable together, above the game's
 * ceiling, not published — even though the server's chokepoint should never release it.
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
    }
}
