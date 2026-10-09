# Afterhours — the app as it is

The first deliverable of `MOOD_REDESIGN_PROMPT.md`, written before anything was drawn. It verifies
the brief's inventory against the code, says where the current design fails the mood, lists what
needs an answer before it can change, and ends with the regression checklist the redesign is
measured against.

Branch `v2` at `f8754c3`. Evidence is cited as code paths and as screenshot goldens
(`<module>/src/test/screenshots/<name>.png`), which are the current design, rendered.

---

## 1. What was read

`CLAUDE.md`; `DECISIONS.md` from D-100; the briefs (`REDESIGN_PROMPT.md`, `V2_DESIGN_PROMPT.md`,
`DESIGN_PROMPT.md`, `CHAT_PROMPT.md`, `TWO_PHONE_PLAY.md`); `core/navigation/.../Route.kt`; every
`*Screen.kt` under `feature/`; `core/designsystem/` (`Color.kt`, `ColorScheme.kt`, `Type.kt`,
`Dimens.kt`, `Motion.kt`, `AfterhoursIcons.kt`, the components); `core/ui/` (`Safety.kt`,
`ScreenScaffold.kt`, `PrimitiveRenderers.kt`, `TierPicker.kt`); `core/model/` (`Intensity`,
`LimitTag`, `BoundaryLevel`); `content/games.json`, one file from each of `content/packs/` and
`content/prompts/`; and 96 goldens across eleven modules.

## 2. Screens and flows

### 2.1 Routes

| Route | What it is | Golden |
|---|---|---|
| `Splash` | Resolves where to start | — |
| `Start` | First launch, whole: "We're both 18 or over — start", "I'm not 18", and **how far tonight** (a tier). No account, no email | `instant-start.png` |
| `AgeGate` | The returning-phone age check: "This is an app for adults", **I'm not** leaves | `age-gate.png` |
| `Home` | Wordmark, headline, Together / Apart toggle and its one line, the tier picker, "Or play something" (Game Night and six games), the under-way card, the free-month banner, the paired line in Apart, **Play Now** docked; settings and (paired) chat in the bar | `home-*.png` (10) |
| `InstantPlay` | The deck: a card, its timer, the tier picker, lanes, Skip / Done; Too far and Stop in the bar | `instant-*.png` |
| `GameNight` | Three lengths → four phases → aftercare | `game-night-*.png` |
| `DealtGame(gameId)` | A game on one phone, through the primitives | `dealt-game*.png` |
| `Game(experienceId, twoPhones)` | A game played a phone each | `game-*.png` (16) |
| `Pairing` | Choose / invite (code, QR, link) / enter / waiting / confirm | `pairing-*.png` |
| `Boundaries` | The limits profile | `limits-*.png` |
| `Store(level)` | Three packs | `store-*.png` |
| `Settings`, `NotificationSettings` | The hub | `settings-hub.png`, `notifications-*.png` |
| `AppLock`, `AppLockSetup` | PIN or biometric | `lock-*.png` |
| *(layer)* | The chat sheet, over every screen (D-138) | `chat-*.png` (11) |
| *(debug)* | `DesignGallery`, `ContentInspector` — out of scope | — |

### 2.2 Together — one phone

1. Fresh install → `Start`: confirm age and choose a tier → `InstantPlay`, first card. **Two taps.**
2. Returning → `Home`. **Play Now** → the deck. Or a game: Game Night → length → phases; any of the
   six → `DealtGame`, which asks for names when the game takes turns and hands the phone over each
   round.
3. Anywhere in the deck: **Done**, **Skip**, **Too far**, **Stop**; the tier changed with one
   thumb; a lane chosen; a card's timer started.
4. A run ends → "Run complete", with an aftercare card after Hot or Extreme.

### 2.3 Apart — two paired phones

1. `Home` → Apart → **Pair** → `Pairing`: one phone shows a code, QR and link; the other enters or
   scans it; both confirm. Pairing sets Apart.
