# PROGRESS

Live build status. Updated at the end of every phase (BUILD_PROMPT.md §0.2).
Phase list and exit criteria: BUILD_PROMPT.md §22.

**Current position:** The 23 phases of BUILD_PROMPT.md are complete, and the rebuild in
`REDESIGN_PROMPT.md` has begun — R1 (chance, desire and six new games) and R2 (two taps to a card,
with no account and no network) are done; R3 (the card player in full: timers, progress, and the
games reachable from it) is next. Phase 22 complete — every phase in BUILD_PROMPT.md §22 is built and verified,
and every §23 deliverable is written. What remains before a public release is setup and review, not
code: `RELEASE.md` is the ordered runbook, `HUMAN_SETUP.md` the console detail, and `legal/` holds
drafts of the privacy policy, the terms and the Play data-safety answers, pending a lawyer.

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
| 6 | Boundaries + engine filters | ✅ **Complete** |
| 7 | Mutual discovery + batched reveals | ✅ **Complete** |
| 8 | Content system + ≥600 seed items | ✅ **Complete** |
| 9 | Experience engine | ✅ **Complete** |
| 10 | Build Our Night + player | ✅ **Complete** |
| 11 | Game primitives + 7 core games | ✅ **Complete** |
| 12 | Realtime sessions | ✅ **Complete** |
| 13 | Together home + Surprise Us | ✅ **Complete** |
| 14 | Apart mode + async | ✅ **Complete** |
| 15 | Voice + photo | ✅ **Complete** |
| 16 | Scheduled surprises + multi-day | ✅ **Complete** |
| 17 | FCM + notification privacy | ✅ **Complete** |
| 18 | Memories + library | ✅ **Complete** |
| 19 | Subscription | ✅ **Complete** |
| 20 | Settings, export, delete, reporting | ✅ **Complete** |
| 21 | Security hardening | ✅ **Complete** |
| 22 | Performance, cost, a11y, polish | ✅ **Complete** |

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


---

## Device verification (after Phase 5)

The hypervisor is in place (`emulator -accel-check`: WHPX installed and usable), so the app ran
on a device for the first time: the `afterhours_a` AVD, API 36, against the emulator suite. It
found five bugs that no JVM test could, all fixed.

### Verified on the device
- **Instrumented tests** `connectedDevDebugAndroidTest`: 2/2 — the app reads and writes
  through the emulators, and one user cannot read another's document (Phase 1's exit
  criterion, proved for the first time).
- **Sign-up** against the Auth emulator; **age gate** writes its attestation with the server's
  time through the real rules; the four welcome beats.
- **Pairing, end to end**: the app entered a partner's code, showed the same three symbols as
  the server, and moved into discovery by itself once the partner approved over HTTP.
- **`FLAG_SECURE`**: the lock screen and discovery capture as solid black, and the
  app-switcher thumbnail is a black card. The pairing screen (deliberately not secure)
  captures normally.
- **Answers** land in `users/{uid}/preferences` from the Android client through the deployed
  rules, with server time and `taxonomyVersion`.
