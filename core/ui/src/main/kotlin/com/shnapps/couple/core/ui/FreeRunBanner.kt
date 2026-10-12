package com.shnapps.couple.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything is open for now, and the day that stops (V2 rule 5, D-151).
 *
 * A date, a sentence about what happens after it, and nothing else: no countdown, no colour that
 * gets more urgent. A date is a fact somebody can plan around, where "3 days left!" is pressure,
 * and the brief rules the second one out. It says what stays free, because that is the thing a
 * couple wants to know on the day they read it, and it says nothing about a price: the store is a
 * tap away on its own terms when [onClick] is given, and a couple mid-evening are not the
 * audience for one.
 *
 * Gold rather than the heat's colour: it is about the app, not about tonight.
 */
@Composable
fun FreeRunBanner(untilMillis: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val spacing = AfterhoursTheme.spacing
    val gold = AfterhoursTheme.colors.brass
    val shape = MaterialTheme.shapes.medium
    val date = onDay(untilMillis)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AfterhoursTheme.colors.brassContainer)
            .border(spacing.hairline, gold.copy(alpha = EDGE), shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                contentDescription = "Everything is open until $date. After that, Mild and Flirty stay free " +
                    "and the other levels are one-time packs."
            }
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = AfterhoursIcons.Spark)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(
                text = "Everything is open until $date",
                style = MaterialTheme.typography.titleSmall,
                color = AfterhoursTheme.colors.onBrassContainer,
            )
            Text(
                text = "Then Mild and Flirty stay free, and the rest are one-time packs.",
                style = MaterialTheme.typography.bodySmall,
                color = AfterhoursTheme.colors.onBrassContainer,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = AfterhoursIcons.Forward,
                contentDescription = null,
                tint = gold,
                modifier = Modifier.size(CHEVRON),
            )
        }
    }
}

/**
 * A date as somebody would say it: "14 November".
 *
 * The phone's own zone, because the only person reading it is holding the phone. No year: a month
 * from now is never a year away, and the extra number makes it read like a contract.
 */
fun onDay(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMMM")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private val CHEVRON = 16.dp
private const val EDGE = 0.4f
