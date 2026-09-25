package com.shnapps.couple.core.model

/** What can be reported (BUILD_PROMPT.md §3.5): something the app wrote, or something they sent. */
enum class ReportTarget { CONTENT, MEDIA }

/**
 * Why (§3.5). Four reasons, in the user's words — and no free-text box: a report is read by a
 * stranger doing moderation, and this is not a screen to invite someone to write on.
 */
enum class ReportReason(val label: String, val detail: String) {
    BROKEN("Something's broken", "It didn't work, or it made no sense."),
    INAPPROPRIATE("Not appropriate", "It shouldn't be in this app at all."),
    UNSAFE("This feels unsafe", "It pushed at something it shouldn't have."),
    TECHNICAL("A technical problem", "Something went wrong with the app itself."),
}
