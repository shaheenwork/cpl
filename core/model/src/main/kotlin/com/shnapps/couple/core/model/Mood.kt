package com.shnapps.couple.core.model

/**
 * The moods a couple can steer a night towards (BUILD_PROMPT.md §14.5).
 *
 * These are selection inputs to the experience engine, not content categories — a night
 * can be built from several at once, and the engine weights candidates by how well they
 * match (§10.3).
 *
 * How a mood is drawn is the design system's business, not the model's: the icon for one
 * is `AfterhoursIcons`' (DESIGN_PROMPT.md §4.9). What lives here is the id, because it has
 * to stay in lockstep with the mood tags used by content.
 */
enum class Mood(val id: String, val label: String) {
    ROMANTIC("romantic", "Romantic"),
    NAUGHTY("naughty", "Naughty"),
    BOLD("bold", "Bold"),
    UNPREDICTABLE("unpredictable", "Unpredictable"),
    MYSTERIOUS("mysterious", "Mysterious"),
    ROLEPLAY("roleplay", "Roleplay"),
    TALK("talk", "Talk"),
    EXPERIMENTAL("experimental", "Experimental"),
    ;

    companion object {
        fun fromId(id: String): Mood? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How adventurous the couple wants the selection to be (§14.5).
 *
 * Feeds the novelty weighting and the repeat cooldown in the engine (§10.3).
 */
enum class Novelty(val label: String) {
    FAMILIAR("Familiar"),
    MIXED("Mixed"),
    SURPRISE_ME("Surprise me"),
}

/** Who is in charge tonight (§14.5). */
enum class Leadership(val label: String) {
    ME("Me"),
    YOU("You"),
    SWITCH("Switch"),
    RANDOM("Random"),
}
