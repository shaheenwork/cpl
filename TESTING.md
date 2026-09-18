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
tests, and the Konsist architecture rules.

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
missing categories, overlong copy and §3.3 prohibited terms all fail the build. The file is a
declared input of the test task, so editing it re-runs the tests.
`BundledTaxonomyRepositoryTest` proves the build really puts it in the assets.

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

**Current: 46 passing**, covering section 7.3 rows A (partner cannot read or list
preferences), B (partner cannot read boundaries), C (no client-written `coupleId`),
I (no client-granted entitlement), K (non-member cannot read a couple), L (engineFilters
unreadable by any client), M (no third member) and Q (reports not client-writable); the
shape of a private answer (four fields only, known values, `secret` only with `CURIOUS`,
server time, taxonomy-shaped ids); the age attestation; forged pairing state;
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
- **e2e**: the callables over HTTP exactly as the app calls them — unauthenticated calls
  refused, identity taken from the token and never the payload, errors mapped to codes.

---

## 3. Instrumented tests — needs an Android emulator **and** the Firebase emulator

```bash
$LOCALAPPDATA/Android/Sdk/emulator/emulator -avd afterhours_a -no-window -no-audio &
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator &
./gradlew connectedDevDebugAndroidTest
```

> **Blocked on this machine.** The x86_64 emulator needs a hypervisor driver that must be
> installed from an elevated shell. See HUMAN_SETUP.md section 1.3 — it is a one-time,
> two-minute fix, and everything else (SDK, system image, the `afterhours_a` AVD) is
> already in place.

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
