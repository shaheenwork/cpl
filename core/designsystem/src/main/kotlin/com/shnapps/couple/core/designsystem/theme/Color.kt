package com.shnapps.couple.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.shnapps.couple.core.model.Intensity

/**
 * The raw palette: after dark (design/redesign/tokens.json, D-145).
 *
 * A room lit low, in red. The ground is near-black with red in it — never pure #000, which reads
 * as a switched-off screen rather than as a dim room — and every surface above it is velvet: the
 * same red-black, lifted a step at a time. Text is blush, the colour of skin in warm light, never
 * white. Gold is the one metal: locks, seals, the back of a card, a code.
 *
 * Reference: a members' club after midnight. Explicitly NOT pink gradients, hearts, cartoon flames
 * or anything from an adult novelty store — the heat is in the light, not in the props.
 *
 * Every foreground/background pairing used by [afterhoursColorScheme] and by [tierColors] is
 * checked against WCAG AA by `ContrastTest`, so these values are verified rather than eyeballed.
 * `design/redesign/src/build.mjs` checks the same pairings for the prototype.
 */
internal object Palette {

    // --- The room and its velvet ----------------------------------------------
    // Four grounds, each a visible step above the last. The prototype's velvets were lifted here
    // until each step clears `ContrastTest`'s ground rule: on an OLED at low brightness, with grain
    // over it, a step smaller than that is one colour.
    /** The room. One per screen, under the scene's light and grain. */
    val Base = Color(0xFF0A0508)

    /** Rows, chips, fields, sheets, the safety rail. Everything a list is made of. */
    val Velvet1 = Color(0xFF1A0C14)

    /** The hero card, and only the hero card. */
    val Velvet2 = Color(0xFF241220)

    /** Something on a card or a sheet: their bubble, a field, a selected row. */
    val Velvet3 = Color(0xFF301829)

    // --- Wine: the second light in the room -------------------------------------
    // Barely a fill. Its job is the bounce low on the screen, which is what makes the dark read
    // as a room with a red lamp in it rather than as black.
    val Wine = Color(0xFF5A0F26)
    val WineDeep = Color(0xFF2A0716)

    // --- Ember and flame: the app's own heat -------------------------------------
    /** The default accent, before a couple has said how far tonight goes. */
    val Ember = Color(0xFFFF3D5A)

    /** The tip of a flame, and the last stretch of a clock. Warmer than any tier. */
    val Flame = Color(0xFFFF7A3D)

    // --- Gold: the one metal ------------------------------------------------------
    val Gold = Color(0xFFD9A54E)
    val GoldBright = Color(0xFFF2C778)
    val GoldContainer = Color(0xFF3A2A12)
    val OnGoldContainer = Color(0xFFF7E4BC)

    // --- Blush: typography --------------------------------------------------------
    val Blush = Color(0xFFF6D6CC)
    val BlushMuted = Color(0xFFCDB0A8)
    val BlushFaint = Color(0xFFA38A85)

    // --- Lines and edges ----------------------------------------------------------
    /**
     * An edge that has to be *seen*: a field, an unselected choice. Clears the 3:1 non-text
     * threshold on every ground; the prototype's hairline does not, so it is not used for this.
     */
    val Outline = Color(0xFF7E6470)
    val Hairline = Color(0xFF4A2E38)
    val HairlineFaint = Color(0xFF2E1A24)

    // --- Signal -------------------------------------------------------------------
    // Used for STOP and for destructive confirmations only. Deliberately distinct from every heat
    // accent so "end this now" can never be mistaken for decoration (§3.1).
    val Signal = Color(0xFFFFB4A8)
    val SignalContainer = Color(0xFF4A1410)
    val OnSignalContainer = Color(0xFFFFDAD4)

    // --- Wax: a sealed thing ---------------------------------------------------------
    val Wax = Color(0xFF8E1430)
    val WaxLight = Color(0xFFC42A4A)

    val Scrim = Color(0xFF000000)
}

/**
 * How far tonight goes, as a colour (DESIGN_PROMPT.md §4.4).
 *
 * A heat owns the card's rim, its bloom, the picker's selected state, the eyebrow, the primary
 * action and — since D-145 — the room's key light, so changing it visibly changes the room.
 *
 * [accent] fills; [onAccent] is what survives on top of that fill; [rim] draws edges and light;
 * [onDark] is the accent as *text* on a raised or card ground, which is not always the same colour
 * as the fill.
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
 * Five steps, cool to hot: lavender, orchid, rose, scarlet, and a red so deep it needs a flame for
 * its rim. Each is a different hue rather than one red at five brightnesses, so a person can tell
 * them apart at a glance — and colour is never the only cue: the flames count the level and the
 * name is always beside them.
 *
 * Every pairing here is measured by `ContrastTest`, which walks all five.
 */
fun tierColors(tier: Intensity): TierColors = when (tier) {
    Intensity.MILD -> MildColors
    Intensity.FLIRTY -> FlirtyColors
    Intensity.SPICY -> SpicyColors
    Intensity.HOT -> HotColors
    Intensity.EXTREME -> ExtremeColors
}

