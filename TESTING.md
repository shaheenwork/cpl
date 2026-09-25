# TESTING

How to run every tier. Prerequisites first — the Gradle daemon needs JDK 21, and the
Firebase CLI needs `java` on a Unix-style PATH:

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
export PATH="/c/Users/shahe/.jdks/jbr-21.0.11/bin:$PATH"
```

---

## 1. Unit tests and static analysis — no emulator needed

```bash
./gradlew check
```

Covers detekt (with the ktlint rule set via `detekt-formatting`), every module's unit
tests, the Konsist architecture rules, and the content gate below. `check` runs `npm ci` in
`tools/` the first time, so Node 22+ must be on PATH.

The engine suite is the highest bar in the repo and runs on its own in seconds, because
`:core:engine` is a pure JVM module:

```bash
./gradlew :core:engine:test
```

### The experience engine (Phase 9)

The engine is implemented twice — the server's TypeScript in `functions/src/engine/`, the
device's Kotlin in `:core:engine` — to one spec, `engine/SPEC.md` (DECISIONS.md D-046).
`engine/engine-fixtures.json` holds the vectors both suites check: the random sequence,
plans, assemblies, validation verdicts, chokepoint cases and whole generations.

- `SharedVectorsTest` (Kotlin) reproduces every vector; the server's `engine.unit.test.ts`
  asserts the committed file is exactly what the TypeScript computes. A difference on either
  side is a bug in one implementation or in the spec.
- `ClientAssemblyTest` builds nights over the real catalogue; `RemixerTest` holds every
  remix and swap to the pool, the ceiling and the rules.
- `:core:engine`'s test task declares the vectors and `content/dist/bundle.json` as inputs,
  so a change to either re-runs it (DECISIONS.md D-015).

After a **deliberate** change to the algorithm, regenerate the vectors from the server,
raise `ENGINE_VERSION` (TypeScript) and `Engine.VERSION` (Kotlin) together, and update the
spec:

```bash
npm --prefix functions run engine:fixtures
```

```bash
./gradlew :architecture:test
```

### Screenshot tests

The design system is screenshot-tested on the JVM through Robolectric, so it needs no
device. `verifyRoborazziDebug` is wired into `check`, so a visual regression fails the
build like any other test.

After an **intentional** visual change, re-record the goldens and review the diff:

```bash
./gradlew :core:designsystem:recordRoborazziDebug
```

Goldens live in each module's `src/test/screenshots/` and are committed — the design
system plus every `:feature:*` module. Look at a new golden before committing it: a
recorded image is not the same as a correct one (Phase 3's first feature goldens were
cream-on-white, which is how the launch-flash bug was found).

### FLAG_SECURE

`SecureScreenTest` (`:core:ui`) composes the case that matters for a privacy flag held by
screens: the handover, where the arriving private screen is already in composition when the
leaving one is disposed. It fails against a `SecureScreen` that simply sets and clears the flag
(D-073). On a device, the check is `adb exec-out screencap -p`: a private screen captures black
apart from the system bars, which are not the app's window.

### The shipped taxonomy

`TaxonomyFileTest` (in `:core:data`) reads `content/taxonomy.json` strictly: unknown or
misspelt fields, anything the app's tolerant parser would drop, duplicate or malformed ids,
missing categories, overlong copy and §3.3 prohibited terms all fail the build. The
prohibited terms come from `content/policy/prohibited-terms.json`, the same list the content
validator uses. Both files are declared inputs of the test task, so editing them re-runs the
tests.

### Content (Phase 8)

```bash
./gradlew validateContent testContentTools      # both run in check
npm --prefix tools run validate                 # the same gate, directly
npm --prefix tools test                         # the tools' own tests (node:test)
```

`validateContent` fails on anything §9.4 lists, on items whose text or preferences are not
covered by a boundary, on too little coverage for the engine, and on a committed
`content/dist/bundle.json` that no longer matches the packs. After editing content:

```bash
npm --prefix tools run bundle
```

On the app side, `ContentBundleFileTest` reads the shipped bundle through the app's own
parser and fails if a single item or list entry would be dropped, or if
`content/vocabulary.json` and the app's enums differ. `AssetShippedContentTest` proves the
bundle is in the assets; `DefaultContentRepositoryTest` runs install, update and deltas
against a real in-memory Room database.

To exercise the download path against the emulators, publish, then open the debug build's
content inspector (Home → Content inspector → Sync now):

```bash
npm --prefix tools run publish-content -- --project afterhours-dev-emulator --emulator
npm --prefix tools run content-delta -- disable flirt_across_the_room --project afterhours-dev-emulator --emulator
```

Note that the Functions end-to-end suite clears the dev project, deltas included.

### Build Our Night and the player (Phase 10)

`:feature:night`'s tests drive whole nights against `FakeNightRepository`, which moves sessions
the way the server does: consent from each phone, chapters, pause and stop from either phone,
"Not this one". `NightRulesTest` holds the pure rules — who leads each chapter, when the host
checks in (always before an own ask-first item, and at decoy moments so a check-in never gives a
limit away), and which stage each phone shows for every session state. `NightSnapshotTest`
records every screen, and checks the promises on screen: a sealed chapter has nothing readable in
the tree, the partner's phone never shows a chapter, STOP is one tap with no confirmation, and a
pause takes the night off the screen but never its controls.

In `:core:data`, `HeldNightTest` and `DefaultNightRepositoryTest` run the phone's side of a night
over the real shipped catalogue: swaps only from what the server released, reported when played,
limits set mid-night applied before a chapter is shown, a night restored after a restart.

### Games (Phase 11)

The games are content: `content/games.json` defines each as steps over primitives, and
`content/prompts/*.json` holds each game's prompts. `validateContent` checks a game's steps like
a compiler would (every reference points back, nobody predicts their own answer, a scored game
reveals) and its prompts like items — plus a whole game at level 1 for every prompt kind, with
one to spare. `npm --prefix tools test` breaks each rule and watches it fail
(`validate-games.test.ts`).

On the app side, `GameRunTest` (`:core:engine`) walks the interpreter through every primitive
and every reveal — hand-offs, gatherings, scores, "Not this one", limits set mid-game — with
small games shaped like the shipped ones, and `ContentBundleFileTest` plays every shipped game
from its first round to its last through the same interpreter. `GameParsingTest` covers the
tolerant parser; `DefaultGameRepositoryTest` the phone's side of a started game.
`:feature:games` has `GameViewModelTest` (whole games against `FakeGameRepository`, consent,
pause and stop, a limit set on the other phone) and `GameSnapshotTest` (every screen, and the
promises on screen: a hand-off shows nothing of what comes next, the partner's phone never
shows the game, STOP is always there). `:architecture`'s `GameLogicTest` is the "no duplicated
game logic" gate: no code names a game, only the interpreter and the parser read a game's
steps, one renderer per primitive, verdicts and scores made only by the interpreter.

### Home, surprises and mystery nights (Phase 13)

`surprise.unit.test.ts` holds what a phone may ask for — nothing it could steer with — and what
the server picks: one mood that is not the last night's, the couple's own ceiling, however long
their recent nights ran, and the same choice for the same seed. The integration tests build real
surprises against the emulator (including a couple with a lower ceiling, and a mystery night), and
the e2e tests call `surpriseNight` over HTTP the way the app does.

On the phone, `HomeViewModelTest` covers the two taps that need no thought, the one-at-a-time rule
and the switch between the same room and apart; `HomeSnapshotTest` records the Together and Apart
surfaces and the "something under way" card, and asserts that Apart offers nothing that does not
work yet. `DefaultNightRepositoryTest` holds a surprise the same way it holds a built night — with
the settings the server chose — and proves "Make it more…" only ever rearranges what was released,
tells the server what it did, and keeps the old night when the server refuses (D-080). The remix
callable's own tests are in `experience.int.test.ts`: a rearrangement accepted, an unreleased id
refused, the other partner's night refused, a started night refused, and a limit set since the
night was built refused.
`BuildNightViewModelTest` covers the fifteen-minute express path (one tap, one choice), and
`NightSnapshotTest` records the "MAKE IT…" row and the mystery shape screen, which shows neither a
chapter count nor a length.

### Things left for later (Phase 16)

`surprises.int.test.ts` covers the whole shape: what is left is visible as a thing and unreadable
as a secret, the sweep delivers nothing early and everything due exactly once, "Open when…" hands
over the oldest kept for that moment and refuses the partner who left it, and what nobody opened
is marked expired long after. The rules half is row S in `rules.test.mjs`: the partner sees the
surprise and is denied its payload, its author is not, and a delivered one is readable by both.
`LaterViewModelTest` covers the screen against a fake that refuses an undelivered payload the way
the rules do.

`arcs.int.test.ts` covers multi-day arcs against the emulator: the plan is a day per beat with a
last night at the end, a second arc is refused while one is running, no night exists until a day is
opened, opening the same day twice gives the same night, a day is refused both before its time and
before the day before it was played, each day is built inside the couple's own ceiling, and an
ended arc opens nothing. `ArcTest` holds which day is due — including the first, which is due the
moment the arc starts — and `ArcViewModelTest` drives the screen against a fake that refuses the
way the server does.

### The account itself (Phase 20)

`account.int.test.ts` covers the three things this phase exits on. Reporting: a report lands in a
collection neither partner can read, a media report must be about this couple's own item, and a
free-text field is refused. The export: it holds their own answers and not one field of the
partner's, an undelivered surprise stays closed even in its author's export, and
`requestDataExport` writes **a real file** that is downloaded back and parsed in the test. The
deletion: nothing happens without `confirm: true`, and then the cascade is asserted across Auth,
Firestore (the user, the couple and everything under both, sessions included), Storage (both
prefixes) and the scheduled things — plus that it is safe to run twice and that the partner keeps
their own account. `storage.test.mjs` holds the export path itself: its owner reads it, the partner
and a stranger cannot, and no client may write one. `SettingsViewModelTest` covers the screen.

### Subscription (Phase 19)

`billing.unit.test.ts` reads a stored record the way the server does: a term that has run out is
free whatever it says, a grace period still entitles, on hold and paused do not, the better of two
members' records is the couple's, and the couple's published copy never carries a purchase token.
`billing.int.test.ts` is the rest: a dev token verified and written, the free tier's four limits
(three nights a week, Mystery Nights, arcs, three surprises waiting), every one of them lifted by
a purchase, the whole Play notification cycle (grace → on hold → recovered, and a refund ending it
at once), and the sweep that writes back a term nobody told us about. Its centre is the exit
criterion: a free couple who have spent their week are refused a *new* night and can still play,
ready and advance the one they have. `PaywallViewModelTest` covers the screen — a cancelled
purchase is not an error — and `PaywallSnapshotTest` asserts the absence of countdowns and
scarcity words, not just the presence of the price.

### Memories (Phase 18)

`memories.int.test.ts` keeps a played night and reads it back: their words, the evening's shape
taken from the night rather than from the phone, and — the assertion that matters — that none of
the night's content ids appear anywhere in the memory. It also covers one memory per night (an
edit, not a second one, and the favourite mark surviving it), attachments limited to media of
their own that was really sent, the refusals (a night nobody played, another couple's night, a
field the server does not know), and that either partner may forget one without taking the voice
note or the night with it. `rules.test.mjs` holds the client side: both read, both may mark a
favourite, and nothing else about a memory may be written from a phone.
`MemoriesViewModelTest` covers the library — pages of twenty, the favourite filter, a mark the
server refuses going back where it was — and `SaveMemoryViewModelTest` the screen that keeps one.

### Notifications (Phase 17)

`notify.unit.test.ts` is the payload inspection Phase 17 exits on: for every kind, what keys a
payload has, that each value is an id or an enum rather than free text, that there is no
`notification` block, and that a title, a name or a whole sentence is refused as a reference.
`notify.int.test.ts` runs the real flows — pairing, a surprise left and delivered, a planned
night, an arc day opened — through a sender that keeps what it was given, and asserts that none of
the words the couple wrote appear anywhere in what was sent; it also covers who is told (not the
one who did it), who is not (off, or asleep on their own clock), and that a token the service
rejects is forgotten. `NotifierTest` is the same promise on the phone: every line that reaches a
lock screen comes from `NotificationText`, previews are hidden until asked for, and a phone that
refused the permission gets nothing at all.

### Voice notes and photos (Phase 15)

`media.int.test.ts` drives the real Storage emulator: a path is reserved before anything exists,
confirming fails until the object is really there, a burn-after-view item is deleted — object
first, record second — as the partner opens it, one that does not burn records when it was opened,
and the sweep removes both what is past its time and uploads that never finished.

`storage.test.mjs` is the rules half (§7.3 rows O and P): an upload is allowed only to the path
the server reserved and only to its author, the content type is held to the folder, both partners
can read what was sent and nobody else can — including an unauthenticated reader — and deleting
the record denies the object. On the phone, `SendViewModelTest` uses a recorder with no
microphone, so recording, sending, opening and burning are all testable without a device.

### A night planned for later (Phase 14)

The async flow is held at three layers. `experience.int.test.ts` covers planning: the night is
built for the partner (their ask-first themes flagged, the planner's left out), the planner is
returned a shape and nothing else, and the plan is opened once, by the partner it belongs to.
`session.int.test.ts` plays the whole thing with the two of them never in it at once — planned at
one time, opened and finished hours later — and holds the two rules that make that safe: the
planner cannot open what they planned, and an async session is given a week rather than ten
minutes before the sweep takes it. The rules tests prove the planner cannot read the night itself,
and that nobody can list experiences.

On the phone, `DefaultNightRepositoryTest` keeps a planned night out of this phone's hands (it is
a shape, not a night to play), `HomeViewModelTest` covers what waits and what was planned, and
`HomeSnapshotTest` records both sides of it.

### Two phones in one session (Phase 12)

A phone each is tested at every layer, because nothing about it can be seen from one of them.
`TwoPhoneRunTest` (`:core:engine`) holds the rule that makes it work at all: whichever seat a
phone plays, it takes its own steps first and waits after them — the server reveals a round only
once both have answered, so a phone that waited first would wait for ever (D-074). It also walks
an answer out to its short string and back.

`DefaultGameRepositoryTest` covers the phone's side of the wire: its own answer sent under its
own id, the reveal read back by seat, the partner's heartbeat told apart from its own, a listener
that drops (retried, with the last state kept and marked), and a snapshot that starts coming from
the cache — which is the only way Firestore says a phone is cut off. `GameViewModelTest` plays a
two-phone round against `FakeGameRepository`, including the phone that did not start the game,
a phone restarted after it had answered, and a partner gone quiet; `GameSnapshotTest` records the
waiting, reconnecting, dropped-out and round-waiting screens.

On the server, `realtime.int.test.ts` drives the submission trigger and the expiry sweep against
the emulator, `realtime.e2e.test.ts` runs two signed-in clients through one session, and the rules
tests (D–G) hold what a phone may write: its own submission only, no read of anyone's, its own
presence, and no write to a reveal.

### Contrast

`ContrastTest` computes WCAG 2.1 ratios for all 20 foreground/background pairs the theme
uses. If a pair fails, the fix is to change the token, not the threshold.

---

## 2. Security-rules tests — needs the Firebase emulator

These prove the privacy architecture in `BUILD_PROMPT.md` section 5 rather than assuming
it, and fill in the pass/fail matrix from section 7.3.

Terminal 1:

```bash
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
```

Terminal 2:

```bash
npm --prefix firebase/tests test
```

**Current: 76 passing**, covering section 7.3 rows A (partner cannot read or list
preferences), B (partner cannot read or list boundaries), C (no client-written `coupleId`),
H (no client-written couple ceiling), L (not even an active member can read the couple's
filters), R (no client-created match; a member may only mark a match seen for themselves;
the reveal queue is unreadable),
I (no client-granted entitlement), K (non-member cannot read a couple), L (engineFilters
unreadable by any client), M (no third member) and Q (reports not client-writable); the
shape of a private answer (four fields only, known values, `secret` only with `CURIOUS`,
server time, taxonomy-shaped ids) and of a private boundary (known level, a short optional
note, server time); the age attestation; forged pairing state; nights and their private
ask-first documents; a night's session (both members read it, no client can write it — no
forged "I'm in", state or stop); feedback (private to its author, one per chapter actually
played, never edited); unauthenticated denial; and the default-deny catch-all. Row N (a reused
pairing code) is proved by the functions integration tests, because codes are server-only.

Rows are added by the phase that introduces the collection they cover.

---

## 2b. Cloud Functions — needs the emulator suite with functions

```bash
firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
```

```bash
npm --prefix functions run test:unit
```

```bash
npm --prefix functions run test:int
```

```bash
npm --prefix functions run test:e2e
```

- **unit**: pure logic (code format, verification symbols). No emulator.
- **int**: the real transactions against the Firestore emulator, including the races — two
  people using one code, a double approval, a partner pairing elsewhere mid-request.
- **int** runs in its own emulator project, `afterhours-int-test`, where no trigger fires
  (D-037). **e2e** stays on `afterhours-dev-emulator` because it wants the triggers — and the
  callables suite clears that project, so any data seeded for manual checks goes with it.
- **unit** also covers matching and reveal timing (`match.unit.test.ts`); **int** the queue
  (`reveals.int.test.ts`); **e2e** reveals through the real triggers (`reveals.e2e.test.ts`).
- **unit** also covers the boundary intersection (`filters.unit.test.ts`): either partner's
  NEVER wins, fail-closed parsing, determinism.
- **int** also drives the transactional filter recompute (`couple.int.test.ts`).
- **e2e** also proves the triggers fire (`triggers.e2e.test.ts`): a boundary written with no
  one calling anything reaches the server-only filters. Rebuild (`npm run build`) before
  running it; the Functions emulator picks up the new `lib/`.
- **e2e**: the callables over HTTP exactly as the app calls them — unauthenticated calls
  refused, identity taken from the token and never the payload, errors mapped to codes.
- **unit** also holds the engine (`engine.unit.test.ts`): the shared vectors, adversarial
  sweeps proving no parameter, mood, seed or history brings back an excluded item, ask-first,
  determinism, the bounded alternates, and 200 requests over the real catalogue. And its
  architecture (`engine.architecture.unit.test.ts`): only `experience/generate.ts` reads the
  catalogue, and the pipeline runs filter → score → sample → assemble → filter again.
- **int** also drives `generateExperience` against Firestore (`experience.int.test.ts`):
  either partner's NEVER, the more careful partner's level, ask-first told only to the
  partner who set it, admin disables, cooldowns, determinism, missing filters computed first,
  refused payload fields, unpaired callers, the rate limit, and a couple that has excluded
  too much for any night.
- **e2e** also calls `generateExperience` over HTTP (`experience.e2e.test.ts`): signed-out
  callers refused, a night inside the couple's ceiling, payloads naming a couple, uid or seed
  refused, and an unpaired caller told so as a precondition.
- **unit** also holds the session transition table (`session.unit.test.ts`) — every legal move,
  every retry a no-op, everything else refused — and how feedback moves affinity
  (`feedback.unit.test.ts`).
- **int** also plays nights against Firestore (`session.int.test.ts`): start to finish with the
  history it leaves, each partner's own "I'm in", pause and stop from either phone, doubled and
  retried advances, host-only moves, swaps that must be released items, a limit set mid-night,
  one night at a time and expiry — and feedback counted once however often the trigger fires
  (`feedback.int.test.ts`).
- **e2e** also plays a night over HTTP (`session.e2e.test.ts`), consent from both tokens, STOP
  from the other phone, and a reaction written through the security rules reaching the author's
  affinity by the real trigger.
- **unit** also chooses games (`game.unit.test.ts`) over the real shipped prompts: every game
  filled at every level under the ceiling, a warm-up that never goes down, every theme's NEVER
  held for every game and seed, the bounded alternates, cooldowns that give way and nothing
  else that does, determinism.
- **int** also starts and plays games against Firestore (`game.int.test.ts`): NEVER from
  either partner, every ask-first theme left out (the host's own too), the more careful
  partner's level, refused payloads, the rate limit, consent at BOLD and WILD, the dare
  reported as played, and a limit set mid-game.
- **e2e** also plays a game over HTTP (`game.e2e.test.ts`): started from the host's token, read
  back through the rules by both partners and by no one else, played through the sessions.

### A couple for manual checks

With the emulators running and the functions built, this creates `alex@example.com` (sign in
with it on the device) and `sam@example.com`, attested at level 5 and paired — and then plays
Sam's phone:

```bash
node functions/scripts/dev-couple.js seed
```

```bash
node functions/scripts/dev-couple.js ready
```

`ready` is Sam's "I'm in" to the night under way; `pause`, `resume` and `stop` are Sam's
controls; `state` prints the couple's sessions, nights, history, feedback and Alex's affinity.

For a game played on a phone each (§12), Sam's phone answers and looks from the terminal:

```bash
node functions/scripts/dev-couple.js answer 1
```

`answer [0|1]` writes Sam's submission for the round the session is on — the same short string
the app sends — and the trigger reveals both; `seen` is Sam's "I've looked", which the round
waits for; `present` is one heartbeat, and stopping it for ninety seconds is how the device shows
"Your partner dropped out — waiting…".
A game under way is a session too (D-070): the same commands answer for it, and `state` shows
`stats.gamesPlayed`.
Run `seed` again after the e2e suite, which clears the dev project.

---

## 3. Instrumented tests — needs an Android emulator **and** the Firebase emulator

```bash
$LOCALAPPDATA/Android/Sdk/emulator/emulator -avd afterhours_a -no-window -no-audio &
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator &
./gradlew connectedDevDebugAndroidTest
```

**Current: 3 passing** on `afterhours_a` (API 36): a round trip through the emulators, one
user refused another's document, and the content catalogue installing from the shipped
bundle with no network (Phase 8).

`ContentOnDeviceTest` needs no emulators. Run it with the device offline to prove the point:

```bash
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.shnapps.couple.ContentOnDeviceTest
adb shell cmd connectivity airplane-mode disable
adb shell svc wifi enable
```

Manual device checks that have no automated equivalent yet, and how they were done:
- `FLAG_SECURE`: `adb exec-out screencap -p` on a secure screen must come back solid black
  (and the app-switcher thumbnail with it); a non-secure screen captures normally.
- Offline: `adb shell cmd connectivity airplane-mode enable|disable` around a few answers,
  then read the documents back from the Firestore emulator.
- Gestures: `adb shell input swipe x1 y x2 y 250` across the card.

The software-rendered emulator is slow; if a "System UI isn't responding" dialog appears,
choose Wait.

After toggling airplane mode, wait until `adb shell ping -c 1 10.0.2.2` answers before
launching the app. Wi-Fi comes back in stages; a cold start in that window finds the server
unreachable and, failing closed, shows the age gate (the attestation is checked against the
server, and the dev build keeps no persistent Firestore cache).

From Phase 12 a second AVD is required, because the synchronized-session test drives two
clients at once.

---

## 4. What each tier is for

| Tier | Catches |
|---|---|
| `check` | Logic bugs, style drift, and layering violations — feature-to-feature deps, Firebase leaking out of `:core:firebase`, Firestore inside a `@Composable`, analytics outside the typed allowlist |
| Rules tests | Privacy regressions. A rule that stops enforcing is otherwise invisible |
| Engine vectors | The server's and the device's engines drifting apart — the same seed giving two different nights |
| Session table | A night that can be double-advanced, restarted after STOP, or started without both partners' consent |
| Instrumented | That the app really reaches the backend, and that real two-device flows work |

A test Gradle doesn't re-run also passes. `:architecture:test` was UP-TO-DATE on every
build that didn't touch the `:architecture` module — for three phases — because Konsist
reads sources Gradle couldn't see. It now declares them. **Prove a gate by watching it
fail on an ordinary build**, not only with `--rerun`.

A rule that matches nothing also passes. When adding a Konsist or rules test, confirm it
**fails** against a deliberate violation before trusting it — the Konsist path rules
silently matched nothing on Windows until path separators were normalised.
