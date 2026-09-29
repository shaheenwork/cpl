package com.shnapps.couple.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.shnapps.couple.core.model.Intensity

/**
 * The raw palette (BUILD_PROMPT.md §15.1, DESIGN_PROMPT.md §4.2 and §4.4).
 *
 * Deep near-black grounds — never pure #000, which reads as cheap OLED rather than as a
 * dim room — warmed with a trace of red so the whole surface stack feels lit by
 * candlelight rather than by a monitor. Text is warm cream, never white.
 *
 * Reference: a luxury after-hours lounge. Explicitly NOT pink gradients, hearts, cartoon
 * flames or anything from an adult novelty store.
 *
 * Every foreground/background pairing used by [afterhoursColorScheme] and by [tierColors]
 * is checked against WCAG AA by `ContrastTest`, so these values are verified rather than
 * eyeballed.
 */
internal object Palette {

    // --- Grounds (§4.2) ------------------------------------------------------
    // Three levels, each about seven percent of luminance apart. The five that came
    // before were under five percent apart in total, which meant the app had one surface
    // colour and nothing could be told from anything else.
    /** The room. One per screen, under the scene's light and grain. */
    val Scene = Color(0xFF0B0708)

    /** Rows, chips, fields, sheets. Everything a list is made of. */
    val Raised = Color(0xFF171115)

    /** The hero card, and only the hero card. */
    val Card = Color(0xFF1F1820)

    // --- Burgundy: HOT, and the app's default accent ------------------------
    val Burgundy = Color(0xFFC75C7A)
    val BurgundyDeep = Color(0xFF8E2A47)
    val BurgundyContainer = Color(0xFF4A1426)
    val OnBurgundyContainer = Color(0xFFFFD9E1)

    /**
     * Burgundy as *text* on a raised or card ground.
     *
     * The fill tone fails AA at 4.29:1 on [Card]; this is the same hue lifted until it
     * passes. Contrast is not a thing to eyeball on a near-black stack — `ContrastTest`
     * holds this pairing.
     */
    val BurgundyBright = Color(0xFFDE8AA1)

    // --- Oxblood: HOT --------------------------------------------------------
    // Hot goes deeper and redder rather than brighter, because escalation in this product is
    // intimacy, not volume. Oxblood is too dark to carry dark text, so its filled state takes
    // cream instead — which is what a genuinely deep accent needs. Lifted from #A3243B, which
    // measured 2.74:1 against the scene and so would have been a filled button a thumb could not
    // find. This is the deepest red that still clears the non-text threshold, which is the real
    // constraint on how dark Hot is allowed to go.
    val Oxblood = Color(0xFFB02A42)
    val OxbloodContainer = Color(0xFF3D0F1A)
    val OnOxbloodContainer = Color(0xFFFFDCD2)

    /**
     * Oxblood as *text* on a raised or card ground.
     *
     * Oxblood itself is a fill, not a word: it measures 2.4:1 on the card. Ember used to do this
     * job, and now that Extreme owns ember outright, Hot needs its own lifted tone. Measured at
     * 4.74:1 on the card and 5.08:1 on raised.
     */
    val OxbloodBright = Color(0xFFE0566F)

    // --- Ember: EXTREME ------------------------------------------------------
    // The top of the ramp, and the one place the app is allowed to run hot rather than deep.
    // Every tier below goes darker and redder in turn; ember breaks that on purpose, because the
    // fifth tier should not look like the fourth with more of it.
    val Ember = Color(0xFFE2603F)
    val EmberContainer = Color(0xFF4A1A0F)
    val OnEmberContainer = Color(0xFFFFDBCF)

    // --- Plum: the second light in the room ---------------------------------
    // Barely used as a fill on purpose. Its real job is the bounce wash in the scene,
    // which is what stops the app reading as one pink on black.
    val Plum = Color(0xFFC49BC4)
    val PlumDeep = Color(0xFF6E3A63)
    val PlumContainer = Color(0xFF3A1F36)
    val OnPlumContainer = Color(0xFFF2DAEF)

    // --- Brass: PLAYFUL, and the metallic highlight -------------------------
    val Brass = Color(0xFFD9B978)
    val BrassDeep = Color(0xFF8A6D32)
    val BrassContainer = Color(0xFF3A2E16)
    val OnBrassContainer = Color(0xFFF7E4BC)

    // --- Cream: typography ---------------------------------------------------
    val Cream = Color(0xFFF2E6DA)
    val CreamMuted = Color(0xFFCBBAAE)
    val CreamFaint = Color(0xFFA2938A)

    // --- Lines and edges -----------------------------------------------------
    val Outline = Color(0xFF7D6C74)
    val OutlineFaint = Color(0xFF3A2F35)

    // --- Signal --------------------------------------------------------------
    // Used for STOP and for destructive confirmations only. Deliberately distinct from
    // every heat accent so "end this now" can never be mistaken for decoration (§3.1).
    val Signal = Color(0xFFFFB4A8)
    val SignalDeep = Color(0xFF8C1D18)
    val SignalContainer = Color(0xFF5C1410)
    val OnSignalContainer = Color(0xFFFFDAD4)

    val Scrim = Color(0xFF000000)
}

