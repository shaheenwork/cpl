package com.shnapps.couple.core.data.content

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.shnapps.couple.core.data.taxonomy.TaxonomyParser
import com.shnapps.couple.core.engine.game.Answer
import com.shnapps.couple.core.engine.game.GameMoment
import com.shnapps.couple.core.engine.game.GameRun
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.engine.game.RoundPrimitive
import com.shnapps.couple.core.engine.game.ServerGame
import com.shnapps.couple.core.engine.game.Verdict
import com.shnapps.couple.core.model.AnswerKind
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrimitive
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.MediaKind
import com.shnapps.couple.core.model.Mood
import com.shnapps.couple.core.model.PromptKind
import com.shnapps.couple.core.model.RevealOutcome
import com.shnapps.couple.core.model.Scoring
import com.shnapps.couple.core.model.StepAudience
import com.shnapps.couple.core.model.TurnOrder
import com.shnapps.couple.core.model.VoteOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/**
 * The shipped bundle, `content/dist/bundle.json`, read the way the app reads it — and held
 * to the strict standard (BUILD_PROMPT.md §9.4).
 *
 * The app's parser is tolerant so that a newer downloaded bundle never breaks an older app.
 * That tolerance must never hide a mistake in the bundle we ship: here nothing may be
 * dropped, no list entry may go unrecognised, and the content vocabulary must match the
 * app's enums exactly. tools/validate-content checks the same files from the authoring
 * side; this is the app's half of that contract.
 *
 * The files are declared inputs of the test task, so editing them re-runs these tests.
 */
class ContentBundleFileTest {

    private val text = File(BUNDLE_PATH)
        .also { check(it.isFile) { "Expected the bundle at ${it.absolutePath}" } }
        .readText()
    private val strict = Json

    // Items carry authoring fields the app has no use for (locale, timestamps).
    private val lenient = Json { ignoreUnknownKeys = true }
    private val raw: BundleDto = strict.decodeFromString(text)
    private val parsed = ContentBundleParser.parse(text)

    @Test
    fun `is in the format this app reads, at the version content json declares`() {
        assertThat(raw.format).isEqualTo(ContentBundleParser.SUPPORTED_FORMAT)
        val declared = strict.decodeFromString<ContentVersionDto>(File(CONTENT_JSON_PATH).readText())
        assertThat(parsed.contentVersion).isEqualTo(declared.contentVersion)
    }

    @Test
    fun `every item is read in full - nothing dropped, nothing unrecognised`() {
        val rawIds = raw.items.map { it.getValue("id").jsonPrimitive.content }
        assertThat(parsed.items.map { it.id }).containsExactlyElementsIn(rawIds)

        raw.items.forEach { element ->
            val dto = lenient.decodeFromJsonElement<ItemDto>(element)
            val item = dto.toContentItem()
            assertWithMessage("${dto.id} is dropped by the app").that(item).isNotNull()
            checkNotNull(item)
            assertWithMessage("${dto.id} modes").that(item.modes.map { it.name }).containsExactlyElementsIn(dto.modes)
            assertWithMessage("${dto.id} moods").that(item.moods.map { it.id }).containsExactlyElementsIn(dto.moods)
            assertWithMessage("${dto.id} chapterKinds").that(item.chapterKinds.map { it.name })
                .containsExactlyElementsIn(dto.chapterKinds)
            assertWithMessage("${dto.id} status").that(item.status).isEqualTo(ContentStatus.PUBLISHED)
            assertWithMessage("${dto.id} exclusions").that(item.excludedByBoundaries).isNotEmpty()
        }
    }

    @Test
    fun `ids are unique`() {
        assertThat(parsed.items.map { it.id }).containsNoDuplicates()
    }

    @Test
    fun `carries the taxonomy the app ships`() {
        val fromBundle = TaxonomyParser.parse(parsed.taxonomyJson)
        val fromSource = TaxonomyParser.parse(File(TAXONOMY_PATH).readText())
        assertThat(fromBundle).isEqualTo(fromSource)
        assertThat(parsed.taxonomyVersion).isEqualTo(fromSource.version)
    }

    @Test
    fun `every pack is readable and every item belongs to one`() {
        val packs = ContentBundleParser.parsePacks(parsed.packsJson)
        assertThat(packs.map { it.id }).containsExactlyElementsIn(raw.packs.map { it.id })
        assertThat(parsed.items.map { it.pack }.toSet()).isEqualTo(packs.map { it.id }.toSet())
        packs.forEach { pack ->
            val levels = parsed.items.filter { it.pack == pack.id }
                .mapNotNull { ContentBundleParser.parseItem(it.json) }
                .map { it.intensity.level }
            assertWithMessage("${pack.id} has items").that(levels).isNotEmpty()
            assertWithMessage("${pack.id} intensities")
                .that(pack.intensityRange.toList())
                .containsAtLeastElementsIn(levels.toSet())
        }
    }

