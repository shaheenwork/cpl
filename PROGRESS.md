# PROGRESS

Live build status. Updated at the end of every phase (BUILD_PROMPT.md §0.2).
Phase list and exit criteria: BUILD_PROMPT.md §22.

**Current position:** Phase 2 complete. Next action: **Phase 3 — Auth, age gate and app lock.**

---

## Phase status

| # | Phase | Status |
|---|---|---|
| 0 | Repo & tooling | ✅ **Complete** |
| 1 | Firebase wiring + emulators | ✅ **Complete** |
| 2 | Design system | ✅ **Complete** |
| 3 | Auth + age gate + app lock | ⬜ Next |
| 4 | Couple pairing | ⬜ |
| 5 | Taxonomy + preference discovery | ⬜ |
| 6 | Boundaries + engine filters | ⬜ |
| 7 | Mutual discovery + batched reveals | ⬜ |
| 8 | Content system + ≥600 seed items | ⬜ |
| 9 | Experience engine | ⬜ |
| 10 | Build Our Night + player | ⬜ |
| 11 | Game primitives + 7 core games | ⬜ |
| 12 | Realtime sessions | ⬜ |
| 13 | Together home + Surprise Us | ⬜ |
| 14 | Apart mode + async | ⬜ |
| 15 | Voice + photo | ⬜ |
| 16 | Scheduled surprises + multi-day | ⬜ |
| 17 | FCM + notification privacy | ⬜ |
| 18 | Memories + library | ⬜ |
| 19 | Subscription | ⬜ |
| 20 | Settings, export, delete, reporting | ⬜ |
| 21 | Security hardening | ⬜ |
| 22 | Performance, cost, a11y, polish | ⬜ |

---

## Phase 0 — Repo & tooling ✅

### Shipped
- **Build system.** Gradle 9.5 / AGP 9.3.3 / Kotlin 2.2.10 / KSP 2.2.10-2.0.2 /
  compileSdk 37 / minSdk 26, all through `gradle/libs.versions.toml`.
- **Convention plugins** in an included `build-logic` build:
  `afterhours.jvm.library`, `afterhours.android.library`,
  `afterhours.android.library.compose`, `afterhours.android.hilt`,
  `afterhours.android.feature`, `afterhours.detekt`.
- **16 modules configured** — 14 `:core:*`, `:architecture`, `:app`.
  `:core:model`, `:core:common` and `:core:engine` are pure JVM, as §4.2 requires.
- **Hilt + KSP** wired end to end (`CplApplication`, `@AndroidEntryPoint MainActivity`).
- **Product flavors** `dev` / `staging` / `prod` with suffixed application ids and a
  `USE_FIREBASE_EMULATOR` BuildConfig field (§4.3).
- **Static analysis**: detekt with `detekt-formatting` (the ktlint rule set), project
  config at `config/detekt/detekt.yml`, wired into `check`.
- **Architecture enforcement**: Konsist rules in `:architecture` covering feature↔feature
  deps, Firebase containment, Compose purity, the analytics allowlist, LiveData and
  GlobalScope.
- **Seed domain types** straight from the spec: `Mode`, `Intensity` (with the
  `effective()` ceiling rule and the both-party-consent flag), `PreferenceValue`,
  `BoundaryLevel`, `Outcome`/`AppError`, `DispatcherProvider`.
- **Analytics allowlist** as a closed `AnalyticsEvent` hierarchy with no free-form
  logging API (§17.1).
- **Type-safe navigation routes** for the §14.1 first-run chain.
- Docs: `CLAUDE.md`, `DECISIONS.md`, `HUMAN_SETUP.md`, `README.md`, this file.

### Verified
```
./gradlew projects                               BUILD SUCCESSFUL   (16 modules configure)
./gradlew assembleDevDebug                       BUILD SUCCESSFUL
./gradlew check                                  BUILD SUCCESSFUL   (detekt + tests + Konsist)
./gradlew :core:model:test :core:engine:test     BUILD SUCCESSFUL
./gradlew :architecture:test                     BUILD SUCCESSFUL
```

### Not yet verified
- **"Empty app launches" was not confirmed on a device.** The machine had no connected
  device, no AVD, and no SDK `cmdline-tools`/system image. Emulator tooling is being
  installed; the launch check is the first item of Phase 1. Everything else in the exit
  criteria passed.

### Deliberately deferred
- **R8 / release optimization** — left disabled as scaffolded. Turned on in Phase 22
  together with keep rules and Crashlytics mapping upload, which is where the exit
  criteria call for it.
