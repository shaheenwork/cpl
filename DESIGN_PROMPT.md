# DESIGN_PROMPT.md

The visual rebuild of Afterhours.

`BUILD_PROMPT.md` is the product. `REDESIGN_PROMPT.md` rebuilt the front half of the flow.
This one is about how the app *looks and feels*, and nothing else. The flow is settled
(one home, one way to start, one game list, one player, one vocabulary — D-103 to D-106);
none of it changes here.

Read `§1` before writing any code. The diagnosis is specific, it names files, and every
instruction after it exists because of something in it.

---

## §0 How to use this

Phases `D1`–`D8` in §8, in order. A phase is done when its commands have been **run** and
seen to pass, `PROGRESS.md` says so, and the goldens are committed. Same working
agreement as every other phase in this repo.

Do not start at the screens. §8 puts the foundations first on purpose: three quarters of
what reads as "prototype" in this app is spacing, light and type, not layout. Redrawing
Home before fixing those would just produce a differently-arranged prototype.

---

## §1 The diagnosis

Evidence, from the committed goldens. Look at each one before reading the line about it.

### 1.1 `feature/instant/src/test/screenshots/instant-card.png`

This is the product — one card, the thing a couple actually looks at — and it is the
worst screen in the app.

- **The glow is a visible artefact.** `Modifier.glow` draws ten concentric rounded
  rectangles (`component/Glow.kt`). At card size they band into a hard pink rectangle
  with a stepped edge. It does not read as light; it reads as a bug.
- **The three heat buttons' halos overlap into one continuous smear** across the bottom
  of the screen. Same cause.
- **Title and body collide.** "Opposite Corners" (Playfair 32) sits directly on top of
  the body with no space between them, because `CinematicCard` spaces its children by one
  uniform gap that was chosen for lists.
- **The bottom 40% of the screen is empty.** Everything is top-aligned in a scrolling
  column. The actions — the only things a thumb needs — are in the middle.
- **Three competing calls to action**: a full-width "Start the 5 minutes", then
  Skip/Done, then "Too far" as *pink text* that fights the pink Done button beside it.
- **Body text runs the full 344 dp** at 17 sp with positive tracking. Roughly 52
  characters a line, no measure, no ragging control.
- **Progress is four words of 11 sp grey text.** "2 of 6 this run". There is no visual
  sense of an evening moving along.
- **The card has no identity of its own.** Nothing about it says HOT except three
  letters of brass at the top. A Playful card and a Wild card are the same object.

### 1.2 `feature/home/src/test/screenshots/home-alone.png`

- **Every element is the same object**: a 16–24 dp rounded rectangle, one hairline
  border, one fill, one gap. Card, row, button and chip are visually interchangeable, so
  the eye has nothing to sort by.
- **One gap value everywhere.** `ScreenColumn` uses `spacing.md` between all children, so
  a section heading is as far from its own content as it is from the previous section.
  This single fact is the strongest "unfinished" signal in the app.
- **Flat ground.** `Palette.Ink` is painted edge to edge with nothing on it. No grain, no
  light, no depth. On an OLED phone this reads as "screen off with text on it".
- **The five ink levels are indistinguishable.** `0B0708 → 151013 → 1E171B → 281F24 →
  332830` are under 5% luminance apart. In practice the app has one surface colour.
- **Nothing anchors the top or bottom.** No app bar, no dock. Content begins 32 dp down
  and ends wherever it ends.

### 1.3 `feature/night/src/test/screenshots/build-night.png`

- Better than the others — because the duration chips and mood chips give it rhythm by
  accident — and it still shows the same halo smear on the heat row.
- The primary action sits at the end of a long scroll instead of being docked.

### 1.4 `feature/auth/src/test/screenshots/sign-up-apart.png`

- **A 300 dp void** between the password field and the buttons, from a `Spacer(weight)`.
- The disabled primary button and the enabled secondary read as equally important.

### 1.5 Across the whole app

- **Emoji are the icon set.** 🔥 🎲 🌙 💌 👀 📖 ⏱. They are inconsistent across
  platforms, cannot be coloured, cannot be animated, cannot be sized, and are the single
  clearest signal that a product has no designer.
- **`Elevations` is declared and never used.** `Dimens.kt` defines four levels; nothing
  reads them.
