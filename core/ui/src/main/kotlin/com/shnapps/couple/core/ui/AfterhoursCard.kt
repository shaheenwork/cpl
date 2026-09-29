package com.shnapps.couple.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shnapps.couple.core.designsystem.component.bloom
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.CARD_MEASURE
import com.shnapps.couple.core.designsystem.theme.CardBodyTextStyle
import com.shnapps.couple.core.designsystem.theme.DisplayFontFamily
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle

/**
 * The hero (DESIGN_PROMPT.md §5.1). One per screen, at most.
 *
 * This is the product — one instruction, on one card, between two people — and it was drawn as
 * a list row: the same fill, the same radius and the same uniform gap as everything else, with
 * the title sitting directly on the body because a card spaced its children the way a list does.
 *
 * What makes it an object rather than a panel:
 *
 *  - The **tier's rim**, so a Mild card and an Extreme card are visibly different things before a
 *    word is read.
 *  - An **index numeral** bled off the corner and clipped by the card — an editorial device that
 *    costs no data and is most of why a page looks composed rather than filled in. It is
 *    decorative, so it is out of the accessibility tree.
 *  - A **drop cap**, when the title is long enough to carry one.
 *  - Its own **internal rhythm**: eyebrow, `tight`, title, `group`, body, `group`, meta. Not one
 *    gap four times.
 *  - A **measure** on the body, so a line stops at about 40 characters instead of running the
 *    full width of the phone.
 */
@Composable
fun AfterhoursCard(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    body: String? = null,
    meta: String? = null,
    index: Int? = null,
    lit: Boolean = true,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val spacing = AfterhoursTheme.spacing
    val tier = AfterhoursTheme.tier
    val shape = MaterialTheme.shapes.extraLarge

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (lit) {
                    Modifier.bloom(color = tier.rim, cornerRadius = spacing.lg, spread = spacing.xl)
                } else {
                    Modifier
                },
            )
            .raised(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = shape,
                elevation = AfterhoursTheme.elevations.floating,
                rimTop = tier.rim.copy(alpha = if (lit) RIM_LIT else RIM_QUIET),
            )
            .clipToBounds(),
    ) {
        if (index != null) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontFamily = DisplayFontFamily,
                    fontSize = NUMERAL,
                    lineHeight = NUMERAL,
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = NUMERAL_ALPHA),
                textAlign = TextAlign.End,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 0.dp)
                    // Bled off the top edge and cut by the card's own corner.
                    .offset(y = NUMERAL_LIFT)
                    .clearAndSetSemantics { },
            )
        }

        Column(modifier = Modifier.padding(spacing.lg)) {
            if (eyebrow != null) {
                Text(text = eyebrow.uppercase(), style = EyebrowTextStyle, color = tier.onDark)
                Box(modifier = Modifier.padding(top = spacing.tight))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (body != null) {
                Text(
                    text = body,
                    style = CardBodyTextStyle,
                    color = AfterhoursTheme.colors.textMuted,
                    modifier = Modifier
                        .padding(top = spacing.group)
                        .widthIn(max = (CARD_MEASURE * CardBodyTextStyle.fontSize.value).dp),
                )
            }
            if (extra != null) {
                Column(
                    modifier = Modifier.padding(top = spacing.group),
                    verticalArrangement = Arrangement.spacedBy(spacing.item),
                    content = extra,
                )
            }
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = AfterhoursTheme.colors.textFaint,
                    modifier = Modifier.padding(top = spacing.group),
                )
            }
        }
    }
}

private val NUMERAL = 128.sp
private val NUMERAL_LIFT = (-40).dp

/** Faint enough to be texture rather than information. */
private const val NUMERAL_ALPHA = 0.045f
private const val RIM_LIT = 0.55f
private const val RIM_QUIET = 0.18f