2. A game started on one phone → a session. The other phone's home shows the **under-way card**,
   which pulses and buzzes once because the *other* phone started it (D-141); "Pick it up".
3. In `Game(twoPhones = true)` both phones show the round. The active phone acts; the watching
   phone sees whose turn it is and what they drew, or nothing at all if the round has a private
   step. A reveal waits for everyone it asked. "Reconnecting…" and "Your partner dropped out —
   waiting…" are states. **Pause** and **Stop** sit in the bottom bar, with the chat beside them.
4. The **chat** opens over any screen from the bar: text, a photo from the camera or the gallery,
   a voice note with a waveform, a once-only photo that burns on viewing, sending and failed states.
5. **Unpair and wipe** in settings clears the pair on both phones.

## 3. Features, verified

### 3.1 Games

Six ship, all in `content/games.json`, each with a prompt file in `content/prompts/`:

| Id | Name | Steps |
|---|---|---|
| `HEAT_DICE` | Heat Dice | `DICE` (active) — What / Where / How long, the last with a clock |
| `TRUTH_OR_DARE` | Truth or Dare | `CHOICE` → `CHALLENGE` (active) |
| `FANTASY_MATCH` | Fantasy Match | `CHOICE` each → `REVEAL` together |
| `WIN_IT_OWE_IT` | Win It / Owe It | `CHOICE` each → `REVEAL` together |
| `WOULD_YOU_RATHER` | Would You Rather | `CHOICE` each → `REVEAL` together |
| `HOT_OR_NOT` | Hot or Not Quiz | `CHOICE` (active) → `PREDICTION` (other) → `REVEAL` |

**Game Night** is the seventh thing on the grid and is not a game in `games.json` (M5): a plan over
the deck in four phases — Warm-up 25 %, Building 35 %, Peak 40 %, then one aftercare card.

Every game is drawn through one renderer per primitive in `core/ui/.../PrimitiveRenderers.kt`. The
design is of **primitives**, not of six screens.

### 3.2 Tiers

`Intensity`: **Mild 1, Flirty 2, Spicy 3, Hot 4, Extreme 5**, each with a label and dots
(`●●●○○`). A tier is a ceiling. Today's accents: Mild brass `#D9B978`, Flirty plum `#C49BC4`, Spicy
rose `#C75C7A`, Hot oxblood `#B02A42`, Extreme ember `#E2603F`, each with six `TierColors` roles.
Mild and Flirty are free; Spicy, Hot and Extreme are one-time packs (`afterhours.pack.spicy`,
`.hot`, `.everything`), and a pack opens every level below it. Everything is open for a month after
install, and `everything_free` in Remote Config opens it globally.

### 3.3 The card

Verified on `content/packs/after-dark.json`: `id`, `version`, `title`, `subtitle`, `body`, `pack`,
`category`, `tags`, `intensity`, `modes`, `interactionType`, `durationMin`, `moods`,
`requiredMutualPreferences`, `boostedByPreferences`, `excludedByBoundaries`, `chapterKinds`,
`noveltyWeight`, `repeatCooldownDays`, `requiresMedia`, `status`, `locale`, `createdAt`,
`updatedAt`. Twelve packs. **There is no Excel file**: the spreadsheet route is the TSV
export/import in `tools/`.

A card face shows title, the tier and duration as an eyebrow (`HOT · 5 MIN`), body, and who it is
for ("Whoever's holding the phone"). `subtitle` is in the data but not drawn on the deck's card —
the redesign may draw it; that is a visual change, not a behaviour change.

### 3.4 Consent and safety