- **`Motion` is rich and barely spent.** `AfterhoursEasing.Anticipation`, `bloom`,
  `ambient`, `Breathing` all exist. The only motion in the app is a button's glow alpha.
- **Plum is effectively unused.** The app is one accent (`Burgundy #C75C7A`) on
  near-black, so it reads monochrome — and slightly cheap, because that pink at that
  saturation on black is the default "adult app" palette.
- **No brand anywhere.** Generic adaptive launcher icon, no splash screen, no wordmark,
  no monogram.
- **No loading or skeleton states.** Screens say "Dealing." in grey body text.

### 1.6 What is already good, and must survive

Do not throw these away:

- The **restraint of the concept** — dark only, no hearts, no cartoon flames, no stock
  couples photography. §15.1's anti-reference is right and the build honoured it.
- **Playfair Display + Inter** is a genuinely good pair for this product. Keep both.
- **Warm near-black rather than `#000`**, and **cream rather than white**. Keep both.
- The **token discipline**: nothing hardcodes a colour, a dp or a duration, and Konsist
  plus `ContrastTest` hold it. Every instruction below goes through tokens.
- **`AfterhoursEasing.Anticipation`**, which is the right signature curve and is barely
  used yet.

---

## §2 The thesis

**A dim room, not a dark app.**

Everything below follows from that one sentence. A dark app is a light app with the
colours inverted: flat panels, hairline borders, uniform spacing. A dim room has a
*source* of light, things that catch it, surfaces with texture, and shadow where the light
does not reach. The current app is the first thing. It needs to be the second.

Three adjectives, in priority order, for every decision:

1. **Intimate** — it is late, the phone is between two people, nobody else is in the room.
2. **Considered** — someone chose this. Nothing is a default.
3. **Charged** — it escalates. Playful and Wild must not look the same.

And one rule that outranks all three: **it must never look like a sex toy website.** The
line is: editorial, not explicit. A good perfume campaign, a hotel bar's menu, the title
sequence of a prestige drama. Not neon, not gradients on buttons, not lips, not silhouettes.

### Reference

- **Light**: a candle at the edge of frame. One warm source, off-centre, falling off fast.
- **Material**: velvet, brushed brass, aged paper, smoked glass.
- **Layout**: a good print magazine — a big first letter, a rule under a heading, generous
  margins, one thing per spread.
- **Colour**: oxblood, plum, brass, cream. The palette of a wine list.

### Anti-reference

Neon pink on black. Gradient-filled buttons. Blurred glass everywhere. Emoji. Rounded
everything. Drop shadows. Stock photography of couples. Anything that would be at home in
a crypto app, a dating app, or a novelty store.

---

## §3 What does not change

Inherited, non-negotiable, enforced by existing tests:

1. **Dark only.** `AfterhoursTheme` takes no `darkTheme` flag (D-009). No light mode.
2. **Tokens only.** No hardcoded `Color`, `dp` or duration in a screen. Everything through
   `AfterhoursTheme.colors / .spacing / .motion / .elevations` and `MaterialTheme`.
3. **WCAG AA** on every foreground/background pairing, held by `ContrastTest`.
4. **48 dp touch targets** and **200% type without loss**, held by `AccessibilityTest` and
   the per-feature accessibility suites.
5. **Reduce-motion**, through `decorativeTween`. Decorative movement collapses; a reveal
   keeps its timing on purpose, because skipping it would leak the content early.
6. **`SecureScreen()`** on anything showing private content.
7. **No content in a golden that the engine did not permit.** Screenshot fixtures use the
   test fixtures, never the real catalogue.
8. **Nothing about the privacy architecture.** No new field travels, no content id reaches
   a notification, nothing a partner may not see appears on a shared surface. If a visual
   idea needs new data, it is the wrong idea.

---

## §4 Foundations

This is the largest section and it is where the win is.

### 4.1 The scene

Retire the idea that the background is a colour. It is a **scene**, composed once in
`AfterhoursSurface` and inherited by every screen:

```
scene = base ink
      + two radial washes (the light)
      + a vignette (the fall-off)
      + grain (the texture)
```