    @Test
    fun `the content vocabulary and the app enums are the same lists`() {
        // content/vocabulary.json is what authors may use; these enums are what the app can
        // play. A value in one and not the other is content the app silently drops, or an
        // app feature no content can ever reach.
        val vocabulary = strict.decodeFromString<VocabularyDto>(File(VOCABULARY_PATH).readText())

        assertThat(vocabulary.moods).containsExactlyElementsIn(Mood.entries.map { it.id }).inOrder()
        assertThat(vocabulary.interactionTypes)
            .containsExactlyElementsIn(InteractionType.entries.map { it.name })
            .inOrder()
        assertThat(vocabulary.chapterKinds).containsExactlyElementsIn(ChapterKind.entries.map { it.name }).inOrder()
        assertThat(vocabulary.media).containsExactlyElementsIn(MediaKind.entries.map { it.name }).inOrder()
        assertThat(vocabulary.statuses).containsExactlyElementsIn(ContentStatus.entries.map { it.id }).inOrder()

        // The games' vocabulary: what a game may be built from, and what the interpreter plays.
        val games = vocabulary.games
        assertThat(games.primitives).containsExactlyElementsIn(GamePrimitive.entries.map { it.name }).inOrder()
        assertThat(games.who).containsExactlyElementsIn(StepAudience.entries.map { it.name }).inOrder()
        assertThat(games.answers).containsExactlyElementsIn(AnswerKind.entries.map { it.name }).inOrder()
        assertThat(games.outcomes).containsExactlyElementsIn(RevealOutcome.entries.map { it.name }).inOrder()
        assertThat(games.turns).containsExactlyElementsIn(TurnOrder.entries.map { it.name }).inOrder()
        assertThat(games.scoring).containsExactlyElementsIn(Scoring.entries.map { it.name }).inOrder()
        assertThat(games.voteOptions).containsExactlyElementsIn(VoteOption.entries.map { it.name }).inOrder()
        assertThat(games.promptKinds).containsExactlyElementsIn(PromptKind.entries.map { it.name }).inOrder()
    }

    @Test
    fun `every game is read in full - nothing dropped`() {
        val games = ContentBundleParser.parseGames(parsed.gamesJson)
        val rawIds = raw.games.map { it.getValue("id").jsonPrimitive.content }
        assertThat(games.map { it.id }).containsExactlyElementsIn(rawIds)
        games.forEach { game ->
            assertWithMessage("${game.id} steps").that(game.steps).isNotEmpty()
            assertWithMessage("${game.id} modes").that(game.modes).isNotEmpty()
        }
    }

    @Test
    fun `every prompt is read in full, belongs to a game, and carries its boundaries`() {
        val games = ContentBundleParser.parseGames(parsed.gamesJson).associateBy { it.id }
        assertThat(parsed.prompts.map { it.id })
            .containsExactlyElementsIn(raw.prompts.map { it.getValue("id").jsonPrimitive.content })
        assertThat(parsed.prompts.map { it.id }).containsNoDuplicates()
        assertThat(parsed.prompts.map { it.id }.toSet().intersect(parsed.items.map { it.id }.toSet())).isEmpty()
        parsed.prompts.forEach { stored ->
            val prompt = ContentBundleParser.parsePrompt(stored.json)
            assertWithMessage("${stored.id} is dropped by the app").that(prompt).isNotNull()
            checkNotNull(prompt)
            val game = games[prompt.game]
            assertWithMessage("${stored.id} names a game the bundle has").that(game).isNotNull()
            checkNotNull(game)
            assertWithMessage("${stored.id} status").that(prompt.status).isEqualTo(ContentStatus.PUBLISHED)
            assertWithMessage("${stored.id} exclusions").that(prompt.excludedByBoundaries).isNotEmpty()
            if (game.promptKinds.isEmpty()) {
                assertWithMessage("${stored.id} kind").that(prompt.kind).isNull()
            } else {
                assertWithMessage("${stored.id} kind").that(game.promptKinds).contains(prompt.kind)
            }
        }
    }

