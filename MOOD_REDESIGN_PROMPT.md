# Afterhours, after dark — a redesign brief

Hand this to whoever does the redesign: a model, a designer, or both in turn. It asks for **one
thing to be built now** — a self-contained HTML preview of the whole app in its new skin — and for
two port files that let the Android app take that skin later without changing what the app does.

It is written against branch `v2` as of commit `88d204c`. The app has been tested end to end on two
phones and every feature works. **Nothing in this brief may change what a feature does.** It
changes how the app looks, moves and feels.

---

## 0. The role

You are a senior product designer who has spent a career on intimacy and mood products for couples:
games, rituals, conversation tools, the things people open when the evening is already going
somewhere. You know that desire is mostly atmosphere and anticipation, that the best of these
products feel like a dim room and a private joke between two people, and that the fastest way to
kill the mood is a screen that looks like a settings page, a casino, or a health app.

You also know that consent is not a feature bolted onto a product like this — it is what makes
people trust it enough to go further. A couple turns the heat up in an app that makes stopping
easy.

## 1. The mood

This app is opened by two adults when they are turned on, playful, and up for something. The design
has to meet them there: **kinky in atmosphere, never explicit in imagery.**

What that means in practice:

- **A candlelit room, not a nightclub.** Low light, warm falloff, things glowing rather than lit.
  Darkness is the canvas; colour is heat.
- **Velvet, silk, wax and gold.** Deep wine and crimson grounds, soft sheen, antique-gold hairlines,
  a wax-seal mark. Materials you would want to touch.
- **Anticipation over reveal.** Cards arrive face down. Heat builds. The next tier is visible before
  it is unlocked. Every reveal is slow enough to feel.
- **Confident and playful, never crude or clinical.** Teasing copy, a wink rather than a leer. Bold
  type. Nothing shy, nothing medical, nothing that looks like a form.
- **Kink lives in the details**: a keyhole, a ribbon tie, a sealed envelope, a lock that glows when
  it opens, a flame that flickers on the tier you are on. Suggestive, ornamental, *abstract*.
- **No bodies.** No figures, silhouettes, lips, skin, nudity, or acts — in any illustration, icon,
  photo or texture. Abstract and non-figurative only. This is not prudishness: the app has discreet
  launcher icons (Notes, Calculator) and a panic gesture, it puts `FLAG_SECURE` on every content
  screen, and a phone that briefly shows a body on a lock screen preview or over a shoulder breaks
  the promise those features make.
- **Nobody else is in the room.** No imagery or copy that implies a third person, an audience, or
  being watched. Everything is between the two of them.

## 2. Hard constraints

1. **Every feature keeps working exactly as it does now.** No feature, game, tier, mode, setting,
   screen, flow or data path is removed, renamed or behaves differently. §4 is the list, and it is
   a regression checklist you run at the end.
2. **The content pipeline is untouched.** Cards are JSON in `content/packs/*.json` and game prompts
   in `content/prompts/*.json`, validated against `content/vocabulary.json` and bundled into
   `content/dist/bundle.json`. The spreadsheet route is `npm --prefix tools run export -- --out
   cards.tsv` and `import` back. There is no Excel file and no "Cards sheet"; do not invent one, and
   do not change a field name.
3. **Card copy is not yours.** It lives in the packs and is written under `CONTENT_GUIDE.md`. The
   design must hold any card the packs contain — the longest title, the longest body — without
   truncation. It does not rewrite them.
4. **UI copy may change, behaviour may not.** You may restyle and rephrase interface words (buttons,
   headings, empty states) in the app's voice, but every change goes in the changelog with the old
   and new wording, and a rephrased control must still do the same one thing.
5. **Safety controls stay one tap and always visible** (§4.3). A redesign that makes Stop smaller,
   prettier-but-hidden, or two taps away has failed, however good it looks.
6. **Ask before** anything that would alter behaviour, the data model, the content format, a
   permission, a notification's wording (`NotificationText` is a fixed table, D-088), a new
   dependency, or a bundled font that adds more than ~300 KB. Write the question down in the report
   and carry on with everything that does not depend on the answer.

## 3. Step 1 — Analyse before you design

Read the project and produce `design/redesign/ANALYSIS.md` **before drawing anything**. The
inventory below is a starting point to *verify*, not to copy — if the code says otherwise, the code
wins and the report says so.