- **Base**: `Ink #0B0708`, unchanged.
- **Wash A**: a radial gradient centred off-screen at roughly `(-10%, -15%)`, radius
  ~120% of the shorter edge, `BurgundyDeep` at **6%** fading to zero alpha. This is the
  candle.
- **Wash B**: the same at `(110%, 105%)`, `PlumDeep` at **4%**. This is the bounce, and it
  is the reason plum finally earns its place in the palette.
- **Vignette**: a radial from transparent at 55% to `#000` at **18%** at the corners.
- **Grain**: a tiled 128×128 monochrome noise at **2.5%** over everything, in
  `BlendMode.Overlay`. Ship it as a tiny PNG in `:core:designsystem/res/drawable`. Do not
  generate it per-frame.

Every gradient stop fades to `color.copy(alpha = 0f)`, never `Color.Transparent` — the
existing gotcha, and it will bite again here.

**Acceptance**: with all text hidden, a screenshot of the scene is visibly not a flat
rectangle, and `ContrastTest` still passes on every pairing over it.

### 4.2 Surfaces, and the rim

Five ink levels that nobody can tell apart become **three** that everybody can:

| Token | Value | Use |
|---|---|---|
| `surfaceScene` | `#0B0708` + scene | The room. One per screen. |
| `surfaceRaised` | `#171115` | Rows, chips, fields, sheets. |
| `surfaceCard` | `#1F1820` | The hero card, and only the hero card. |

Each step is ~7% luminance, which is the smallest gap that survives an OLED at low
brightness with grain on top.

Then the thing that actually makes a surface look physical:

- **Rim light.** A 1 dp border drawn as a *vertical gradient*: `Cream @ 8%` at the top
  edge falling to `Cream @ 2%` at the bottom. Not a uniform hairline. This single detail
  is the difference between "panel" and "object", and it costs one modifier.
- **Contact shadow.** Below `surfaceCard` only: a 24 dp downward blur of `#000 @ 45%`,
  offset 8 dp. On near-black it is nearly invisible as a shadow and reads as *weight*.

New modifier: `Modifier.raised(level: Elevation)` in `:core:designsystem`, which applies
the right fill, rim and contact shadow together. `Elevations` finally gets used.

### 4.3 Light, properly

**Retire `Modifier.glow` as it stands.** Replace with three primitives:

1. **`Modifier.bloom(color, radius, alpha)`** — `Modifier.blur` with
   `BlurredEdgeTreatment.Unbounded` on API 31+, and on 26–30 a **three**-layer fallback at
   wider spread (not ten — ten is what bands). Cap `alpha` at 0.18. Bloom is for *one*
   element per screen, never for a row of three.
2. **`Modifier.rim(color, alpha)`** — the gradient border from 4.2.
3. **`sceneLight()`** — the washes from 4.1, applied once.

Rules:

- **At most one bloomed element per screen.** The card, or the primary action, never both.
- **Never bloom something in a row with its siblings.** The heat picker loses its glow
  entirely and gets a filled selected state instead.
- A pressed button brightens its **fill and rim**, not its halo.

### 4.4 Heat is a colour

Today all three heats are the same pink. The product's whole emotional arc is escalation,
and the design says nothing about it. Give each heat an identity that the card, its rim,
its bloom, the picker's selected state and the eyebrow all take:

| Heat | Accent | Container | Reads as |
|---|---|---|---|
| Playful | `Brass #D9B978` | `#3A2E16` | warm, lit, unhurried |
| Hot | `Burgundy #C75C7A` | `#4A1426` | the existing accent |
| Wild | `Oxblood #A3243B` + `Ember #E2603F` rim | `#3D0F1A` | deeper, hotter, less pink |

Wild deliberately goes **darker and redder**, not brighter pink. Escalation in this
product is intimacy, not volume.

Add `AfterhoursColors.heat(heat: Heat): HeatColors` returning accent, container,
onContainer and rim. `ContrastTest` gains a row per heat.

### 4.5 Type

The pairing is right; the usage is thin.

- **Measure.** Card body gets `Modifier.widthIn(max = 34 * bodySize)` — about 38–42
  characters. Nothing else in the app changes width.
- **Card body style** (new): Inter 19 sp / 30 line height / `0.sp` tracking. The current
  17/26 with +0.15 tracking is a list style being used for the hero.
