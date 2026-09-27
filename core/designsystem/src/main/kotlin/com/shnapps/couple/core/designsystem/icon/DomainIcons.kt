package com.shnapps.couple.core.designsystem.icon

import androidx.compose.ui.graphics.vector.ImageVector
import com.shnapps.couple.core.model.ChapterReaction
import com.shnapps.couple.core.model.CloseCheckIn
import com.shnapps.couple.core.model.Mood

/**
 * How a domain answer is drawn (DESIGN_PROMPT.md §4.9).
 *
 * `Mood`, `CloseCheckIn` and `ChapterReaction` each used to carry an emoji, which put a
 * presentation decision inside the model and gave the app eight more emoji than the icon
 * set has icons. The id and the label stay in the model, because content is tagged by
 * them; how one is drawn is the design system's business and lives here.
 *
 * A mapping, not a lookup table with a default: adding a case to any of these three enums
 * is a compile error here until somebody decides what it looks like.
 */
fun Mood.icon(): ImageVector = when (this) {
    Mood.ROMANTIC -> AfterhoursIcons.Heart
    Mood.NAUGHTY -> AfterhoursIcons.Flame
    Mood.BOLD -> AfterhoursIcons.Spark
    Mood.UNPREDICTABLE -> AfterhoursIcons.Dice
    Mood.MYSTERIOUS -> AfterhoursIcons.Eye
    Mood.ROLEPLAY -> AfterhoursIcons.People
    Mood.TALK -> AfterhoursIcons.Moon
    Mood.EXPERIMENTAL -> AfterhoursIcons.Spark
}

fun CloseCheckIn.icon(): ImageVector = when (this) {
    CloseCheckIn.GLOWING -> AfterhoursIcons.Spark
    CloseCheckIn.GOOD -> AfterhoursIcons.Heart
    CloseCheckIn.A_BIT_MUCH -> AfterhoursIcons.Moon
    CloseCheckIn.LETS_TALK -> AfterhoursIcons.People
}

fun ChapterReaction.icon(): ImageVector = when (this) {
    ChapterReaction.LOVED -> AfterhoursIcons.Flame
    ChapterReaction.FINE -> AfterhoursIcons.Check
    ChapterReaction.NOT_FOR_US -> AfterhoursIcons.Close
}
