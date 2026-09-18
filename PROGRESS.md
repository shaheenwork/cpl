# PROGRESS

Live build status. Updated at the end of every phase (BUILD_PROMPT.md §0.2).
Phase list and exit criteria: BUILD_PROMPT.md §22.

**Current position:** Phase 5 complete. Next action: **Phase 6 — Boundaries, content level and engine filters.**

---

## Phase status

| # | Phase | Status |
|---|---|---|
| 0 | Repo & tooling | ✅ **Complete** |
| 1 | Firebase wiring + emulators | ✅ **Complete** |
| 2 | Design system | ✅ **Complete** |
| 3 | Auth + age gate + app lock | ✅ **Complete** |
| 4 | Couple pairing | ✅ **Complete** |
| 5 | Taxonomy + preference discovery | ✅ **Complete** |
| 6 | Boundaries + engine filters | ⬜ Next |
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


---

## Phase 3 — Auth, age gate and app lock ✅

### Shipped
- **Three feature modules**: `:feature:onboarding` (splash routing, age gate, welcome),
  `:feature:auth` (sign in/up, reset, sign out), `:feature:applock` (lock + lock setup).
- **Auth**: email/password via `AuthDataSource` → `AuthRepository`. Profiles at
  `users/{uid}` created on first sign-in, merged rather than overwritten.
- **18+ gate** (§3.1): explicit attestation, stored with the **server's** timestamp.
  Splash routing trusts the server record only; the local hint can never open the gate.
- **App lock** (§3.4, §57): PIN (salted PBKDF2, 120k iterations, constant-time compare)
  with biometrics layered on top. The app **starts locked**; re-lock is time-based so a
  glance at a notification doesn't demand a fingerprint. Biometrics require a PIN to fall
  back on; a lockout routes to the PIN rather than offering a retry that can't work.
- **Lock setup** flow: PIN entered twice, a mismatch restarts from the first entry, and
  setting the lock never locks the user out of the screen they set it from.
- **`SecureScreen()`** on every new screen (FLAG_SECURE).
- `Clock` injected everywhere time matters; `AuthRepository` and `AppPreferencesStore` are
  interfaces with real fakes in `:core:testing`.

### Verified
```
./gradlew check assembleDevDebug        BUILD SUCCESSFUL
JVM unit tests                          all passing (see counts below)
npm --prefix firebase/tests test        26/26
Feature screenshot goldens              10, all reviewed by eye
```

| Suite | Tests |
|---|---|
| AppLockManagerTest | 16 |
| PinHasherTest | 10 |
| AppLockViewModelTest | 9 |
| AppLockSetupViewModelTest | 8 |
| AuthViewModelTest | 10 |
| SplashViewModelTest | 8 |
| AgeGateViewModelTest | 6 |

**Mutation-tested**: trusting the local age hint, and advancing the gate on a failed
write, were each planted and each caught.

### Found and fixed — the important ones
- **The architecture rules have never run on an ordinary build** (D-015). Konsist reads
  the whole repo at runtime; Gradle couldn't see that and marked the task UP-TO-DATE.
  Fixed by declaring the sources as inputs, and verified on a normal build.
- **The app would have flashed white on every cold start** (D-014). Template light window
  theme. Now Ink, including the Android 12+ system splash.
- **A test hung for an hour and then passed** (D-013). mockk `coEvery` inside `runTest`.
  Replaced suspend mocks with real fakes; added a 10-minute ceiling on every test task.
- **Sign-up copy confirmed that an email had an account** (D-012). Softened; residual
  risk documented, with email-link sign-in recommended before launch.
- **An attestation-timestamp rule would have blocked every profile edit after 24 hours.**
  Caught while writing it; the rule now checks only the write that changes the
  attestation, and a regression test guards it.
- Welcome page dots failed WCAG 1.4.11 non-text contrast; now `outline` (4.07:1).

### NOT verified — still blocked on the hypervisor (HUMAN_SETUP.md §1.3)
Nothing has run on a device. In particular, **these need a real device**: the biometric
prompt itself, `FLAG_SECURE` actually blocking screenshots, the lock re-engaging after
backgrounding, and the absence of a launch flash. Every one is covered by logic tests;
none is proven end-to-end.

### Deferred
- **Email-link sign-in** — recommended before launch (D-012).
- **Google Sign-In** — optional per §7; needs SHA fingerprints in Firebase (HUMAN_SETUP).
- **Settings as the home for lock setup and sign-out** — Phase 20. Until then they live
  on the placeholder screens so they're reachable on a device.


---

## Phase 4 — Couple pairing ✅

### Shipped
- **Cloud Functions project** (`functions/`, TypeScript, firebase-functions v7, Node 22).
  Five callables: `createPairingCode`, `requestPairing`, `respondToPairing`,
  `cancelPairing`, `unpairCouple`. App Check enforced outside the emulator; identity always
  from the verified token, never the payload.
- **Pairing handshake** (D-016): a code only creates a request; the creator approves after
  both phones show the same three symbols. Stops strangers pairing via guessed codes.
- **Transactions** for every state change: one couple per person, exactly two members,
  single-use codes, no self-pairing, only the creator can approve.
- **Rules**: couples readable only by their two ACTIVE members; no client writes to couples,
  members, or the `pairing` field.
- **`:feature:pairing`**: choose / invite (code + QR + share) / confirm symbols / enter code
  / waiting / declined. Server state drives the step, so both phones converge and a
  mid-handshake process death resumes in place.