- **Optical sizing.** Playfair is variable with an `opsz` axis. Wire it:
  display sizes get `opsz` matched to their point size, which is what stops 32 sp Playfair
  looking like 16 sp Playfair scaled up.
- **Tabular numerals** on every timer, count and score:
  `FontFeatureSettings("tnum")`. A clock that shifts width as it counts down is the
  cheapest possible tell.
- **True small caps** on the eyebrow: `FontFeatureSettings("smcp", "case")` instead of
  `.uppercase()` plus tracking. Keep the 2.4 sp tracking.
- **One display size per screen.** If two things on a screen are Playfair, one of them is
  wrong.
- **Drop cap**, optional, on the card title only: the first letter in Playfair at 2× the
  title size, `Brass @ 40%`, aligned to the first two lines. Editorial, cheap, and it makes
  a card look composed rather than filled in.

### 4.6 Rhythm

The single highest-value change in this document.

Retire "one gap for everything". `Spacing` gains **semantic** gaps, and `ScreenColumn`
stops applying a uniform `spacing.md`:

| Token | dp | Between |
|---|---|---|
| `tight` | 4 | A label and the thing it labels |
| `item` | 12 | Two rows in the same list |
| `group` | 20 | Two groups inside a section |
| `section` | 36 | Two sections |
| `screenTop` | 24 | The bar and the first content |
| `dock` | 16 | Inside the bottom action dock |

Rules:

- A section heading gets **`section` above, `tight` below**. Never equal gaps on both
  sides — that is what makes a heading look like it belongs to the section above it.
- Inside a card: eyebrow → `tight` → title → `group` → body → `group` → meta.
- Screen side gutter stays 24 dp. Do not tighten it.

`ScreenColumn` takes an explicit `Arrangement` and every screen states its own rhythm.

### 4.7 Shape

| Token | dp | Use |
|---|---|---|
| `extraSmall` | 8 | Chips, badges, segments |
| `small` | 12 | Rows, fields |
| `medium` | 16 | Buttons, sheets |
| `large` | 20 | Raised panels |
| `extraLarge` | 28 | The hero card only |

Drop the current 6 and 10. Keep everything on a 4 dp step so radii sit on the same grid as
spacing.

### 4.8 Motion

The tokens exist. Spend them.

- **Screen entry**: content rises 8 dp and fades in over `standard`, staggered **40 ms**
  per section, `Decelerate`. Under reduce-motion it appears instantly, composed.
- **Card deal** (the signature move): scale `0.94 → 1`, rotate `-1.5° → 0`, opacity
  `0 → 1`, over `slow` with `Anticipation`. It should feel like a card being laid down. It
  is the one moment the app performs, and it is the moment the product is about.
- **Press**: scale `0.985` on rows and buttons, `quick`, `Standard`. Nothing else.
- **Heat change**: the accent cross-fades over `standard` everywhere at once — card rim,
  eyebrow, picker, dock. Changing the heat should visibly change the room.
- **Timer**: a ring, not a number alone. Sweeps linearly; the last 10 seconds pulse the
  rim at `Breathing`.
- **Reveal**: unchanged. It keeps its timing under reduce-motion (§3.5).

No animation blocks input for more than `Motion.MAX_BLOCKING_MS` (400).

### 4.9 Icons

**Every emoji comes out of the app.** They are the loudest amateur signal left.

- A hand-drawn set of ~22 `ImageVector`s in
  `core/designsystem/src/main/kotlin/.../icon/AfterhoursIcons.kt`.
- **1.5 dp stroke, 24 dp box, round caps, round joins, no fills.** Consistent optical
  weight matters more than draughtsmanship — a set that is uniformly plain beats a set that
  is individually pretty.
- Needed: back, close, play, pause, stop, skip, dice, spark, lock, unlock, heart, bookmark,
  clock, timer, send, mic, camera, image, calendar, gift, eye, eye-off, settings, chevron,
  check, plus, flame, moon, people, shield.
- Icons are **never** the only label. Everything keeps its `contentDescription`.

Add a Konsist rule — the `SOUND` rule in `ContentChokepointTest` is the precedent —
failing the build on an emoji literal in any user-facing string in `:feature:*`,
`:core:ui` or `:core:designsystem`.

### 4.10 Texture and imagery

