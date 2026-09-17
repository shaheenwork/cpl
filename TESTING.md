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

**Current: 19 passing**, covering section 7.3 rows A (partner cannot read preferences),
B (partner cannot read boundaries), K (non-member cannot read a couple), L (engineFilters
unreadable by any client) and Q (reports not client-writable), plus server-only ownership
of `coupleId` and `entitlement`, unauthenticated denial, and the default-deny catch-all.

Rows are added by the phase that introduces the collection they cover.

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

A rule that matches nothing also passes. When adding a Konsist or rules test, confirm it
**fails** against a deliberate violation before trusting it — the Konsist path rules
silently matched nothing on Windows until path separators were normalised.