- **`:feature:*` modules** — created per phase (DECISIONS.md D-006).
- **The real design system** — `AfterhoursTheme` is a dark-only Material 3 placeholder.
  Phase 2 replaces it with the §15 tokens.
- **`config/detekt/baseline.xml`** — not generated. There are currently zero findings, so
  a baseline would only hide future ones.

### Known issues
- Gradle reports *"Deprecated Gradle features were used in this build, making it
  incompatible with Gradle 10"* on every run. Source not yet identified (likely AGP 9 or
  the detekt plugin). Harmless now; worth resolving before a Gradle 10 upgrade.
- `android.disallowKotlinSourceSets=false` prints an experimental-option warning on every
  configuration. Unavoidable while KSP needs it (DECISIONS.md D-007).


---

## Phase 1 — Firebase wiring + emulators ✅

### Shipped
- **Firebase SDKs** via BOM 34.19.0: Auth, Firestore, Storage, Functions, Messaging,
  Analytics, Crashlytics, Performance, Remote Config, App Check.
- **Emulator auto-connect.** `FirebaseModule` is the single place every Firebase client is
  constructed, because emulator endpoints must be set before first use. The `dev` flavor
  redirects all of them to the local Emulator Suite, so the app runs with **no Firebase
  project and no credentials**.
- **`FirebaseEnvironment`** carries the variant decision as plain data, so no core module
  reads BuildConfig. Firestore also gets a memory-only cache under the emulator, since a
  wiped emulator plus a persisted local cache produces confusing stale state.
- **App Check**: debug provider for debug/emulator builds, Play Integrity otherwise.
- **Crashlytics and Analytics collection are disabled** whenever the emulator is in use,
  so dev runs never reach real dashboards.
- **Typed analytics is now end to end**: `FirebaseAnalyticsLogger` is the only bridge to
  Firebase Analytics, and it accepts nothing but the closed `AnalyticsEvent` hierarchy.
- **Remote Config**: `RemoteConfigSource` plus `remote_config_defaults.xml` covering
  feature flags, content/taxonomy pointers, the reveal jitter window (section 5.3) and the
  engine ranking weights (section 10.3). Defaults apply before any fetch, and the fetch is
  launched off the main thread without being awaited.
- **`firebase.json`, `.firebaserc`, `firestore.rules`, `storage.rules`, indexes.**
- **Security-rules test harness** at `firebase/tests/` — **19 tests, all passing**.
- **Variant gating**: `staging` and `prod` disable themselves until their
  `google-services.json` exists, so `check` works on a fresh clone. They re-enable
  automatically once a human adds the files.
- `:core:firebase` declares its own permissions manifest — caught by lint, not guessed.

### Verified
```
./gradlew check                    BUILD SUCCESSFUL   (detekt + tests + Konsist, all modules)
./gradlew assembleDevDebug         BUILD SUCCESSFUL
./gradlew assembleDevDebugAndroidTest  BUILD SUCCESSFUL
firebase emulators:start           auth + firestore + storage ready, rules loaded clean
npm --prefix firebase/tests test   19/19 passing
```

Rules rows from section 7.3 now proven: **A** (partner cannot read preferences),
**B** (partner cannot read boundaries), **K** (non-member cannot read a couple),
**L** (engineFilters unreadable by any client), **Q** (reports not client-writable).

### NOT verified — blocked on a human
**The app has still never been run.** The x86_64 Android emulator cannot start:

```
ERROR | x86_64 emulation currently requires hardware acceleration!
CPU acceleration status: Android Emulator hypervisor driver is not installed
```

The hardware is capable (virtualization enabled in firmware, SLAT and DEP present) — only
the hypervisor driver is missing, and installing one needs an elevated shell this session
does not have. **See HUMAN_SETUP.md section 1.3**; it is a one-time fix.

Consequently these remain unrun, though both compile:
- `FirebaseEmulatorSmokeTest` — the Phase 1 exit criterion (anonymous sign-in, Firestore
  round trip, and a cross-user read denial from a real client).
- "Empty app launches" from Phase 0.

Everything else is in place: `cmdline-tools`, the `android-36 google_apis x86_64` image,
and an AVD named `afterhours_a`.

### Fixed along the way
- **The Konsist rules were silently passing for the wrong reason.** They matched on
  `"/core/firebase/"` while Konsist reports native Windows paths, so the path filters never
  matched and the containment rules were decoration. Now normalised, and confirmed to fire
  by watching them fail before the fix. Containment now applies to production sources only,
  since a test must be able to import what it tests.
- `withoutServerFields` was called with both a map and a key set; it only worked for one.
  Replaced with a single key-set helper used by create and update alike.