- **Invite links** `afterhours://pair?code=` (D-017), held in `PendingInvite` until the user
  reaches pairing through the normal flow. Unpair with confirmation on the Home placeholder.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
npm --prefix firebase/tests test            34/34   (7.3 rows K, M, N, plus C)
npm --prefix functions run test:unit        6/6
npm --prefix functions run test:int         27/27   (real transactions, incl. races)
npm --prefix functions run test:e2e         5/5     (callables over HTTP)
PairingViewModelTest 14, PendingInviteTest 2, 5 pairing goldens reviewed
```

**Races tested against the real emulator:** two people entering one code at once (exactly
one request); approving twice at once (one couple); the joiner pairing elsewhere before
approval (no second couple). **Mutation-tested:** removing the `busy` guard and the
already-paired re-check each failed exactly their race test.

### Found and fixed
- **The glow never glowed** (D-018). Visible only in card corners since Phase 2.
- **One Firestore listener per collector** on the profile document (D-019).
- A dead duplicate of the code-rejection logic with *different* semantics from the real
  transaction (it named reasons; the transaction deliberately doesn't) — removed.

### NOT verified on a device (hypervisor, HUMAN_SETUP.md §1.3)
Two real phones pairing; the share sheet; the deep link opening the app; a phone camera
scanning the QR. The server side is proven end to end over HTTP; the client is proven by
unit and screenshot tests.

### Deferred
- **App Links** (`https://`) — needs a domain (HUMAN_SETUP.md §2.7).
- **Storage couple-membership rules** — still deny-all until Phase 15 introduces media,
  where they can be tested against real paths rather than guessed at now.
- **What happens to shared memories on unpair** — decided with account deletion, Phase 20.


---

## Phase 5 — Taxonomy + private preference discovery ✅

### Shipped
- **`content/taxonomy.json` v1** — 7 categories, 27 themes, 51 items. By intensity floor:
  14 at level 1, 20 at 2, 13 at 3, 4 at 4 (so 14 / 34 / 47 / 51 cards at levels 1 to 4+).
  Broad, consensual and non-graphic: a map of curiosities, not a catalogue of acts (§9.5).
  An item's theme is its boundary theme (D-020).
- **Shipped in the APK** by a `:core:data` build task, byte-identical to the source file.
  Tolerant parser for remote updates; strict `TaxonomyFileTest` for the file we ship.
- **Private answers** at `users/{uid}/preferences/{itemId}`: `value`, `secret`,
  `updatedAt` (server time), `taxonomyVersion`. One shared, owner-only listener. Written
  per deliberate answer, unchanged answers skipped (D-022); removable.
- **Rules**: exactly those four fields, a known value, `secret` only with `CURIOUS`, server
  time, a taxonomy-shaped id. Owner-only read, write and delete.
- **"Secretly curious"** is an answer of its own, with an explicit promise that binds Phase 7
  (D-021).
- **`PreferenceSwipeCard`** reworked: description, six answers in a fixed grid, and an
  earlier answer marked when the user comes back to a card.
- **`:feature:preferences`** — intro (privacy promise + the user's own content level),
  one-card deck (gentlest first, left-swipe skips, right-swipe goes back, never answers —
  D-023), done, review by category, edit or remove an answer. `SecureScreen`. Analytics
  gets counts only.
- **Navigation**: pairing now continues into discovery (§14.1); Home gets an entry point.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
npm --prefix firebase/tests test            46/46   (7.3 rows A and B, preference shape)
TaxonomyFileTest 9, TaxonomyParserTest 6, BundledTaxonomyRepositoryTest 1, PreferenceAnswerTest 3
PreferenceDiscoveryViewModelTest 22, DiscoveryDeckTest 5 (swipes by injected touch)
7 discovery goldens, 10 re-recorded gallery goldens, pairing-waiting — all reviewed
APK assets/content/taxonomy.json == content/taxonomy.json (byte for byte)
```

**Mutation-tested rules:** dropping the secret-only-with-CURIOUS clause, the field allowlist,
the server-time check, or owner-only read each failed exactly its own test.

**The tripwire works:** the first draft's roleplay subtitle ("Being someone else for an
evening") hit the third-party term list; the copy was reworded rather than the check relaxed.

### Found and fixed
- **Gallery goldens were 320dp wide and clipped at 470dp** since Phase 2 (D-024).
- **The waiting indicator's halo** rendered as a grey square (D-024).
- **A refused listener could crash the app** during sign-out (D-025).
- Firestore errors were classified by message text; now by status code.

### NOT verified on a device (hypervisor, HUMAN_SETUP.md §1.3)
Swipe feel under a real finger; TalkBack through the deck; `FLAG_SECURE` on the screen;
answering offline against a real Firestore cache; the Android client's writes against the
deployed rules (the rules tests use the JS SDK with the same field shapes).

### Deferred
- **`contentLevelEffective` recompute** when a member changes their own level — Phase 6
  (`onCoupleMemberChange`). Until then a couple keeps the value set at pairing; nothing
  reads it before the engine (Phase 9).
- **Remote taxonomy updates** — Phase 8's content pipeline (D-020).
- **Free vs full preference library** (§19) — Phase 19.
- **Boundary shape validation** — Phase 6, with the boundaries UI.
- **For Phase 7:** secret answers match only secret answers (D-021); changing an answer to a
  negative, or removing it, must withdraw a match that has not been revealed yet.
