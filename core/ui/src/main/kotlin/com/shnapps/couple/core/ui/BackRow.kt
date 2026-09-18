package com.shnapps.couple.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/** A labelled back control: one target, announced once. */
@Composable
fun BackRow(label: String, onBack: () -> Unit) {
    val spacing = AfterhoursTheme.spacing
    Row(
        modifier = Modifier
            .heightIn(min = spacing.touchTarget)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(end = spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = AfterhoursTheme.colors.textMuted,
        )
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = AfterhoursTheme.colors.textMuted)
    }
}
