package com.shnapps.couple.core.model

/**
 * The eight things a limits profile asks about.
 *
 * The content is authored against twenty-seven taxonomy themes — `sensory_blindfold`,
 * `power_yielding`, `comm_confessions` and so on — and every item and prompt carries the ones it
 * belongs to in `excludedByBoundaries`. Those themes are right for authoring and wrong for asking:
 * twenty-seven questions is not a profile a couple fills in, it is a form they abandon.
 *
 * So the profile asks eight, and each one **owns** a set of themes. Saying no to power play writes
 * a limit on all five of its themes at once; reading the profile back takes the strictest answer
 * any of a tag's themes holds. The themes stay the unit the engine filters on, which means the
 * server's chokepoint (§3.2) and all 1,431 authored entries are untouched by this.
 *
 * The ownership is a **partition**: no theme belongs to two tags. Sharing one would mean a no on
 * teasing quietly answered the power play question too, and a profile that changes answers the
 * person did not give is worse than a longer one. `LimitTagTest` holds it.
 *
 * Three themes belong to no tag on purpose — `mood_gentle`, `mood_charged` and
 * `mood_unpredictable`. They describe how an evening feels rather than what happens in it, and the
 * thing that governs that is the tier. Ninety of the 1,431 entries carry only a mood, and for
 * those the tier is the whole gate, which is what a tier is for.
 */
enum class LimitTag(
    val label: String,
    /** One line, in the second person, for the row in the profile. */
    val asks: String,
    val themes: Set<String>,
) {
    KISSING(
        label = "Kissing",
        asks = "Kissing, and flirting that leads there",
        themes = setOf("comm_flirting"),
    ),
    TOUCH(
        label = "Touch",
        asks = "Hands, massage, warm and cool",
        themes = setOf("sensory_touch", "sensory_temperature"),
    ),
    TEASING(
        label = "Teasing",
        asks = "Being made to wait, and games about it",
        themes = setOf("teasing_words", "teasing_anticipation", "teasing_games"),
    ),
    ROLE_PLAY(
        label = "Role-play",
        asks = "Other names, other stories, characters",
        themes = setOf("roleplay_strangers", "roleplay_characters", "roleplay_mystery_date", "roleplay_power"),
    ),
    SENSORY(
        label = "Sensory",
        asks = "Light, sound, scent, blindfolds",
        themes = setOf("sensory_atmosphere", "sensory_blindfold"),
    ),
    VERBAL(
        label = "Verbal",
        asks = "Saying it out loud, confessions, voice notes",
        themes = setOf("comm_confessions", "comm_voice"),
    ),
    POWER_PLAY(
        label = "Power play",
        asks = "Who leads, who follows, instructions",
        themes = setOf(
            "power_leading", "power_yielding", "power_switching", "power_negotiated",
            "teasing_instructions",
        ),
    ),
    FANTASY(
        label = "Fantasy",
        asks = "Imagining it, and trying something new",
        themes = setOf(
            "comm_fantasies", "explore_novelty", "explore_surprise", "explore_trust",
            "explore_shared_curiosity",
        ),
    ),
    ;

    companion object {
        /**
         * Themes no tag owns, because a tier governs them rather than a limit.
         *
         * Named here rather than left implicit so that a new mood added to the taxonomy has to be
         * decided about — `LimitTagTest` fails on a theme that is in neither list.
         */
        val GOVERNED_BY_TIER: Set<String> = setOf("mood_gentle", "mood_charged", "mood_unpredictable")

        /** Every theme any tag owns. */
        val OWNED: Set<String> = entries.flatMapTo(mutableSetOf()) { it.themes }

        /** The tag that owns [theme], or null when the tier governs it. */
        fun owning(theme: String): LimitTag? = entries.firstOrNull { theme in it.themes }
    }
}

/**
 * What one partner said about one tag (the brief's Yes / Maybe / No).
 *
 * Kept separate from [BoundaryLevel], which has five values and is what the *documents* hold: this
 * is the question a person is asked, and [asBoundary] is how the answer is written down. A profile
 * that offered five answers would be the twenty-seven-row form again in a different shape.
 */
enum class LimitAnswer(val label: String) {
    YES("Yes"),
    MAYBE("Maybe"),
    NO("No"),
    ;

    /** How this answer is stored on each of the tag's themes. */
    fun asBoundary(): BoundaryLevel = when (this) {
        YES -> BoundaryLevel.ALWAYS_OK
        MAYBE -> BoundaryLevel.CURIOUS
        NO -> BoundaryLevel.NEVER
    }

    companion object {
        /**
         * What a tag reads as, given the levels its themes hold.
         *
         * The strictest wins: a tag is a no if any theme under it is, because the tag is only a
         * way of asking and the theme is the thing that filters. Null when nothing is set, which
         * is a real state — a profile is optional and most couples will never open it.
         */
        fun of(levels: Collection<BoundaryLevel>): LimitAnswer? = when {
            levels.isEmpty() -> null
            levels.any { it == BoundaryLevel.NEVER || it == BoundaryLevel.NOT_TONIGHT } -> NO
            levels.any { it == BoundaryLevel.CURIOUS || it == BoundaryLevel.ASK_FIRST } -> MAYBE
            else -> YES
        }
    }
}
