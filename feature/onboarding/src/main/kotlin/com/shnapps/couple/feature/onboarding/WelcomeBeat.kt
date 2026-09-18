package com.shnapps.couple.feature.onboarding

/** The four opening beats, verbatim from BUILD_PROMPT.md §14.1. */
internal data class WelcomeBeat(val eyebrow: String, val headline: String, val body: String)

internal val welcomeBeats = listOf(
    WelcomeBeat(
        eyebrow = "YOURS ONLY",
        headline = "This is your private space.",
        body = "Just you two. Nobody else can see any of it.",
    ),
    WelcomeBeat(
        eyebrow = "DISCOVER",
        headline = "Explore what you're both curious about.",
        body = "You answer privately. We only ever tell you what you have in common.",
    ),
    WelcomeBeat(
        eyebrow = "SURPRISE",
        headline = "Find out things you didn't know about each other.",
        body = "The good kind of surprise. On your terms, inside your limits.",
    ),
    WelcomeBeat(
        eyebrow = "TONIGHT",
        headline = "Build nights around your chemistry.",
        body = "And make distance feel a lot less distant.",
    ),
)