Read at least: `CLAUDE.md`, `DECISIONS.md` (D-100 onward is the rebuild), `REDESIGN_PROMPT.md`,
`V2_DESIGN_PROMPT.md`, `DESIGN_PROMPT.md`, `CHAT_PROMPT.md`, `TWO_PHONE_PLAY.md`, the routes in
`core/navigation/.../Route.kt`, every `*Screen.kt` under `feature/`, the design system in
`core/designsystem/` (theme, components, icons, gallery), the shared screens in `core/ui/`
(`Safety.kt`, `TierPicker.kt`, `PrimitiveRenderers.kt`, `ScreenScaffold.kt`), `content/games.json`
and one file from each of `content/packs/` and `content/prompts/`. Look at the screenshot goldens
under `*/src/test/screenshots/` — they are the current design, rendered.

### 3.1 Screens (verify against `Route.kt`)

| Route | What it is |
|---|---|
| `Splash` | Resolves where to start |
| `AgeGate` | One tap, "We're both 18+"; "no" leaves the app |
| `Home` | Play Now, the Together / Apart toggle and the line saying what it means, the tier picker, the game grid, the "under way" card, a free-month banner, the paired line in Apart |
| `Start` | First launch, whole: "we're both adults" and how tonight feels (a tier), in two taps |
| `InstantPlay` | The card deck — the whole of Together mode's quick play |
| `GameNight` | A guided 30 / 45 / 60-minute evening: warm-up, building, peak, aftercare |
| `DealtGame(gameId)` | A game played on one phone |
| `Game(experienceId, twoPhones)` | A game played a phone each, paired |
| `Pairing` | Six-digit code, QR, and the `afterhours://pair?code=` invite link |
| `Boundaries` | The limits profile |
| `Store(level)` | The three packs |
| `Settings`, `NotificationSettings` | The hub: limits, notifications, app lock, panic, unpair and wipe, packs owned, privacy, terms, version |
| `AppLock`, `AppLockSetup` | PIN or biometric |
| *(layer, not a route)* | The chat sheet, drawn over every screen by `:app` (D-138) |
| *(debug only)* | `DesignGallery`, `ContentInspector` — out of scope |

### 3.2 Games (verify against `content/games.json`)

Six games ship, plus Game Night, which is not in `games.json` (M5):

| Id | Name | Steps |
|---|---|---|
| `HEAT_DICE` | Heat Dice | `DICE` — several dice rolled at once, one may carry a clock |
| `TRUTH_OR_DARE` | Truth or Dare | `CHOICE` → `CHALLENGE`, turn by turn |
| `FANTASY_MATCH` | Fantasy Match | `CHOICE` each, privately → `REVEAL` together |
| `WIN_IT_OWE_IT` | Win It / Owe It | `CHOICE` each → `REVEAL` |
| `WOULD_YOU_RATHER` | Would You Rather | `CHOICE` each → `REVEAL` |
| `HOT_OR_NOT` | Hot or Not Quiz | `CHOICE` (active) → `PREDICTION` (other) → `REVEAL` |

Games are content, not code: every game is drawn by **one renderer per primitive** in
`core/ui/.../PrimitiveRenderers.kt`. Design the primitives — dice, choice, challenge, reveal,
prediction, timer, media proof, the turn handover — not six bespoke screens. A seventh game added
in JSON tomorrow must look right with no design work.

### 3.3 The card model (verify against a pack)

`id`, `version`, `title`, `subtitle`, `body`, `pack`, `category`, `tags`, `intensity` (1–5),
`modes` (`TOGETHER` / `APART`), `interactionType`, `durationMin`, `moods`,
`requiredMutualPreferences`, `boostedByPreferences`, `excludedByBoundaries`, `chapterKinds`,
`noveltyWeight`, `repeatCooldownDays`, `requiresMedia`, `status`, `locale`, timestamps. Twelve packs:
after-dark, confessions, couple-challenges, deep-talk, fantasy-talk, flirt, long-distance,
power-dynamics, roleplay, sensory, surprise, tease. A pack is offered as a **lane** only when it has
cards at the current tier (D-125).

On a card face a couple sees: title, subtitle, body, the tier (label and dots), duration when there
is one, and a timer when the card asks for one. The rest drives filtering and is never shown.

### 3.4 The five tiers

