package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme

/**
 * Who is passing the phone (D-121).
 *
 * Asked once, at the first hand-off, and never again. It is not a setup form: a form before a game
 * is exactly what the rebuild removed, and this appears only at the moment the app is about to say
 * a name out loud — which is the first moment a name is worth anything.
 *
 * Skipping is a real answer. Without names a vote stays relative and every line that would have
 * said a name says something true instead ("Pass the phone over"), so nothing here is a wall.
 *
 * The names never leave the phone. They are not sent, not synced, and not part of anything the
 * server sees — they exist so the screen can say "Over to Sam" instead of "Over to them".
 */
@Composable
fun WhoIsPlaying(
    first: String,
    second: String,
    onFirst: (String) -> Unit,
    onSecond: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AfterhoursTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.item),
    ) {
        ScreenHeading(
            eyebrow = "Before you pass it",
            headline = "Who's playing?",
            body = "So the phone can say whose turn it is. It stays on this phone, and you can " +
                "skip it — nothing needs a name.",
        )
        Name(value = first, onValue = onFirst, label = "Whoever started", last = false)
        Name(value = second, onValue = onSecond, label = "The other one", last = true)
    }
}

@Composable
private fun Name(value: String, onValue: (String) -> Unit, label: String, last: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.take(MAX_NAME)) },
        label = { Text(text = label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AfterhoursTheme.tier.accent,
            unfocusedBorderColor = AfterhoursTheme.colors.edgeFaint,
            focusedLabelColor = AfterhoursTheme.tier.onDark,
            unfocusedLabelColor = AfterhoursTheme.colors.textFaint,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AfterhoursTheme.spacing.touchTarget),
    )
}

/**
 * Both given, and different.
 *
 * Two people called the same thing is worse than two people called nothing: "Over to Sam" would be
 * true of either of them, which is the bug this whole change exists to fix.
 */
fun namesReady(first: String, second: String): Boolean =
    first.isNotBlank() && second.isNotBlank() && !first.trim().equals(second.trim(), ignoreCase = true)

private const val MAX_NAME = 24
