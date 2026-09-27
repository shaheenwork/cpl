package com.shnapps.couple.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing (DESIGN_PROMPT.md §4.6).
 *
 * The raw steps are still here, because a component sometimes needs a number. What
 * matters more are the **semantic** gaps below them.
 *
 * Every gap in the app used to be [md]. A section heading therefore sat exactly as far
 * from its own content as it did from the section above it, and that one fact is what
 * made every screen look unfinished — more than colour, more than type, more than layout.
 * A heading now gets [section] above it and [tight] below it, so it belongs to what
 * follows rather than floating between two things.
 */
@Immutable
data class Spacing(
    val hairline: Dp = 1.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,

    // --- semantic gaps: what a screen should actually reach for --------------
    /** A label and the thing it labels. */
    val tight: Dp = 4.dp,
    /** Two rows of the same list. */
    val item: Dp = 12.dp,
    /** Two groups inside one section. */
    val group: Dp = 20.dp,
    /** Two sections. */
    val section: Dp = 36.dp,
    /** The bar and the first content under it. */
    val screenTop: Dp = 24.dp,
    /** Inside the bottom action dock. */
    val dock: Dp = 16.dp,

    /** Standard screen side gutter. Do not tighten it. */
    val gutter: Dp = 24.dp,
    /** Minimum touch target (§20). Never shrink a control below this. */
    val touchTarget: Dp = 48.dp,
    /** The height of a screen's top bar. */
    val bar: Dp = 56.dp,
    /** How far the dock's scrim reaches up, so content can scroll under it. */
    val dockScrim: Dp = 96.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }

/**
 * Corner radii (§4.7), on the same 4 dp step as spacing.
 *
 * Sharp corners read as utilitarian and this product should feel upholstered — but not
 * everything should be equally soft, which is what happens when there is only one radius.
 * The hero card is the only thing that gets [Shapes.extraLarge].
 */
val AfterhoursShapes = Shapes(
    /** Chips, badges, progress segments. */
    extraSmall = RoundedCornerShape(8.dp),
    /** Rows and fields. */
    small = RoundedCornerShape(12.dp),
    /** Buttons and sheets. */
    medium = RoundedCornerShape(16.dp),
    /** Raised panels. */
    large = RoundedCornerShape(20.dp),
    /** The hero card, and nothing else. */
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Elevation (§4.2). Declared for three phases and never read; it is the input to
 * `Modifier.raised` now, which is the one place fill, rim and contact shadow are applied
 * together.
 */
@Immutable
data class Elevations(
    val flat: Dp = 0.dp,
    val raised: Dp = 2.dp,
    val floating: Dp = 8.dp,
    val lifted: Dp = 16.dp,
    /** How far a hero card's contact shadow falls. On near-black it reads as weight. */
    val contact: Dp = 24.dp,
)

val LocalElevations = staticCompositionLocalOf { Elevations() }