`Intensity`: Mild (1), Flirty (2), Spicy (3), Hot (4), Extreme (5). A tier is a **ceiling**, not a
filter for one level. Today each is its own hue — Mild brass, Flirty plum, Spicy rose-burgundy, Hot
oxblood, Extreme ember — and the tier owns every accent on the screen through `ProvideTier`
(`TierColors`: `accent`, `onAccent`, `rim`, `onDark`, `container`, `onContainer`). Mild and Flirty
are free for ever; Spicy, Hot and Extreme are one-time packs, and **a pack opens every level below
it**. Everything is open for the first month after install.

### 3.5 Consent and safety, as built

- **Stop** (`SafeWordAction`, a shield) is on every screen that plays anything. One tap, no
  question. It lands on "Everything's stopped" with exactly two ways on: **Turn it down to Mild**
  or **End here**. A two-phone game has it in `StopPauseBar` beside Pause.
- **Too far** is about one card, not the evening: it asks "Skip this kind of thing?" with **Just
  this one** or **Never show me these again**, says "your partner never sees this", and is
  honoured from the next card. It is private to this phone.
- **The Extreme gate**: "Both of you want this?", two taps ("We're both in" → "Yes — both of us"),
  **once per session**, and "Not tonight — turn it down" in one. It shows no Extreme content on the
  gate itself.
- **The limits profile**: eight tags — Kissing, Touch, Teasing, Role-play, Sensory, Verbal, Power
  play, Fantasy — each Yes / Maybe / No, over twenty-seven taxonomy themes. Stored on this phone. A
  card touching a "No" never appears.
- **"Turn it up?"** after five yeses in a row, with **Keep it here**. Never automatic.
- **Aftercare** after any Hot or Extreme run, from cards written to land on.
- Skip is always free. There is no limit that can stop an evening that has started.

### 3.6 Apart mode

Pairing by code, QR or link; Apart is chosen on pairing. A game played a phone each shows the round
on **both** phones; only the person whose turn it is can act; the watching phone sees whose turn it
is and what they drew — and sees **nothing** of a round with a private step. A reveal waits until
everyone it asked has answered. "Reconnecting…" and "Your partner dropped out — waiting…" are real
states. When the *other* phone starts a game, this phone's "under way" card pulses and buzzes once
(D-141). The chat is a sheet over everything: text, photos (camera or gallery), voice notes with a
waveform, once-only photos that burn on viewing, an unread mark, a sending state.

### 3.7 Discretion

Discreet launcher icons (default, **Notes**, **Calculator**). **Panic**: two taps of back locks the
app (and asks for the PIN if one is set). `SecureScreen()` on every content screen. Notifications
never say what was sent — the wording is a fixed table. These are features: the redesign must not
put anything on screen that defeats them, and the panic and lock screens must stay *neutral*.

### 3.8 The design system today

Tokens through `AfterhoursTheme.colors / .tier / .spacing / .motion / .elevations` and Material
slots through `MaterialTheme`. **Dark only** (D-009). Fonts bundled: **Playfair Display** (display)
and **Inter** (body), both variable. Spacing on a 4 dp step with semantic gaps (`tight`, `item`,
`group`, `section`, `gutter` 24, `touchTarget` 48). Shapes 8 / 12 / 16 / 20 / 28. Motion `quick`
120 → `ambient` 2400 ms, with `decorativeTween` collapsing under reduce-motion. Depth from
`Modifier.raised` (fill + rim light) and `Modifier.bloom` (glow). Icons: a 28-glyph line set,
24 dp, 1.5 dp stroke, no emoji (`AfterhoursIcons`: Flame, Dice, Spark, Moon, Clock, Lock, Shield,
Heart, Eye, Gift, Message, Mic, Camera, Send, Play, Pause, Stop, …). Components: `GlowButton`,
`CinematicCard`, `AfterhoursCard`, `RevealCard`, `TierPicker`, `CountdownRing`, `StopPauseBar`,
`ScreenScaffold`, `ChoiceRow`, `LockedLevelCard`, and the rest of `ComponentGallery.kt`.

### 3.9 What to look for

Say plainly where the current design fails the mood. At minimum assess: where it reads as a
settings page; whether the tier progression *feels* like heat rising or like five colour swatches;
whether a card's arrival has any anticipation; whether Stop and Too far are visible without being
alarming; whether pairing state is legible at a glance; how much of each screen is chrome versus
the thing being played; and anything that looks generic or templated. Use the goldens as evidence
and cite them by file name.