private val MildColors = TierColors(
    accent = Color(0xFFB9A6E0),
    onAccent = Color(0xFF17102A),
    rim = Color(0xFFB9A6E0),
    onDark = Color(0xFFC9B9EA),
    container = Color(0xFF241B3A),
    onContainer = Color(0xFFE6DEF8),
)

private val FlirtyColors = TierColors(
    accent = Color(0xFFE58AD0),
    onAccent = Color(0xFF2A0A24),
    rim = Color(0xFFE58AD0),
    onDark = Color(0xFFECA2DB),
    container = Color(0xFF3B1535),
    onContainer = Color(0xFFFBDDF4),
)

private val SpicyColors = TierColors(
    accent = Color(0xFFFF4F8B),
    onAccent = Color(0xFF2A0414),
    rim = Color(0xFFFF4F8B),
    onDark = Color(0xFFFF7AA5),
    container = Color(0xFF4A0F27),
    onContainer = Color(0xFFFFD6E4),
)

private val HotColors = TierColors(
    accent = Color(0xFFE8283F),
    onAccent = Color(0xFF14020A),
    rim = Color(0xFFFF4A5E),
    onDark = Color(0xFFFF6B7C),
    container = Color(0xFF470A17),
    onContainer = Color(0xFFFFD3D8),
)

// Deep enough that dark text fails on it, so its fill takes blush; and too deep to be its own
// edge, so its rim is the flame. Lifted from the prototype's #B3122E, which measured 2.93:1 on
// the room — a filled button a thumb could not find.
private val ExtremeColors = TierColors(
    accent = Color(0xFFBA1530),
    onAccent = Color(0xFFFFE9E4),
    rim = Palette.Flame,
    onDark = Color(0xFFFF7A55),
    container = Color(0xFF3A0611),
    onContainer = Color(0xFFFFD6C9),
)

/**
 * Semantic colours that Material 3's [androidx.compose.material3.ColorScheme] has no slot for.
 * Reached through `AfterhoursTheme.colors`.
 */
@Immutable
data class AfterhoursColors(
    /** The one metal: locks, vaults, codes, seals and the back of a card. */
    val brass: Color,
    val brassBright: Color,
    val brassContainer: Color,
    val onBrassContainer: Color,
    /** Text that must recede: hints, timestamps, helper copy. */
    val textFaint: Color,
    /** Text one step down from primary: subtitles, supporting copy. */
    val textMuted: Color,
    /** Hairlines and card edges that should barely register. */
    val edgeFaint: Color,
    /**
     * The rim light (§4.2): a surface's border is a vertical gradient, brighter along the top edge
     * than the bottom, because that is what makes an object look lit rather than outlined. Gold
     * now, faintly: the lamp in this room is warm.
     */
    val rimTop: Color,
    val rimBottom: Color,
    /** The room's light when nothing is playing, or when it has been stopped: no heat in it. */
    val sceneCalm: Color,
    /** The two wine bounces low on the screen (§4.1), and the fall-off past the light. */
    val sceneKey: Color,
    val sceneBounce: Color,
    val sceneVignette: Color,
    /** The silk: a diagonal sheen, very faint, that makes the dark read as fabric. */
    val sceneSheen: Color,
    /** The grain laid over everything, at the alpha it is drawn with. */
    val grain: Color,
    /**
     * A pairing code's plate and its modules.
     *
     * The one pair in the app that does not follow the theme: a camera reads it, so it needs a
     * light ground and dark modules whatever else is on screen. They are the palette's own blush
     * and ink rather than pure white and black, so the plate still belongs to the room.
     */
    val codePlate: Color,
    val codeModule: Color,
    /** The last stretch of a clock: warmer than any tier, and not one of them. */
    val ember: Color,
    /** Wax: something sealed until both of them open it. */
    val seal: Color,
    val sealLight: Color,
)

internal val afterhoursColors = AfterhoursColors(
    brass = Palette.Gold,
    brassBright = Palette.GoldBright,
    brassContainer = Palette.GoldContainer,
    onBrassContainer = Palette.OnGoldContainer,
    textFaint = Palette.BlushFaint,
    textMuted = Palette.BlushMuted,
    edgeFaint = Palette.HairlineFaint,
    rimTop = Palette.GoldBright.copy(alpha = 0.22f),
    rimBottom = Palette.Blush.copy(alpha = 0.03f),
    sceneCalm = Palette.Blush.copy(alpha = 0.05f),
    sceneKey = Palette.Wine.copy(alpha = 0.45f),
    sceneBounce = Palette.WineDeep.copy(alpha = 0.7f),
    sceneVignette = Palette.Scrim.copy(alpha = 0.6f),
    sceneSheen = Palette.Blush.copy(alpha = 0.035f),
    grain = Palette.Blush.copy(alpha = 0.035f),
    codePlate = Palette.Blush,
    codeModule = Palette.Base,
    ember = Palette.Flame,
    seal = Palette.Wax,
    sealLight = Palette.WaxLight,
)
