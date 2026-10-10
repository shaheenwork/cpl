# Component map — the preview's elements, and what each becomes in Compose

Every element in `afterhours-redesign.html`, the composable it restyles, and what the port means.

- **Restyle**: same composable, same parameters, new values. A golden changes; nothing else does.
- **New token**: the composable reads something `tokens.json` adds.
- **New component**: there is nothing to restyle. Each one needs a `ComponentGallery` entry, a
  golden, and an `AccessibilityTest` case when it lands (CLAUDE.md, D-096).
- **Proposed**: the element exists in the preview because ANALYSIS.md §6 assumes a yes. It is a
  behaviour change, and is not ported until that yes is given.

Paths are under `core/designsystem/src/main/kotlin/com/shnapps/couple/core/designsystem/` (**ds**)
or `core/ui/src/main/kotlin/com/shnapps/couple/core/ui/` (**ui**).

## Tokens

| Preview | Compose | Port |
|---|---|---|
| `--color-*` | ds `theme/Color.kt` → `object Palette` | Restyle. Values replace; `Scene` → `base`, `Raised` → `velvet1`, `Card` → `velvet2`, `Cream*` → `blush*`, `Brass` → `gold` |
| `--scheme-*` | ds `theme/ColorScheme.kt` → `afterhoursColorScheme()` | Restyle. `surfaceContainerHighest` gains its own value (`velvet3`) — today it equals `High` |
| `--ah-*` | ds `theme/Color.kt` → `AfterhoursColors` | New token: `signal` and `seal`/`sealLight` (the safety colour and the wax). `rimTop` becomes gold, not cream |
| `--tier-{tier}-*`, `[data-tier]` | ds `theme/Color.kt` → `tierColors(tier)`, ds `theme/Theme.kt` → `ProvideTier` | Restyle. Six roles per tier, all five tiers; the hue order moves cool → hot (lilac, orchid, rose, scarlet, crimson) |
| `--space-*` | ds `theme/Dimens.kt` → `Spacing` | Unchanged |
| `--radius-*` | ds `theme/Dimens.kt` → `AfterhoursShapes` | New token: `card` 26 (the deck card) and `pill` |
| `--motion-*`, `--ease-*` | ds `theme/Motion.kt` → `Motion`, `AfterhoursEasing` | New token: `flicker` 1700, `heartbeat` 1800, `flip` 760, `sheet` 420 |
| `--type-*` | ds `theme/Type.kt` → `AfterhoursTypography`, `EyebrowTextStyle` | New token: `cardTitle`, `cardBody`, `tease` (italic serif), `numeral`. Families unchanged (Playfair, Inter) |
| Grain (`.screen::after`) | ds `theme/Scene.kt` | Restyle. Same 3.5 %, warmer |
| Silk sheen (`.screen::before`) | ds `theme/Scene.kt` | New token: two faint diagonal gradients over the scene |
| Key light in tonight's tier | ds `theme/Scene.kt` (`sceneKey`) | Restyle: the key light takes `tier.accent`, so the room warms with the heat |

## Icons

| Preview | Compose | Port |
|---|---|---|
| The 28 app icons | ds `icon/AfterhoursIcons.kt` | Unchanged — the preview reads them out of that file |
| `SafeWord`, `Limits`, `Keyhole`, `Link`, `Phones`, `LockOpen`, `Candle`, `Facets`, `Rings`, `Scales`, `Fork`, `FlameAsk`, `Sealed`, `Aftercare`, `Lanes`, `CardBack`, `Retry`, `Down`, `Up`, `Backspace`, `Fingerprint`, `Share`, `Qr` | ds `icon/AfterhoursIcons.kt` | New token: 23 icons in `src/icons.mjs`, already in `icon(name, vararg paths)` form |
| Flame glyph (filled, 1–5) | — | New component: `HeatFlames(level, flicker)`, a filled path ×n with a gold core |
| Wax seal | — | New component: `Seal(size)`, the brand mark. The blob path is generated once; port it as a fixed path |
| Card-back sigil | — | New component: `Sigil()`, line art on `Canvas` |

## Components

