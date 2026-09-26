# REDESIGN — Afterhours, instant

The brief for rebuilding the front half of this app. `BUILD_PROMPT.md` stays in force for everything
it says about privacy, safety, the engine and the server; this document replaces what it says about
onboarding, home, night building and the player. Where the two disagree, this one wins, except on
§3 (safety), §5 (privacy), §7 (security) and §10.3 (the chokepoint), which are not negotiable and do
not change.

The interactive prototype is `design/afterhours-instant.html`.

---

## 1. What is wrong

The app works. Nobody will use it.

A couple who open it at 11pm, already in the mood, meet this: age gate → welcome beats → sign up →
email and password → pairing code → the partner enters it → matching symbols → approval → twenty
private preference cards → a limits screen with five levels per theme → a build form with mood,
length, intensity, leadership and novelty → a "shape" screen that deliberately tells them nothing →
and then, finally, a chapter. Eleven screens and about ten minutes, most of it typing and reading,
all of it before anything happens.

Every one of those screens is defensible. The pairing handshake is genuinely good security. The
preference deck is what makes the engine personal. The limits screen is what keeps people safe. The
shape screen exists so the night stays a surprise. Together they are a wall, and what is on the
other side of the wall is a paragraph of text on a dark background.

Two faults, and they are different:

1. **Everything is asked for before anything is given.** The app collects its entire model of a
   couple before it shows them a single thing to do.
2. **What it finally shows is a document, not a game.** One long text card per chapter, a progress
   line, and a check-in. Nothing to touch, nothing that reacts, nothing that feels like play.

## 2. What it should be

Two people, in the same room or a thousand miles apart, feeling like it. One of them opens the app.
Within twenty seconds something is happening between them. Nothing else is more important than that
sentence.

- **Two taps to the first card.** Confirm 18+, pick a heat level, play.
- **No account to play.** No sign-up, no email, no password, until they want something that genuinely
  needs a server.
- **No partner link to play.** One phone passed between two people is the default, because that is
  what a couple in a room actually does.
- **Heat, not configuration.** Three levels — Playful, Hot, Wild — always on screen, changeable
  mid-card with one thumb.
- **A card is one instruction.** Large, warm, immediate. No chapter counts, no "something
  interesting is coming", no shape screens.
- **Every no is one silent tap.** Skip asks nothing. "Too far" asks one question, and the partner is
  never told.
- **Apart is a thread**, not a mode: what they left you, what you sent, what is still waiting.

## 3. What survives, untouched

This is a re-front, not a rewrite. All of this stays exactly as built and must not regress:

- the server chokepoint: nothing is assembled without `hardFilter` first (§10.3, D-048);
- `engineFilters` computed from both partners' private documents, readable by no client (D-032);
- every row of the §7.3 matrix, and the rules tests that hold them;
- data-only notifications with no content, ever (D-088);
- media reserved → uploaded → confirmed, burning on open, swept hourly (D-083);
- couple-scoped, server-decided entitlement, and the four gates at their chokepoints (D-091, D-092);
- memories that hold the shape of an evening and never a content id (D-090);
- account deletion that really deletes (D-093, D-094);
- the app lock, `FLAG_SECURE`, and the dark-only theme;
- the whole content pipeline: packs, the vocabulary, the validator, the bundle, the games as data.

If a change in this document appears to require breaking one of those, the change is wrong. Say so
rather than breaking it.

---

## 4. The new architecture of the front half

### 4.1 Local play, and the server behind it

Today every night is a server round trip: `generateExperience` → an experience document → a session.
That is right for a planned evening and far too heavy for "give me a dare".

Add a second, lighter path — **instant play** — and keep the existing one for everything that needs
a couple, a record, or two devices.

| | Instant play | A planned night (existing) |
|---|---|---|
| Needs an account | no | yes |
| Needs a partner link | no | yes |
| Where cards come from | the shipped bundle, on the device | the server, after `hardFilter` |
| What is stored | nothing, beyond local heat and skips | the experience and the session |
| Used for | one phone in a room | apart, two devices, arcs, planned nights, memories |

**The rule that makes this safe:** instant play uses the **device's own** filter — heat level, this
device's "never show me these" list, and nothing else — and it may only ever draw from content at or
below the heat level chosen on this device. It never sees the partner's limits, because it never asks
for them, because there is no partner account involved yet. The moment a couple is paired and signed
in, instant play stops being local: the deck is fetched through the existing server path, filtered by
`engineFilters` as it is today, and the local filter becomes a second, narrower gate on top. Never a
looser one.

This is the one genuinely new architectural idea in the document, and it needs its own decision entry
in `DECISIONS.md` before it is built.

### 4.2 Progressive identity

Nothing is asked for until it pays for itself, and when it is asked for, the ask names what it buys:

| Asked for | When | What it buys |
|---|---|---|
| 18+ confirmation | first launch, one tap, with heat | the app at all (§3.1 unchanged) |
| Heat level | first launch, one tap | what tonight looks like |
| An account | the first time they save a memory, go apart, or want their history | keeping things |
| A partner link | the first time they are apart, or want two phones | distance |
| A limit | the first time they tap "too far" | never seeing that again |
| Preference answers | offered after a few sessions, as a game | better cards |

The twenty-card preference deck is **not deleted**. It becomes an optional, re-entrant game —
"What are you curious about?" — offered from home once the couple has actually played, and the engine
uses whatever exists. Ten answers make it better; zero answers still work.

### 4.3 The five games are content

