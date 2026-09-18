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
 */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.gutter, vertical = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        content = content,
    )
}

/** The eyebrow, headline and optional line of explanation that open a screen. */
@Composable
fun ScreenHeading(eyebrow: String, headline: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AfterhoursTheme.spacing.sm)) {
        Text(text = eyebrow.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.colors.brass)
        Text(
            text = headline,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Text(text = body, style = MaterialTheme.typography.bodyLarge, color = AfterhoursTheme.colors.textMuted)
        }
    }
}