## 4. The regression checklist

Copy this into the report, verify each item against the code, add anything the code has that this
list missed, and at the end mark each **pass / fail / not in preview** for the HTML build. Every
"not in preview" needs a reason.

**Entry and home**
1. Age gate is one tap; "no" leaves the app.
2. Play Now reaches the first card within two taps of opening.
3. The Together / Apart toggle decides whether a game opens on one phone or on both, and the line
   under it says so.
4. The tier picker shows all five tiers; locked tiers say they are locked and open the store on tap.
5. The game grid shows all six games and Game Night.
6. The "under way" card appears for a session in progress; pulses and buzzes once only when the
   other phone started it.
7. Settings and (when paired) the chat are both reachable from home's bar, and neither covers the
   other (D-141).

**Playing**
8. The deck climbs to the chosen tier and never repeats a card in a run.
9. Lanes (packs) are offered only when they have cards at this tier.
10. Done and Skip advance; Skip is free.
11. A card with a duration offers its timer; the timer counts and ends.
12. "Turn it up?" appears after five yeses and never raises the tier on its own.
13. "Nothing left at this tier" is a real state, with a way on.
14. Aftercare follows any Hot or Extreme run.
15. Game Night offers three lengths, plays four phases, lights only the peak, and ends on aftercare.
16. Every game renders through its primitives; the turn handover asks for names and says whose turn
    it is.

**Consent and safety**
17. Stop is on every playing screen, one tap, no question.
18. Stop lands on "Everything's stopped" with Turn it down to Mild and End here.
19. Too far asks one question with two answers and is private to this phone.
20. The Extreme gate is two taps to confirm, one to turn down, once per session, and shows no
    Extreme content.
21. The limits profile has eight tags × Yes / Maybe / No, and a "No" hides matching cards.

**Apart**
22. Pairing works by code, QR and link.
23. A game a phone each shows the round on both phones; only the active phone can act.
24. The watching phone sees whose turn it is and what they drew, and nothing of a private step.
25. Reconnecting and partner-away states are shown.
26. The chat opens over any screen without disturbing the game underneath; text, camera photo,
    gallery photo, voice note, once-only photo, sending and failed states all exist.
27. Unpair and wipe is in settings.

**Discretion and money**
28. Discreet launcher icons, panic (two backs), app lock and secure screens all remain, and the
    panic and lock screens give nothing away.
29. The store sells Spicy, Hot and Everything; a pack opens every level below it; restore exists.
30. The first-month free banner shows a date, never a countdown.

**Accessibility**
31. Every control is at least 48 dp; everything clickable is labelled.
32. Reduce-motion collapses decorative movement; reveals keep their timing.
33. Text holds AA contrast on its actual background; 200% type does not clip.

## 5. Design direction

### 5.1 Palette

Start here and adjust only to pass contrast. Keep every value as a token (§8).

| Role | Value | Use |
|---|---|---|
| Base | `#0A0508` | The room. Near-black with red in it, never neutral grey |
| Velvet | `#1A0910` → `#2A0716` | Raised surfaces, sheets |
| Wine | `#5A0F26` | Containers, selected states |
| Ember | `#FF3D5A` | The primary action at heat |
| Flame | `#FF7A3D` | The hottest highlights, the flame tip |
| Gold | `#D9A54E` | Hairlines, the seal, locks, ornament — sparingly |
| Blush | `#F6D6CC` | Primary text |
| Blush muted | `#CDB0A8` | Secondary text |
| Blush faint | `#9C8580` | Hints, timestamps — must still pass AA on its surface |

**The heat ramp goes cool to hot**, and each tier is a *different hue* a person can tell apart at a
glance — five shades of one red is not a ramp (the reason the app moved away from it once already).
Starting points:

| Tier | Accent | Feel |
|---|---|---|
| Mild | `#C7A9D9` | Dusky lilac blush — cool, soft |
| Flirty | `#F08AB4` | Orchid pink |
| Spicy | `#FF5C7A` | Rose ember |
| Hot | `#E8283F` | Scarlet |
| Extreme | `#B3122E` core with `#FF7A3D` flame | Deep crimson that burns at the edges |