Home offers named games — Truth or Dare, Never Have I Ever, Spin, Hot Seat, and one more the content
team picks. These are `content/games.json` entries, played by the existing `GameRun` interpreter,
rendered by the existing primitive renderers. **No new screen per game, and no code that names a
game** (D-072 holds).

---

## 5. Screen by screen

### 5.1 First open — one screen

Replaces: age gate, welcome beats, sign up, sign in.

- The 18+ confirmation and the heat picker on one screen, with `Start`.
- The attestation is still recorded, still gates the app, and still cannot be passed by a local flag
  — but with no account, it is recorded locally and re-recorded server-side the moment an account
  exists. `AgeGateViewModel`'s refusal to advance optimistically stays.
- A quiet second action: "My partner isn't here" → pairing.
- Text, in full: "Two questions, then we start. No account. No setup. Nothing saved anywhere until
  you ask us to."

### 5.2 Home

Replaces: the Together/Apart home, Build Our Night's six-field form, and the shape screen.

- Together / Apart segmented control at the top.
- One card showing the current heat, and one big **Start now**.
- Five game tiles.
- A wide tile: "Connect your partner — for nights apart. Skip it for now if you like."
- The heat selector pinned at the bottom, always reachable.

Gone from this screen: mood chips, time chips, intensity ladder, who-leads, novelty. The engine still
uses all five; it derives them from heat, the time of day, and what this couple said yes to last time.
`BuildNightScreen` stays reachable for anyone who wants it — from home, as "Plan a whole evening" —
because the couples who want a structured night are real, just not the first-run case.

### 5.3 Playing

Replaces: the chapter player, anticipation screens, the check-in, and the feedback screen.

- One card: heat pill, the instruction in the display face, and who it is for.
- A timer when the card has one, "no clock" when it does not.
- Progress dots.
- `Skip` and `Done ✓` side by side; `Too far` beneath, quiet.
- **Too far** opens a sheet: "Skip this kind of thing? Nobody is told." → "Just this one" /
  "Never show me these again". The second writes a boundary through the existing boundary repository.
- After five consecutive `Done`s, a sheet offers to turn the heat up: "Much hotter / A bit hotter /
  Keep it here". This replaces the intensity ladder entirely.
- PAUSE and STOP remain available and remain instant (§3.1). STOP still ends everything with no
  confirmation and no reason asked.

### 5.4 Apart

Replaces: the Apart surface and the Send screen.

- Four tiles: Send a dare, Voice note, A photo, Thinking of you.
- Beneath them, today's thread: what arrived, what was sent, what is still sealed, with the burn
  state visible.
- The existing media pipeline, surprises, countdowns and arcs all keep working; they are surfaced in
  the thread rather than on separate screens.

### 5.5 Pairing

Unchanged in mechanism, moved in time. QR, a four-digit code, or a link. The matching symbols and the
approval step stay — they are what makes the handshake trustworthy — but they happen when two people
decide to link, not before the first dare. "Later — we're in the same room" is a real button.

---

## 6. Design direction

The prototype is the reference. In short:

- **Dark, warm, candlelit.** `#120A10` ground, `#1C1018` cards, `#F6E7E3` text, `#E9B44C` gold for
  everything calm, and red-pink `#FF3D68` reserved for heat alone. The current palette is close;
  the current *application* of it is flat.
- **A display face with warmth** (the prototype uses Fraunces) for card text and headings, against a
  clean sans for everything else. Card text is the product, so it gets the typography.
- **Motion where it means something**: cards deal in, sheets rise, the heat colour bleeds through the
  card's glow. Nothing decorative; `decorativeTween` and reduce-motion still apply.
- **Touch targets stay ≥ 48 dp** and type still scales to 200% — `AccessibilityTest` holds.

---

## 7. Build order

Each phase ends the way every phase in this repo ends: implemented, verified with commands actually
run, `PROGRESS.md` updated, committed.

| # | Phase | Exit criteria |
|---|---|---|
| **R1** | Local deck: a device-side `InstantDeck` over the shipped bundle, heat as a three-level type, local skips and limits in DataStore. Pure JVM, fully tested | a deck at each heat level, never above it; a "never again" is never drawn; `:core:engine` tests green |
| **R2** | First open, one screen: age + heat + start, no account. Attestation recorded locally and reconciled on sign-up | cold install to first card in two taps, on a device |
| **R3** | The card player: card, skip, done, too-far sheet, heat-up sheet, timer, progress | a full deck played on a device with no account and no network |
| **R4** | Home: segmented mode, Start now, five game tiles, heat selector, "plan a whole evening" as a secondary route | every tile reaches a playable card; `GameLogicTest` still passes |
| **R5** | Progressive identity: sign-up offered at the first save/apart/history moment, carrying local state into the account | a local session's skips and limits survive sign-up; the server's copy matches |
| **R6** | Apart as a thread, pairing moved and skippable | an apart exchange end to end on two devices |
| **R7** | Preferences as an optional game, engine using partial answers | a night built with zero answers, ten answers, and all of them — each sensible |
| **R8** | Polish pass: motion, haptics, sound off by default, the 200% and 48 dp suites re-run | `./gradlew check` green; goldens re-recorded deliberately |

## 8. What this must not become

- **A funnel.** No "complete your profile" nags, no progress ring pushing setup, no blocking modals.
- **A porn app.** The content rules in §3.3 and `CONTENT_GUIDE.md` are unchanged. Suggestive, warm,
  never explicit, never degrading, never anything either partner has not agreed to.
- **Less private.** Every shortcut above removes a *step*, never a *protection*. If a shortcut would
  let one partner learn something about the other, it is not a shortcut, it is a bug.
