# Changelog — after dark

What the redesign changes, what it keeps, and what it changes about behaviour. The prototype is
`afterhours-redesign.html`. The behaviour below is **in the app** (phase 1, DECISIONS.md D-143 and
D-144); the visual changes are the prototype's until phase 2 ports them.

## Behaviour changes

Seven were proposed in the prototype; the answers are in ANALYSIS.md §6. Six were made as drawn,
and one the other way round:

| # | Change | Answer | Where |
|---|---|---|---|
| 1 | **Stop on one-phone games and Game Night** | Yes | `SafetyRail` on `DealtGameScreen` and `GameNightScreen` |
| 2 | **The Extreme gate** | **Removed everywhere** — choosing the tier is the consent | `InstantPlayViewModel`; the paired "I'm in" (D-061) is untouched |
| 3 | **Too far in one-phone games**, beside "Not this one" | Yes | `DealtGameViewModel.refuse`, `RefusalChoices` in a sheet |
| 4 | **Cards dealt face down, tap to reveal** | Yes, always face down | `TurnOverCard`; Done and the timer wait for it |
| 5 | **The heat picker leaves the deck screen** for a chip in the bar | Yes | `HeatChip` + `AfterhoursSheet`; the lanes too |
| 6 | **One safety rail at the bottom of every playing screen** | Yes | `SafetyRail`: Stop, then Too far or Pause, then the chat |
| 7 | **The lock screen says nothing about the app** | Yes | "Unlock / Enter your PIN" in `AppLockScreen` |

Inter stays for UI, and Done, Skip and Too far keep their names. "Keep this card" in the Too far
sheet became "Keep this one", to match "Just this one" above it.

## What changed visually

- **The room.** Near-black with red in it (`#0A0508`), a key light that takes tonight's tier so the
  room warms as the heat rises, a wine bounce low on the screen, a faint diagonal silk sheen, and the
  app's own grain. Calm enough that the card is the only lit thing on a playing screen.
- **The heat ramp runs cool to hot**: Mild lilac, Flirty orchid, Spicy rose, Hot scarlet, Extreme
  crimson with a flame rim. Spicy, Hot and Extreme sit close in hue, so they are separated three ways
  at once — lightness, the number of flames (one to five), and the label — and never by hue alone.
- **Flames for dots.** The picker draws one to five filled flames per tier; the chosen one
  flickers. A gradient heat bar under it carries an ember to where tonight sits, and a one-line hint
  says what the tier feels like.
- **A brand mark.** A wax seal with a keyhole pressed into it, drawn in SVG. On home, the start
  screens and the ends of evenings — never on the lock screen.
- **Cards arrive face down** (proposal 4): velvet, a double gold border, a symmetric sigil around a
  keyhole, "tap to reveal" in italic. The flip is 3D, 760 ms, with the tier's bloom opening as the
  face turns. Each card wears its own tier, so a run that climbs visibly warms.
- **The card face** draws `subtitle` (in the data, not drawn today) as an italic tease line, and the
  card's moods as small caps in the footer.
- **Type** keeps Playfair Display and Inter. New: italic serif for the line that flirts ("one
  evening.", "want this?"), a larger card title, and a 17 px card body. Jost is in the panel as an
  alternative for UI labels; it costs about 120 KB to bundle.
- **Gold** — hairlines, the sigil, locks, the seal's word — used sparingly, as ornament.
- **Home** is a grid, not a list: Game Night wide, then two columns, each game with its own glyph
  (dice, two facets, linked rings, scales, a fork, an asking flame). The Together / Apart switch has a
  sliding thumb lit in the tier. In Apart a pairing strip shows two linked dots that beat like a
  heart while paired.
- **The watching phone** shows a large initial, "Alex's turn", and what they drew in a lit card —
  not a status line with two thirds of the screen empty.
- **Sheets** for the heat picker, lanes, limits and Too far: a velvet sheet with a gold top edge,
  rising over a dimmed screen.
- **Stopped** is the calmest screen in the app: no accent, no bloom, a shield in the safety colour.
- **The Extreme gate**: a gold lock that warms to flame on the first tap and opens on the second,
  with two marks that light in turn. Nothing Extreme appears until the lock has opened.
- **Pairing**: the code in six gold-edged serif tiles, a QR, and two seals meeting when it works.
- **The store**: one card per pack, flames to show how far each goes, gold around Everything.
- **Motion**: slow, nothing bounces. Flames flicker, the pairing dots beat, a card from the other
  phone breathes, the gate's lock warms. Under reduce-motion all of it settles to its resting state,
  and the card's reveal becomes a cross-fade that keeps its timing.

## What was kept

Every screen, route, game, tier, field, mode, setting and data path. Dark only. One primary action
docked at the bottom. 48 dp targets. The voice of the copy. The spacing scale, unchanged. The 28 app
icons, unchanged — the prototype reads them out of `AfterhoursIcons.kt`.

## Copy

Interface words only; card copy is the packs' and is untouched.

| Where | Before | After |
|---|---|---|
| Home, above the picker | *(the tier's name)* | "How far tonight" |
| Heat hints | *(none)* | Mild "Warm. Nothing you'd blush about." · Flirty "A look that lasts a little too long." · Spicy "Now you're blushing." · Hot "Close, warm, and in no hurry." · Extreme "The furthest this goes. Both of you, or not at all." |
| Home, pairing strip | "Paired with their phone." / "Not paired yet." | The same, plus a second line: "Games open on both phones." / "Pair once, and every game opens on both." |
| Home, primary action | "Play Now" | "Play now" |
| Start | — | Adds a tagline under the wordmark: "For the two of you" |
| Deck, under the actions | *(none)* | "Skip is always free — pass, no questions asked." |
| Deck, card back | *(no card back)* | "Card 01", "tap to reveal" |
| Too far | *(two answers)* | Adds a way out: "Keep this card" |
| Too far, after "Never" | *(none)* | A confirmation: "Done. Nothing like it again — and nobody's told." |
| Turn it up? | "You've both said yes to five in a row." | The same, plus "{next} is next — or stay right here." |
| Run complete, with aftercare | *(as today)* | Adds "Check in with each other." under the aftercare card |
| Stopped | *(as today)* | Adds "Breathe. Check in with each other." |
| Extreme gate, eyebrow | "Extreme" | "Once, tonight" |
| Game Night, the peak | *(lit only)* | Labelled "The peak" |
| Store | eyebrow "Afterhours" | Headline "Turn it all the way up." |
| Limits | "…never told what you said, and nothing you mark as no will come up for either of you." | Split in two: the privacy line at the top, and "Anything touching a No is hidden — for both of you." beside Done |
| Lock screen | "Welcome back" / "Unlock your private world" | "Enter PIN" (proposal 7) |

## Prices

The store's prices in the prototype are placeholders. The app shows Play's price for the phone's
own store and country, and that does not change.

## Building the prototype

```bash
node design/redesign/src/build.mjs
```

It reads `tokens.json`, computes every text pairing's contrast and **fails below 4.5:1**, writes the
ratios back into `tokens.json`, lifts the app's icons out of `AfterhoursIcons.kt`, samples real cards
from `content/dist/bundle.json`, and inlines everything into `afterhours-redesign.html`. Open that
file in a browser; it needs nothing else but the Google Fonts it links (with system fallbacks).