- Empty test source directories failed `check` under Gradle 9 (`failOnNoDiscoveredTests`).

### Deferred
- **Cloud Functions** — `functions/` arrives in Phase 4 with the first callable
  (transactional pairing). The emulator config already reserves port 5001.
- **Storage couple-scoped rules** — denied outright until Phase 4 defines couple
  membership, rather than left permissive in the meantime.


---

## Phase 2 — Design system ✅

### Shipped
- **Tokens.** Colour, typography, spacing, shape, elevation and motion, all in
  `:core:designsystem`. Material 3 is the substrate and is fully re-themed — deep
  warm-tinted near-black grounds (never pure #000), burgundy and plum accents, brass
  reserved for locks and vaults, warm cream type.
- **Editorial typography.** Playfair Display for display/headings, Inter for body, both
  bundled as variable fonts (DECISIONS.md D-010). Plus an `EyebrowTextStyle` for the
  wide-tracked section labels the layout leans on.
- **Motion tokens** with the anticipation curve, and `LocalReduceMotion` wired to the
  system animator scale. Decorative movement collapses to zero under reduce-motion;
  reveals keep their timing, because skipping them would surface content early and break
  the anticipation the product runs on (§15.2).
- **All 16 components from §15.3**: CinematicCard, RevealCard, LockedCard, ChapterCard,
  GlowButton, IntensityDial, MoodChipRow, DurationPicker, PreferenceSwipeCard,
  BoundarySlider, WaitingForPartner, CountdownRing, VoiceRecorderBar, StopPauseBar,
  EmptyState, and SecureScreen (in `:core:ui`).
- **Component gallery** at `gallery/ComponentGallery.kt`, reachable in the app from the
  dev landing screen.
- **New domain types**: `Mood`, `Novelty`, `Leadership` in `:core:model`, matching §14.5.

### Safety and accessibility built into the components, not bolted on
- `StopPauseBar` encodes §3.1: one tap, no confirmation gauntlet, warm neutral copy, and
  the error colour kept deliberately distinct from the burgundy accent so STOP can never
  read as decoration.
- `RevealCard` and `LockedCard` treat concealment as **absence**: hidden content is not
  composed at all, so it cannot be reached by a screen reader or a screenshot (§5.7).
- `IntensityDial` marks the level 4/5 both-party-consent boundary visibly, and shows the
  effective ceiling rather than hiding unavailable levels.
- `BoundarySlider` uses explicit tappable levels rather than a draggable handle, and spells
  out each level's consequence — `NEVER` removes content for both partners and nothing
  overrides it.
- `PreferenceSwipeCard` carries the privacy reassurance itself, so a screen cannot forget
  to show it, and answers are buttons rather than gesture-only.
- Every state carries a word as well as a colour; touch targets are 48dp; decorative emoji
  are stripped from the accessibility tree.

### Verified
```
./gradlew check                                  BUILD SUCCESSFUL
./gradlew assembleDevDebug                       BUILD SUCCESSFUL
./gradlew :core:designsystem:verifyRoborazziDebug  10/10 screenshots match
ContrastTest                                     8 tests, 20 WCAG pairs, all AA
```

Contrast is **computed, not eyeballed**. The tightest margins are `outline on surfaceHigh`
at 3.25:1 (needs 3.0) and `faint on surfaceHighest` at 4.76:1 (needs 4.5).

### Bugs the screenshot tests caught
- **The glow gradient faded to `Color.Transparent`**, which is RGBA(0,0,0,0) — so it
  interpolated its *colour* towards black as well as its alpha, painting a dirty black
  halo over everything below it on a dark theme. Now fades the source colour to zero alpha
  instead. `drawBehind` also does not clip, so the glow was spilling over sibling
  components; it is clipped to bounds now.
- `RevealCard`'s concealed face used `fillMaxSize()` on a `Text` inside a wrap-height Box.

Both rendered as obviously wrong images. Neither would have been caught by a compile or a
unit test.

The gate itself was then verified by breaking a colour token and watching
`verifyRoborazziDebug` fail — same discipline that exposed the vacuous Konsist rules in
Phase 1. `verifyRoborazziDebug` is wired into `check`, because Roborazzi captures nothing
unless a record/verify flag is set and would otherwise pass silently.

### Deferred
- **Light theme** — deliberately never (DECISIONS.md D-009).
- **Font subsetting** — ~1.2 MB of variable fonts ships today; trimming to used glyphs is
  a Phase 22 size optimisation.
- **Dynamic-type screenshots at 200%** — the tokens are all `sp` and scale, but a
  large-font golden set is worth adding when real screens exist to test.