For each tier define all six `TierColors` roles (`accent`, `onAccent`, `rim`, `onDark`,
`container`, `onContainer`) and **compute** every text pairing's contrast in the preview — a
visible contrast table in the HTML's token panel, not a claim.

### 5.2 Type

- **Display and card copy**: a high-contrast serif. The app already bundles **Playfair Display**;
  use it unless another serif earns its bytes. **Cormorant Garamond** is a credible alternative
  for a more sensual italic — but its small sizes are fragile on a phone, so if you propose it, show
  a card body at 16 px in both and choose on legibility.
- **UI labels**: a geometric sans. Inter is bundled and is neo-grotesque, not geometric; propose
  **Jost** or **Outfit** (both OFL, both on Google Fonts) if you want the geometric feel, and say
  what it costs to bundle.
- **Metadata**: small caps with wide tracking (the current `EyebrowTextStyle` is this).
- Body text never below 16 px. Card titles get real scale — a card is the hero of its screen.
- Italic serif is the voice of the teasing line (subtitles, "tap to reveal"), not of everything.

### 5.3 Texture and light

- An SVG `feTurbulence` grain over everything, at very low alpha (the app already draws grain at
  3.5%).
- Velvet: layered radial gradients with a soft sheen highlight, not flat fills.
- Light comes from somewhere: a warm key glow behind the card, a cooler bounce, a vignette.
  Glows fade a colour to **its own zero alpha**, never to `transparent`, or they go muddy on this
  ground (a CLAUDE.md gotcha).
