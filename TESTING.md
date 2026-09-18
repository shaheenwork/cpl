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

The engine suite is the highest bar in the repo and runs on its own in about a second,
because `:core:engine` is a pure JVM module:

```bash
./gradlew :core:engine:test
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
npm --prefix tools run content-delta -- disable flirt_one_look --project afterhours-dev-emulator --emulator
```

Note that the Functions end-to-end suite clears the dev project, deltas included.

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

**Current: 60 passing**, covering section 7.3 rows A (partner cannot read or list
preferences), B (partner cannot read or list boundaries), C (no client-written `coupleId`),
H (no client-written couple ceiling), L (not even an active member can read the couple's
filters), R (no client-created match; a member may only mark a match seen for themselves;
the reveal queue is unreadable),
I (no client-granted entitlement), K (non-member cannot read a couple), L (engineFilters
unreadable by any client), M (no third member) and Q (reports not client-writable); the
shape of a private answer (four fields only, known values, `secret` only with `CURIOUS`,
server time, taxonomy-shaped ids) and of a private boundary (known level, a short optional
note, server time); the age attestation; forged pairing state;
unauthenticated denial; and the default-deny catch-all. Row N (a reused pairing code) is
proved by the functions integration tests, because codes are server-only.

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

---

## 3. Instrumented tests — needs an Android emulator **and** the Firebase emulator

```bash
$LOCALAPPDATA/Android/Sdk/emulator/emulator -avd afterhours_a -no-window -no-audio &
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator &
./gradlew connectedDevDebugAndroidTest
```

**Current: 2 passing** on `afterhours_a` (API 36): a round trip through the emulators, and
one user refused another's document.

Manual device checks that have no automated equivalent yet, and how they were done:
- `FLAG_SECURE`: `adb exec-out screencap -p` on a secure screen must come back solid black
  (and the app-switcher thumbnail with it); a non-secure screen captures normally.
- Offline: `adb shell cmd connectivity airplane-mode enable|disable` around a few answers,
  then read the documents back from the Firestore emulator.
- Gestures: `adb shell input swipe x1 y x2 y 250` across the card.

The software-rendered emulator is slow; if a "System UI isn't responding" dialog appears,
choose Wait.

From Phase 12 a second AVD is required, because the synchronized-session test drives two
clients at once.

---

## 4. What each tier is for

| Tier | Catches |
|---|---|
| `check` | Logic bugs, style drift, and layering violations — feature-to-feature deps, Firebase leaking out of `:core:firebase`, Firestore inside a `@Composable`, analytics outside the typed allowlist |
| Rules tests | Privacy regressions. A rule that stops enforcing is otherwise invisible |
| Instrumented | That the app really reaches the backend, and that real two-device flows work |

A test Gradle doesn't re-run also passes. `:architecture:test` was UP-TO-DATE on every
build that didn't touch the `:architecture` module — for three phases — because Konsist
reads sources Gradle couldn't see. It now declares them. **Prove a gate by watching it
fail on an ordinary build**, not only with `--rerun`.

A rule that matches nothing also passes. When adding a Konsist or rules test, confirm it
**fails** against a deliberate violation before trusting it — the Konsist path rules
silently matched nothing on Windows until path separators were normalised.