/**
 * How far tonight goes, as a colour (DESIGN_PROMPT.md §4.4).
 *
 * All three heats used to be the same pink, so the one thing the product is actually
 * about — escalation — was invisible. A heat now owns the card's rim, its bloom, the
 * picker's selected state, the eyebrow and the primary action, so changing it visibly
 * changes the room.
 *
 * [accent] fills; [onAccent] is what survives on top of that fill; [rim] draws edges and
 * light; [onDark] is the accent as *text* on a raised or card ground, which is not always
 * the same colour as the fill.
 */
@Immutable
data class TierColors(
    val accent: Color,
    val onAccent: Color,
    val rim: Color,
    val onDark: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * The colours of one tier. The only place a tier becomes a colour.
 *
 * Five steps, and each is a different hue rather than one red at five brightnesses: gold, plum,
 * rose, deep red, ember. Plum used to be kept off fills entirely — its job was the bounce wash in
 * the scene — but five tiers need five accents a person can tell apart at a glance, and plum is
 * the one that sits naturally between brass and burgundy.
 *
 * Every pairing here is measured by `ContrastTest`, which walks all five.
 */
fun tierColors(tier: Intensity): TierColors = when (tier) {
    Intensity.MILD -> TierColors(
        accent = Palette.Brass,
        onAccent = Palette.Scene,
        rim = Palette.Brass,
        onDark = Palette.Brass,
        container = Palette.BrassContainer,
        onContainer = Palette.OnBrassContainer,
    )
    Intensity.FLIRTY -> TierColors(
        accent = Palette.Plum,
        onAccent = Palette.Scene,
        rim = Palette.Plum,
        onDark = Palette.Plum,
        container = Palette.PlumContainer,
        onContainer = Palette.OnPlumContainer,
    )
    Intensity.SPICY -> TierColors(
        accent = Palette.Burgundy,
        onAccent = Palette.Scene,
        rim = Palette.Burgundy,
        onDark = Palette.BurgundyBright,
        container = Palette.BurgundyContainer,
        onContainer = Palette.OnBurgundyContainer,
    )
    Intensity.HOT -> TierColors(
        accent = Palette.Oxblood,
        // Oxblood is deep enough that dark text fails on it. Cream is what a deep accent wants,
        // and it is the reason Hot can go darker instead of louder.
        onAccent = Palette.Cream,
        rim = Palette.Oxblood,
        onDark = Palette.OxbloodBright,
        container = Palette.OxbloodContainer,
        onContainer = Palette.OnOxbloodContainer,
    )
    Intensity.EXTREME -> TierColors(
        accent = Palette.Ember,
        onAccent = Palette.Scene,
        rim = Palette.Ember,
        onDark = Palette.Ember,
        container = Palette.EmberContainer,
        onContainer = Palette.OnEmberContainer,
    )
}

/**
 * Semantic colours that Material 3's [androidx.compose.material3.ColorScheme] has no slot
 * for. Reached through `AfterhoursTheme.colors`.
 */
@Immutable
data class AfterhoursColors(
    /** Metallic highlight for locks, vaults, codes and sealed envelopes. */
    val brass: Color,
    val brassContainer: Color,
    val onBrassContainer: Color,
    /** Text that must recede: hints, timestamps, helper copy. */
    val textFaint: Color,
    /** Text one step down from primary: subtitles, supporting copy. */
    val textMuted: Color,
    /** Hairlines and card edges that should barely register. */
    val edgeFaint: Color,
    /**
     * The rim light (§4.2): a surface's border is a vertical gradient, brighter along the
     * top edge than the bottom, because that is what makes an object look lit rather than
     * outlined. One modifier, and it is most of the difference between panel and object.
     */
    val rimTop: Color,
    val rimBottom: Color,
    /** The two lights in the scene (§4.1), and the fall-off past them. */
    val sceneKey: Color,
    val sceneBounce: Color,
    val sceneVignette: Color,
    /** The grain laid over everything, at the alpha it is drawn with. */
    val grain: Color,
    /**
     * A pairing code's plate and its modules.
     *
     * The one pair in the app that does not follow the theme: a camera reads it, so it
     * needs a light ground and dark modules whatever else is on screen. They are the
     * palette's own cream and ink rather than pure white and black, so the plate still
     * belongs to the room it is held up in.
     */
    val codePlate: Color,
    val codeModule: Color,
    /** The last stretch of a clock: warmer than any tier, and not one of them. */
    val ember: Color,
)

internal val afterhoursColors = AfterhoursColors(
    brass = Palette.Brass,
    brassContainer = Palette.BrassContainer,
    onBrassContainer = Palette.OnBrassContainer,
    textFaint = Palette.CreamFaint,
    textMuted = Palette.CreamMuted,
    edgeFaint = Palette.OutlineFaint,
    rimTop = Palette.Cream.copy(alpha = 0.16f),
    rimBottom = Palette.Cream.copy(alpha = 0.03f),
    sceneKey = Palette.BurgundyDeep.copy(alpha = 0.30f),
    sceneBounce = Palette.PlumDeep.copy(alpha = 0.20f),
    sceneVignette = Palette.Scrim.copy(alpha = 0.55f),
    grain = Palette.Cream.copy(alpha = 0.035f),
    codePlate = Palette.Cream,
    codeModule = Palette.Scene,
    ember = Palette.Ember,
)