- **No photography of people.** Ever. Wrong for the product, wrong for privacy, and it is
  the fastest way to look like every other app in the category.
- **Allowed**: the grain (4.1), one velvet-weave texture at ≤4% on the hero card, and the
  scene washes.
- **Not allowed**: illustrations of couples, silhouettes, lips, flames, hearts as art
  (the heart *icon* is fine), gradient meshes as decoration, glassmorphism.

---

## §5 Components

Each one gets acceptance criteria, because "looks better" is not reviewable.

### 5.1 `AfterhoursCard` — the hero (replaces `CinematicCard` for content)

The most important object in the app. One per screen, at most.

- `surfaceCard`, `extraLarge` radius, rim in the **heat's** accent at 22%, contact shadow.
- A large index numeral behind the text: the card's position in the run, Playfair, 96 sp,
  `Cream @ 5%`, bled off the top-right corner and clipped by the card. Editorial device,
  no extra data, and it is what makes a card look designed.
- Optional velvet texture at 4%.
- Internal rhythm per §4.6, not one uniform gap.
- Body at the new card body style, with a measure.
- Bloom only when it is the active card.

**Acceptance**: a Playful card and a Wild card are obviously different objects at a
glance, with the text covered.

### 5.2 Buttons

Keep three styles, redraw all three.

- **Primary**: heat accent fill, `Ink` label, `medium` radius, 52 dp tall, rim at 30%,
  bloom at 12% only when it is the screen's single primary. Press darkens the fill 6% and
  scales 0.985.
- **Secondary**: transparent fill, rim in `Cream @ 14%`, cream label. Press fills to
  `Cream @ 6%`.
- **Quiet**: label only, `textMuted`, no rim, 48 dp target. Press brightens to `Cream`.

Remove `leadingEmoji` from the API. Add `leadingIcon: ImageVector?`.

**Acceptance**: exactly one Primary per screen, enforced by review; the three are
distinguishable in a greyscale screenshot.

### 5.3 `ChoiceRow`

The workhorse. It is currently a card pretending to be a row.

- `surfaceRaised`, `small` radius, **no rim** — rows in a list should not each look like an
  object. Separation comes from the `item` gap alone.
- Leading icon 24 dp in `textMuted`; title `titleMedium` cream; subtitle `bodySmall`
  `textMuted`; trailing chevron, not a word.
- `locked = true`: everything at 45% opacity, trailing becomes a lock icon plus the word,
  and the row keeps its full touch target.
- Press: fill to `Cream @ 5%`, scale 0.985.

**Acceptance**: ten rows in a column read as a list, not as ten cards.

### 5.4 `HeatPicker`

- One `surfaceRaised` track, `extraSmall` radius, three segments inside it.
- Selected segment: the **heat's own** container fill, accent label, rim at 25%. The
  selection slides between segments over `standard` with `Standard`.
- **No bloom on any segment.** This kills the smear in the current goldens.
- Dots stay; they are good and they are the only non-verbal indicator of level.

**Acceptance**: no halo overlaps any neighbour at any font scale, including 200%.

### 5.5 The screen frame (new)

Every screen gets the same frame, in `:core:ui`:

- **`ScreenScaffold(title, onBack, dock) { }`**
- **Top bar**: 56 dp, back chevron at the left, eyebrow-style title centred, transparent
  over the scene. A `Cream @ 8%` hairline fades in only once content scrolls under it.
- **Bottom dock**: actions pinned to the bottom, over a 96 dp gradient scrim from
  `surfaceScene` to zero alpha, so content scrolls *under* it. This is what fixes the
  "everything floats in the middle with dead space below" problem in one move.
- **`ScreenColumn`** keeps its gutter, gains explicit arrangement, and stops being the
  thing that fakes a screen.

**Acceptance**: no screen has more than 120 dp of unintentional empty space at the bottom.

### 5.6 `RunProgress` (new)

Replaces "2 of 6 this run".

- A segmented rail: one segment per card, 3 dp tall, `extraSmall` radius, 4 dp apart.
- Done segments: `Cream @ 35%`. Current: heat accent, full. To come: `Cream @ 10%`.
- Sits under the top bar, full gutter width.
- `contentDescription` says "Card 2 of 6".

### 5.7 `TimerRing` (new)

