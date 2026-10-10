package com.shnapps.couple.feature.instant

import com.google.common.truth.Truth.assertThat
import com.shnapps.couple.core.data.entitlement.LevelAccess
import com.shnapps.couple.core.data.instant.InstantPlayRepository
import com.shnapps.couple.core.engine.GameNight
import com.shnapps.couple.core.engine.InstantCard
import com.shnapps.couple.core.engine.InstantDeck
import com.shnapps.couple.core.engine.NightLength
import com.shnapps.couple.core.engine.game.ReleasedGame
import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem
import com.shnapps.couple.core.model.ContentPack
import com.shnapps.couple.core.model.ContentStatus
import com.shnapps.couple.core.model.Game
import com.shnapps.couple.core.model.GamePlayers
import com.shnapps.couple.core.model.GamePrompt
import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.InteractionType
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.testing.FakeLaunchOffer
import com.shnapps.couple.core.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

/**
 * The card, and the three things a person can do about it (REDESIGN_PROMPT.md §5.3).
 *
 * The assertions worth having are about what is *not* asked and *not* recorded: a skip records
 * nothing, a refusal is written to this phone alone, and the heat is offered rather than applied.
 */
class InstantPlayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val instant = FakeInstantPlayRepository()
    private val launchOffer = FakeLaunchOffer()

    @Test
    fun `a card is on screen without an account, a partner, or a network`() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `done moves on, and asks nothing`() = runTest {
        val viewModel = viewModel()
        val first = viewModel.uiState.value.card

        viewModel.done()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.card).isNotEqualTo(first)
        assertThat(instant.refusals).isEmpty()
    }

    @Test
    fun `skip records nothing at all`() = runTest {
        val viewModel = viewModel()

        viewModel.skip()
        advanceUntilIdle()

        assertThat(instant.refusals).isEmpty()
        assertThat(viewModel.uiState.value.streak).isEqualTo(0)
    }

    @Test
    fun `five in a row offers to turn it up, and does not turn it up`() = runTest {
        val viewModel = viewModel()

        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isEqualTo(Intensity.SPICY)
        assertThat(instant.tierSet).isNull()
    }

    @Test
    fun `at the top, nothing is offered`() = runTest {
        instant.tierState.value = Intensity.EXTREME
        val viewModel = viewModel()

        repeat(6) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
    }

    @Test
    fun `keeping the tier carries on rather than stopping`() = runTest {
        val viewModel = viewModel()
        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        viewModel.keepTier()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(instant.tierSet).isNull()
    }

    @Test
    fun `too far asks one question and sends nothing yet`() = runTest {
        val viewModel = viewModel()

        viewModel.tooFar()

        assertThat(viewModel.uiState.value.refusing).isNotNull()
        assertThat(instant.refusals).isEmpty()
    }

    @Test
    fun `just this one refuses the card and not its kind`() = runTest {
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card

        viewModel.tooFar()
        viewModel.refuse(alsoItsKind = false)
        advanceUntilIdle()

        assertThat(instant.refusals).containsExactly(card to false)
        assertThat(viewModel.uiState.value.refusing).isNull()
    }

    @Test
    fun `never again refuses everything of its kind`() = runTest {
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card

        viewModel.tooFar()
        viewModel.refuse(alsoItsKind = true)
        advanceUntilIdle()

        assertThat(instant.refusals).containsExactly(card to true)
    }

    @Test
    fun `no clock runs until someone starts one`() = runTest {
        val viewModel = viewModel()
        viewModel.reveal()

        assertThat(viewModel.uiState.value.timer).isNull()

        viewModel.toggleTimer()

        assertThat(viewModel.uiState.value.timer?.remainingSeconds).isEqualTo(5 * 60)
    }

    @Test
    fun `the clock counts down, and stops when it is stopped`() = runTest {
        val viewModel = viewModel()
        viewModel.reveal()
        viewModel.toggleTimer()

        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(3_100)

        assertThat(viewModel.uiState.value.timer?.remainingSeconds).isEqualTo(5 * 60 - 3)

        // The same control: the ring they started is the ring they tap to stop it.
        viewModel.toggleTimer()
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(5_000)

        assertThat(viewModel.uiState.value.timer).isNull()
    }

    @Test
    fun `moving to the next card puts the clock away`() = runTest {
        val viewModel = viewModel()
        viewModel.toggleTimer()

        viewModel.done()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.timer).isNull()
    }

    @Test
    fun `a run of six asks whether to carry on, rather than ending`() = runTest {
        val viewModel = viewModel()

        repeat(4) { viewModel.skip() }
        assertThat(viewModel.uiState.value.runComplete).isFalse()

        repeat(2) { viewModel.skip() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.runComplete).isTrue()
        assertThat(viewModel.uiState.value.position).isEqualTo(6)
    }

    @Test
    fun `keeping going starts the count again and deals on`() = runTest {
        val viewModel = viewModel()
        repeat(6) { viewModel.skip() }
        advanceUntilIdle()
        val seen = instant.seen.map { it.first }

        viewModel.keepGoing()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.runComplete).isFalse()
        assertThat(viewModel.uiState.value.position).isEqualTo(0)
        assertThat(viewModel.uiState.value.played).isEqualTo(6)
        // A second run is a second run: not card seven of one list, and not one they just had.
        assertThat(seen).doesNotContain(viewModel.uiState.value.card!!.id)
    }

    @Test
    fun `carrying on keeps the heat rather than climbing again`() = runTest {
        instant.tierState.value = Intensity.EXTREME
        val viewModel = viewModel()
        val opener = viewModel.uiState.value.card!!.intensity

        repeat(6) { viewModel.skip() }
        advanceUntilIdle()
        viewModel.keepGoing()
        advanceUntilIdle()

        // On the device card seven came back two tiers down, because carrying on reset the deal
        // index and the run climbed from the bottom again. A run's climb is for the start of an
        // evening, and six cards in, the evening has started.
        val seventh = viewModel.uiState.value.card!!.intensity
        assertThat(seventh.level).isGreaterThan(opener.level)
        assertThat(seventh.level).isAtLeast(Intensity.HOT.level)
    }

    @Test
    fun `a card never comes round twice while there is anything left`() = runTest {
        val viewModel = viewModel()

        // The bug this replaces: the deck was dealt from index zero in bundle order on every
        // launch, so an evening only ever reached the first few cards of the first pack (D-124).
        repeat(items.size) {
            viewModel.done()
            viewModel.keepGoing()
            advanceUntilIdle()
        }

        val ids = instant.seen.map { it.first }
        assertThat(ids).containsNoDuplicates()
        assertThat(ids).hasSize(items.size)
    }

    @Test
    fun `both done and skip count as seen, and a refusal does too`() = runTest {
        val viewModel = viewModel()

        viewModel.done()
        advanceUntilIdle()
        viewModel.skip()
        advanceUntilIdle()

        assertThat(instant.seen).hasSize(2)
        // Measured against the deck, so the record can reset once it covers all of it.
        assertThat(instant.seen.map { it.second }.toSet()).containsExactly(items.size)
    }

    @Test
    fun `asking for a lane deals from it, and starts the run again`() = runTest {
        val viewModel = viewModel()
        repeat(3) { viewModel.skip() }
        advanceUntilIdle()

        viewModel.setLane("AFTER_DARK")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lane).isEqualTo("AFTER_DARK")
        assertThat(viewModel.uiState.value.laneLabel).isEqualTo("After dark")
        assertThat(viewModel.uiState.value.position).isEqualTo(0)
        assertThat(viewModel.uiState.value.card!!.pack).isEqualTo("AFTER_DARK")
    }

    @Test
    fun `dealing from everything again is one tap back`() = runTest {
        val viewModel = viewModel()
        viewModel.setLane("AFTER_DARK")
        advanceUntilIdle()

        viewModel.setLane(null)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lane).isNull()
        assertThat(viewModel.uiState.value.laneLabel).isNull()
        assertThat(viewModel.uiState.value.lanes.map { it.id })
            .containsExactly("FLIRT", "AFTER_DARK")
    }

    @Test
    fun `a deck with nothing left says so instead of showing nothing`() = runTest {
        instant.deckState.value = InstantDeck.of(emptyList(), Intensity.MILD)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.card).isNull()
        assertThat(viewModel.uiState.value.loading).isFalse()
    }

    @Test
    fun `the safe word stops everything and asks nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.toggleTimer()

        viewModel.stop()

        assertThat(viewModel.uiState.value.stopped).isTrue()
        // No confirmation, and nothing left running: a couple who reach for this need it now.
        assertThat(viewModel.uiState.value.timer).isNull()
        assertThat(viewModel.uiState.value.playing).isFalse()
        assertThat(viewModel.uiState.value.showsTierPicker).isFalse()
    }

    @Test
    fun `a stop can be carried on from, at the gentlest tier`() = runTest {
        val viewModel = viewModel()
        viewModel.stop()

        viewModel.carryOnGently()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.stopped).isFalse()
        assertThat(instant.tierSet).isEqualTo(Intensity.MILD)
    }

    @Test
    fun `the stop does not refuse the card it interrupted`() = runTest {
        val viewModel = viewModel()
        viewModel.tooFar()

        viewModel.stop()
        advanceUntilIdle()

        // Stopping is not a judgement on anything: it is a stop.
        assertThat(instant.refusals).isEmpty()
        assertThat(viewModel.uiState.value.refusing).isNull()
    }

    @Test
    fun `choosing Extreme is the consent, and it plays straight away`() = runTest {
        instant.tierState.value = Intensity.EXTREME
        val viewModel = viewModel()

        // There used to be a "both of you want this?" screen here. The level is a ceiling the
        // couple chose together; asking again on top of it was a question nobody asked for (D-144).
        assertThat(viewModel.uiState.value.playing).isTrue()
        assertThat(viewModel.uiState.value.card).isNotNull()
    }

    @Test
    fun `a card arrives face down, and only a read card can be timed`() = runTest {
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card!!

        assertThat(viewModel.uiState.value.revealed).isFalse()
        viewModel.toggleTimer()
        assertThat(viewModel.uiState.value.timer).isNull()

        viewModel.reveal()

        assertThat(viewModel.uiState.value.revealed).isTrue()
        assertThat(viewModel.uiState.value.card!!.id).isEqualTo(card.id)
    }

    @Test
    fun `the next card is face down again, without anything having to remember to turn it`() = runTest {
        val viewModel = viewModel()
        viewModel.reveal()

        viewModel.done()
        advanceUntilIdle()

        // The reveal names a card, so a new card is unread by construction.
        assertThat(viewModel.uiState.value.revealed).isFalse()
    }

    @Test
    fun `the heat and the lanes open over the card, and choosing closes them`() = runTest {
        val viewModel = viewModel()

        viewModel.showSheet(DeckSheet.HEAT)
        assertThat(viewModel.uiState.value.sheet).isEqualTo(DeckSheet.HEAT)
        viewModel.turnUp(Intensity.HOT)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.sheet).isNull()

        viewModel.showSheet(DeckSheet.LANES)
        viewModel.showSheet(null)
        assertThat(viewModel.uiState.value.sheet).isNull()
    }

    @Test
    fun `a hot run ends with something written to land on`() = runTest {
        instant.tierState.value = Intensity.HOT
        val viewModel = viewModel()

        repeat(6) { viewModel.skip() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.runComplete).isTrue()
        assertThat(viewModel.uiState.value.aftercare).isNotNull()
        assertThat(viewModel.uiState.value.aftercare!!.aftercare).isTrue()
    }

    @Test
    fun `a mild run does not`() = runTest {
        instant.tierState.value = Intensity.MILD
        val viewModel = viewModel()

        repeat(6) { viewModel.skip() }
        advanceUntilIdle()

        // An evening that never went anywhere does not need landing from.
        assertThat(viewModel.uiState.value.runComplete).isTrue()
        assertThat(viewModel.uiState.value.aftercare).isNull()
    }

    @Test
    fun `a locked level tapped changes nothing, and says what it costs`() = runTest {
        instant.accessState.value = LevelAccess.FREE
        val viewModel = viewModel()
        val card = viewModel.uiState.value.card

        viewModel.tapLocked(Intensity.SPICY)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.lockedLevel).isEqualTo(Intensity.SPICY)
        assertThat(viewModel.uiState.value.lockedPrice).isEqualTo("£3.99")
        // Nothing moved underneath it: not the heat, not the card, not the deal.
        assertThat(instant.tierSet).isNull()
        assertThat(viewModel.uiState.value.card).isEqualTo(card)

        viewModel.dismissSheet()
        assertThat(viewModel.uiState.value.lockedLevel).isNull()
        assertThat(viewModel.uiState.value.card).isEqualTo(card)
    }

    @Test
    fun `the climb never offers a level they do not have`() = runTest {
        instant.accessState.value = LevelAccess.FREE
        instant.tierState.value = Intensity.FLIRTY
        val viewModel = viewModel()

        // Five in a row is when it would normally ask about turning things up. Spicy is the next
        // one up and nobody has bought it, so the question is not asked at all — "shall we turn it
        // up?" followed by a price is the one sales pitch this app must never make.
        repeat(5) { viewModel.done() }
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.offeringHotter).isNull()
        assertThat(viewModel.uiState.value.lockedLevel).isNull()
    }

    @Test
    fun `nothing runs out mid-evening, however long they play`() = runTest {
        instant.accessState.value = LevelAccess.FREE
        val viewModel = viewModel()

        repeat(40) { viewModel.skip() }
        advanceUntilIdle()

        // V1 counted thirty cards a day. V2 sells the top of the range instead, so an evening has
        // no end but theirs: there is always a card, and it is always playable.
        viewModel.keepGoing()
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.card).isNotNull()
        assertThat(viewModel.uiState.value.playing).isTrue()
    }

    private fun viewModel(): InstantPlayViewModel {
        val viewModel = InstantPlayViewModel(instant, launchOffer)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    /** A real object with real state, not a mock: the fakes rule (DECISIONS.md D-013). */
    private class FakeInstantPlayRepository : InstantPlayRepository {
        val deckState = MutableStateFlow(InstantDeck.of(items, Intensity.EXTREME))
        val tierState = MutableStateFlow(Intensity.DEFAULT)

        /** Open, as it is for the first month after install (V2 rule 5). */
        val accessState = MutableStateFlow(LevelAccess.OPEN)

        var tierSet: Intensity? = null
            private set
        val refusals = mutableListOf<Pair<InstantCard, Boolean>>()

        val laneState = MutableStateFlow<String?>(null)
        val playedState = MutableStateFlow(emptySet<String>())
        val packsState = MutableStateFlow(
            listOf(
                ContentPack("FLIRT", "Flirt", "The fun of wanting each other.", 1..5),
                ContentPack("AFTER_DARK", "After dark", "Later, bolder.", 3..5),
            ),
        )

        /** Every card the player said was seen, with the deck size it was measured against. */
        val seen = mutableListOf<Pair<String, Int>>()

        override val deck: Flow<InstantDeck> = deckState
        override val tier: Flow<Intensity> = tierState
        override val access: Flow<LevelAccess> = accessState

        override suspend fun packPrices(): Map<Intensity, String> =
            LevelAccess.PACKS.associateWith { "£${it.level}.99" }
        override val packs: Flow<List<ContentPack>> = packsState
        override val lane: Flow<String?> = laneState
        override val played: Flow<Set<String>> = playedState

        override val mode: Flow<Mode> = MutableStateFlow(Mode.TOGETHER)

        override suspend fun setMode(mode: Mode) = Unit

        override suspend fun setLane(pack: String?) {
            laneState.value = pack
            deckState.value = InstantDeck.of(items, Intensity.EXTREME, pack = pack)
        }

        override suspend fun markPlayed(card: InstantCard, deckSize: Int) {
            seen += card.id to deckSize
            playedState.value = playedState.value + card.id
        }

        /** The card player never asks for names; it is kept here only to satisfy the contract. */
        override val players: Flow<GamePlayers> = MutableStateFlow(GamePlayers.UNNAMED)

        override suspend fun rememberPlayers(players: GamePlayers) = Unit

        override suspend fun setTier(tier: Intensity) {
            tierSet = tier
            tierState.value = tier
        }

        override suspend fun refuse(card: InstantCard, alsoItsKind: Boolean) {
            refusals += card to alsoItsKind
        }

        override suspend fun refusePrompt(prompt: GamePrompt, alsoItsKind: Boolean) = Unit

        // The card player never starts a game; these exist so the fake is the whole repository.
        override val games: Flow<List<Game>> = MutableStateFlow(emptyList())

        // The card player never plans an evening; these exist so the fake is the whole repository.
        override suspend fun planNight(length: NightLength): GameNight =
            GameNight.plan(deckState.value, Intensity.DEFAULT, length)

        override suspend fun startGame(gameId: String, autoHeat: Boolean): ReleasedGame? = null
    }

    private companion object {
        fun item(
            id: String,
            intensity: Intensity,
            pack: String = "FLIRT",
            tags: Set<String> = setOf("flirting"),
        ) = ContentItem(
            id = id,
            version = 1,
            pack = pack,
            category = "communication",
            title = "Card $id",
            subtitle = "",
            body = "Something to do, for $id.",
            tags = tags,
            intensity = intensity,
            modes = setOf(Mode.TOGETHER),
            interactionType = InteractionType.CHALLENGE,
            durationMin = 5,
            moods = emptySet(),
            requiredMutualPreferences = emptySet(),
            boostedByPreferences = emptySet(),
            excludedByBoundaries = setOf("mood_gentle"),
            chapterKinds = setOf(ChapterKind.WARM_UP),
            noveltyWeight = 0.5,
            repeatCooldownDays = 30,
            requiresMedia = null,
            status = ContentStatus.PUBLISHED,
        )

        val items = List(12) { item("card_$it", Intensity.MILD) } +
            List(4) { item("dark_$it", Intensity.EXTREME, pack = "AFTER_DARK") } +
            List(4) { item("hot_$it", Intensity.HOT) } +
            listOf(item("care_1", Intensity.HOT, tags = setOf("aftercare")))
    }
}
