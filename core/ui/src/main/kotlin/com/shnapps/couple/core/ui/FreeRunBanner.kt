package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything is unlocked for now, and when that stops (V2 rule 5).
 *
 * One line with a date on it. It is the opposite of a countdown: a date is a fact somebody can
 * plan around, where "3 days left!" is pressure, and the brief rules the second one out. It says
 * nothing about buying anything — the store is a tap away on its own terms, and a couple who are
 * mid-evening are not the audience for a price.
 */
@Composable
fun FreeRunBanner(untilMillis: Long, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .raised(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small)
            .padding(AfterhoursTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AfterhoursIcons.Spark,
            contentDescription = null,
            tint = AfterhoursTheme.colors.brass,
            modifier = Modifier.size(ICON),
        )
        Text(
            text = "Everything unlocked until ${onDay(untilMillis)}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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

private val ICON = 18.dp