Replaces "Start the 5 minutes" as a bare button.

- A 72 dp ring, 3 dp stroke, sweeping from the top. Track `Cream @ 10%`, progress in the
  heat accent. Remaining time in the centre, tabular numerals.
- Last 10 seconds: the rim pulses at `Breathing`, and the accent shifts towards `Ember`.
- Tapping it pauses. Long-press resets. Both labelled.

### 5.8 States

Three that the app currently does not really have.

- **Loading**: skeletons, not the word "Dealing." A card skeleton is the card's shape in
  `surfaceRaised` with a slow shimmer at `ambient`. Under reduce-motion, no shimmer.
- **Empty**: `EmptyState` gains an icon slot above the title and always takes an action
  (D-106 already requires the action; this makes it look like a designed state).
- **Error**: `ErrorBanner` gets `surfaceRaised`, a `Signal` rim, an icon, and a retry.

### 5.9 Sheets

The "choices in place" pattern from U2 stays — it is testable and it works. Give it a
proper treatment: `surfaceRaised`, `medium` radius, a grab handle, a scrim over the
content behind it at `#000 @ 40%`, and entry over `standard` with `Decelerate`.

---

## §6 Screens

Only what changes visually. No flow changes.

### 6.1 Home

- Scene, top bar with the wordmark rather than a title, no back.
- The heading block keeps Playfair but drops to one display size with `section` below it.
- **Start now** becomes the docked primary at the bottom, always reachable, with the
  scene scrolling under it. Everything else scrolls.
- Heat picker directly under the heading, as a compact track (it is the room's setting, so
  it belongs with the room, not at the bottom).
- Sections get real headings with `section` rhythm: **Tonight** / **When you're not
  together** / **Games**.
- Game rows get their icon and lose their card-ness (§5.3).
- The live cards (something waiting, something under way) become the one bloomed hero on
  the screen when present.

### 6.2 The card player

The screen this document exists for.

- Top bar: back, and `RunProgress` under it.
- The card, centred in the remaining space, bloomed, with the index numeral and the heat's
  rim.
- `TimerRing` between card and dock when the card has minutes.
- Dock: **Done** as the single primary, **Skip** as secondary beside it at 40/60 width.
  "Too far" moves to a quiet control in the **top bar**, as an icon plus label — it is a
  safety control, not a third call to action, and it must never compete with Done.
- Card-to-card: the old card slides out 12 dp and fades, the new one deals in (§4.8).

### 6.3 The game player

- Same frame, same progress rail, same dock.
- Each primitive renderer gets the new card treatment; none of them gets its own layout
  language. A `DRAW` lands on the card. A `DIAL` is a track across the card's width. A
  `RECORD` is the mic in the dock.
- Hand-off ("pass the phone") becomes a full-bleed moment: the scene darkens, one line of
  Playfair, one primary. It is the only screen in the app with no chrome.

### 6.4 Plan the evening

- The three questions become three labelled groups with `section` rhythm.
- Duration and mood chips get the new chip treatment (`extraSmall`, `surfaceRaised`,
  selected = heat container).
- "Build the evening" docks.

### 6.5 Apart

- The thread becomes a real timeline: a 1 dp `Cream @ 8%` rail down the left gutter, each
  entry a node on it, mine right-aligned and theirs left-aligned.
- Sealed items get the brass lock icon and a smoked-glass treatment: `surfaceRaised` plus a
  2% grain bump, never a blur of real content.

### 6.6 Memories

- A two-column staggered grid rather than a list. Each memory is a small card with its
  date in Playfair, its moods as dots, and a brass bookmark when kept.

### 6.7 Account, settings, pairing

- Fields get `surfaceRaised`, `small` radius, a rim that brightens on focus, and 56 dp
  height.
- Settings rows use `ChoiceRow`, grouped with `section` rhythm and real headings.
- Pairing's code display becomes the one moment brass is used large: Playfair, 44 sp,
  tabular, letter-spaced, on a raised panel.

---

## §7 Brand

The app has none. It needs the minimum that a store listing requires and a home screen
deserves.

- **Monogram**: an "A" in Playfair Display Medium, optically centred, on a
  `BurgundyDeep → Ink` radial.
