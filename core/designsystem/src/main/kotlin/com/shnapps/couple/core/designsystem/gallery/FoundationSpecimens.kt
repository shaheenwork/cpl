package com.shnapps.couple.core.designsystem.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shnapps.couple.core.designsystem.component.bloom
import com.shnapps.couple.core.designsystem.component.raised
import com.shnapps.couple.core.designsystem.component.rim
import com.shnapps.couple.core.designsystem.icon.AfterhoursIcons
import com.shnapps.couple.core.designsystem.theme.AfterhoursTheme
import com.shnapps.couple.core.designsystem.theme.CardBodyTextStyle
import com.shnapps.couple.core.designsystem.theme.EyebrowTextStyle
import com.shnapps.couple.core.designsystem.theme.NumeralTextStyle
import com.shnapps.couple.core.designsystem.theme.ProvideTier
import com.shnapps.couple.core.model.Intensity

/**
 * The foundations, as specimens (DESIGN_PROMPT.md §8, D1).
 *
 * These are the goldens that hold the rebuild. Three quarters of what read as "prototype"
 * in this app was light, space and type rather than layout, and none of it is visible in a
 * screen screenshot — a wrong gap looks like a gap. Here it is on its own, at size, so a
 * regression in any of it is an image diff rather than a feeling.
 */

/** §4.2 — three grounds, about seven percent of luminance apart. */
@Composable
internal fun SurfaceSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
        Ground("scene", MaterialTheme.colorScheme.surfaceContainerLowest, Modifier.weight(1f))
        Ground("raised", MaterialTheme.colorScheme.surfaceContainer, Modifier.weight(1f))
        Ground("card", MaterialTheme.colorScheme.surfaceContainerHigh, Modifier.weight(1f))
    }
}

@Composable
private fun Ground(name: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(GROUND)
            .background(color, MaterialTheme.shapes.small),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = AfterhoursTheme.colors.textFaint,
            modifier = Modifier.padding(AfterhoursTheme.spacing.sm),
        )
    }
}

/**
 * §4.2 — the rim light, beside the uniform hairline it replaces.
 *
 * The left panel is what every surface in the app used to be. The right one is the same
 * panel with a gradient border, brighter along the top edge. That is the whole difference
 * between a panel and an object, and it is one modifier.
 */
@Composable
internal fun RimSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PANEL)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .rim(
                    shape = MaterialTheme.shapes.medium,
                    top = AfterhoursTheme.colors.edgeFaint,
                    bottom = AfterhoursTheme.colors.edgeFaint,
                ),
            contentAlignment = Alignment.Center,
        ) { Caption("uniform hairline") }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PANEL)
                .raised(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) { Caption("rim light") }
    }
}

/** §4.3 — one bloomed element, and the weight under a card. */
@Composable
internal fun LightSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing.lg),
        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PANEL)
                .bloom(AfterhoursTheme.tier.rim, cornerRadius = spacing.md, spread = spacing.xl)
                .raised(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) { Caption("bloom") }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(PANEL)
                .raised(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    elevation = AfterhoursTheme.elevations.floating,
                ),
            contentAlignment = Alignment.Center,
        ) { Caption("weight") }
    }
}

/**
 * §4.4 — how far tonight goes, as a colour.
 *
 * All three heats used to be the same pink, so the one thing this product is actually
 * about was invisible. Hot goes deeper and redder rather than brighter: escalation here
 * is intimacy, not volume.
 */
@Composable
internal fun TierSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), modifier = Modifier.fillMaxWidth()) {
        Intensity.entries.forEach { tier ->
            ProvideTier(tier) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(AfterhoursTheme.tier.container, MaterialTheme.shapes.small)
                        .rim(MaterialTheme.shapes.small, top = AfterhoursTheme.tier.rim.copy(alpha = RIM_SAMPLE))
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.tight),
                ) {
                    Text(
                        text = tier.dots,
                        style = MaterialTheme.typography.labelMedium,
                        color = AfterhoursTheme.tier.onDark,
                    )
                    Text(
                        text = tier.label,
                        style = MaterialTheme.typography.titleMedium,
                        color = AfterhoursTheme.tier.onDark,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SWATCH)
                            .background(AfterhoursTheme.tier.accent, MaterialTheme.shapes.extraSmall),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.labelMedium,
                            color = AfterhoursTheme.tier.onAccent,
                        )
                    }
                }
            }
        }
    }
}

/**
 * §4.6 — rhythm, beside the one gap it replaces.
 *
 * Every gap in the app used to be `spacing.md`, which put a section heading exactly as far
 * from its own content as from the section above it. The left column is that. The right one
 * is `section` above a heading and `tight` below it. It is the single highest-value change
 * in the rebuild and the hardest to see in a screen.
 */
@Composable
internal fun RhythmSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Caption("one gap")
            RhythmHeading("Tonight")
            RhythmBar()
            RhythmBar()
            RhythmHeading("Apart")
            RhythmBar()
            RhythmBar()
        }
        Column(modifier = Modifier.weight(1f)) {
            Caption("semantic")
            Column(modifier = Modifier.padding(top = spacing.group)) {
                RhythmHeading("Tonight")
                Column(
                    modifier = Modifier.padding(top = spacing.tight),
                    verticalArrangement = Arrangement.spacedBy(spacing.item),
                ) {
                    RhythmBar()
                    RhythmBar()
                }
                Column(modifier = Modifier.padding(top = spacing.section)) {
                    RhythmHeading("Apart")
                    Column(
                        modifier = Modifier.padding(top = spacing.tight),
                        verticalArrangement = Arrangement.spacedBy(spacing.item),
                    ) {
                        RhythmBar()
                        RhythmBar()
                    }
                }
            }
        }
    }
}

@Composable
private fun RhythmHeading(text: String) {
    Text(text = text.uppercase(), style = EyebrowTextStyle, color = AfterhoursTheme.tier.onDark)
}

@Composable
private fun RhythmBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR)
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.extraSmall),
    )
}

/** §4.5 — the styles that were missing: a card's body, and anything that counts. */
@Composable
internal fun MeasureSpecimen() {
    val spacing = AfterhoursTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.group), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Sit at opposite sides of the room and talk only by message. Nobody moves " +
                "until someone gives in.",
            style = CardBodyTextStyle,
            color = AfterhoursTheme.colors.textMuted,
            modifier = Modifier.width(MEASURE),
        )
        Text(text = "04:38 · 2 of 6", style = NumeralTextStyle, color = AfterhoursTheme.tier.onDark)
    }
}

/**
 * §4.9 — the icon set, to one specification: 24 dp box, 1.5 dp stroke, no fills.
 *
 * Wrapped rather than scrolled: a row that scrolls captures the eight icons that happen to fit,
 * which is a golden that cannot catch a broken path in the other eighteen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconSpecimen() {
    val spacing = AfterhoursTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        modifier = Modifier.fillMaxWidth(),
    ) {
        AfterhoursIcons.all.forEach { (name, vector) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.width(ICON_CELL),
            ) {
                Icon(
                    imageVector = vector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(ICON),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = AfterhoursTheme.colors.textFaint,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = AfterhoursTheme.colors.textFaint)
}

private val GROUND = 72.dp
private val PANEL = 84.dp
private val SWATCH = 28.dp
private val BAR = 30.dp
private val ICON = 24.dp
private val ICON_CELL = 56.dp
private val MEASURE = 280.dp

/** Enough rim for a heat to be told apart at a glance, without drawing a stroke. */
private const val RIM_SAMPLE = 0.5f