    @Test
    fun `every shipped game plays from its first round to its last through the interpreter`() {
        val prompts = parsed.prompts.mapNotNull { ContentBundleParser.parsePrompt(it.json) }
        ContentBundleParser.parseGames(parsed.gamesJson).forEach { game ->
            val run = play(GameRun.start(released(game, prompts.filter { it.game == game.id }), PLAYERS))
            assertWithMessage("${game.id} finished").that(run.moment).isInstanceOf(GameMoment.Finished::class.java)
            val verdicts = run.results.map { it.verdict }
            when (game.scoring) {
                Scoring.NONE -> assertWithMessage("${game.id} scores nothing").that(verdicts).isEmpty()
                Scoring.MATCHES -> assertWithMessage("${game.id} compares every round")
                    .that(verdicts.all { it is Verdict.Matched }).isTrue()
                Scoring.POINTS -> assertWithMessage("${game.id} awards points")
                    .that(verdicts.all { it is Verdict.Point }).isTrue()
            }
            if (game.scoring != Scoring.NONE) {
                assertWithMessage("${game.id} reveals every round").that(verdicts).hasSize(game.rounds)
            }
        }
    }

    /** The game as the server could have started it: the first prompts of each kind, by id. */
    private fun released(game: Game, prompts: List<GamePrompt>): ReleasedGame {
        val kinds: List<PromptKind?> = game.promptKinds.ifEmpty { listOf(null) }
        val byKind = kinds.associateWith { kind -> prompts.filter { it.kind == kind }.sortedBy { it.id } }
        val rounds = (0 until game.rounds).map { round -> kinds.map { byKind.getValue(it)[round].id } }
        val byId = prompts.associateBy { it.id }
        return ReleasedGame.fromServer(
            ServerGame(game.id, rounds, emptyList(), effectiveIntensity = 5),
            game,
        ) { byId[it] }
    }

    /** Plays [start] to the end, answering every turn the way its contract asks. */
    private fun play(start: GameRun): GameRun {
        var run = start
        repeat(MAX_MOVES) {
            run = when (val moment = run.moment) {
                is GameMoment.HandOff, is GameMoment.Gather -> run.proceed()
                is GameMoment.RoundOver -> {
                    assertThat(moment.playedPromptId).isNotNull()
                    run.nextRound()
                }
                is GameMoment.Finished -> return run
                is GameMoment.Turn -> {
                    val answered = run.submit(answerFor(moment.primitive))
                    checkNotNull(answered) { "${moment.primitive} refused" }
                }
            }
        }
        error("${start.game.id} did not finish in $MAX_MOVES moves")
    }

    private fun answerFor(primitive: RoundPrimitive): Answer = when (primitive) {
        is RoundPrimitive.Question -> when (primitive.answer) {
            AnswerKind.SPOKEN -> Answer.Done
            AnswerKind.TEXT -> Answer.Text("An answer")
            AnswerKind.STATEMENTS -> Answer.Statements(listOf("One", "Two", "Three"), lie = 1)
        }
        is RoundPrimitive.Choice -> Answer.Picked(primitive.options.first().id)
        is RoundPrimitive.Prediction -> primitive.options
            ?.let { Answer.Picked(it.first().id) }
            ?: Answer.Text("A guess")
        is RoundPrimitive.Vote -> Answer.Picked(primitive.options.first().id)
        is RoundPrimitive.Reveal ->
            if (primitive.verdict == Verdict.AwaitingJudge) Answer.Judged(close = true) else Answer.Done
        is RoundPrimitive.Timer, is RoundPrimitive.Challenge -> Answer.Done
        is RoundPrimitive.Reaction -> Answer.Reacted(ChapterReaction.LOVED)
    }

    private companion object {
        // Test tasks run with the module directory (core/data) as the working directory.
        const val BUNDLE_PATH = "../../content/dist/bundle.json"
        const val TAXONOMY_PATH = "../../content/taxonomy.json"
        const val VOCABULARY_PATH = "../../content/vocabulary.json"
        const val CONTENT_JSON_PATH = "../../content/content.json"
        const val MAX_MOVES = 1_000
        val PLAYERS = GamePlayers("Alex", "Sam")
    }
}

@Serializable
private data class ContentVersionDto(val description: String, val contentVersion: Int)

@Serializable
private data class VocabularyDto(
    val description: String,
    val moods: List<String>,
    val interactionTypes: List<String>,
    val chapterKinds: List<String>,
    val media: List<String>,
    val statuses: List<String>,
    val tags: List<String>,
    val boundaryKeywords: List<BoundaryKeywordDto>,
    val games: GamesVocabularyDto,
)

@Serializable
private data class GamesVocabularyDto(
    val primitives: List<String>,
    val who: List<String>,
    val answers: List<String>,
    val outcomes: List<String>,
    val turns: List<String>,
    val scoring: List<String>,
    val voteOptions: List<String>,
    val promptKinds: List<String>,
)

@Serializable
private data class BoundaryKeywordDto(val pattern: String, val themes: List<String>)
