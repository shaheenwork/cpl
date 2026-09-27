package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.component.CinematicCard
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * One thing you can choose, in the one style everything choosable uses.
 *
 * A game on home, a game in the list, an evening to plan, apart, the library: they were four card
 * styles and three button styles, which is the reason a person could not tell what any screen wanted
 * of them. This is the row: what it is, what it does to an evening, and what tapping it will do.
 *
 * [trailing] is a word, not a button — the whole row is the target, so a screen reader hears one
 * thing and a thumb has the full width. [locked] greys it and replaces the trailing word: a row
 * that needs an account still shows, and still says why, rather than being hidden until later.
 */
@Composable
fun ChoiceRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    locked: Boolean = false,
) {
    val colors = AfterhoursTheme.colors
    val words = listOfNotNull(title, subtitle, trailing?.takeIf { !locked }, NEEDS_ACCOUNT.takeIf { locked })

    CinematicCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentDescription = words.joinToString(". "),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AfterhoursTheme.spacing.touchTarget),
            horizontalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (locked) colors.textMuted else MaterialTheme.colorScheme.onSurface,
                )
                subtitle?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted)
                }
            }
            Text(
                text = (if (locked) NEEDS_ACCOUNT else trailing ?: PLAY).uppercase(),
                style = EyebrowTextStyle,
                color = if (locked) colors.textFaint else colors.brass,
            )
        }
    }
}

/** What a row that needs a partner or an account says instead of what it would have done. */
private const val NEEDS_ACCOUNT = "Set up"

/** What a row does when nobody said otherwise. Most of them are games. */
private const val PLAY = "Play"
