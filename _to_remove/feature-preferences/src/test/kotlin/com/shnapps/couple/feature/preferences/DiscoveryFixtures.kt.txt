package com.shnapps.couple.feature.preferences

import com.shnapps.couple.core.model.Intensity
import com.shnapps.couple.core.model.Mode
import com.shnapps.couple.core.model.PreferenceAnswer
import com.shnapps.couple.core.model.PreferenceItem
import com.shnapps.couple.core.model.PreferenceValue

/** Screen states with real taxonomy copy, so the goldens show what users will read. */
internal object DiscoveryFixtures {

    val followInstructions = item(
        id = "power_follow_instructions",
        prompt = "Following instructions",
        description = "Being told what to do next, within limits you both set.",
        floor = Intensity.NAUGHTY,
        categoryId = "power",
    )
    val romantic = item(
        id = "mood_romantic",
        prompt = "Romantic nights",
        description = "Candlelight energy. Slow, warm, all about each other.",
        floor = Intensity.SOFT,
        categoryId = "mood",
    )
    val slow = item(
        id = "mood_slow",
        prompt = "Slow and unhurried",
        description = "Nowhere to be. Taking the long way round.",
        floor = Intensity.SOFT,
        categoryId = "mood",
    )
    val blindfold = item(
        id = "sensory_blindfold",
        prompt = "Blindfolded",
        description = "Taking away sight, so everything else is sharper.",
        floor = Intensity.NAUGHTY,
        categoryId = "sensory",
    )

    val intro = PreferenceDiscoveryUiState(
        stage = DiscoveryStage.Intro,
        contentLevel = Intensity.NAUGHTY,
        remainingCount = 38,
        answeredCount = 4,
    )

    fun answering(position: Int = 7, answer: PreferenceAnswer? = null) = PreferenceDiscoveryUiState(
        stage = DiscoveryStage.Answering,
        contentLevel = Intensity.NAUGHTY,
        card = DiscoveryCard(followInstructions, "Power & dynamics", answer),
        position = position,
        deckSize = 38,
    )

    val done = PreferenceDiscoveryUiState(
        stage = DiscoveryStage.Done,
        remainingCount = 0,
        answeredCount = 38,
        answeredThisSession = 34,
    )

    val review = PreferenceDiscoveryUiState(
        stage = DiscoveryStage.Review,
        answeredCount = 4,
        review = listOf(
            ReviewSection(
                categoryTitle = "Mood",
                entries = listOf(
                    ReviewEntry(romantic, PreferenceAnswer(PreferenceValue.YES)),
                    ReviewEntry(slow, PreferenceAnswer(PreferenceValue.MAYBE)),
                ),
            ),
            ReviewSection(
                categoryTitle = "Power & dynamics",
                entries = listOf(ReviewEntry(followInstructions, PreferenceAnswer.SECRETLY_CURIOUS)),
            ),
            ReviewSection(
                categoryTitle = "Senses",
                entries = listOf(ReviewEntry(blindfold, PreferenceAnswer(PreferenceValue.NOT_FOR_ME))),
            ),
        ),
    )

    val editing = PreferenceDiscoveryUiState(
        stage = DiscoveryStage.Editing,
        card = DiscoveryCard(blindfold, "Senses", PreferenceAnswer(PreferenceValue.NOT_FOR_ME)),
    )

    private fun item(id: String, prompt: String, description: String, floor: Intensity, categoryId: String) =
        PreferenceItem(
            id = id,
            prompt = prompt,
            description = description,
            intensityFloor = floor,
            modes = setOf(Mode.TOGETHER, Mode.APART),
            categoryId = categoryId,
            themeId = id,
        )
}