| Control | Where it is | What it does |
|---|---|---|
| **Stop** (`SafeWordAction`, shield) | `InstantPlay` bar; `StopPauseBar` in the two-phone game | One tap, no question → "Everything's stopped" → **Turn it down to Mild** or **End here** |
| **Too far** | `InstantPlay` bar | "Skip this kind of thing?" → **Just this one** / **Never show me these again**; "your partner never sees this"; private |
| **Extreme gate** | `InstantPlay` only | "Both of you want this?" → **We're both in** → **Yes — both of us**; **Not tonight — turn it down**; once per session |
| **Limits** | `Boundaries` | Eight tags (Kissing, Touch, Teasing, Role-play, Sensory, Verbal, Power play, Fantasy) × Yes / Maybe / No, over 27 themes, on this phone only |
| **Turn it up?** | `InstantPlay` | After five yeses in a row; **Keep it here**; never automatic |
| **Aftercare** | Deck and Game Night | After any Hot or Extreme run |
| **Pause** | Two-phone game | Takes the game off the screen, never its controls |

### 3.5 Discretion

Launcher icons: default, **Notes**, **Calculator** (aliases in the manifest). **Panic**: two taps of
back locks the app, and asks for the PIN if one is set. **App lock**: PIN or biometric.
`SecureScreen()` (`FLAG_SECURE`) on every content screen. Notifications say nothing — a fixed
table (D-088).

### 3.6 The design system

Dark only. Palette: Scene `#0B0708`, Raised `#171115`, Card `#1F1820`, Cream text `#F2E6DA`. Fonts:
**Playfair Display** and **Inter**, both variable and bundled. Spacing on a 4 dp step with semantic
gaps; `gutter` 24, `touchTarget` 48. Shapes 8 / 12 / 16 / 20 / 28. Motion 120 → 2400 ms with
`decorativeTween` honouring reduce-motion. Depth from `raised` (fill + rim light) and `bloom`
(glow). Grain at 3.5 %. A 28-glyph line icon set, 24 dp / 1.5 dp stroke, no emoji.

## 4. Where the code disagrees with the brief

The brief's inventory said to trust the code. These are the places the code says something
different, and two of them matter.

1. **Stop is not on every playing screen.** It is on the deck (`InstantPlayScreen.kt:122`) and in
   the two-phone game's `StopPauseBar`. It is **absent from `DealtGameScreen`** (a game on one
   phone — see `dealt-game-dice.png`: a back arrow and nothing else) **and from `GameNightScreen`**.
   M7 says the safe word is on every screen that plays anything. This is a pre-existing gap, not
   something the redesign introduced.