- Gold hairlines as 1 px borders that catch the light at the top edge (the app's rim light).
- The background stays calm. The card is the only lit object on a playing screen.

### 5.4 Motion

Slow and deliberate. Nothing bounces.

- **The reveal**: cards arrive face down and flip in 3D — perspective, ~700 ms (`reveal`), a slight
  lift and a glow that blooms as the face turns. This is a *reveal*, so it keeps its timing under
  reduce-motion as a cross-fade.
- **The flame**: the selected tier's flame flickers — an organic, low-amplitude wobble on the flame
  path and its glow. The hotter the tier, the livelier.
- **The heat bar**: a gradient bar under the tier picker fills to the chosen tier with an ember
  front.
- **The pair**: a slow pulse on the paired indicator, a heartbeat rather than a blink.
- **The gate**: the lock warms from gold to flame when both have confirmed, then opens.
- Everything decorative honours `prefers-reduced-motion` by settling to its resting state.

### 5.5 Iconography and ornament

A single line-icon language, matching the current spec (24 px box, 1.5 px stroke, round caps and
joins), extended with:

- **Flames** at one to five tongues, one per tier, plus a filled "active" variant.
- **A wax seal** as the brand mark — an abstract monogram or keyhole pressed into wax, drawn in SVG.
- **A card-back sigil**: a symmetric ornament (keyhole, ribbon knot, interlaced lines) for every
  face-down card.
- **Lock** (closed, warming, open), **shield** (Stop), **check-shield** (limits), **link** (pairing),
  **timer**, **pause**, a **keyhole** (discreet / panic), **envelope with seal** (chat, sent proof).
- No emoji anywhere. No bodies, lips or silhouettes anywhere.

### 5.6 Voice

Short, warm, teasing, adult. The app talks *to both of them*. Examples of the register:

- "Pass — no questions asked." not "Give up".
- "Both of you want this?" not "Unlock Extreme".
- "Breathe. Check in with each other." after an intense card, never "Are you sure?".
- Never shame, push, count, compare or dare anyone past a limit. Never imply someone is watching.

## 6. Assets — use what exists, build what does not

**Use**
- The bundled fonts, `AfterhoursIcons` (as SVG paths — the path strings are in
  `AfterhoursIcons.kt`; reuse them so the preview's icons *are* the app's icons), the current
  palette as the before-state, and the goldens as reference.
- The **ui-ux-pro-max** skill in `.claude/skills/ui-ux-pro-max`. Query it for targeted guidance, not
  for a style: `--stack jetpack-compose` for anything you will port, and `--domain ux` with one
  observable outcome at a time for accessibility (`"focus not obscured"`, `"touch target size"`,
  `"reduced motion"`). Run its scripts from outside the repo directory. **Do not adopt its
  `--design-system` default** for this product — asked for one, it returns a landing-page pattern
  on a light palette ("Liquid Glass", `#FAFAF9`), which is the opposite of this brief. Its useful
  outputs here are the font pairings (it independently suggests Cormorant for luxury), the
  no-emoji rule and the pre-delivery checklist.

**Build in SVG or CSS** — never source external images:
the flame set, the wax seal, the card-back sigil, the lock states, a silk-fold background, candle
glow, gold ornamental rules and corner flourishes, the grain filter, velvet gradients.

**Do not use** stock photography, AI imagery, or any supplied artwork without asking first — and
never anything figurative or sexual.

## 7. The preview — every screen, every state

One HTML file, `design/redesign/afterhours-redesign.html`. It is a **working prototype**, not a
poster: the controls do what the app's controls do, against sample data.

### 7.1 The frame

- A phone frame at **392 × 840** (the size every golden is rendered at) centred on desktop; full
  bleed from **360 px** wide on a phone.
- A slim, collapsible **designer panel** beside the frame (below it on mobile): jump to any screen
  or state, toggle Together / Apart, toggle "paired", switch tier, toggle reduce-motion, toggle
  200% type, and show the token and contrast tables. The panel is chrome for reviewing; it is not
  part of the design.
- One-handed reach: primary actions in the bottom third, nothing essential in the top corners
  except the bar's own controls.

### 7.2 Sample data

Pull real cards from `content/dist/bundle.json` with a short Node script and embed them as JSON —
at least two per tier, across several packs, including the **longest title and longest body in
the bundle**, a card with a duration, and an aftercare card. Real copy is the only honest layout
test. Embed the real `games.json` steps and a few real prompts per game.

### 7.3 Screens

For every screen design the **normal, empty, loading and error** states where they exist.

1. **First launch (`Start`) and the age gate** — the seal, one line, the tier for tonight, one
   button, a quiet way out. Two taps from a fresh install to the first card.
2. **Home** — the brand mark; the Together / Apart switch and its one-line meaning; in Apart, a
   **pairing strip** (paired / not paired, a gently pulsing indicator when paired, a way to pair
   when not); the heat selector; Play Now; the game grid; the "under way" card in its calm and its
   pulsing states; the free-month banner; settings and chat in the bar, side by side.
3. **Heat selector** — five flames with a gradient heat bar; the active tier's flame flickering; its
   one-line hint ("Warm. Nothing you would blush about.", "Now you're blushing." … in the app's
   voice — propose the five); locked tiers with a lock and the price; Extreme behind its gate.
   Screen-reader text per level: "Level 3 of 5, Spicy" (and "locked" when it is).
4. **Card stage (InstantPlay)** — the face-down card with the sigil and "tap to reveal"; the 3D flip;
   the face with title, subtitle, body, tier tag, duration and timer; the lane picker; and the
   action row (§7.4).
5. **The timer** — a countdown ring in the tier's colour, with its last stretch warming to flame.
6. **Turn it up?** — after five yeses.
7. **Nothing left at this tier.**
8. **Run complete** — played count, and the aftercare card with a "Check in with each other" line.
9. **Stopped** — calm, no accent, no bloom: "Everything's stopped", Turn it down to Mild, End here.
10. **Too far** — the one question and its two answers, with "your partner never sees this".
11. **Extreme gate** — no Extreme content anywhere on it; two taps; the lock warming and opening.
12. **Game Night** — the length choice (three cells, as built in D-142), a phase card, the peak
    lit, the ending.
13. **A game on one phone** — the turn handover with names; Heat Dice rolling (dice faces, the clock
    die); Truth or Dare's choice then challenge; a private choice; a reveal of both answers; a
    prediction; a media proof step.
14. **A game a phone each** — the active phone, and the **watching** phone beside it showing whose
    turn it is and what they drew; a private step (the watcher sees nothing); waiting for the other
    to answer; reconnecting; partner away; the Pause / Stop bar with the chat beside it.
15. **Pairing** — the six-digit code, the QR, entering a code, the moment two phones connect.
16. **Limits** — as a **bottom sheet**: the eight tags, Yes / Maybe / No each, and the sentence
    "Anything touching a No is hidden — for both of you."
17. **Chat** — the sheet over a game; bubbles in runs; a photo, a once-only plate, a seen-and-gone
    plate, a voice note with waveform playing, a recording bar, something sending, something failed,
    the empty state.
18. **Store** — three packs, what each opens, owned and restore states.
19. **Settings** — every row it has today, and the discreet-icon picker showing the three icons.
20. **App lock and panic** — neutral. A lock screen that gives nothing away is the design.

### 7.4 The action row

**Done**, **Skip**, and **Too far**, as they behave today, styled as one row in reach of a thumb.
Too far is a quieter control than Done but never hidden. **Stop** is separate from the row and
present on every playing screen — the shield, one tap.

If you want to rename them (the pasted direction suggests "Do it", "Pass", "Next"), propose it in
the changelog with the reasoning and keep today's behaviour exactly: a rename that merges two
controls or splits one is a behaviour change, and is asked about first.

### 7.5 Behaviour the prototype must actually simulate

Mode switch; tier select (locked tiers open the store screen); Play Now → face-down → flip →
Done / Skip / Too far → next card; five yeses → Turn it up?; the timer running; Stop from any
playing screen → Stopped → Mild or End; the Extreme gate's two taps and one-tap turn-down; Game
Night length → phases → aftercare; one dealt game end to end; the two-phone pair side by side with
the turn passing; opening and closing the chat over a game; the limits sheet hiding a card tagged
with a "No". Keep the logic small and readable — it exists to prove the design, not to reimplement
the engine.

## 8. Accessibility and responsiveness

- 360 px to desktop, no horizontal scroll.
- Every target ≥ 48 px (the app's own rule, stricter than 44).
- WCAG AA for all text on its real background, including text over glows and gradients — measure
  the worst point, not the average.
- Visible focus rings in the gold hairline style; full keyboard path through the prototype.
- Every icon-only control labelled; flames read as "Level 3 of 5, Spicy"; decorative ornament
  hidden from screen readers.
- `prefers-reduced-motion`: decoration settles; reveals cross-fade.
- At 200% type nothing clips — the panel's toggle proves it on every screen.

## 9. Deliverables

All under `design/redesign/`:

1. **`ANALYSIS.md`** — §3's report, with the verified inventory, the problems found (citing goldens),
   and the open questions from §2.6.
2. **`afterhours-redesign.html`** — one self-contained file: CSS and JS inline, SVG assets inline,
   fonts from Google Fonts with real fallback stacks. **Design tokens as CSS custom properties at
   the top of the file**, named to match the Compose tokens they will become
   (`--color-scene`, `--tier-spicy-accent`, `--space-gutter`, `--motion-reveal`, …).
3. **`tokens.json`** — the same tokens shaped like the app's theme objects: `palette`,
   `colorScheme` (Material slots), `afterhoursColors`, `tierColors` for all five tiers × six roles,
   `spacing`, `shapes`, `motion` (durations and easings), `typography` (family, size, line height,
   weight, tracking per style), `elevations`. Every colour pairing used for text listed with its
   computed contrast ratio.
4. **`COMPONENT_MAP.md`** — a table: new element → the existing composable it restyles (with its
   file path) → **restyle only / new token / new component** → notes for the port. Every new
   component is flagged, because each one needs a gallery entry, a golden and an accessibility test
   when it is ported.
5. **`CHANGELOG.md`** — what changed visually; what was kept; every UI-copy change, old → new; and
   behaviour changes, of which there should be **none**. Any that you believe are necessary are
   listed as *proposals awaiting a yes*, not as changes made.
6. **The regression checklist** (§4), completed, at the end of `ANALYSIS.md`.

## 10. Definition of done

- The analysis exists and was written first.
- Every screen and state in §7.3 is reachable from the designer panel.
- Every item in §4 is marked, and nothing is "fail".
- The token table and contrast table are in the HTML and agree with `tokens.json`.
- No bodies, no third parties, no emoji, no external images, no shame.
- Nothing in the repo outside `design/redesign/` has changed.

## 11. After this — the port, so the preview is built for it

Not part of this brief, but the preview must make it straightforward. The port will change
`core/designsystem` (palette, `tierColors`, typography, icons, components) and nothing else
structurally; it will re-record every golden, re-run `ContrastTest` and `AccessibilityTest`, and
pass `./gradlew check`. So: no effect in the HTML that Compose cannot draw (blur behind content is
expensive on Android — prefer glows and gradients), no layout that depends on hover, and every
value a token.