- **Adaptive launcher icon**: monogram foreground, `Ink` background layer, safe-zone
  correct. Replace the template `ic_launcher_*`.
- **Splash**: `androidx.core:core-splashscreen`, `Ink` window, monogram, no spinner. It
  must hand over to the first frame with no seam — `themes.xml` already sets
  `windowBackground` to `@color/ink`, so keep that.
- **Wordmark**: "AFTERHOURS" in Inter Medium at 2.4 sp tracking, small caps. Used on home's
  top bar and on first launch. Not a logo, and it does not need to be.
- **Store assets**: feature graphic and screenshots that are the real app on the real
  scene. No device frames with marketing copy over them.

---

## §8 Phases

Each phase: implement, run the commands, update `PROGRESS.md`, record the decisions worth
recording in `DECISIONS.md`, commit.

### D1 — Foundations
Scene, grain, three surfaces, rim light, contact shadow, retire `Modifier.glow`, the new
bloom primitives, semantic spacing, the type changes (measure, optical sizing, tabular
numerals, small caps), the shape scale, heat colours.
No screen changes. Goldens will move everywhere; that is the point.
**Exit**: `./gradlew check` green with re-recorded goldens; `ContrastTest` covers all three
heats; the gallery shows scene, surfaces, rim, bloom and the type scale as specimens.

### D2 — Icons
The icon set, every emoji removed, `leadingIcon` on buttons and rows, the Konsist emoji
rule failing on a planted emoji.
**Exit**: `./gradlew check`; the new rule fails on a deliberate emoji and passes once it
is removed. Prove the gate by watching it fail.

### D3 — The frame
`ScreenScaffold`, top bar, scroll hairline, bottom dock, screen-entry stagger, press
scale. Applied to every screen mechanically, without redrawing their contents.
**Exit**: no screen with more than 120 dp of unintended bottom space; a 200% golden per
screen family.

### D4 — The card and the player
`AfterhoursCard`, `RunProgress`, `TimerRing`, the deal animation, the card player relaid
out, "Too far" moved to the bar.
**Exit**: `instant-card.png` re-recorded and unrecognisable; the three heats produce three
visibly different cards; `InstantAccessibilityTest` green at 200%.

### D5 — Home
Home relaid out per §6.1, live cards as the single hero, game rows, sections.
**Exit**: `home-alone.png` and `home-paired.png` still identical in structure (the U1
claim), and both look finished.

### D6 — Games
Every primitive renderer on the new card, the hand-off moment, game-specific states.
**Exit**: every game golden re-recorded; `GameLogicTest` still passes, so no renderer
learned a game's name.

### D7 — The rest
Plan, apart timeline, memories grid, auth, settings, pairing, loading and error states.
**Exit**: `./gradlew check`; every screen in the app has been through the frame.

### D8 — Brand
Monogram, launcher icon, splash screen, wordmark, store assets.
**Exit**: a cold start shows monogram → home with no white flash and no seam, on a device.

---

## §9 Gates

A visual change with no gate is a visual change that regresses in two phases.

- **Roborazzi** everywhere, as now. Every phase re-records and commits.
- **`ContrastTest`** gains a case for every new pairing, including each heat's accent on
  `surfaceCard` and on `surfaceRaised`.
- **`AccessibilityTest`** gains a 200% golden for each redrawn component, and every feature
  keeps its own suite.
- **New Konsist rule**: no emoji in a user-facing string outside content.
- **New Konsist rule**: no `Color(`, `.dp` literal or `tween(` with a literal duration
  inside a `:feature:*` composable. The convention exists; make it a build failure.
- **A gallery entry for every component**, as the existing convention already requires.
- **The device walk**, per phase, on the emulator. The goldens prove the pixels; only a
  device proves the feel.

---

## §10 Definition of done

The app is done looking like a prototype when all of these are true:

1. A screenshot of any screen, with all text replaced by grey boxes, still has a clear
   visual hierarchy.
2. No two different kinds of thing share the same shape, fill and border.
3. Every screen has exactly one primary action, and it is within thumb reach.
4. No screen has more than 120 dp of unintended empty space.
5. A Playful card and a Wild card are different objects.
6. There is not one emoji left in the app.
7. Nothing in the app is a Material 3 default.
8. Someone shown the home screen for two seconds can say what the app is for.