2. **The Extreme gate is only on the deck.** `ExtremeGateContent` is used by `InstantPlayScreen`
   alone. A Game Night or a dealt game at Extreme reaches Extreme content with no "both of you"
   confirmation (`DealtGameViewModel.kt:98` deals at the phone's tier, Extreme included).
3. **Too far is only on the deck.** A dealt game has "Not this one" (skip the prompt), which is
   the right control for a game, but no way to say "never this kind of thing".
4. **There are two age checks**, `Start` for a fresh install and `AgeGate` for a returning one.
   Both are in scope.
5. `Intensity.requiresBothPartyConsent` is true from **Hot** up, and governs a paired session's
   consent on the server. The gate a couple sees is Extreme only. Neither changes here.
6. The deck's card does not draw `subtitle`.
7. **The lock screen gives the app away.** `AppLockScreen` says "Welcome back" and "Unlock your
   private world". With the launcher icon set to Notes or Calculator, that second line tells anyone
   looking over a shoulder what this is — the one thing the discreet icons exist to prevent.

Items 1–3 are behaviour, and the brief says behaviour is asked about rather than changed. **The
preview shows Stop, the gate and Too far where M7 says they belong, marked "proposed", and the app
is unchanged until you say yes.**

## 5. Where the design fails the mood

The current design is careful, consistent and readable, and it looks like a well-made settings app
that happens to be dark red. Specifically:

1. **Heat does not look like heat.** The tier picker is five rows of dots in a black trough
   (`home-paired.png`, `instant-card.png`). Five dot patterns are an abstraction you have to count;
   nothing warms, nothing flickers, and the only sign of the tier is a small eyebrow word. The
   colours change, but they are applied to chrome, not to the room.
2. **Nothing is anticipated.** A card arrives face up (`instant-card.png`). The single most
   sensual mechanic in a game like this — not knowing what is coming, then finding out — is spent
   before the screen appears. `RevealCard` exists in the design system — the games' reveal primitive
   uses it ("Ready?") — and the deck, where most of the evening happens, does not.
3. **Home is a list.** "Or play something" is seven identical rows with the same sparkle glyph and
   a chevron (`home-paired.png`). It reads as a settings menu. The six games have no identity.
4. **The deck screen repeats home.** The full tier picker sits under the card
   (`instant-card.png`), so a third of the playing screen is a control the couple set a minute ago.
5. **Stop and Too far are in the top corner** (`instant-card.png`): visible, but out of thumb reach
   on a 6.5-inch phone, and styled as small text actions. The two-phone game puts Stop in the
   bottom bar (`game-watching.png`); the deck puts it at the top. Same control, two places.
6. **The watching phone is mostly empty** (`game-watching.png`): a card in the top third and two
   thirds of nothing. The moment of watching your partner do a dare is the most charged moment in
   Apart mode, and it is drawn as a status message.
7. **The limits screen is a form** (`limits-answered.png`): eight segmented controls in a scrolling
   column. It is honest and clear, and it is the least intimate screen in the app — exactly where
   trust is built.
8. **No brand mark.** The wordmark is set type (`AFTERHOURS` in tracked caps). There is no seal, no
   symbol, nothing a couple would recognise.
9. **Materials are flat.** Raised surfaces are near-black fills with a rim; there is no velvet, no
   sheen, no warmth in the light. The grain is there; the room is not.
10. **Pairing state is a line of text.** "Paired with their phone" in settings and a line under
    the toggle. Nothing shows the connection is *live*.
11. **The voice is right; the type is too polite.** Headlines are good ("One phone, both of you.")
    but there is no italic, no tease, no contrast between the line that flirts and the line that
    instructs.

What is good and stays: the dark ground; Playfair for headlines; one primary action docked at the
bottom; one lit object per screen; the honest, warm copy; 48 dp targets; reduce-motion handling.

## 6. Open questions — answers needed before the app changes

The preview proceeds on the assumption in **bold**; nothing in the app changes until you answer.

1. **Add Stop to one-phone games and Game Night?** (§4.1) **Assumed yes — shown in the preview as
   proposed.** It restores M7; it is a new control on two screens.
2. **Gate Extreme in Game Night and one-phone games too?** (§4.2) **Assumed yes, once per session,
   shared with the deck's gate.**
3. **Too far in one-phone games?** (§4.3) **Assumed yes, beside "Not this one".**
4. **Face-down cards with tap-to-reveal on the deck.** This adds one tap per card. It is the heart of
   the new mood, and it is a behaviour change. **Assumed: shown in the preview, with a setting to
   deal face up.** Alternative: the card turns over by itself after a beat, keeping today's tap
   count.
5. **Fonts.** Playfair Display stays for display. **Assumed: Inter stays** for UI (bundled, proven at
   200 %); Jost is shown as an alternative in the designer panel, ~120 KB to bundle.
6. **Action-row names.** **Assumed: Done, Skip, Too far are kept.** "Do it / Pass / Next" was
   suggested; "Pass — no questions asked" is used as the *explanation* under Skip, not as a rename.
7. **The tier picker leaves the deck screen** and becomes a single heat chip that opens it. Same
   control, one tap further. **Assumed yes** — say no and it stays inline.
8. **The lock screen says "Enter PIN" and nothing else** (§4.7). A copy change, but one that alters
   what the screen discloses. **Assumed yes.**

## 7. Regression checklist

Run against the prototype on 9 October 2026, by a script driving every control in the page (42
checks) plus a sweep of all 58 states for target size, overflow at 360 px and console errors.
**Pass** — the prototype does it. **Proposed** — drawn, but a behaviour change from §6 awaiting a
yes. Nothing is *not in preview*, and nothing failed.

| # | Item | Result | Note |
|---|---|---|---|
| | **Entry and home** | | |
| 1 | Age gate is one tap; "no" leaves | Pass | Both `Start` and the returning `AgeGate` |
| 2 | Play Now reaches the first card within two taps | Pass | One tap to the card. Reading it is a second when dealt face down — proposal §6.4 |
| 3 | Together / Apart decides one phone or both, and says so | Pass | The app's own line under the switch |
| 4 | All five tiers; locked ones say so and open the store | Pass | A lock on the cell, "Level 4 of 5, Hot, locked" to a screen reader |
| 5 | Six games and Game Night on home | Pass | |
| 6 | Under-way card; pulses and buzzes only when the other phone started it | Pass | The buzz is the app's (`rememberNudge`); a browser cannot vibrate |
| 7 | Settings and chat both in home's bar, neither covering the other | Pass | D-141 |
| | **Playing** | | |
| 8 | The deck climbs to the tier and never repeats in a run | Pass | |
| 9 | Lanes offered only with cards at this tier | Pass | |
| 10 | Done and Skip advance; Skip is free | Pass | |
| 11 | A card with a duration offers a timer that counts and ends | Pass | |
| 12 | "Turn it up?" after five yeses; never automatic | Pass | |
| 13 | "Nothing left at this tier" with a way on | Pass | |
| 14 | Aftercare after Hot or Extreme | Pass | |
| 15 | Game Night: three lengths, four phases, only the peak lit, ends on aftercare | Pass | |
| 16 | Games through primitives; turn handover with names and whose turn | Pass | Dice, choice, challenge, private choice, prediction, reveal, media proof |
| | **Consent and safety** | | |
| 17 | Stop on every playing screen, one tap, no question | Pass · Proposed | Today's screens pass. One-phone games and Game Night have no Stop in the app (§4.1); the prototype adds it, marked |
| 18 | Stop → "Everything's stopped" → Mild or End | Pass | "Turn it down to Mild" carries on with what was stopped |
| 19 | Too far: one question, two answers, private | Pass · Proposed | Proposed in one-phone games (§4.3) |
| 20 | Extreme gate: two taps to confirm, one to turn down, once a session, no Extreme content | Pass · Proposed | Proposed before Game Night and games too (§4.2) |
| 21 | Limits: eight tags × Yes / Maybe / No; a No hides matching cards | Pass | The prototype matches a No on card tags; the app's filter is the taxonomy's, unchanged |
| | **Apart** | | |
| 22 | Pairing by code, QR and link | Pass | The QR is drawn for the design; it is not a scannable code |
| 23 | Both phones show the round; only the active one acts | Pass | Two phones side by side |
| 24 | The watcher sees whose turn and what they drew; nothing of a private step | Pass | |
| 25 | Reconnecting and partner-away states | Pass | |
| 26 | Chat over a game; text, camera, gallery, voice, once-only, sending, failed | Pass | |
| 27 | Unpair and wipe | Pass | |
| | **Discretion and money** | | |
| 28 | Discreet icons, panic, app lock, secure screens; lock and panic give nothing away | Pass · Proposed | The prototype's lock is neutral; the app's says "Unlock your private world" today (§4.7) |
| 29 | Store: Spicy, Hot, Everything; a pack opens every level below; restore | Pass | Prices are placeholders |
| 30 | Free-month banner shows a date, never a countdown | Pass | |
| | **Accessibility** | | |
| 31 | Every target ≥ 48 px; everything clickable labelled | Pass | All 58 states. Chips (36 px) and the Yes / Maybe / No segments (44 px) extend their hit area to 48 |
| 32 | Reduce-motion collapses decoration; reveals keep their timing | Pass | Flicker, pulse, heartbeat and shimmer stop; the card's flip becomes a 700 ms cross-fade |
| 33 | AA contrast on the real background; 200 % type does not clip | Pass | 31 pairings computed from `tokens.json`, lowest 4.61:1. At 200 % a card scrolls rather than clipping; no horizontal scroll at 360 px |