- **Offline**: in airplane mode the deck kept moving; nothing reached the server; both
  answers synced within 3 s of reconnecting, "secretly curious" intact. (The dev flavor uses
  Firestore's memory cache, so this proves the queue, not survival of process death offline
  — that is prod's persistent cache.)
- **Swipes**: left skips and right goes back at every position tried (20+). Twice, the first
  swipe right after the harness's first accessibility dump of a new screen was dropped; three
  trials without the dump worked, so it is attributed to the harness (see below).
- **Relaunch** of a paired user routes to Home; discovery reopens with the answered cards gone.

### Found and fixed
- **Cleartext to the emulators was refused**, so the dev build had never worked on a device
  (D-026).
- **Every launch without a lock ended on a dead lock screen** (D-027).
- **Content under the status bar and the keyboard**; dark status-bar icons on the dark app
  (D-028).
- **Firebase configured twice per process** under instrumented tests (D-029).
- **Screen-reader duplicates**: the waiting indicator and the card's privacy note announced
  their description and then their visible text again; both now clear and set.
- **Swipe distance** is counted as events arrive rather than read back from the animation,
  removing a race that could turn a fast swipe into a spring-back under load.

### Still needs a person
- **TalkBack by ear.** TalkBack is installed on the AVD (Settings > Accessibility); the
  accessibility tree was checked, the speech was not.
- **How the swipe feels** under a real finger.


---

## Phase 6 — Boundaries, content level and engine filters ✅

### Shipped
- **Boundaries** at `users/{uid}/boundaries/{themeId}`: `level`, optional `note` to self (200
  characters), server-time `updatedAt`. One per taxonomy theme; unset means unrestricted
  (D-030). Rules validate the shape; owner-only read, write and delete.
- **`:feature:boundaries`** — the user's own content level, then every theme by category with
  its current limit; an editor with the five levels spelled out, a private note, and clear.
  A level saves on tap; a note on Save, on the keyboard's Done, or on leaving. `SecureScreen`.
- **Server filters** (D-032): a pure intersection in `functions/src/engine/filters.ts`, a
  transactional recompute, and three triggers (profile, boundary, preference NEVER). Filters
  hold `maxIntensity`, `excludedThemes`, `askFirstThemes`, `curiousThemes`, `excludedItems`
  (D-031). `contentLevelEffective` now follows either partner's level. Deleted on unpair.
- Pairing reads content levels through the same fail-closed function as the filters.
- Shared screen scaffolding moved into `:core:ui` (D-034); `BoundarySlider` accepts "nothing
  chosen yet" and announces each option once.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
npm --prefix firebase/tests test            55/55   (7.3 rows A, B, H, L; boundary shape)
npm --prefix functions run test:unit        20/20   (14 on the intersection)
npm --prefix functions run test:int         34/34   (7 on the recompute)
npm --prefix functions run test:e2e         10/10   (5 proving the triggers fire)
BoundariesViewModelTest 13, BoundariesSnapshotTest 4 (3 goldens reviewed + a screen-reader check)
```
- **Mutation-tested rules:** dropping the level list, the field allowlist, the server-time
  check or owner-only read each failed exactly its test. Letting an *active member* read
  `engineFilters` was caught only by the new member-level L test — the old one read a couple
  that did not exist, so it would have passed.
- **Mutation-tested accessibility:** reverting the slider to plain `semantics` fails the
  "announced once" test.
- **On the device:** a limit set in the app reached Firestore through the rules, and the
  trigger updated the server-only filters within about half a second. The boundaries screen
  captures as black (`FLAG_SECURE`); the note saved through the rules.

### Observed, not reproduced
One integration run (of 12) failed on its first test right after the Functions emulator
hot-reloaded new code — a cold-start trigger contending for the same couple. 11 runs since,
all green. Production triggers retry; Phase 9 recomputes at generation time (D-032).

### Deferred
- **Phase 7:** mutual matching joins `onPreferenceWritten`; secret answers match only secret
  answers (D-021); withdrawing a positive must withdraw an unrevealed match.
- **Phase 9:** recompute filters at generation time; apply `excludedItems` to content tied to
  those items; ASK FIRST prompts go to the listed partner only.
- **Phase 10:** the per-item "Not this one" → NOT TONIGHT / NEVER shortcut (§3.1) writes through
  `BoundaryRepository`.
- **Cost (Phase 22):** a boundary change costs one recompute — up to about 60 reads and 2
  writes for a couple with every theme set.


---

## Phase 7 — Mutual discovery, batched reveals, Secretly Curious ✅

### Shipped
- **Matching** (`functions/src/discovery/match.ts`): two positives match as BOTH_YES,
  BOTH_CURIOUS or MIXED_POSITIVE; two secret curiosities as BOTH_SECRET; a secret never
  matches an open answer (D-021); anything unrecognised is no match.
- **The reveal queue** (D-035, D-036): every visible change waits server-side for its own
  random time; the scheduled `releaseMutualRevealBatches` (every 15 minutes) and the
  `releaseRevealsNow` callable (on app open, minimum delay only) apply it. Triggers queue on
  every answer change and on pairing; unpairing clears the queue.
- **Rules**: members read released matches and may mark one seen for themselves, nothing
  more (7.3 row R: no client-created match); the queue is unreadable by every client.
- **`:feature:discovery`**: one reveal at a time — a concealed card whose content is not in
  the tree, a timed reveal that keeps its pacing under reduce-motion, and for the secret one
  a two-beat reveal ("WAIT… YOU BOTH PICKED THIS 👀", brass bloom). Then everything shared,
  counted truthfully (§14.10). `SecureScreen`. Analytics gets a count.
- First run now goes pairing → private discovery → mutual discovery → Home (§14.1). The app
  asks for due reveals on every start. Callable error mapping is shared in `:core:firebase`.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
npm --prefix firebase/tests test            60/60   (7.3 row R; the queue unreadable)
npm --prefix functions run test:unit        31/31   (11 on matching and timing)
npm --prefix functions run test:int         48/48   (14 on the queue), 4 runs in a row
npm --prefix functions run test:e2e         14/14   (4 on reveals through the triggers)
MutualDiscoveryViewModelTest 10, MutualDiscoverySnapshotTest 6 (5 goldens reviewed)
```
- **Exit criteria.** *A two-user test yields a match*: at every tier, and on the device.
  *A non-match leaks nothing*: nothing queued, nothing revealed, no document anywhere.
  *Timing jitter verified*: 400-draw spread tests, the window at millisecond precision, an
  early release refused, and 12 queued matches getting 12 different times.
- **Mutation-tested rules:** letting a member create a match, change other fields, mark
  their partner's entry, read the queue, or letting an outsider read matches — each failed
  exactly its test.
- **On the device**: the partner answered over the server, Alex answered in the app; the
  trigger queued BOTH_YES for about 160 minutes out, invisible to both. Aged past the minimum,
  a fresh app start released it; the concealed card kept its content out of the
  accessibility tree; revealing it wrote `seenBy` through the rules.

### Deferred
- **Phase 10:** "Build a night around this" (disabled with an honest caption until then,
  D-038); `exploredAt` on a match.
- **Phase 17:** a quiet notification when a reveal lands.
- **Phase 20:** what happens to an ended couple's revealed matches.
- **Phase 22 (cost):** every app start is one callable invocation (throttled to one per five
  minutes per process); every answer change is one small transaction.

---

## Phase 8 — Content system and 626 seed items ✅

### Shipped
- **626 published items in 12 packs** (§9.3), every pack over its minimum, both modes, and
  every level it spans; written to be played, not filler — then checked for repeated
  *concepts* across packs, which the trigram check cannot see (D-039, D-044):

  | Pack | Items | 1 · 2 · 3 · 4 · 5 | Together · Apart |
  |---|---|---|---|
  | FLIRT | 63 | 13 · 14 · 12 · 12 · 12 | 50 · 41 |
  | TEASE | 60 | 12 · 12 · 12 · 12 · 12 | 46 · 26 |
  | CONFESSIONS | 52 | 11 · 10 · 10 · 10 · 11 | 45 · 43 |
  | FANTASY_TALK | 52 | 10 · 10 · 10 · 10 · 12 | 44 · 41 |
  | ROLEPLAY | 47 | 10 · 9 · 10 · 9 · 9 | 40 · 16 |
  | POWER_DYNAMICS | 45 | 9 · 9 · 9 · 9 · 9 | 38 · 14 |
  | SENSORY | 46 | 10 · 9 · 9 · 9 · 9 | 37 · 12 |
  | SURPRISE | 41 | 9 · 8 · 8 · 8 · 8 | 29 · 16 |
  | COUPLE_CHALLENGES | 51 | 11 · 10 · 10 · 10 · 10 | 45 · 20 |
  | LONG_DISTANCE | 61 | 13 · 12 · 12 · 12 · 12 | 7 · 58 |
  | AFTER_DARK | 63 | – · – · 21 · 21 · 21 | 41 · 27 |
  | DEEP_TALK | 45 | 12 · 12 · 11 · 10 · – | 38 · 45 |

  Every chapter kind has at least 19 items per mode, and there are at least three finales
  at every intensity in both modes (the engine's minimums, §10.4).
- **`tools/`** (TypeScript, node:test): `validate` (the §9.4 list plus boundary coverage
  and engine readiness), `bundle`, `publish-content` (Storage, then the pointer; versions
  immutable), `content-delta` (disable, enable, replace, list), `import` (TSV, rows land as
  drafts). One loader and one pack-file format for all of them.
- **The gate in `check`**: `:validateContent` (content valid, committed bundle current) and
  `:testContentTools` (27 tests), with `npm ci` in `tools/` on first use (D-039).
- **On the device** (D-040): `content.db` (Room 2.8.5, its own database, schema exported),
  `ContentRepository` — shipped-bundle install on first use and after app updates, bundle
  sync when the pointer moves, deltas laid over the bundle, fail-closed parsing — and
  `ContentSyncWorker` (WorkManager 2.11.2 via Hilt) at every signed-in session start and
  twice a day.
- **The taxonomy now travels in the bundle** (§9.5): `TaxonomyRepository` reads the
  installed bundle's copy; the separate taxonomy asset and Remote Config key are gone.
- **Rules**: `content/{id}` readable by any signed-in app, writable by none;
  `contentMeta/pointer`, the emulator's stand-in for Remote Config (D-041).
- **The debug content inspector** (D-042): Home → Content inspector, debug builds only.
- Remote Config cleanup: the unused client reveal-timing keys are gone (D-045).
- The Phase 7 commit had picked up 13 JVM crash dumps from an out-of-memory build; they are
  removed and `hs_err_pid*.log` / `replay_pid*.log` are now ignored.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL (content gate included)
npm --prefix tools test                     27/27
npm --prefix firebase/tests test            64/64   (4 new: deltas and the content pointer)
:core:data      ContentBundleFileTest 6, ContentBundleParserTest 5,
                DefaultContentRepositoryTest 15 (real in-memory Room), AssetShippedContentTest 2
:core:database  ContentDaoTest 4
:app            ContentInspectorViewModelTest 3
connected       ContentOnDeviceTest 1/1 — device in airplane mode, Wi-Fi off, no network at all
```
- **Exit criteria.** *Validator passes*: 626 published items, no problems, and `check` fails
  on any edit that breaks it or leaves the bundle stale. *Content loads and browses fully
  offline*: with no network at all, a fresh cache installed all 626 items from the shipped
  bundle in 2.2 s on the emulator; in the app, the inspector browsed and filtered with
  airplane mode on (After dark · apart · 5 → 10 items, as in the bundle).
- **The download path, on the device**: `publish-content --emulator`, then a test bundle v3
  behind the pointer — "Sync now" installed v3 (the retitled item showed); `content-delta
  disable` → 625 items (Flirt 62); `enable` → 626.
- **Mutation-checked gates**: the validator tests break one thing each (a prohibited term,
  an uncovered sensitive theme, an unexcludable preference, a mode mismatch, an unreachable
  requirement, unknown vocabulary, a near-duplicate, a duplicate id, a thin pack) and assert
  exactly that is reported. And through Gradle: a one-word edit to a pack, without a
  rebuilt bundle, failed `./gradlew validateContent` ("stale, it no longer matches the
  packs"); reverting it passed again.

### Found and fixed on the device
- **"Sync now" offline reported success**: Firestore answered the delta query from its cache,
  and the sync time moved. Pointer and deltas are now read from the server only; offline it
  says `failed: Network` and the time stays.
- **The first sync ran before sign-in** (the rules refuse signed-out reads) and nothing tried
  again. A sync now runs whenever a signed-in session starts.
- **A session's sync waited out an old retry**: an offline attempt sat in a 5-minute backoff
  and `KEEP` ignored the new request. Session requests now `REPLACE` it.

### Observed, not changed
- An offline cold start in the dev build shows the age gate: the attestation is checked
  against the server and fails closed, and the dev build keeps Firestore's cache in memory
  only. Staging and prod persist the cache. Offline launch is a Phase 22 topic.
- One ANR ("No response to onStartJob") right after `adb install`: the main thread was
  rendering, and system_server and SystemUI dropped frames at the same moment — emulator
  load, not app code. Not seen again.

### Deferred
- **The "Plus" content** (D-043): game prompts (Phase 11), roleplay scenarios and secret
  missions (Phases 11/14), Open When templates (Phase 14), multi-day arcs (Phase 16) — each
  with its own format and validator rules, in the phase that defines it.
- **Phase 9:** the engine consumes `ContentRepository.items`; the server half of the engine
  (§10.1) needs the same content — from the published bundle in Storage. *Done in Phase 9
  (D-054).*
- **Before staging/prod:** a service account for `publish-content` (HUMAN_SETUP.md §2.9);
  until then the shipped bundle is all any build has, which is complete.
- **Phase 20:** exclude `content.db` from backups along with the rest (it is rebuildable).
- **Phase 22 (cost):** a sync is one Remote Config fetch (throttled by the SDK), one
  delta query returning only changes (≥1 read), and a Storage download only when the version
  moves. Browsing costs no Firestore reads.

---

## Phase 9 — The experience engine ✅

### Shipped
- **The spec, `engine/SPEC.md`** — the algorithm, precise enough that two implementations
  give byte-identical nights: inputs, mulberry32, the chokepoint's eight reasons in order,
  the score, the plan (chapter counts, kinds weighted by mood and leadership, targets that
  rise to the ceiling), sampling, assembly (slot rules, a bounded depth-first fill, variety
  and duration passes), validation (11 codes) and retries. Shared vectors beside it in
  `engine/engine-fixtures.json`, generated by the server, checked by both (D-046, D-047).
- **The server's engine** (`functions/src/engine/`, TypeScript): `chokepoint` — the hard
  filter, and nothing it removes reaches scoring; `score`; `plan`; `sample` — a bounded pool,
  never the permitted set (D-049); `assemble` (D-050); `validate`; `pipeline` — filter,
  score, sample, assemble, then validate and filter again, preferring a finale at the
  requested level (D-051); `catalogue` — the deployed bundle, a newer published one, and
  the deltas (D-054); `weights` — from the server's Remote Config template, clamped (D-055).
- **`generateExperience`**, the callable: the server's own seed and a strict payload (D-052);
  the couple from the server's records; 30 an hour; filters computed first if missing; both
  partners' affinity averaged, used and never returned. It writes
  `couples/{cid}/experiences/{id}`, which holds nothing private, and each asked partner's
  `askFirst/{uid}` (D-053), and returns the night, its alternates and only the caller's own
  ask-first chapters. A couple that has excluded too much gets `not-enough-content` (D-058).
- **The device's engine** (`:core:engine`, pure JVM): `Planner`, `Assembler` (`SlotRules`,
  `Filler`, `VarietyPass`, `Passes`), `Validator`; `PermittedPool`, whose only way in is
  `fromServer`; `PublicScore`, the server's formula with every private term neutral; and
  `Remixer` — ten "Make it more…" modifiers and "Not this one" (D-048, D-057).
- **Rules**: an experience is readable by the couple's active members and written by no
  client; `askFirst/{uid}` is get-only, for that uid; `engineState/**` is closed.
- **Gates**: Konsist `ContentChokepointTest` — the catalogue read only by an allowlist, and
  the pool's constructor, `Planner` and `Assembler` internal. `engine.architecture.unit.test.ts`
  — the catalogue and pipeline only in `experience/generate.ts`, the pipeline's order, and
  the combined boundaries read only where they are computed, filtered or loaded.
- `publish-content` now writes the Firestore pointer in every environment, since the
  server's engine reads it (D-041, amended). `PairingError` is now `ServiceError` (D-056).

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL (engine and architecture included)
:core:engine    SharedVectorsTest 5, ClientAssemblyTest 4, RemixerTest 10
npm --prefix functions run test:unit        54/54   (engine 18, engine architecture 5)
npm --prefix functions run test:int         60/60   (generateExperience 12)
npm --prefix functions run test:e2e         18/18   (generateExperience 4)
npm --prefix firebase/tests test            69/69   (5 new: experiences, ask-first, engine state)
npm --prefix tools test                     27/27
```
- **Exit criteria.** *Full engine test suite green on both implementations*: the server's
  engine, architecture, integration and end-to-end suites, and all 19 Kotlin engine tests.
  *Determinism proven*: the same seed and state give byte-identical nights on the server
  (unit and against Firestore); the Kotlin engine reproduces the server's random sequence,
  23 plans, 37 assemblies and 48 validation verdicts exactly; remixes are deterministic.
- **The real catalogue**: in a sweep of 2,880 requests (both modes, 15–120 minutes, every
  level, six mood sets, two leaderships, four seeds) 2,878 built a valid night, 86% on the
  first attempt; the two that did not are D-058's. In the suite, the finale reaches the
  requested level in at least 95% of nights.
- **Mutation-checked gates**, each seen to fail and then restored: `scoreAll` imported into
  `generate.ts`; the pipeline filtering after scoring; `PermittedPool`'s constructor made
  public; `ContentRepository` imported into the discovery ViewModel.

### Found and fixed
- **Nights overshot their length**: sampling ignored duration, so short nights were built from
  long items. Duration now counts in sampling and in the fill's ranking.
- **`TYPE_RUN` slipped through**: the run check counted the night's length so far instead of
  the plan's.
- **`FINALE_NOT_PEAK`**: a middle chapter could outrank every finale the pool had. Chapters
  are now capped by what the finale can reach.
- **Greedy dead ends**: replaced by the bounded depth-first fill (D-050).
- **Intensity-5 requests peaking at 2**: the retries now prefer a finale at the requested
  level (D-051).
- **Real projects never got the server's pointer**: `publish-content` wrote it only under the
  emulator (D-041, amended).

### Deferred
- **Phase 10:** the builder and player call `generateExperience`; the player writes
  `engineState/history` (cooldowns) and feedback into affinity; `not-enough-content` gets a
  retry and a "try a longer night" (D-058); `ClientContext` is filled from revealed matches
  and local history.
- **Phase 13:** the "Make it more…" and "Not this one" UI, on top of `Remixer`.
- **Phase 21:** SECURITY.md — many generations together could approximate the permitted set;
  the rate limit bounds it (D-049).
- **Phase 22 (content):** more short, intense roleplay and finale items (D-058). **(Cost):** a
  generation reads the user, couple, filters and history documents, the rate-limit document,
  the couple's mutual matches and both partners' affinity (at most 200 each); it writes the
  experience, up to two ask-first documents and the rate limit. Deltas and weights are cached
  per instance.

---

## Phase 10 — Build Our Night and the experience player ✅

### Shipped
- **Build Our Night** (`:feature:night`, §14.5): time, mood, intensity (the dial reaches as
  far as the couple's shared level and no further), who leads, novelty — then the server
  builds the night. "Build a night around this" from a reveal opens it with a focus (D-064).
  A night that finds nothing gets one more try with a fresh server seed before the couple is
  asked to change anything (D-058).
- **The player** (§14.6): the shape first ("Tonight has 5 chapters", never the contents), a
  countdown, then one sealed chapter at a time — kind, level and who leads, "Something
  interesting is coming…" between chapters, chapters cross-dissolving. PAUSE and STOP on every
  live screen, one tap, no confirmation; a pause takes the night off the screen but never its
  controls (D-068). "Not this one" swaps instantly from what the server released and offers to
  keep the theme out, not tonight or never (§3.1). The close: "How are we doing?" (not stored,
  D-065), then private per-chapter reactions, then "Night complete ❤️".
- **Sessions** (§12, D-059): `createSession`, `markReady`, `advanceSession`, `endSession`, every
  move checked against a pure transition table in a transaction and idempotent; one night at a
  time per couple, expiring after six hours untouched; only the host moves it on; either phone
  pauses or stops it; nothing records who stopped.
- **Consent** (§3.1, D-061): BOLD and WILD wait for each partner's own "I'm in", from their own
  phone. The partner's home shows the request; "Not tonight" from either phone ends it warmly.
- **Ask-first on one phone** (D-060): a partner's ask-first themes are left out of a night
  played on the other's phone; the host's own are asked about, hidden among decoy check-ins.
- **Limits set mid-night** (D-062): every step re-runs the chokepoint over what is still to
  come; the phone swaps blocked chapters before they are shown, or passes over one nothing can
  replace. Swaps are reported when played and must be released items (D-066); the cooldown
  history records what was actually played.
- **Feedback into affinity** (D-063): private to its author, one per played chapter, turned into
  the author's own affinity by `onFeedbackCreated`, counted once however often it fires.
- **The engine**: a night's focus — a public score term and a retry preference, nights without
  one bit-identical (D-064); `PermittedPool.without`, which can only shrink a pool.
- **Home**: "Build tonight", and a card for the night under way on either phone.
- `node functions/scripts/dev-couple.js` — a paired couple for device checks, and the partner's
  phone (TESTING.md).

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:feature:night  NightRulesTest 13, NightViewModelTest 16, BuildNightViewModelTest 5,
                NightSnapshotTest 27 (22 screens recorded, 5 on-screen promises)
:core:data      HeldNightTest 7, DefaultNightRepositoryTest 9 (the real shipped catalogue)
:core:engine    22 (focus and the shrink-only pool added to RemixerTest)
npm --prefix functions run test:unit        80/80   (session table 17, feedback 5, focus 4)
npm --prefix functions run test:int         76/76   (sessions 11, feedback 3, generateExperience 14)
npm --prefix functions run test:e2e         22/22   (sessions 4)
npm --prefix firebase/tests test            77/77   (8 new: sessions and feedback)
npm --prefix tools test                     27/27
```
- **Exit criterion — a full night generated and played end-to-end on one device**: on the
  emulator (AVD `afterhours_a`, dev build, Emulator Suite), signed in as Alex: Build tonight →
  15 minutes, Romantic → "Tonight has 3 chapters. About 15 minutes, rising to FLIRTY." → Begin →
  the countdown → each chapter sealed ("Chapter 1. Sealed, ready to open"), opened, done, with
  "Something interesting is coming…" between → "How are we doing?" → a reaction → "Night
  complete ❤️". On the server: the session COMPLETED, all three chapters DONE, the history
  holding exactly the three items played, `stats.nightsPlayed` 1, the couple's place cleared.
  A second night's reaction reached Alex's own affinity through the trigger
  (`category:exploration`, `interaction:CHALLENGE`, `mood:romantic`, `mood:talk` → 1.00).
- **Consent, pause and stop on the device**: a WILD night stopped at "This one goes up to WILD";
  after Alex's "I'm in" it waited for Sam's, given from "Sam's phone"
  (`dev-couple.js ready`), then counted down. Sam paused it — Alex's phone showed "Paused."
  with the chapter gone and Resume and Stop in place — and resumed it; Alex's STOP ended it with
  "Ending here. All good." The server: ABANDONED, STOPPED, two acknowledgements, the chapter
  under way counted as played, nobody named.
- **"Not this one" with real content**: "Same Answer" swapped at once for "Our Best Day", with
  the offer to keep "Games" or "Shared curiosity" out; the server recorded `deep_best_day` as
  chapter 1, and never cooled down the item that was refused.
- **FLAG_SECURE held**: `adb exec-out screencap` of every night screen is black; the checks
  above read the accessibility tree instead.

### Found and fixed
- **On the device, reactions were never saved**: the app checks for its own feedback before
  writing it (feedback cannot be written twice), and the rules refused a read of a document that
  did not exist yet — while the save was fire-and-forget, so nothing said so. The rule now lets a
  partner check their own feedback's id, and only theirs, so the other's still cannot even be
  probed; a failed save keeps the couple on the reactions, told so. Found only by playing a night
  for real.
- **TalkBack said the wrong thing** in three places, found on the device: a sealed chapter read
  "not available yet" although it opens with a tap, the countdown "2 Here we go remaining", and
  the progress "Chapter 0 of 3" before the night began.
- **A finished night could vanish from the player**: the couple's pointer to its night clears
  in the same write that completes it, so a player following the pointer could lose the ending.
  The player follows the session by its own id.
- **STOP waited behind a request in flight**: a slow "Done" held STOP back. STOP and PAUSE never
  wait (a test holds a request open and stops through it).
- **A paused night could still be tapped**: the veil let touches and TalkBack through to "Done".
  A pause now takes the chapter off the screen.
- **`toRoute()` returned defaults in unit tests**, and a player test passed with the wrong night
  (D-067).
- **The keep-out offer sat below the fold** after a swap; it now comes first.

### Deferred
- **Phase 11:** chapters of type CHOICE, PREDICTION and REVEAL play as prompts for now; the
  game primitives make them interactive. *(Games shipped in Phase 11; a night's chapters still
  play as prompts.)*
- **Phase 12:** both phones playing a night together, and a partner's ask-first asked
  privately on their own phone (D-060); heartbeats, reconnection, and expiry by the scheduled
  function; process-death restore from Room (a restarted player reloads from the server today).
- **Phase 13:** "Make it more…" on the shape screen (the Remixer is ready), Surprise Us, the
  15-minute express path, and the real Together home.
- **Phase 14:** Apart nights — the builder is Together-only until then.
- **Phase 18:** "Save to Our Memories" from Night complete.
- **Phase 21:** SECURITY.md — the mid-night blocked list (D-062) and pool unions (D-049).
- **Phase 22 (cost):** a night of n chapters is one generation, one createSession, n + 1
  advances (each re-checking the limits: two reads of filters and matches plus the catalogue
  cache), and at most n feedback writes and triggers.

---

## Phase 11 — Game primitives and the core games ✅

### Shipped
- **Eight games as content** (§11.2): Predict My Answer, Me or You, Would You Rather, Truth or
  Dare, Who Knows Who, Two Truths & A Lie, Couple Battle and Deep Talk — each a definition in
  `content/games.json` (steps over primitives: who takes each, whether it is private, what it
  shows, what a reveal compares) and **366 prompts** in `content/prompts/`, held to the items'
  standard: the vocabulary, the prohibited-terms tripwire, boundary-keyword coverage,
  near-duplicates, and coverage per level and kind — a whole game at SOFT with one to spare.
  The validator checks a game's steps like a compiler would; the bundle carries `games` and
  `prompts` beside `items` (content v2), and older bundles still install.
- **Server** (D-070): `startGame` chooses a game's rounds behind both partners' boundaries —
  hard filter, the night's ranking, a warm-up from two below the ceiling to it, the chokepoint
  again (engine/SPEC.md §10) — with every ask-first theme left out, the host's own too, because
  both hold the phone (D-069). Rounds are stored as an experience of type GAME and played
  through the same session as a night: consent at BOLD and WILD, PAUSE and STOP from either
  phone, each round moved on by the server, the played prompt (a dare over a truth) reported
  and cooled down, a limit set mid-game blocking what it covers. Cooldowns give way — never a
  boundary — when a game could not otherwise be filled.
- **The interpreter** (`:core:engine`, D-072): `GameRun` plays any game the definitions can
  describe — turns alternating from whoever goes first, a hand-off before every private step,
  a gathering before a shared one, eight primitives each with one answer contract, six kinds
  of reveal, points or matches — plus "Not this one" (a released alternate of the same kind,
  never more intense) and limits set mid-game. It starts only from a `ReleasedGame` built by
  `fromServer`.
- **`:feature:games`**: the games hub and setup (how far, up to the couple's shared level; who
  goes first, by name, remembered on the phone), and one player for every game: one renderer
  per primitive, "Over to Sam. Only Sam should look.", "Together now.", reveals, a timer, the
  close ("How are we doing?", then the score). "Not this one" keeps a theme out of the rest of
  that game only — a game never writes anyone's limits (D-071). The partner's phone gives its
  "I'm in", and can pause or stop, but never shows the game.
- **"No duplicated game logic"** is a build rule (`GameLogicTest`): no code names a game; only
  the interpreter and the parser read a game's steps; exactly one renderer per primitive;
  verdicts and scores made only by the interpreter; the interpreter starts only from what the
  server released. Proven by planting all four violations and watching them fail.
- Home: "Play a game", and the card for what is under way now opens a night or a game.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:engine    41   (GameRunTest 19: every primitive, every reveal, hand-offs, "Not this one",
                      limits mid-game, only what the server released)
:core:data      79   (ContentBundleFileTest 9 — every shipped game played to its end through the
                      interpreter; GameParsingTest 6; DefaultGameRepositoryTest 9)
:core:database  5    (prompts installed and replaced with the items)
:feature:games  27   (GameViewModelTest 10, GamesViewModelTest 6, GameSnapshotTest 11: 11 screens
                      recorded, hand-offs showing nothing of what comes next, STOP always there)
:feature:night  61   (unchanged behaviour; the close check-in now shared from :core:model)
:core:ui        2    (SecureScreenTest: the handover between two private screens, D-073)
:architecture   17   (GameLogicTest 6 — four planted violations each failed the build)
npm --prefix tools test                     42/42   (validate-games 14, bundle 5)
npm --prefix functions run test:unit        98/98   (game choice 16, architecture 7)
npm --prefix functions run test:int         87/87   (games 11)
npm --prefix functions run test:e2e         25/25   (games 3)
npm --prefix firebase/tests test            77/77
```
- **Exit criterion — each game playable, on the device**: on the emulator (AVD
  `afterhours_a`, dev build, Emulator Suite), signed in as Alex, all eight games were started from
  "Play a game" and played from the first round to the last, passed between "Alex" and "Sam":
  a hand-off before every private step ("Over to Sam. Only Sam should look."), a gathering before
  each reveal, typed answers and guesses, three statements and a lie, timers, dares, reactions —
  then "How are we doing?" and the score. Would You Rather 8 rounds ("You've matched on 8 of 8");
  Me or You 8 ("0 of 8": each named the other); Predict My Answer 8 (4–4); Truth or Dare 10;
  Who Knows Who 8 (4–4); Two Truths & A Lie 6 (3–3); Couple Battle 8 (every battle a tie, 0–0);
  Deep Talk 6. The scores are even because the script always takes the last option.
- **On the server**: eight game sessions COMPLETED, `stats.gamesPlayed` 8, the couple's place
  cleared, and 64 prompts in the cooldown history — every Truth or Dare round recorded as the
  dare that was played, and not one truth that was only offered.
- **FLAG_SECURE held**: every game screen reads as black to `screencap` — once the handover
  bug below was fixed — so the run read the accessibility tree instead.

### Found and fixed
- **Every private screen was capturable after a handover** (D-073): `SecureScreen` set
  FLAG_SECURE on enter and cleared it on dispose, so a leaving screen switched protection off
  under the screen that had just arrived. Found on the device: the games hub screenshotted in
  full after "Another game" — and so did the night player after Build Our Night. The flag is now
  held by every private screen in composition and cleared only when the last leaves; the new
  `SecureScreenTest` fails against the old code. Re-checked on the device: the hub, a game's
  setup, a game screen, the hub again after leaving a game and the night player all capture
  black (only the system bars, which are not the app's window), and Home still captures in full.
- **Truth or Dare could not be filled at SOFT**: ten rounds need ten truths and ten dares at
  level 1, and the pack had eight of each — the server's game tests found it, choosing eight
  rounds. Six prompts added, and the validator now asks every game for a whole game at level 1
  of every kind, with one to spare (so the released set is never the whole permitted set).
- **A night test pinned the content version** at 1; it now reads the catalogue's own.
- **The interpreter grew past detekt's limits**: split into the moves (`GameRun`), reading the
  round (`RoundReading`) and one function per kind of reveal (`Reveals.kt`).

### Deferred
- **Phase 12:** each partner answering on their own phone (submissions, `AWAITING_SUBMISSIONS`
  → `REVEAL`), which also lets a partner's ask-first be asked privately in a game; the running
  answers restored after process death (a restarted game resumes at the server's round).
- **Phase 13–14:** SecretMission, PartnerControl and Scenario (Tonight's Director, Apart games).
- **Phase 15:** VoicePrompt and PhotoPrompt.
- **Later:** Ranking, RandomSelection and Memory primitives; roleplay scenarios and secret
  missions as content; feedback on game rounds into affinity.
- **Phase 22 (cost):** a game of n rounds is one `startGame` (a catalogue load and the
  couple's filters, matches, history and affinity), one createSession, n + 1 advances.

---

## Phase 12 — Realtime sessions ✅

### Shipped
- **The state machine, in full** (§12.1): `CHAPTER_ACTIVE → AWAITING_SUBMISSIONS →
  BOTH_SUBMITTED → REVEAL → CHAPTER_COMPLETE →` the next chapter or `COMPLETED`, added to the
  one transition table both phones follow (`functions/src/session/transitions.ts`, D-059).
  `RECONNECTING` is never stored: it is what a phone says about itself.
- **What a phone may write** (§12.2): its own `submissions/{chapter}_{uid}`, its own
  `presence/{uid}`, its own "I'm in", and a PAUSE/STOP request — nothing else, enforced by the
  rules. No phone can read a submission, not even its own partner's: the `onSubmissionCreated`
  trigger is the only reader, and in one transaction it writes a single `rounds/{chapter}`
  reveal both may read. Duplicate submissions, premature reveals and double advancement are
  refused by the transaction, not by hope.
- **A session that ends itself** (§12.3): `expireStaleSessions` runs every five minutes and
  abandons any session whose phones have been silent for ten.
- **A phone each** (D-074): the games that both partners answer at once — Would You Rather, Me
  or You, Predict My Answer, Who Knows Who — can be played on two phones. The setup asks "One
  phone" or "A phone each", and with a phone each nobody is named: each side is "You" and "Your
  partner", and no names travel. Each phone answers its own step, sends that one answer as a
  short string (`Answers.kt`), waits, and reads the same reveal. The round moves on only once
  both have seen it; until then the phone that is ready says so rather than showing a button
  that does nothing. A game of turns is still one phone passed between you — being handed it is
  the turn.
- **Cut off, and back** (§12.3): a phone reading its own cache says "Reconnecting…" and keeps
  the last thing the server said on screen; a listener that actually fails is retried with a
  backoff. A partner whose phone has not been heard from for ninety seconds shows as dropped
  out, on the waiting screen and at the round's end.
- **Nothing about a round is written to the phone** (D-075): a phone restarted mid-round rejoins
  at the session's round, never answers twice, and takes its own answer back from the reveal.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:engine    46   (TwoPhoneRunTest 5: each phone answers first and waits after, the reveal
                      read the same on both, an answer encoded and read back)
:core:data      83   (DefaultGameRepositoryTest 13: the answer sent as this phone's own, the
                      reveal by seat, the partner's heartbeat, a dropped listener, a cached read)
:feature:games  40   (GameViewModelTest 16, GamesViewModelTest 8, GameSnapshotTest 16)
:feature:night  61   (unchanged: a night is still played on one phone)
:architecture   17
npm --prefix tools test                     42/42
npm --prefix functions run test:unit        104/104
npm --prefix functions run test:int         95/95   (realtime 8)
npm --prefix functions run test:e2e         27/27   (two clients, one session)
npm --prefix firebase/tests test            84/84   (submissions, reveals, presence: D–G)
```
- **Exit criterion — two devices in one session**: on the emulator (dev build, Emulator Suite)
  signed in as Alex, with `node functions/scripts/dev-couple.js answer|seen|present` playing
  Sam's own phone. Would You Rather with "A phone each": Alex answered and saw "Over to them.";
  Sam answered; both answers appeared at once on Alex's phone ("You matched."). The round did
  not move on until Sam had seen it too — "Still with them." while it waited. Round 2 with the
  order reversed (Sam first) revealed the moment Alex answered.
- **Process death**: the app was killed and reopened mid-round. It came back through "Your game
  is waiting" to the same round, with both answers — its own from the reveal, never from a
  cache — and did not send a second submission.
- **Ninety seconds of silence**: with Sam's heartbeat stopped, "Your partner dropped out —
  waiting…" appeared and stayed.
- **Airplane mode**: "Reconnecting…" appeared with the network gone, the reveal stayed on
  screen under it, and both cleared when the network came back.
- **No regression on one phone**: Me or You played as before — "Over to Alex. Only Alex should
  look.", the gathering, the reveal with their own names.

### Found and fixed
- **A phone each would have deadlocked on a game of turns** (D-074): the first reading placed
  the other partner's steps in order, so the second seat waited for the first — but the reveal
  arrives only once *both* have answered. Each phone now takes its own steps first and waits
  after them, and the choice is offered only for games whose steps are all `EACH` or `TOGETHER`.
- **A heartbeat loop in the ViewModel hung every test** (D-076): `while (true) { delay }` in
  `viewModelScope` never lets `advanceUntilIdle()` finish. The beat moved into the repository.
- **"Reconnecting…" never showed on a real network loss**: Firestore does not fail a listener
  when the network goes — it goes quiet and serves the cache. The listener now includes metadata
  changes, and a snapshot that starts coming from the cache *is* the reconnecting state.

---

## Phase 13 — Together home, Surprise Us and Mystery Night ✅

### Shipped
- **Home** (`:feature:home`, §14.2): it opens with "Tonight could get interesting." — never a
  questionnaire — and one prominent switch drives the surface: the same room, or apart. What the
  couple has under way, from either phone, is the first thing on it. Together holds 🔥 Build
  tonight · 🎲 Surprise us · 🎮 Play a game · ⏱ We have 15 minutes, a 🌙 Mystery Night card, both
  ways into discovery, and the quiet things (limits, app lock, sign out, unpair). Apart names what
  is coming in Phase 14 rather than offering controls that do nothing (D-079). The switch is kept
  on the phone, because it is about where this phone is rather than about the couple.
- **Surprise Us** (D-078): one tap. The phone sends *nothing* — `parseSurprise` refuses every
  field it could steer with — and the server picks the shape from what only it can see: one mood
  that is not the last night's, the couple's own ceiling, and however long their recent nights
  have run. It goes through the same `buildExperience` as every other night, and returns what it
  chose so the phone can still remix it.
- **Mystery Night** (D-077): the same night with `mystery` set. The shape screen says "You'll find
  out as you go." — no chapter count, no length, no "Make it…" — and the progress row counts up
  as it goes instead of showing how many are left. Everything else (STOP, PAUSE, limits, "Not this
  one") is unchanged, because nothing else is.
- **"MAKE IT…"** (§14.6): the ten words, on the night's shape screen, planned again by the
  engine's `Remixer` over the pool the server already released — never past the night's own
  ceiling (D-048). Because a remix can change how many chapters a night has, and a session counts
  them, the new shape is written back through `remixNight`, which takes only ids that night
  released, re-checks them at the chokepoint, and refuses once the night has started (D-080).
- **"We have 15 minutes"** (§14.5): one route argument, `Route.BuildNight(express = true)`. One
  screen, one question — a mood, or "surprise us" — at fifteen minutes and the couple's own level.
  Nobody sets five dials for something short.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:data      87   (a surprise held like any night, a remix agreed with the server, and one
                      the server refuses not played either)
:feature:home   8    (HomeViewModelTest 5, HomeSnapshotTest 3)
:feature:night  67   (express, "Make it…", and the mystery shape screen)
npm --prefix tools test                     42/42
npm --prefix functions run test:unit        111/111  (surprise 7)
npm --prefix functions run test:int         104/104  (Surprise Us and Mystery Night 5, remix 4)
npm --prefix functions run test:e2e         29/29    (surpriseNight over HTTP 2)
npm --prefix firebase/tests test            84/84
```
- **Exit criterion — every Together entry point reaches a playable session**: on the emulator
  (dev build, Emulator Suite), signed in as Alex with the script as Sam. Home → **Surprise us**
  built a 6-chapter, 54-minute night at their own ceiling from one tap; "⚡ Shorter" and
  "❤️ More romantic" each rebuilt it (51 minutes, peaking BOLD) and the couple's stored night
  changed with it; both said "I'm in"; the player opened "Chapter 1 of 6" — the same six the
  server had — and the first chapter played. Home → **🌙 Mystery night** showed "You'll find out
  as you go." with no count and no length, and its player read "CHAPTER 1" with no total though
  the session held six. Home → **We have 15 minutes** → "Naughty" gave a 3-chapter, 15-minute
  night in two taps. **Apart** showed what is being built and nothing that pretends to work.

### Found and fixed
- **A local remix desynchronised the phone from the server**: "Make it…" could change how many
  chapters a night has, but the session's count comes from the stored experience — the player
  showed "Chapter 1 of 4" for a five-chapter session. Caught on the device on the first run.
  A remix is now written back through `remixNight` (ids only, all of them already released, the
  chokepoint re-applied, refused once the night has started), and a remix the server will not
  take is not played here either.

---

## Phase 14 — Apart mode, and a night planned for later ✅

### Shipped
- **Apart is its own way in** (§2.6, §13): the switch on home leads to a surface built for being
  apart — a night for the two of you when you are not in the same room, a surprise, and the thing
  Apart is really for: planning something they find later.
- **"Your partner planned tonight 👀"** (§2.5 #2, D-081): one partner sets the shape and the
  server builds the night **for the other** — their boundaries, their ask-first themes, their
  phone. The planner is told how many chapters it has and nothing else, ever: a planned night is
  readable by the partner it was planned for alone, listing experiences is closed to everyone, and
  `planNight` returns no ids. What both may see is the plan — that there is one, how big, and
  whether it has been opened.
- **Async sessions** (§12.4, D-082): a session for a planned night is `ASYNC`. The planner's "I'm
  in" is recorded when it opens, because they chose how far it goes; only the partner's is still
  to give. Nobody is expected to be present, and the sweep gives it a week rather than ten
  minutes.
- **Home says what is waiting**: "Something is waiting for you" on the partner's side, with the
  chapter count and no more; "You've planned something. They haven't found it yet." on the
  planner's, which becomes "They've opened what you planned." when it lands.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:data      89   (a plan is a shape here, never a night to play; plans say whose they are)
:feature:home   11   (what waits, what was planned, and what neither side is told)
:feature:night  71   (planning for them asks the server to build it for them)
npm --prefix tools test                     42/42
npm --prefix functions run test:unit        111/111
npm --prefix functions run test:int         110/110  (planned nights 3, the async flow 3)
npm --prefix functions run test:e2e         29/29
npm --prefix firebase/tests test            86/86    (a planner cannot read what they planned)
```
- **Exit criterion — the async flow completes with the partners never online at once**: on the
  emulator, signed in as Alex: Apart → "Plan a night they'll find later" → 45 minutes, mysterious
  → "6 chapters, waiting for them." Home then said "You've planned something. They haven't found
  it yet." The server held the plan as WAITING, the night as `plannedFor: sam`, and nothing about
  its contents reached Alex's phone. Signed out; signed in as Sam on the same device: home showed
  "Something is waiting for you. 6 chapters", opening it flipped the plan to OPENED, and the night
  played — `kind: ASYNC`, Alex's consent already recorded, chapter 1 of 6 opened and read, then
  STOP, and the couple's place was free again. Alex was never in the app for any of it.

### Found and fixed
- **The rules suite could only run once against a given emulator**: "written once" tested a
  submission the previous run had left behind, so a second run failed on a document that was
  supposed to be fresh. The fixture now clears both partners' submissions for that chapter, and
  the suite passes twice in a row.

---

## Phase 15 — Voice notes, photos, and a burn that really burns ✅

### Shipped
- **Send something** (`:feature:send`, §13): record a voice note or pick a photo, add a line, and
  send it to the two of you and nowhere else. What is recorded stays in this app's own cache
  until it is sent; what is opened is fetched with the user's own credentials. Nothing anywhere
  holds a download URL.
- **Reserve, upload, confirm** (D-083): the server writes the record first and names the one path
  the phone may upload to; the Storage rules allow that path only to the author of a PENDING
  record; the server confirms the object landed before the partner can see there is one. So there
  is no object without a record, and no record without an object for long.
- **Burn after view** — really: opening a burn-after-view item deletes the object and then the
  record, server-side. The Storage rule asks for the record before it allows a read, so the path
  stops serving the moment the record is gone (§7.3 row P). An expiry sweep does the same hourly,
  and takes unfinished uploads with it.
- **Storage rules** (§7.2): couple membership checked against the same document Firestore checks,
  images ≤ 10 MB, audio ≤ 20 MB, the content type held to the folder, no client delete, no client
  overwrite, and no listing.
- Home says when something is waiting: "They sent you a voice note", and whether it is once-only.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:data      89
:feature:home   12   (what they sent waits here until it is opened)
:feature:send   10   (SendViewModelTest 6, SendSnapshotTest 4)
npm --prefix tools test                     42/42
npm --prefix functions run test:unit        111/111
npm --prefix functions run test:int         116/116  (media 6: reserve, confirm, burn, sweep)
npm --prefix functions run test:e2e         29/29
npm --prefix firebase/tests test            92/92    (Storage O and P, and six more rows)
```
- **Exit criterion — rules tests O and P pass; media never public**: `storage.test.mjs` proves an
  upload is allowed only to the path the server reserved and only to its author, that the content
  type is held to the folder, that both partners can read what was sent and nobody else can (row
  O, including an unauthenticated read), and that deleting the record denies the object (row P).
- **On the device**: signed in as Sam, recorded a voice note, added a line, turned on "gone after
  they open it", and sent it; the server held it as SENT with the object beside it. Signed out;
  signed in as Alex: home said "They sent you a voice note — once only", opening it played from a
  file on the phone and said "This one is gone now — for both of you", and the server had neither
  the record nor the object any more. A second note, sent without the burn, was still there.

### Found and fixed
- **The emulator's default bucket is not the app's** (D-084): the first send uploaded fine and
  then failed, because the functions runtime defaults to `<project>.appspot.com` while the app
  uses `<project>.firebasestorage.app`. The server now names the bucket when it runs in the
  emulator. Only the device check could have found this: both halves were correct on their own.

---

## Redesign R2 — Two taps, and you are playing ✅

The wall came down. What used to be eleven screens and about ten minutes before the first card is
one screen and two taps, with no account, no partner and — as the device check proved — no network.

### Shipped
- **`Heat`**: three levels where the engine has five. A person meets Playful, Hot and Wild and can
  change them mid-card with one thumb; `Intensity` still runs the engine underneath. Each heat names
  a **ceiling**, never a promise.
- **`InstantDeck`** (D-100): a device-side deck over the content that shipped in the APK. Its safety
  rests on there being no partner yet — everything it reads belongs to the person holding the phone.
  Bounded to published, `TOGETHER`, at or below this phone's heat, needing no media and no mutual
  match. `narrow` can only ever remove, which is what will keep it a second gate rather than a way
  round the server's filter when a couple does sign in.
- **One screen for first launch**: 18+ and how far tonight goes, together, with "My partner isn't
  here" as the quiet second action. The attestation is still the gate §3.1 requires — recorded
  locally while there is no account, and still checked against the **server** record on a signed-in
  device, so a local flag has never been enough.
- **The card player**: one instruction, `Skip` and `Done`, and `Too far` beneath. Skip asks nothing
  and records nothing. "Too far" asks one question — just this one, or never again — and writes the
  card's own boundary themes to this phone, which is how a limit is set now: one tap, during play,
  instead of a twenty-card wizard at midnight.
- **A heat ramp that asks**: after five yeses in a row the app offers to turn it up. Offered, never
  applied.
- **Signed out is no longer a dead end**: the splash sends someone new to that one screen, and
  someone who has already been through it straight back to playing.

### Verified
```
./gradlew check                                BUILD SUCCESSFUL
:core:engine InstantDeckTest                    9/9
:feature:instant InstantPlayViewModelTest      10/10
:feature:onboarding SplashViewModelTest        11/11  (2 rewritten for the new routing)
```
- **On the device, with Wi-Fi and mobile data switched off**, from a cleared install: notification
  prompt, then the one screen, then **two taps to a real card** — "HOT · 5 MIN · Opposite Corners".
- Skip moved on with nothing recorded. Five Dones offered "Turn it up?", and taking it moved the
  deck to WILD. "Too far" → "Never show me these again" moved to the next card, and a cold restart
  came back **straight to playing** at the heat that had been chosen, which is the local record
  doing its job. No `FATAL EXCEPTION` in logcat.
- The exit criterion in `REDESIGN_PROMPT.md` was "cold install to first card in two taps, on a
  device". It is met with the network off, which is a stronger claim than the phase asked for.

---

## Redesign R1 — Chance, desire, and six games that are not quizzes ✅

The first phase of `REDESIGN_PROMPT.md`: the device walk said the app looks basic, and half the
reason was the games. All eight were talk — guess, predict, confess. Nothing physical, nothing
where chance decides, nothing that escalates.

### Shipped
- **Two new primitives**, each one vocabulary entry, one model enum, one interpreter case and one
  renderer, as the rule requires: `DRAW` (chance picks from the prompt's own pool) and `DIAL` (how
  much, on a scale nobody says out loud), plus the `DISTANCE` reveal that compares two dials.
- **A draw that is not a roll** (D-099): the landing is computed from the round —
  `hash("gameId|round|step|promptId")` through `mulberry32` — so a test can assert it, two phones
  agree without exchanging anything, and a restarted phone lands where it did before. The screen
  animates towards a decision already taken. `engine/SPEC.md` §10 now states it.
- **A dial with no direction**: `DISTANCE` publishes the gap and the lower number as a floor, never
  who wanted it more — because that is what would teach someone to answer dishonestly next time.
- **Six games, as content**: Spin, Dirty Dice, Yes No Maybe, Rate It, Three Minutes Yours and Strip
  Round — 114 prompts across five intensity levels, all authored under the same rules as items, all
  refusable through their own boundary themes. No new screen, and no code that names a game.

### Verified
```
npm --prefix tools run validate              content OK, 14 games
npm --prefix tools test                       42/42
npm --prefix functions run test:unit         133/133 — every new game fills every round at every level
:core:engine DrawAndDialTest                  10/10
./gradlew check                               BUILD SUCCESSFUL
```
- **The interpreter plays all fourteen**: `ContentBundleFileTest` walks every shipped game from its
  first round to its last through `GameRun`, so a game that cannot be played cannot be shipped.
- **The server can fill them**: `game.unit.test.ts` releases every round of every game at intensity
  1 to 5, under the ceiling, from that game's own prompts.
- Three tests had to be taught the new shapes, and each was a real assertion rather than a
  formality: the primitive-to-renderer map (one renderer each), the interpreter walk (a gap counts
  as a comparison), and the game list (named server-side, counted in the tools).

---

## Phase 22 — Performance, cost, accessibility, polish ✅

### Shipped
- **R8, on and verified** (D-095): the release build shrinks to **5.5 MB** from a 31.3 MB debug
  build, with `mapping.txt` for Crashlytics. The keep list in `app/proguard-rules.pro` is three
  entries long and each says why — kotlinx.serialization's generated serializers, the messaging
  service the manifest names, and line numbers in stack traces. The release APK installs, launches
  and plays a night with nothing missing at runtime.
- **The accessibility audit as tests** (D-096): `AccessibilityTest` composes the specimens at
  `fontScale = 2.0` and records four 200% goldens, asserts every clickable node is at least 48 dp
  in both directions, and asserts every clickable node carries text or a content description.
  `ContrastTest` already held WCAG AA against the dark palette.
- **A leak test that counts listeners** (D-097): `ListenerReleaseTest` proves a Firestore listener
  lasts exactly as long as its collector, that two collectors are two listeners, and that nothing
  registers before something collects or while signed out. LeakCanary rides along in debug builds
  for the run-through §18 asks for.
- **`COST.md`**: reads, writes and invocations per night counted from the code — ~70/150/20 on one
  phone, ~120/300/30 on two — with the three drivers (the 20-second presence heartbeat, five
  scheduled sweeps, live-session listeners), what already holds each one down, and what would
  change the numbers.
- **`ACCEPTANCE.md`**: the two-device script, fifteen sections, each step saying what to do and what
  you should see, with a run log that states plainly which sections were walked and which were not.
- **A session that is gone is signed out on open** (D-098) — found by the device check, and the
  first thing a person actually hits: after the Auth emulator restarted, the app still held a
  session, every Firestore call failed `UNAUTHENTICATED [ INVALID_REFRESH_TOKEN ]`, and the age
  gate's write — queued offline, retried for ever — left "One moment…" on screen with no way out.
  The splash now refreshes the token before routing and signs out a session that is definitively
  dead (deleted, disabled, revoked, password changed elsewhere); being unable to tell, offline,
  keeps the session. `confirmAge` is bounded at ten seconds so no write can hold a spinner for ever.

### Verified
```
./gradlew check assembleDevDebug             BUILD SUCCESSFUL
npm --prefix functions run test:unit         133/133
npm --prefix functions run test:int          158/158
npm --prefix firebase/tests test             121/121
unit tests, every module                     559 tests, 0 failures
:core:designsystem AccessibilityTest         6    (four 200% goldens recorded)
:core:data ListenerReleaseTest               4
:feature:onboarding SplashViewModelTest      10   (2 new: a dead session, and the order)
:app:assembleDevRelease                      BUILD SUCCESSFUL — 5.5 MB, mapping.txt written
```
- **Exit criterion — `COST.md`, a11y audit clean, no leaks, R8 release build installs and runs,
  `ACCEPTANCE.md` executed**: the first four are above and in the file list; the fifth is executed
  as far as one machine can take it, and `ACCEPTANCE.md`'s run log says exactly which parts were
  not — section 8 on two physical devices, notification delivery, and the Play purchase flow.
- **On the device (release APK)**: the shrunk APK installed on the AVD, passed the age gate, signed
  in, and built a real night — "Tonight has 4 chapters. About 36 minutes, rising to FLIRTY" — with
  **no** `FATAL EXCEPTION`, `ClassNotFoundException`, `NoSuchMethodError`, `NoClassDefFoundError` or
  serialization failure in logcat, which is what R8 breaks when it breaks.
- **On the device (LeakCanary)**: a walk through build-a-night, games and send-something, then seven
  activity recreations by rotation. LeakCanary watched all seven `MainActivity` instances and
  reported **no retained objects** — no heap dump, no leak trace.
- **On the device (the session fix)**: with the app signed in, every Auth account was deleted under
  it. A cold start reached the sign-in screen in 8 seconds instead of hanging on "One moment…",
  and nothing crashed.
- **Cold start**, measured on the headless AVD with `am start -S -W`: **1.9 s and 2.4 s for the
  release build**, 5.5 s for the debug build — which is what a debug build costs: not shrunk, not
  optimised, and installing LeakCanary on the way up. §18’s target is under 2 s to first frame on a
  mid-range device; a software-rendered emulator on a 7.4 GB machine is not that device, so the
  release numbers are recorded as encouraging rather than as proof. The target stays unverified
  until it runs on real hardware — `ACCEPTANCE.md` says so too.

---

## Phase 21 — Security hardening ✅

### Shipped
- **The §7.3 matrix, row by row**: a suite named "§7.3 — the mandatory matrix" with one test per
  row, A to T, named as the brief words each scenario — so the table and the test names map
  one-to-one. Rows O and P stay in `storage.test.mjs`, where the objects are.
- **The server's own doors**: `security.e2e.test.ts` reads the callable surface out of `index.ts`
  rather than a hand-kept list, so a callable added later is swept the moment it exists. Every one
  refuses a caller with no token and one with a forged token, says nothing about itself while
  refusing, is declared with the shared options (App Check included), and takes identity only from
  `callerUid`.
- **Two real flaws found and fixed** by that sweep on its first run: `respondToPairing` and
  `unpairCouple` validated their payloads *before* checking who was calling, so an unauthenticated
  caller learned the shape of a request it was never allowed to make. Identity comes first now.
- **Two flaky suites made repeatable**: the submissions suite takes its own session id per run and
  resets it before each test (with the functions emulator running, `onSubmissionCreated` moves the
  real session on as the tests write to it), and the matrix uses its own uids and couple so it
  cannot disturb fixtures the suites above it create through the rules.
- **`SECURITY.md` written**: who this defends against and who it does not, what each promise rests
  on and where, how the layers fit together, ten residual risks stated plainly — including the
  sampled-alternates inference §5.5 asks to be honest about — and the matrix with its tests.
- **The e2e tier caught up**: Phase 19's free-tier gates had broken two e2e tests that were never
  run at the time. The e2e fixtures now buy premium the way the app does, with a `dev-` purchase
  token only the emulator's verifier accepts.

### Verified
```
npm --prefix firebase/tests test            121/121  (the matrix: 20 rows, A–T) — twice in a row
npm --prefix functions run test:unit        132/132
npm --prefix functions run test:int         158/158
npm --prefix functions run test:e2e          35/35   (security 6)
./gradlew check                             BUILD SUCCESSFUL
```
- **Exit criterion — every row of §7.3 passes; `SECURITY.md` written**: the matrix suite is the
  table, and it is green twice in succession from a cleared emulator.

---

## Phase 20 — Settings, an export, and an ending ✅

### Shipped
- **Settings** (`:feature:settings`, §14.9): one hub for everything about this account and this
  couple — private answers, limits, notifications, app lock, memories, premium, unpair, export,
  delete, sign out. Home keeps only memories, limits and a way in.
- **Reporting** (§3.5): four reasons, no free-text box, on any chapter they played and on anything
  the partner sent. A report lands in `reports/{id}`, which **no client may read**, and the
  partner is never told. The same `ReportSheet` in both places, saying so.
- **A data export that is a real file**: the server assembles it and writes it to
  `users/{uid}/exports/…` — readable by that person alone, writable by no client — and the app
  fetches it with their own credentials. It holds their own documents and what the couple shares;
  never a field of the partner's private record, and never the inside of a surprise that has not
  been delivered (D-093).
- **Deleting an account ends the couple** (D-093), because almost nothing here belongs to one
  person: the cascade takes every document under the user and under the couple, both Storage
  prefixes, everything scheduled, and the Auth user last. The partner keeps their own account,
  their own answers and their own limits — and nothing the two of them made. The screen says all
  of it before the first tap, and the real button sits behind a second one.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:feature:settings   16   (SettingsViewModelTest 6, NotificationSettingsViewModelTest 4, snapshots 6)
npm --prefix functions run test:int         158/158  (account 9)
npm --prefix firebase/tests test            102/102  (an export is read by its owner alone)
```
- **Exit criterion — delete cascades across Auth/Firestore/Storage/schedules; export produces a
  real file**: `account.int.test.ts` asserts both directly — the cascade across all four, twice
  over, and an export written, downloaded back and parsed.
- **On the device**: the export came back as a real file — 606 bytes at
  `cache/exports/afterhours-1790345807995.json`, holding Alex's own account, his couple's shared
  record and nothing of Sam's. A photo Sam sent was reported as "This feels unsafe": the sheet
  said "It goes to us, not to them", and the report landed in `reports/{id}` with `state: OPEN`.
  Then the account was deleted from the screen that says what goes: afterwards Alex's user
  document, preferences, tokens, Storage objects and **Auth user** were all gone, the couple and
  every object under it were gone, and Sam still had their account with `coupleId: null`. The app
  returned to the sign-in screen.
- **Found on the device** (D-094): the first attempt stopped half-way — deleting documents the
  phone was still watching threw `CANCELLED` from the listen stream, and the account survived its
  own deletion. Every delete is now retried and stepped over rather than abandoning the cascade,
  and the account's own removal no longer depends on the couple's data going cleanly.

---

## Phase 19 — Subscription, and a gate that never stands in the way ✅

### Shipped
- **The client never decides.** A phone hands `verifyPurchase` a token and nothing else; the
  server asks the store what it is worth, writes `users/{uid}.entitlement` — which §7.3 row I
  denies to every client — and publishes the couple's derived tier onto the couple document,
  without a purchase token in it.
- **Couple-scoped** (§19): `coupleEntitlement` takes the better of the two members' own records,
  so one subscription covers both partners with nothing copied between accounts.
- **The store is a flavour** (D-091): `app/src/dev` binds a fake client whose tokens begin `dev-`,
  `app/src/play` binds the real Play Billing client for staging and prod, and the dev verifier
  refuses to run outside the Emulator Suite. Everything under the token is the production path.
- **The whole subscription lifecycle**: purchase, renewal, grace (still in), on hold (out),
  paused, cancelled (runs to the end of its term), expired, and a refund that ends it at once —
  applied from Play's notifications, with a six-hourly sweep for the ones that never arrive.
- **Four gates, each at the chokepoint its feature already has** (D-092): three built nights a
  week and Mystery Nights inside `buildExperience`, multi-day arcs in `startArc`, three surprises
  waiting in `createSurprise`. Everything else is free — every game, voice notes and photos, the
  library, planning a night for a partner.
- **Never inside a night.** Nothing that moves a night on calls a gate, and the integration suite
  proves it by spending a free couple's week, watching the fourth build refused, and playing the
  night they already have anyway.
- **The paywall** (`:feature:paywall`, new) says what is included, what it costs and that
  everything they have stays theirs. A refusal becomes `PremiumNudge` where the tap happened.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:feature:paywall    10   (PaywallViewModelTest 7, PaywallSnapshotTest 3)
npm --prefix functions run test:unit        132/132  (billing 12)
npm --prefix functions run test:int         149/149  (billing 11)
npm --prefix firebase/tests test            101/101
```
- **Exit criterion — fake billing entitles in `dev`; no paywall interrupts an active session**:
  `billing.int.test.ts` buys with a `dev-` token and watches every limit lift, and its centre is a
  free couple who have spent their week being refused a *new* night while starting, readying and
  advancing the one they have.
- **On the device**: a free couple tapped MYSTERY NIGHT and got "Mystery Nights are premium." with
  "See what's included" and "Not now" — nothing built, nothing lost. The paywall showed what is
  included and the dev price; "Go premium" bought with a `dev-` token, and the server wrote the
  entitlement **on the buyer's account only** (`source: DEV`), published the couple's copy, and the
  screen said "Premium, for both of you." The same tap then built and opened a Mystery Night.
  Dropped back to free with `dev-couple.js free`, a fourth night that week was refused with
  "That's this week's nights."
- **Not verifiable locally**: the real Play purchase flow. There is no Play account, Play Billing
  has no emulator, and the `staging`/`prod` variants are disabled here for want of a
  `google-services.json` — so `PlayBillingClient` has not been compiled or run at all. It is
  written against Billing 7.1.1 and reviewed by eye only; the console steps that make it testable
  are HUMAN_SETUP.md §3.1.

---

## Phase 18 — Memories, and the library that may list ✅

### Shipped
- **A memory is their words and the shape of an evening** (§14.8): moods, how long it ran, how
  many chapters they played, a title, the moment they want to keep, a note, a rating, and any
  voice note or photo they attached. **Never a content id** (D-090) — which is exactly why the
  library may list freely when experiences cannot be listed at all (D-081).
- **The server fills in what the phone could be wrong about**: the date, the length, the moods and
  the fact that the night was played come from the night itself. A night that is not theirs, or
  that nobody played, is refused.
- **One memory per night**, keyed by the experience: saving again edits what they kept, and
  neither the favourite mark nor who first kept it is lost to the edit.
- **`favorite` is the one field a phone writes**; keeping and forgetting are callables. Either
  partner may forget one, and what it pointed at keeps its own life — a voice note still burns on
  its own terms (D-083).
- **Pages of twenty, newest first**: a library only grows, so it is never read whole.
- **The screens** (`:feature:memories`, new): "Save to Our Memories" at the close of a night —
  one tap from NIGHT COMPLETE ❤️ — and the library itself, with "Our nights" and "We love".

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:feature:memories   13   (MemoriesViewModelTest 6, SaveMemoryViewModelTest 4, MemoriesSnapshotTest 3)
npm --prefix functions run test:int         138/138  (memories 5)
npm --prefix firebase/tests test            101/101  (both read, both may mark, nothing else)
```
- **Exit criterion — save, browse (paginated), favorite, media attached**: `memories.int.test.ts`
  saves a played night and reads it back, `MemoriesViewModelTest` pages twenty at a time and
  marks one, and `SaveMemoryViewModelTest` attaches up to four of the things they sent each other.
- **On the device**: Alex built a fifteen-minute night, played all three chapters, left feedback,
  and tapped "Save to Our Memories" at NIGHT COMPLETE ❤️. The library then showed
  "TALK · 15 MIN · ALL 3 — The Tuesday one — "the bit where you laughed"", marking it a favourite
  reached the server, and the stored memory carried **none** of that night's three content ids.
- **Deliberately not built yet**: the Room cache of the library's first page (§6.3). It is a cost
  and cold-start concern, and belongs with Phase 22.

---

## Phase 17 — Notifications that say nothing ✅

### Shipped
- **One place sends** (`functions/src/notify/notify.ts`), and every message is **data-only**: no
  `notification` block at all, so nothing is ever displayed on the app's behalf. The payload is
  four fields — `kind`, `channel`, `at`, and at most one document id — and `buildMessage` refuses a
  reference that is not a Firestore id, so a title cannot ride along even by accident (D-088).
- **The words are the phone's.** `NotificationText` in `:core:notifications` is a fixed table with
  no placeholders: "They left you something 👀", never "Your partner selected 'blindfold'". With
  previews hidden — the default — even the kind is left out and it says only "Something's waiting
  for you."
- **All ten triggers of §16.1**, each after the transaction that caused it: pairing asked and
  accepted, something left and something delivered, a night planned, a day of an arc opened, a
  partner finished their part, both in, a voice note or photo sent, and an async night an hour
  from timing out.
- **Whether to send is the server's** (D-089): `users/{uid}.notificationSettings` — on, off, and
  quiet hours read on the user's own clock from their `timezone`, wrapping midnight the way people
  mean it. A window the server cannot parse is ignored rather than obeyed.
- **Tokens are owner-only**: `users/{uid}/fcmTokens/{tokenId}`, shape-checked, server-timestamped,
  and dropped the moment the push service says a token is gone. A token in someone else's list
  would forward their notifications to the writer's phone.
- **The settings screen** (`:feature:settings`, new): three switches and the promise in plain
  words. Four channels, created at first launch so one can be silenced in Android's own settings
  before anything arrives. Android 13+ is asked once, on start.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:notifications  NotifierTest 5
:feature:settings    6   (NotificationSettingsViewModelTest 4, SettingsSnapshotTest 2)
npm --prefix functions run test:unit        121/121  (notify 10 — the payload inspection)
npm --prefix functions run test:int         133/133  (notify 6)
npm --prefix firebase/tests test            100/100  (fcmTokens owner-only, settings held to shape)
```
- **Exit criterion — a payload-inspection test proves no content in any notification**:
  `notify.unit.test.ts` takes every kind apart (keys, values, no `notification` block) and refuses
  a title, a name or a sentence as a reference; `notify.int.test.ts` drives the real flows — a
  surprise titled "For Friday" with a line inside it, a planned night, an arc day — through a
  sender that keeps what it was given, and asserts none of those words appear anywhere in what was
  sent. `NotifierTest` proves the same on the phone: every line on a lock screen comes from the
  app's own table.
- **On the device**: the permission prompt appeared on first start; the four channels
  (`pairing`, `surprises`, `together`, `messages`) are on the phone, from launch, before any
  message. The settings screen wrote `{enabled: true, showContent: true, quietHours: 22:00–07:00}`
  to the user document — accepted by the rules — and said so back ("They left you something 👀").
- **Not verifiable locally**: delivery itself. FCM has no emulator, and the dev flavour's project
  is not real, so the token fetch fails (`FCM Registration failed`) and nothing is registered —
  handled without an error on screen. The console steps for staging are HUMAN_SETUP.md §2.3a.

---

## Phase 16 — Things left for later (surprises, Open When, capsules, countdowns, arcs) ✅

### Shipped
- **One mechanism, three shapes** (§13): a **scheduled surprise** for a time, an **"Open when…"**
  for a moment (you miss me · you need a laugh · you can't sleep · you want a surprise · you're
  thinking about me · you need attention · you want to remember us), and a **capsule** for
  further off. All three are a surprise document plus a payload document beside it.
- **Guarded until its moment** (D-085, §5.7): the surprise says there *is* something — both
  partners see that, which is the whole feeling — and the payload is readable by its author at
  any time, and by the couple only once the server has written `deliveredAt`. §7.3 row S is a
  rule, not a screen.
- **Delivered by the clock**: `deliverSurprises` runs every five minutes and delivers everything
  whose moment has come, once. "Open when…" is delivered by a callable that hands over exactly
  one — the oldest kept for that moment — and refuses the partner who left it.
- **Countdowns** (§13): either partner keeps one, either drops it, and the rules hold it to a
  title, a moment and its author. There is nothing private in a countdown, so there is no server
  logic in it either.
- **Home and the Later screen**: "Leave something for later" on the Apart surface; what has
  arrived is a card that opens it; what is still waiting says only that something is coming.
- **A few days of this** (§13): one · three · seven · fourteen days, a night a day, over the seven
  beats — curiosity, teasing, a secret, a challenge, a fantasy, a surprise, and a last night.
  `couples/{cid}/arcs/{id}` is the plan and nothing else; a day's night is built by
  `buildExperience` when the day is opened, at the couple's own ceiling, so nothing exists for a
  day they may never reach (D-086). One arc at a time.
- **A sequence, not a menu**: `openArcDay` refuses a day the clock has not reached, and one whose
  day before was never played — checked against that night's own status, not against anything the
  phone says. Opening the same day twice gives the same night. `closeArc` ends it; an ended arc
  opens nothing, and another can begin.
- **The arc screen** (`:feature:send`): the whole plan from the start — which beat each day is —
  with today's day the only thing it offers to open. The first day is due the moment the arc
  starts, and the screen asks again every five seconds so a day that comes round while it is open
  says so (D-087). Arcs are client-read-only in the rules; every write is a callable.

### Verified
```
./gradlew check assembleDevDebug            BUILD SUCCESSFUL
:core:data      89
:core:model     ArcTest 3
:feature:send   29   (SendViewModelTest 6, LaterViewModelTest 8, ArcViewModelTest 4, SendSnapshotTest 8)
npm --prefix tools test                     42/42
npm --prefix functions run test:unit        111/111
npm --prefix functions run test:int         127/127  (surprises 6, arcs 5)
npm --prefix firebase/tests test            96/96    (row S, countdowns held to their shape, arcs read-only)
```
- **Exit criterion (first half) — the scheduled function delivers on time in the emulator, and
  rules test S passes**: `surprises.int.test.ts` shows nothing is delivered an hour early and
  everything due is delivered once; `rules.test.mjs` shows the partner can see the surprise and
  not read its payload, while its author can, and that a delivered one is readable by both.
- **Exit criterion (second half) — an arc runs day by day**: `arcs.int.test.ts` shows the plan, the
  refusals (too early, and the day before unplayed), the ceiling, and that a night appears only
  when a day is opened.
- **On the device**: Alex started a three-day arc from Apart. The plan appeared at once — DAY 1 ·
  OPEN Curiosity, DAY 2 · LOCKED Teasing, DAY 3 · LOCKED The last night — with only "Open today"
  offered. Opening day one built a real night (4 chapters, ~27 minutes, rising to WILD, their own
  ceiling) and the arc kept its id; days two and three still had none. Ending the arc left nothing
  running and the chooser back. The first render said "Nothing due yet" until the phone's clock
  passed the server's — the reason for D-087.
- **On the device (first half)**: the script left a surprise as Sam, due in a minute. Alex's Later
  screen said "Something is coming" and nothing else. Running the sweep delivered it; the screen became
  "🎁 IT'S HERE — For you", opening it showed the line inside, and the server marked it OPENED.
  An "Open when you miss me" left the same way appeared as a moment to pull, and pulling it
  delivered and opened exactly that one. A countdown was added from the same screen.