| Preview element | Restyles | Port |
|---|---|---|
| `.btn.primary` | ds `component/GlowButton.kt` (`Primary`) | Restyle: a sheen over the tier fill, a tier bloom |
| `.btn.secondary` / `.quiet` | `GlowButton` (`Secondary`, `Quiet`) | Restyle |
| `.btn.gold` | `GlowButton` | New token: a `Gold` style — outline in gold, for "Start the minutes", pairing, the store |
| `.velvet` | ds `component/Light.kt` → `Modifier.raised` | Restyle: a sheen gradient over the fill |
| `.lit` | ds `component/Light.kt` → `Modifier.bloom` | Restyle |
| `.card-static` | ui `AfterhoursCard.kt` | Restyle; draws `subtitle` as `tease` |
| `.card3d` (face down → flip) | ds `component/RevealCard.kt` | Restyle + **Proposed** on the deck: `RevealCard` is used by the games' reveal today and the deck does not use it (ANALYSIS.md §6.4) |
| `.face.front` + sigil | `RevealCard` concealed face | Restyle: the sigil replaces "Ready?" |
| `.heat` (five cells, bar, hint) | ui `TierPicker.kt` | Restyle: flames for dots, a gradient heat bar, the one-line hint. **New token**: `tierColors(tier).hint` |
| `.chip.tier-chip` | — | New component: `HeatChip`, the picker collapsed into the bar on playing screens (ANALYSIS.md §6.7) |
| `.chip` (lane) | ui `LanePicker.kt` | Restyle: the lane becomes a chip that opens a sheet |
| `.mode-switch` | feature/home `HomeScreen.kt` → `ModeToggle` | Restyle: a sliding tier-lit thumb |
| `.pair-strip` | home's `PairedLine` (feature/home `HomeScreen.kt`) | Restyle: two linked dots, a heartbeat when paired |
| `.under-way` | feature/home `UnderWay` over ds `CinematicCard` | Restyle; `pulsing` already exists (D-141) |
| `.tile` grid | feature/home `HomeScreen.kt` game rows | Restyle: a two-column grid, a glyph per game, Game Night wide |
| `.ribbon` | ui `FreeRunBanner.kt` | Restyle |
| `.rail` | ds `component/StopPauseBar.kt`; ui `Safety.kt` → `SafeWordAction`; InstantPlay's `BarAction`s | Restyle + **Proposed**: one bottom rail on every playing screen. Today Stop is in the top bar on the deck and the bottom bar in a two-phone game; this puts it in the same place everywhere, in thumb reach |
| `.stop` | ui `Safety.kt` → `SafeWordAction` | Restyle: `signal` colour, never the accent. New icon `SafeWord` |
| Stop on one-phone games and Game Night | — | **Proposed** (ANALYSIS.md §4.1) |
| Too far on one-phone games | — | **Proposed** (ANALYSIS.md §4.3) |
| `.sheet` | ui `ReportSheet.kt` pattern | New component: `AfterhoursSheet` — the heat, lanes, limits and too-far sheets share it |
| Limits sheet (`.seg`, `.limit-row`) | feature/boundaries `LimitsScreen.kt` | Restyle; reachable as a sheet as well as a screen. Yes / Maybe / No unchanged |
| Too far sheet | feature/instant `InstantPlayScreen.kt` refusal | Restyle: from a full screen into a sheet over the card |
| Stopped | ui `Safety.kt` → `PausedContent`, `PausedDock` | Restyle. Adds one line, "Breathe. Check in with each other." (CHANGELOG) |
| ~~Extreme gate~~ | ~~ui `Safety.kt` → `ExtremeGateContent`, `ExtremeGateDock`~~ | **Removed** (D-144): choosing the tier is the consent. The prototype still draws it; the app does not |
| `.ring` | ui `TimerRing.kt`, ds `component/CountdownRing.kt` | Restyle: the last fifth warms to `flame` |
| `.progress` | ui `RunProgress.kt` | Restyle |
| `.phases` | feature/instant `GameNightScreen.kt` | New component: labelled phase segments sized to their share |
| `.lengths` | feature/instant `GameNightScreen.kt` `LengthCell` (D-142) | Restyle: large numerals |
| `.dice` / `.die` | ui `PrimitiveRenderers.kt` → `DiceRenderer`, `Die` | Restyle: velvet faces, gold edges |
| `.choices` / `.choice` | `ChoiceRenderer`, `PredictionRenderer` | Restyle; Truth / Dare as two large tiles |
| `.private-note` | `PrimitiveRenderers.kt` private-step notice | Restyle |
| `.reveal-pair` | `RevealRenderer` | Restyle |
| `.who` / `.av` | ui `WhoIsPlaying.kt` | Restyle: an initial in a tier ring |
| Handover / pass screens | feature/games `DealtGameScreen.kt` | Restyle |
| `.mic-take`, `.meter` | `RecordRenderer` → `VoiceProof`; ds `component/VoiceRecorderBar.kt` | Restyle |
| Watching phone | feature/games `GameStageViews.kt` → `WatchingThem`, `TheirTurn` | Restyle: the dare in a lit card under a large initial, instead of a status line |
| Keyhole "just for them" plate | `GameStageViews.kt` private watch state | Restyle |
| `.status-line` | feature/games `GameScreen.kt` reconnecting / partner-away line | Restyle |
| Paused veil | feature/games `GameScreen.kt` → `PausedVeil` | Restyle |
| `.code`, `.keypad`, `.qr` | feature/pairing `PairingScreen.kt` | Restyle |
| `.joined` (two seals meeting) | feature/pairing confirm state | New component: the paired moment |
| Chat sheet | feature/chat `ChatOverlay.kt`, `ChatTimeline.kt`, `ChatBubbles.kt`, `ChatComposer.kt` | Restyle: a gold-hairline sheet, `velvet3` bubbles, the tier container for yours |
| `.pack` | feature/store `StoreScreen.kt` | Restyle: flames per pack, gold for the best |
| `.set-group`, `.set-row` | feature/settings `SettingsScreen.kt` | Restyle |
| `.icons-pick` | `SettingsScreen.kt` → `DiscreetIconRow` | Restyle |
| Lock screen (`.neutral`) | feature/applock `AppLockScreen.kt` | Restyle — **deliberately unbranded**: no seal, no heat, no name |
| `.skeleton` | ds `component/EmptyState.kt` / loading states | New component: `Shimmer` for loading placeholders |
| `.banner` | ui `ErrorBanner.kt` | Restyle: the `signal` colour |
| `.toast` | — | New component, for confirmations ("Yours. It opens every level below it, too.") |
| `.ornament` | — | New component: a gold rule with a glyph in it, for section breaks |
| Designer panel | — | Not part of the design; not ported |
