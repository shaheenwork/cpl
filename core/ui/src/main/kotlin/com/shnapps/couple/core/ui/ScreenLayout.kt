package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * A scrolling screen body with the standard gutters. Insets are already applied at the
 * root of the app (DECISIONS.md D-028), so screens add none of their own.
 *
 * Its children are `group` apart rather than `md` (DESIGN_PROMPT.md §4.6). Every gap in the app
 * used to be the same number, which put a section heading exactly as far from its own content as
 * from the section above it — the single strongest reason every screen looked unfinished. A
 * screen that wants a bar and a docked action uses `ScreenScaffold` instead; this is for the ones
 * that are a column and nothing more.
 */
@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.group),
        content = content,
    )
}

/**
 * The eyebrow, headline and optional line of explanation that open a screen.
 *
 * The eyebrow sits `tight` above its headline because it belongs to it; the body sits `item`
 * below, because it is a separate thought about the same thing.
 */
@Composable
fun ScreenHeading(eyebrow: String, headline: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.tight)) {
        Text(text = eyebrow.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.heat.onDark)
        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = AfterhoursTheme.colors.textMuted,
                modifier = Modifier.padding(top = AfterhoursTheme.spacing.sm),
            )
        }
    }
}
