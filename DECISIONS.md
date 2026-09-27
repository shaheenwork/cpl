# DECISIONS

Every non-obvious call made during the build, and why. Newest last.

---

## D-001 — Keep the existing package `com.shnapps.couple`
**Phase 0.** `BUILD_PROMPT.md` §0.6 proposed `app.afterhours.android`, but the working directory already
contained an Android Studio scaffold using `com.shnapps.couple` with a matching `applicationId`,
theme name (`Theme.Cpl`) and `rootProject.name = "cpl"`.

Renaming would churn every file for zero functional gain and would desync the human's Android Studio
project, `.idea` config and any Firebase project they may already have registered against that
application ID.

**Decision:** keep `com.shnapps.couple` as the package and application ID. "Afterhours" remains the
*product* name and lives only in `strings.xml` (`app_name`), as §0.6 requires — so the display name is
still a one-line rename.

---

## D-002 — Adopt the scaffold's toolchain instead of the versions named in the spec
**Phase 0.** The spec (§1.1) named `compileSdk 36` / `targetSdk 36`. The scaffold was generated with a
newer toolchain than the spec anticipated:

| | Spec | Scaffold | Adopted |
|---|---|---|---|
| AGP | (unspecified, 8.x implied) | 9.3.3 | **9.3.3** |
| Gradle | (unspecified) | 9.5.0 | **9.5.0** |
| Kotlin | 2.x | 2.2.10 | **2.2.10** |
| compileSdk / targetSdk | 36 | 37 | **37** |
| minSdk | 26 | 26 | **26** |
| JDK toolchain | (unspecified) | 25 | **25** |

**Decision:** go with the scaffold. The spec's intent was "latest stable," and downgrading to match a
number written before the scaffold existed would be cargo-culting. minSdk 26 already matches.

**Consequence to watch:** AGP 9 ships **built-in Kotlin support**, so modules do *not* apply
`org.jetbrains.kotlin.android`. This differs from most AGP 8-era documentation and from
NowInAndroid-style convention plugins found online. Convention plugins in `build-logic/` are written
against AGP 9 semantics.

---

## D-003 — Convention plugins in an included `build-logic` build
**Phase 0.** The module graph in §4.2 is ~30 modules. Duplicating `android { compileSdk = ... }`,
Compose setup, Hilt setup and test wiring across 30 build files is unmaintainable and drifts.

**Decision:** an included build at `build-logic/` publishing convention plugins
(`afterhours.android.library`, `afterhours.android.feature`, `afterhours.jvm.library`, etc.). Each
module's `build.gradle.kts` then declares only its plugins and its own dependencies.

---

## D-004 — JDK 25 via the Android Studio JBR
**Phase 0.** `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=25`. The Android Studio
bundled JBR at `C:\Program Files\Android\Android Studio\jbr` is OpenJDK 25.0.2, so no download is
needed.

**Decision:** builds run with `JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"`. Recorded in
`CLAUDE.md` and `HUMAN_SETUP.md`. `.jdks/jbr-21.0.11` also exists but is too old for the pinned
daemon toolchain — do not use it.

---

## D-005 — The Gradle daemon runs on JDK 21, not the bundled JDK 25
**Phase 0.** detekt 1.23.8 (the latest stable) embeds the Kotlin 2.0.21 compiler. Its bundled
IntelliJ `JavaVersion.parse` cannot parse the string `"25.0.2"`:

```
java.lang.IllegalArgumentException: 25.0.2
  at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.parse(JavaVersion.java:307)
  at org.jetbrains.kotlin.com.intellij.util.lang.JavaVersion.current(JavaVersion.java:176)
  at org.jetbrains.kotlin.cli.jvm.modules.JavaVersionUtilsKt.isAtLeastJava9(javaVersionUtils.kt:11)
  ...
  at io.gitlab.arturbosch.detekt.core.settings.EnvironmentFacade.environment_delegate$lambda$0
```

It reads the **running** JVM, so no combination of `jvmTarget`, `languageVersion` or
`jdkHome` on the task avoids it — all three were tried and confirmed irrelevant. There is
no newer stable detekt.

**Decision:** pin `gradle/gradle-daemon-jvm.properties` to `toolchainVersion=21`. JDK 21
is LTS and is supported by Gradle 9.5, AGP 9.3.3, Kotlin 2.2.10 and detekt alike. JDK 25
is currently ahead of the Android tooling ecosystem. Compiled bytecode still targets
Java 17, so nothing about the app output changes.

**Alternative rejected:** dropping detekt for Spotless+ktlint. That would have kept JDK 25
but lost the correctness rules, which are worth more than the JDK version.

---

## D-006 — `:core:*` modules created up front, `:feature:*` modules per phase
**Phase 0.** BUILD_PROMPT.md §22 Phase 0 calls for a "module skeleton" covering the full
graph in §4.2 — roughly 30 modules.

Creating 16 empty feature modules that stay empty until Phase 10+ costs configuration time
on every build for ten phases and produces no signal in return.

**Decision:** all 14 `:core:*` modules plus `:architecture` exist now, because they are
depended on immediately and give the Konsist rules something to enforce. Feature modules
are created by the phase that introduces them, using the `afterhours.android.feature`
convention plugin — a three-line build file plus one line in `settings.gradle.kts`.

The layering rules in `:architecture` are already written against `/feature/` paths, so
they start enforcing the moment the first feature module appears.

---

## D-007 — `android.disallowKotlinSourceSets=false`
**Phase 0.** KSP `2.2.10-2.0.2` registers its generated source directories through the
`kotlin.sourceSets` DSL. AGP 9's built-in Kotlin support rejects that by default and fails
configuration with `Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with
built-in Kotlin`.

AGP documents this property as the supported escape hatch. Hilt needs KSP, so there is no
avoiding it today.

**Decision:** set the flag in `gradle.properties` with a comment pointing at AGP's docs.
Revisit when KSP ships an AGP-9-aware release that uses `android.sourceSets`.

---

## D-008 — Content is distributed as a Storage bundle, not per-item Firestore reads
**Phase 0 (recorded now, implemented in Phase 8).** BUILD_PROMPT.md §9.2 and §75 pull in
opposite directions: content must be remotely updatable, but per-item Firestore reads for
browsing would dominate the cost model.

**Decision:** authored JSON in `content/packs/`, compiled to a versioned bundle in Cloud
Storage, pointed at by a Remote Config `contentVersion` key, downloaded once and cached in
Room. Firestore holds only admin-edited deltas and disable flags, so a single item can
still be pulled globally without an app release.

---

## D-009 — Dark only, with no light theme
**Phase 2.** BUILD_PROMPT.md §15.1 asks for deep near-black grounds and a "luxury
after-hours lounge" feel. A light scheme was considered and dropped.

`AfterhoursTheme` takes no `darkTheme` parameter and ignores the system setting. This is
a private room after dark; a light mode would be a different product, and half-supporting
one — a washed-out palette nobody designed — is worse than not offering it.

**Consequence:** every contrast pair is verified against the dark palette only, which is
also why `ContrastTest` can be exhaustive rather than sampling.

---

## D-010 — Variable fonts, bundled rather than downloadable
**Phase 2.** §15.1 calls for editorial typography, which rules out Roboto: it reads as
stock Android, exactly what the brief warns against.

Playfair Display (high-contrast display serif) and Inter (body) are bundled as **variable**
fonts — 2 files rather than 12 static instances, the full weight range, ~1.2 MB total.
Weights are selected with `FontVariation.Settings`, which needs API 26; minSdk is 26.

Downloadable Fonts was rejected: it needs Play Services at runtime, fails on a cold
network, and would make screenshot tests depend on a font provider.

Both are SIL OFL; licence texts ship in `assets/licenses/`. Subsetting to the glyphs
actually used is a Phase 22 size optimisation.

---

## D-011 — Roborazzi over Paparazzi for screenshot tests
**Phase 2.** Paparazzi's only AGP-9-era release is `2.0.0-alpha05`. Roborazzi 1.74.0 is
stable and runs through Robolectric, which tracks new AGP versions far more closely.

The deciding factor is that Roborazzi runs on the **JVM**: with the Android emulator
blocked on a hypervisor install this session cannot perform (HUMAN_SETUP.md §1.3), it is
the difference between Phase 2 being provable now and being blocked indefinitely.

`verifyRoborazziDebug` is wired into `check`, because Roborazzi captures nothing unless a
record/verify flag is set — without that wiring a visual regression would pass a green
build. The gate was confirmed by breaking a colour token and watching it fail.

---

## D-012 — Account enumeration: hidden on sign-in and reset, residual on sign-up
**Phase 3.** For this product, learning that an email has an account here is itself
sensitive — *"is my partner using a secret intimacy app?"* — so the auth flow must not
confirm it.

- **Sign-in:** "no such account" and "wrong password" both become the same
  `AppError.Unauthenticated` and the same copy. Tested.
- **Password reset:** always reports "if there's an account, a link is on its way",
  whatever Firebase returns. Tested, including that both paths produce identical state.

**Residual risk, stated plainly:** sign-up cannot fully hide it. Firebase's client SDK
rejects a duplicate email synchronously, and the only branch that reaches the collision
message is "that email exists". The copy is softened (*"We couldn't create that account.
If you've been here before, try signing in"*) but it still leaks on inspection.

The real fix is **email-link (passwordless) sign-in**, which answers identically for
known and unknown addresses — and removes passwords from the threat model entirely. It
needs a Hosting domain configured for App Links, which is a human step, and it departs
from the spec's "email/password initially" (§7). Recommended before public launch; not
built now. HUMAN_SETUP.md §2.6 also asks for Firebase's email-enumeration protection.

---

## D-013 — Fakes over mocked suspend functions, and a hard test-task timeout
**Phase 3.** One `AppLockManagerTest` case took **3,592 seconds** and then passed. The
cause was a `coEvery { ... }` stub inside `runTest`: mockk records suspend stubs through
an internal `runBlocking`, which blocks the thread `runTest` needs, so `runTest`'s own
timeout can never fire. Removing the (unnecessary) stub took the suite to 2.9 s.

**Decisions:**
1. Anything a ViewModel depends on is an **interface with a real fake** in `:core:testing`
   (`AuthRepository` → `FakeAuthRepository`, `AppPreferencesStore` →
   `FakeAppPreferencesStore`). Fakes keep what is written, so behaviour that depends on
   saved state is genuinely exercised — a relaxed mock silently drops writes, which made
   "biometrics require a PIN" untestable.
2. Every test task has a **10-minute ceiling** in the convention plugins. A hang now fails
   the build instead of silently consuming it.

---

## D-014 — The window theme is dark, and screens share the app's root surface
**Phase 3.** Screenshot tests of the new screens came out cream-on-white. Two causes, one
of them a real product bug:

- The tests drew screens bare; `MainActivity` wraps them in a `Surface`. Fixed by
  extracting `AfterhoursSurface`, used by **both**, so tests render exactly what ships.
- The XML window theme was the template's `Theme.Material.Light`. **The app would have
  flashed white on every cold start** before Compose drew — and on Android 12+ the system
  splash would have been white too. Jarring in a dark room, and conspicuous on a shared
  phone. Now `Theme.Material.NoActionBar` with `windowBackground` and
  `windowSplashScreenBackground` set to Ink.

`windowLightNavigationBar` is API 27 against minSdk 26 (caught by lint), so it lives only
in the `values-v31` overlay; its default of `false` is already right below that.

---

## D-015 — `:architecture:test` declares every source it scans
**Phase 3.** Proving the feature→feature rule by planting a violation showed the rule
**never ran**: `:architecture:test` was UP-TO-DATE, because Konsist reads every Kotlin
file in the repository at runtime and Gradle could not see that. Forced with `--rerun`,
the same rule failed correctly.

So since Phase 0, every architecture rule ran **only on builds that happened to change
the `:architecture` module itself**. Any violation made anywhere else passed `check`.

**Decision:** `:architecture:test` declares `**/src/**/*.kt` as a task input. Verified in
both directions on a normal build — a planted violation re-runs and fails, and the clean
tree comes back from cache.

**General lesson, now in TESTING.md:** a test that inspects files Gradle does not know
about must declare them, or it silently stops running. Prove a gate by watching it fail
on an ordinary build, not only with `--rerun`.

---

## D-016 — Pairing is a handshake: entering a code creates a request, not a couple
**Phase 4.** §8 asks for a six-digit code and requires "prevent unauthorized joining".
Those pull against each other: a million values with only a few live at any moment means
rate limiting slows guessing but cannot stop someone with many free accounts from
eventually landing on a stranger's live code. If entering a code paired immediately, that
stranger would be joined to an unknown person — in an app built around private intimacy.

**Decision:** three steps.
1. Creator makes a code (`createPairingCode`).
2. Joiner enters it (`requestPairing`) — this only creates a **request**, and both phones
   now show the same three verification symbols.
3. Creator checks the symbols with their partner and approves (`respondToPairing`).

A guessed code produces a request whose symbols nobody on the other end can vouch for.
Declining cancels the code outright, so the guess is worthless afterwards.

Supporting choices:
- "Not found", "expired", "cancelled" and "used" return the **same** error, so a guesser
  learns nothing about which codes exist. Tested on server and client.
- Guessing is rate-limited per account (10 attempts / 10 min), counted *before* the lookup.
- Symbols are drawn only from Emoji 5.0 or earlier: minSdk 26 cannot render newer ones, and
  a symbol that shows as a box on one phone defeats the comparison.
- Who unpaired is deliberately not stored.

---

## D-017 — Invite links use a custom scheme until an App Links domain exists
**Phase 4.** `afterhours://pair?code=123456` works today with no domain, and is what the QR
encodes. Two honest limitations:
- Many messengers (WhatsApp among them) do not linkify custom schemes, so the shared link
  may arrive as plain text. The six digits in the same message always work.
- Phone cameras handle custom-scheme QR codes inconsistently.

Both disappear with verified `https://` App Links, which need a domain serving
`assetlinks.json` (HUMAN_SETUP.md §2.7). A link never navigates on its own: the user may be
signed out or not past the age gate, so the code waits in `PendingInvite` until the pairing
screen is reached normally.

---

## D-018 — The glow is a shape-following halo, not a clipped radial gradient
**Phase 4.** Pairing screenshots showed the Phase 2 glow was visible **only in the four
corners** of a glowing card: a radial gradient clipped to the element's rectangular bounds,
with an opaque rounded card painted over the middle. It read as a faint rectangle, not a
glow — and it had been in every "glowing" golden since Phase 2 without being noticed.

**Decision:** concentric rounded rectangles in one translucent colour, drawn past the
element's edge. They accumulate near the edge and thin out beyond it — an approximate blur
that works on every API level (`Modifier.blur` needs 31). Callers pass their shape's corner
radius so the halo hugs it. All goldens re-recorded and reviewed.

---

## D-019 — The profile listener is shared app-wide
**Phase 4.** `AuthRepository.currentProfile` was a cold flow, so every collector opened its
own Firestore listener — pairing state and couple membership alone would have been two
listeners on one document. Now `shareIn(appScope, WhileSubscribed(5s), replay = 1)`.
`@ApplicationScope` moved to `:core:common` (a plain `javax.inject` qualifier, still pure
JVM) so repositories can use it. §75 cost control, applied before it compounds.

---

## D-020 — The taxonomy: an item's theme is its boundary, and the file ships in the APK
**Phase 5.** §9.5 asks for `category → theme → item`, each item carrying "the boundary theme
it maps to". A separate `boundaryTheme` field could disagree with where the item is filed.

**Decision:** the theme an item is filed under *is* its boundary theme. Sensitive items get
a theme of their own (blindfolds, warm and cool, negotiated power play, power-dynamic
scenarios, strangers), so a boundary on them is precise; gentle items share broad themes.

- Ids match `^[a-z0-9_]{1,64}$`, the same pattern the rules enforce on preference ids.
- An item is only asked about at a content level at or above its `intensityFloor`. The
  level is the user's own, chosen on the discovery intro.
- `content/taxonomy.json` is the source of truth. A per-variant task in `:core:data` copies
  exactly that file into the assets; the APK's copy is byte-identical to it.
- **Two standards.** The app's parser is tolerant — unknown fields ignored, items it cannot
  represent dropped — because §9.5 makes the taxonomy remotely updatable and a newer file
  must never break an older app. The shipped file is read strictly by `TaxonomyFileTest`:
  unknown or misspelt fields fail, anything the parser would drop fails, all seven
  categories in order, unique ids, copy lengths, and a §3.3 prohibited-term tripwire. The
  file is a declared input of the test task (D-015).
- Remote updates ride on Phase 8's content pipeline. `TaxonomyRepository` is a `Flow`, so a
  newer version can replace the bundled one without touching callers.
- Answers store `taxonomyVersion`, so if an item is ever materially reworded its old answers
  can be recognised rather than silently reinterpreted.

---

## D-021 — "Secretly curious" is an answer, and never reveals more than an open one
**Phase 5.** The Phase 2 card put a secret toggle beside all five answers, which allowed
"secretly never" and left the meaning open.

**Decision:** `PreferenceAnswer(value, secret)`, with `secret` only alongside `CURIOUS` —
enforced by the model, the parser and the rules (removing the rule clause fails exactly
its test). The intro makes the promise explicit: *"Secretly curious stays hidden unless they
secretly pick it too."*

That binds Phase 7: a secret curiosity matches **only another secret curiosity**, and is
left out of ordinary matching. Choosing "secretly" must never reveal more than answering
openly would — the conservative reading of §14.7's "if only one picks, it stays private
forever". The both-secret match is the reveal §14.7 asks to give the best animation.

---

## D-022 — Answers are written once per deliberate answer, not debounced
**Phase 5.** §18 says "debounce preference writes". In a one-card-at-a-time flow every answer
is a deliberate tap that moves the deck on, so there is no burst to coalesce — and a
time-debounced write waits in memory, where leaving the screen or a process death loses it.
Silently losing a private answer is worse than an extra write.

**Decision:** write when the user answers; skip the write when the answer is unchanged (it
would also move `updatedAt`); let Firestore's offline cache queue writes while offline. A
full deck is about fifty single-document writes per person, once. Changing one's mind costs
one more.

---

## D-023 — Swipes skip and go back; they never answer
**Phase 5.** The "swipe flow" of the Phase 5 exit criterion: swipe left to skip for now, right
for the previous card. Neither records an answer. An accidental swipe is easy, and an
accidental "yes" on this data could become a match; answering stays a deliberate tap on a
button, which is also the non-gesture path §20 requires.

A session deals a snapshot of the unanswered cards, gentlest first, so answering never
reshuffles what is left; skipped cards come back next session. The gestures are tested with
injected touch under Robolectric (`DiscoveryDeckTest`), not yet with a real finger.

---

## D-024 — Gallery goldens render at phone size
**Phase 5.** The design-system goldens were rendered on Robolectric's default 320×470dp
screen: `PHONE_WIDTH = 392` never took effect, and any specimen taller than 470dp was cut
off — the private-answers golden had never shown the `BoundarySlider` at all.
`robolectric.properties` now sets `w392dp-h1600dp-mdpi`; all ten goldens were re-recorded
and reviewed. Doing so exposed the waiting indicator's halo, drawn inside its scaled
`graphicsLayer` and cut to a grey square; it now sits outside the layer and breathes by alpha.

---

## D-025 — A refused Firestore listener ends quietly instead of crashing the app
**Phase 5.** The shared listeners (profile, preferences) run in the application scope via
`shareIn`. When Firestore refuses a listener — typically during sign-out, when the rules see
the old listener without credentials — the error was thrown into that scope, which has no
handler: an app crash. The per-user inner flow now catches it and ends; the auth change that
follows starts the next listener. Firestore failures are also classified by
`FirebaseFirestoreException.Code` now, rather than by matching message text.

---

## D-026 — Dev builds allow cleartext to the emulator hosts, and nothing else
**First device run.** The Firebase Emulator Suite speaks plain HTTP, which Android refuses by
default: every dev build had failed on a device with *"Cleartext HTTP traffic to 10.0.2.2 not
permitted"*. No JVM test could see it. `src/dev/AndroidManifest.xml` points at a network
security config allowing cleartext to `10.0.2.2` (the host from an emulator) and
`127.0.0.1`/`localhost` (a physical phone through `adb reverse`) only. Staging and prod never
merge that manifest, so they keep HTTPS-only.

---

## D-027 — The lock state is unknown until read; the lock screen is never a dead end
**First device run.** The activity collected the lock state with `initialValue = Locked`, so the
first frame always navigated to the lock screen. With no lock set — the default — the real
state (unlocked) arrived a moment later, but the lock screen had nothing to offer and never
left: **every launch ended on a dead "Locked" screen.** Two fixes, each sufficient alone:
- The activity starts from `null` ("not known yet") and renders nothing until the state is
  read, so a cold start still can never flash the app at someone who should see the lock.
- The lock screen leaves itself whenever the manager reports unlocked, however it got there.

---

## D-028 — Edge to edge: the root insets content; system-bar icons are always light
**First device run.** `enableEdgeToEdge()` drew every screen under the status bar (the clock sat
on the eyebrow text) and under the keyboard (the sign-up button was unreachable while
typing). The nav host now takes `safeDrawingPadding()` — system bars, cutouts and the IME —
so screens need no insets of their own; a screen that wants to paint under the bars later
opts out explicitly. The default system-bar style follows the *system* theme, which put dark
icons on this dark-only app (D-009); it is pinned to `SystemBarStyle.dark`.

---

## D-029 — Firebase clients are configured once per process
**First device run.** Instrumented tests build a fresh Hilt `SingletonComponent` per test, but
Firebase clients are process-wide, and settings or `useEmulator()` applied after first use
throw. `FirebaseModule` now configures each client once per process, whichever component asks.

---

## D-030 — What a boundary means: the stricter partner wins, nothing lapses on its own
**Phase 6.**
- **Unset means unrestricted.** A theme with no boundary document is neither boosted nor
  limited; content level and preferences still apply.
- **Either partner's NEVER or NOT TONIGHT removes the theme for both.** ASK FIRST flags it with
  whoever must be asked; CURIOUS is a ranking signal only, and never survives an exclusion.
- **NOT TONIGHT never expires by itself.** An automatic lapse would bring a paused theme back
  without anyone choosing it — a silent escalation, which §3.1 forbids. It stays until the
  user changes it; the screen always shows it as paused.
- **Fail closed.** The server treats a boundary level it cannot read as NEVER, and a malformed
  content level as the lowest. The app's own parser skips an unknown level, which can only
  under-report a boundary on screen, never weaken one on the server.

---

## D-031 — Answering "Never" removes that item, even though it is an answer and not a boundary
**Phase 6.** §3.2's hard exclusion is written in terms of boundaries, which are per theme. But
someone who answers "Never" to "Blindfolded" in discovery and then meets a blindfold chapter
would rightly feel betrayed. So the couple's filters also carry `excludedItems`: every
preference item either partner answered NEVER. Phase 9 removes content tied to those items
exactly as it removes excluded themes. "Not for me" stays a ranking signal, not an exclusion.

---

## D-032 — `engineFilters` is a server-only cache, rebuilt in a transaction and deleted on unpair
**Phase 6.** `couples/{cid}/engineFilters/current` holds `maxIntensity`, `excludedThemes`,
`askFirstThemes` (theme → who to ask), `curiousThemes`, `excludedItems`, `version` and
`computedAt`. Three triggers rebuild it from scratch — on a member's profile (content level,
pairing, unpairing), on any boundary write, and on a preference write that adds or removes a
NEVER — always from the current documents inside one transaction, so duplicate or reordered
deliveries converge. Triggers retry on failure: a boundary that silently failed to apply is
the one failure this app cannot afford. The same recompute keeps `contentLevelEffective` in
step. When a couple unpairs, the filters are deleted rather than left behind.

Because triggers are asynchronous, the stored document can trail a change by moments.
**Phase 9 must recompute at generation time** and never build a night from the stored copy
alone.

---

## D-033 — The shared ceiling can reveal a partner's lower content level; accepted
**Phase 6.** `contentLevelEffective` is the lower of the two levels and both partners can read
it — the intensity dial has to show the couple's ceiling. So a partner who chose 5 and sees 3
learns the other chose 3. Content level is not one of the private answers in §5, the
alternative (hiding the ceiling) would make intensity choices inexplicable, and the copy
frames it honestly: "Together, you only ever go as far as the more careful of you."

---

## D-034 — Screen scaffolding lives in `:core:ui`
**Phase 6.** Features cannot depend on each other, so the scrolling column, heading, back row
and error banner that discovery and boundaries share are `ScreenColumn`, `ScreenHeading`,
`BackRow` and `ErrorBanner` in `:core:ui`. Pairing still has its own heading; moving it would
re-lay out five reviewed goldens for no user-visible gain, so it moves when pairing is next
touched.

---

## D-035 — Every visible change to a match waits in a server-only queue for a random time
**Phase 7.** §5.3: a match that appeared the instant B answered would tell A exactly what B
just answered. So nothing a partner can see changes when an answer does:
- A new match, a match whose level changed, and a match withdrawn are all queued in
  `couples/{cid}/revealQueue`, which no client can read. Only released matches exist in
  `mutualPreferences`, so a pending match cannot leak by existing.
- Each queued change gets its own release time, uniform in the window (default 30–180
  minutes, from Remote Config `reveal_min_delay_minutes` / `reveal_max_delay_minutes`). No
  configuration can take the minimum below a hard floor of 15 minutes.
- A queued match that changes keeps its original time, so re-answering cannot nudge it.
- A release re-checks the answers first and drops anything they no longer support; a batch
  is only sent when it holds at least one visible change.
- **"Release now on the couple's next app open"** is read conservatively: opening the app
  releases only changes that have already waited the minimum delay. It can make a reveal
  arrive sooner than its random time, never soon enough to date the answer behind it.
- An ended couple's queue is cleared; nothing still waiting is ever revealed.

---

## D-036 — A match either partner withdraws is taken back, on its own random delay
**Phase 7.** If one partner changes a matched answer to a negative, or removes it, the match
is no longer true, and the app should stop saying "you both want this" — consent can be
withdrawn. Before its reveal it simply vanishes. After its reveal, a retraction is queued
and applied at its own random time, so the moment it disappears does not date the change
either.

---

## D-037 — Integration tests run in their own emulator project
**Phase 7.** The Functions emulator serves `afterhours-dev-emulator`, so its triggers fire on
anything written there — including by integration tests with a pinned clock, which then
raced the triggers' real clock. Integration tests now use `afterhours-int-test`, where no
trigger runs; end-to-end tests stay on the dev project precisely because they want the
triggers. (This is also the likely cause of the one-off Phase 6 flake.)

---

## D-038 — "Build a night around this" says it is coming, rather than pretending
**Phase 7.** §14.7 ends every reveal with that button, but Build Our Night is Phase 10. Until
then it is shown disabled, with one line saying it arrives in a later update — not wired to
a placeholder that would look like a broken promise.

---

## D-039 — Content is authored as JSON, gated by a Node validator, and shipped as a committed bundle
**Phase 8.** §9.1–9.4. Packs live in `content/packs/*.json`, one item per line in a fixed key
order so a pull request shows one line per edited item. The closed vocabulary (moods,
interaction types, chapter kinds, tags, media, statuses, boundary keywords) is
`content/vocabulary.json`; the §3.3 tripwire is `content/policy/prohibited-terms.json`, read
by both the validator and the taxonomy tests, so there is one list.
- **The tooling is TypeScript on Node** (`tools/`), like the Cloud Functions: the validator,
  the bundle builder, publishing, deltas and a TSV importer share one loader. The app build
  never runs Node; only `check` does, through `:validateContent` and `:testContentTools`.
- **The bundle is committed** (`content/dist/bundle.json`) and copied into the app's assets
  by `:core:data`. So the Android build needs no Node, what ships is byte-for-byte what the
  validator passed, and `check` fails when the bundle is stale — it is a build artifact
  that must never drift from its sources.
- **Beyond the §9.4 list**, the validator enforces what the boundary engine relies on: an
  item leaning on a preference must be excludable by that preference's boundary theme; an
  item whose text mentions a sensitive theme (blindfolds, strangers, instructions, control,
  voice notes, fantasies…) must list a boundary that covers it; a required preference must be
  askable at the item's intensity; and the catalogue must cover every chapter kind, finale
  intensity and warm-up in both modes, so the engine can always assemble a night.
- Near-duplicates are word-trigram Jaccard ≥ 0.4 across the whole catalogue. The seed content
  was also checked by hand for repeated *concepts* across packs, which trigrams cannot see.

---

## D-040 — The device keeps content in its own Room database; the taxonomy travels in the bundle
**Phase 8.** §9.2, §9.5. `content.db` holds the installed bundle's items (each stored as the
JSON it arrived as, so a new content field needs no migration), its header, and the admin
deltas. It is a rebuildable cache — from the shipped asset or by downloading again — so it
may be dropped on a schema change; data that cannot be rebuilt belongs in another database.
- **First launch works offline**: the shipped bundle is installed on first use, and again
  after an app update that ships a newer one. A newer downloaded bundle is never replaced
  by an older shipped one.
- **The taxonomy is inside the bundle**, so a content release can update it without an app
  release. `TaxonomyRepository` now reads the installed bundle's taxonomy; the separate
  `taxonomy_version` Remote Config key is gone.
- **Parsing is tolerant, safety is not**: unknown fields and list entries are skipped, but
  an item this version cannot play safely — unknown interaction type, intensity, status or
  media, no usable mode or chapter, or missing its boundary exclusions — is dropped whole.
  An unreadable delta hides its item. A bundle in an unknown format, or not the version it
  was fetched as, is refused and the device keeps what it has.
- **Deltas** (`content/{id}`) carry a status and optionally an edited item. A delta applies
  to its item's bundled version and earlier, so a disable survives later bundles until the
  item is edited past it. They are fetched with an inclusive `updatedAt` cursor and never
  deleted (`enable` writes `published`).
- `ContentSyncWorker` runs every 12 hours, and once whenever a signed-in session starts — at
  launch when already signed in, and at each sign-in — always only with a network. The
  session's request replaces any earlier attempt still waiting out its retry backoff. It
  attempts the bundle and the deltas independently — a global disable (§9.6) must land even
  when a download fails — and does not retry while signed out or on an invalid bundle.
- The pointer and the deltas are read from the server only. Offline, Firestore would answer
  from its cache and the sync would look successful, recording a sync that never reached
  the server (found on the device).

---

## D-041 — Against the emulators, the content pointer is a Firestore document
**Phase 8.** Remote Config has no emulator, so in the `dev` flavor the app reads the content
version from `contentMeta/pointer` instead (signed-in read, no client write). Staging and
prod apps read Remote Config `content_version`, as §9.2 says. Publishing uploads the bundle
first and moves the pointer second, only ever forwards; a published version is immutable
(identical bytes are a no-op, different bytes are refused).

*Amended in Phase 9:* the server's engine needs the content version too, and reads the
Firestore document in every environment (D-054). So `publish-content` now writes
`contentMeta/pointer` everywhere, in a forward-only transaction, and on a real project
raises Remote Config after it.

---

## D-042 — There is no content browser in the product; debug builds get an inspector
**Phase 8.** The exit criterion says content "browses fully offline". A browsing screen in
the product would put items in front of a user without the engine's boundary filter —
exactly the bypass §3.2 forbids. So browsing is a debug-only inspector (`app/src/debug`),
reached from Home in debug builds, listing the cache with pack/mode/intensity filters and a
"sync now" button. Release builds compile a stub that sends anyone who lands there back.

---

## D-043 — The "Plus" content waits for the phases that define its formats
**Phase 8.** §9.3 also asks for ≥300 game prompts, ≥40 roleplay scenarios, ≥30 secret
missions, ≥25 Open When templates and ≥15 multi-day arcs. Their shapes are defined by
Phases 11, 14 and 16 (games, Apart primitives, multi-day arcs). Writing them now would mean
inventing formats those phases would then have to live with or rewrite. Each is authored,
with its own validator rules, in the phase that introduces it; Phase 8 delivers the 626
chapter items the engine (Phase 9) consumes.

---

## D-044 — Seed content is suggestion, never anatomy
**Phase 8.** §3.6 and §9.3's "never crude for its own sake". Intensity rises through
anticipation, control, confession and daring — not through explicit description. The
prohibited-terms policy includes explicit anatomical terms as a tripwire, and bold items say
"tasteful and entirely your choice", "inside your limits" and name the safe word where
power is involved. Every item keeps to the couple: no third parties, no audiences, no
substances, no one who cannot consent.

---

## D-045 — Reveal timing is not a client Remote Config key
**Phase 8.** The client declared `reveal_jitter_min/max_minutes` with defaults, but reveal
timing is decided only by the server (D-035), from its own template keys
`reveal_min_delay_minutes` / `reveal_max_delay_minutes`. A client copy could only drift or
mislead, so it is removed.

---

## D-046 — The engine is specified once and implemented twice, held together by shared vectors
**Phase 9.** §10.1 splits the engine: the server filters, scores and samples; the device
assembles, varies and remixes. Two implementations of one algorithm drift unless something
holds them together. `engine/SPEC.md` is the algorithm, down to the last tie-break, and
`engine/engine-fixtures.json` holds the shared vectors: a 96-item synthetic catalogue, the
random sequence, 23 plans, 37 assemblies, 13 hand-made validation cases, 35 assembled nights
re-validated, 55 chokepoint cases and 5 full generations. The TypeScript suite asserts the
committed file is exactly what it computes; `:core:engine`'s `SharedVectorsTest` asserts
Kotlin reproduces every plan, assembly and verdict. The server generates the file
(`npm --prefix functions run engine:fixtures`); a deliberate change to output regenerates it
and raises `ENGINE_VERSION` (§10.6).

---

## D-047 — Determinism: mulberry32, and only the arithmetic both runtimes agree on
**Phase 9.** "Same inputs + seed → same night" (§10.6) has to hold across the JVM and V8.
mulberry32 is 32-bit integer arithmetic only (`Math.imul` and Kotlin `Int` both wrap), so
its sequence is identical bit for bit. Scores and fits use only `+ − × /`, comparisons and
`floor` — no `pow`, `log` or `sqrt`, whose last bit is not guaranteed to agree. Every sort
is stable with ties broken by id, compared by UTF-16 code unit as both languages do, and
every random draw happens in the order the spec lists. Retry `a` uses
`seed + a·0x9E3779B9 mod 2³²`, so one stored seed reproduces the whole generation.

---

## D-048 — The server decides what a couple may see; the device only rearranges what it was given
**Phase 9.** The combined boundaries (`engineFilters`, D-032), both partners' affinity and
the "curious" themes never leave the server, so the hard filter and the full score can only
run there (§3.2, §5.4). The device gets a night and a bounded pool of alternates and
assembles or remixes only within them. `PermittedPool` has an internal constructor; its one
public way in, `fromServer`, builds it from a server night and drops, as defence in depth,
anything of another mode, above the ceiling or unpublished. The device's score is the
server's formula with every private term held neutral (`PublicScore`).

Both sides are gated. Konsist fails the build if the pool's constructor, `Planner` or
`Assembler` becomes public, or if anything outside an allowlist reads the content catalogue
(`ContentChokepointTest`). The TypeScript architecture test keeps the catalogue and the
pipeline to `experience/generate.ts`, and holds `pipeline.ts` to the order filter → score →
sample → assemble → filter again (§10.3). Each gate was seen to fail on a deliberate
violation before it was trusted.

---

## D-049 — The alternates are sampled, never the permitted set
**Phase 9.** §5.5: a client must never be able to infer the couple's boundaries. Returning
everything that passed the filter would do exactly that — its complement is what someone
excluded. The pool is the best candidate for each slot plus two weighted random picks, about
three items per chapter, resampled with every seed; if it ever covered the whole permitted
set, one is dropped. Sampling ranks by score plus `2 × fit(duration)`, so a pool of perfect
but long items cannot make a short night. Residual risk: many generations could together
approximate the permitted set. The rate limit (30 an hour per user) bounds that, and it goes
into SECURITY.md's residual risks (Phase 21).

---

## D-050 — Assembly is a bounded depth-first search, not a greedy pass
**Phase 9.** A greedy fill dead-ended against the slot rules: the escalation limit looks at
both neighbours, the finale must be the peak, and no three chapters in a row may share a
type. Fill is a depth-first search in ranked order — a finale not at E last, then lengths
that would leave the ±20% window unreachable, then value, then id — capped at 2,000 steps,
keeping the first complete night as a fallback. The cap bounds what a pathological pool can
cost a callable. The variety and duration passes then polish that night.

---

## D-051 — A night whose finale reaches the requested level beats the first valid night
**Phase 9.** Early runs gave WILD requests valid nights that peaked at 2 — rule-abiding, but
not what was asked. The pipeline keeps the first valid night as a fallback and spends its
remaining retries (five attempts, each with a derived seed) looking for one whose finale is
at E, returning that at once. On the real catalogue the finale reaches E in at least 95% of
nights (asserted in the suite); in a 2,880-request sweep, 86% of nights came from the first
attempt.

---

## D-052 — The server picks the seed, and a payload with fields it does not know is refused
**Phase 9.** A client that chose seeds could hold the randomness still and vary one
parameter at a time, reading the filter from the differences. The seed comes from
`crypto.randomInt` on the server and is returned with the night, which is all
reproducibility needs. `parseRequest` refuses any field besides mode, durationMin, moods,
intensity, leadership, novelty and media instead of ignoring it: a payload carrying a seed,
a uid or a coupleId is a client trying to decide something only the server decides, and
failing loudly makes that bug visible in development.

---

## D-053 — Ask-first flags are private to each partner; the play history is server-only
**Phase 9.** §3.2: an ASK_FIRST theme keeps its items but asks first, and only the partner
who set it may know it was set. The flags are written per partner to
`couples/{cid}/experiences/{id}/askFirst/{uid}`, readable by that uid alone (no list, no
write), and the callable returns only the caller's. The experience document holds nothing
private. The cooldown history, `couples/{cid}/engineState/history`, is closed to clients
entirely: none needs it, and one that could write it could lift the cooldowns. Phase 9 reads
it; the player writes it as chapters are played (Phase 10).

*Amended in Phase 10:* a night played on one phone leaves a partner's ask-first themes out
altogether, and the private document holds the host's own items rather than chapter indexes
(D-060).

---

## D-054 — The server's engine reads the content the phones read
**Phase 9.** The server must never pick an item a phone cannot show, and a disable must reach
the engine as fast as it reaches the app. `loadCatalogue` takes the bundle deployed with the
functions (copied from `content/dist` at build time) as the floor, loads a newer published
bundle from Storage when `contentMeta/pointer` names one (once per instance), and lays the
admin deltas over it by the app's own rule, re-read at most once a minute. So
`publish-content` writes the pointer document in every environment (D-041, amended).

---

## D-055 — Ranking weights come from the server's Remote Config template, clamped
**Phase 9.** §10.3 keeps w1–w7 in Remote Config so ranking can be tuned without a release.
They are used on the server, so they are read from the server template (`getServerTemplate`)
under the keys the app's template already declares, and cached for ten minutes. A missing,
negative, non-numeric or absurd (above 20) value falls back to its default: a typo in the
console must never turn ranking upside down. Under the emulators the defaults apply.

---

## D-056 — `PairingError` is now `ServiceError`
**Phase 9.** The error that maps to callable error codes was named after the first callable
that used it. The engine's callable needs the same mapping, so it was renamed; no behaviour
changed.

---

## D-057 — Remixes re-plan inside the pool, and "more intense" never raises the ceiling
**Phase 9.** "Make it more…" (§14.6) and "Not this one" (§3.1) run on the device, instantly
and offline, so only within the `PermittedPool`. A modifier adjusts the request — a mood, the
novelty, one standard length shorter, or where the climb starts — then re-plans and
re-assembles over the pool. The result must validate and must differ from the night it
replaces, or the answer is null and the app asks the server for a fresh night. The ceiling
is always the night's: "more intense" starts one level below E instead of two, climbing
sooner and never higher, because raising E would need the server's filter. "Not this one"
swaps one chapter for the best released item that keeps every rule, at the same level or
lower — except a finale, which may rise to the night's ceiling.

---

## D-058 — A few very short, very intense nights find nothing, and the server says so
**Phase 9.** Three chapters in 15 minutes (±20%), climbing to 5 no more than one level at a
time, ending on a finale at 5, with the roleplay mood pulling the middle towards roleplay.
In a 2,880-request sweep over the real catalogue the only two failures were such Together
requests; for that exact combination about 7% of seeds find no night. Rather than relax a
rule, the callable answers `failed-precondition: not-enough-content`. The plan for Phase
10's builder: retry once, since a fresh server seed almost always succeeds, then suggest a
longer night. The lasting fix is content — more short, intense roleplay and finale items.

---

## D-059 — A night plays on the phone of the partner who built it; sessions start now
**Phase 10.** The exit criterion is a night "played end-to-end on one device", and realtime
two-phone sessions are Phase 12. So a night plays on its host's phone, in front of both
partners, and the session document (§6.1) starts now with a subset of §12.1's states:
WAITING_FOR_PARTNER, READY, CHAPTER_ACTIVE, PAUSED, COMPLETED, ABANDONED. Phase 12 extends the
same table with rounds, submissions and reveals rather than replacing it.

Every transition is a callable (`createSession`, `markReady`, `advanceSession`,
`endSession`) checked against a pure transition table inside a transaction; no client
writes the session. Every event is idempotent: a retry of something that already happened
changes nothing, so a lost reply is safe to resend, and two taps can never double-advance a
night. Only the host moves the night on; either partner can pause, resume or stop it from
their own phone. One live session per couple: a second is refused (`session-in-progress`)
unless the first has been untouched for six hours, when it expires. The session records why
a night ended, never who ended it — `endedBy` from §6.1 is deliberately left out, so nothing
can ever surface who stopped (§3.1).

---

## D-060 — On one phone, a partner's ask-first themes are left out; the host's own are asked, among decoys
**Phase 10.** §3.2: an ASK_FIRST item needs an in-session prompt to the partner who set it —
and only they may know it was set (D-053). On a phone both partners are looking at, asking
the other partner would tell the host that limit exists, and where. So a night built for one
phone leaves out every theme someone other than its host asked to be asked about first (the
server moves them into the night's exclusions before the chokepoint runs); leaving something
out reveals nothing, because the alternates are a sample (D-049).

The host's own ask-first items stay in, flagged — returned to the host alone and stored in
`experiences/{id}/askFirst/{hostUid}` as item ids, so a restarted player still asks. Before
each one the player shows a neutral check-in ("Before the next one — still good?"). The same
check-in also comes before the step up into BOLD and before about one other chapter in four,
chosen by the night's seed, so a check-in never tells anyone watching that a limit exists.
When both partners play on their own phones (Phase 12), each can be asked privately and a
partner's ask-first themes can come back.

---

## D-061 — BOLD and WILD need each partner's "I'm in", each from their own phone
**Phase 10.** §3.1 requires both partners' explicit "I'm in" before the first chapter of a
level 4 or 5 night, stored on the session. Two taps on one phone would record a consent the
host could give alone. So each acknowledgement is `markReady` from the partner's own signed-in
phone, recorded under their uid with the server's time; the first chapter cannot start until
every member has acknowledged (the transition table refuses it). The partner's home screen
shows the request ("Your partner has a night ready. Are you in?"), and "Not tonight" from
either phone ends the night as DECLINED, warmly. A session needs consent when any chapter is
BOLD or above — the level the night actually reaches, not the one asked for.

---

## D-062 — A limit set during a night holds for the rest of it
**Phase 10.** §3.2: NEVER cannot be overridden, and that includes a NEVER set halfway through
a night, on either phone. Every `advanceSession` runs the chokepoint again over the chapters
still to come and the alternates, against the couple's current filters, and returns what is
no longer permitted. The phone swaps each blocked chapter for something released that still
is — before it is ever shown — and passes over, unseen, a chapter nothing can replace. The
host's own "Not this one → Never" applies to the rest of the night at once, on the phone,
without waiting for the server to recompute. Cooldowns are not re-applied mid-night: the night
itself is what set them.

Residual risk, for SECURITY.md (Phase 21): a technical user inspecting traffic could see which
released items became blocked mid-night, and so that the partner just excluded one of their
themes. Nothing on screen shows it, and chapters are sealed until played.

---

## D-063 — Feedback is private to whoever gave it, and only for chapters played
**Phase 10.** §6.1 keeps feedback under the couple and §8 turns it into affinity. A reaction
to a chapter is close to a private answer, so the rules let only its author read it, and hold
its creation to exactly one reaction per partner per chapter (the document id says which), for
a chapter of this couple's night that was actually played, naming the content that chapter was,
with the server's time — and never edited after. A partner may read only ids ending in their
own uid, whether or not that feedback exists yet (the app checks before writing, since it cannot
write twice), so neither can learn whether the other reacted. The `onFeedbackCreated` trigger moves the
author's own affinity (`users/{uid}/affinity`) as a running mean over about the last twenty
reactions, marking the feedback processed in the same transaction so a trigger delivered twice
counts once. Reading the catalogue there — for one played item's category, type and moods — is
the one other place allowed to load it; it selects nothing.

---

## D-064 — "Build a night around this" is a focus: a score term and a retry preference
**Phase 10.** §14.7 ends every reveal with a night built around it. The request carries an
optional `focus` — a taxonomy item the server checks is one of the couple's revealed matches —
and an item is about the focus when it is boosted by it or requires it. The score gains
`W_FOCUS × F` (4.0, more than any single weight), and the retries prefer a night holding a focus
item when one is permitted; the response says whether it does. Nights without a focus are
bit-identical to before — the regenerated vectors changed only by the new field and the new
case — so `ENGINE_VERSION` stays 1. The device's score carries the same term, so a swap keeps
to the focus.

---

## D-065 — The check-in at the close is not stored
**Phase 10.** "How are we doing?" (§3.1) is aftercare. Its answers only decide what to say
next — and "A bit much" offers the limits screen, one tap away. Nothing is recorded: an answer
about how a night felt is not something either partner, or the product, needs to keep.

---

## D-066 — A swapped chapter is reported when it is played, and must be one the server released
**Phase 10.** "Not this one" swaps on the phone, instantly and offline (D-057). The server
learns the swap when the chapter is finished: `advanceSession` carries `playedItemId`, which
must be one of the night's released items and not another chapter's, and the stored night and
the cooldown history record what was actually played. An item swapped away was never played,
so it is not cooled down.

---

## D-067 — Route arguments are read by property name, not with toRoute()
**Phase 10.** `SavedStateHandle.toRoute()` decodes through Android's Bundle, which the plain
JVM unit tests stub out: in a test it quietly returned defaults, and a player test passed with
the wrong night id. ViewModels read their arguments with
`savedStateHandle.get<String>(Route.Night::experienceId.name)` — the key type-safe navigation
itself uses — so a renamed property still breaks the build rather than a night.

---

## D-068 — A pause takes the night off the screen, never its controls
**Phase 10.** A pause is often someone at the door. While a night is paused the chapter is not
drawn at all — out of sight, out of reach, and out of TalkBack — and PAUSE and STOP stay where
they always are (§15.2: never animate a required control out of reach). STOP never waits for
anything else in flight: it is sent even while a chapter is still being finished.

---

## D-069 — A game is passed between the partners on one phone; answers never leave it
**Phase 11.** Phase 12 builds two-phone sessions with submissions; until then a game is played
on one phone, handed back and forth. Every private step — a choice, a prediction, a typed
answer, three statements — sits behind a hand-off ("Over to Sam. Only Sam should look."), and
private answers give way to a shared screen only behind a gathering ("Together now."). What
either partner answers lives in the running game on that phone and nowhere else: not in
Firestore, not in analytics, not in the session. The server learns only which prompt each round
played, for cooldowns.

Because both partners hold that phone, every ask-first theme is left out of a game — the host's
own included (`forSharedPhone`): asking anyone on a shared phone would show the other that a
limit exists. This is stricter than a night (D-060), where the host's own ask-first items are
asked, among decoys, on the host's own phone.

---

## D-070 — A game is an experience played through the same session as a night
**Phase 11.** `startGame` chooses a game's rounds on the server — hard filter, then the same
ranking as a night, then a warm-up ramp from two below the couple's ceiling to it, then the
chokepoint again (engine/SPEC.md §10) — and stores them as an experience of `type: GAME` whose
rounds are its `chapters`. Everything a night's session does then applies unchanged: "I'm in"
from each phone at BOLD and WILD, PAUSE and STOP from either, each round moved on by the server
(`advanceSession`), a limit set mid-game blocking what it covers in the rounds to come. A truth
or dare round holds both prompts; the played one is reported and leads the chapter's
`contentIds`. The session records `experienceType`, so either phone opens the right player, and
a completed game counts in `stats.gamesPlayed`.

Selection lives only on the server: the device never chooses a prompt, and holds a game only as
a `ReleasedGame` built by `fromServer`, whose constructor is internal (Konsist). Cooldowns keep
games fresh but give way — never a boundary — when a game could not otherwise be filled; the
ranking still puts what was played longest ago first.

---

## D-071 — In a game, "Not this one" keeps a theme out of that game only
**Phase 11.** "Not this one" swaps the prompt for a released alternate of its kind at once and
offers to keep what it was about out. On a night that writes the host's own boundary (D-062);
a game's phone is in both partners' hands, and whoever tapped may not be the account holder.
So a game never writes anyone's limits: the theme is taken out of the rest of that game, and
the offer says that either partner can keep it out for good in their own limits.

---

## D-072 — Games are data; one interpreter plays them all, and the build enforces it
**Phase 11.** `content/games.json` defines each game as steps over eight primitives — QUESTION,
CHOICE, PREDICTION, VOTE, REVEAL, TIMER, CHALLENGE, REACTION — with who takes each (ACTIVE,
OTHER, EACH, TOGETHER), whether it is private, what it shows and what it compares. The tools
check a definition like a compiler would (every reference points back, nobody predicts their
own answer, a scored game reveals), and the app's parser drops a game it cannot play in full.
`GameRun` in `:core:engine` is the only interpreter; `:feature:games` has one renderer per
primitive and none per game. `GameLogicTest` fails the build if code names a game, if anything
but the interpreter and the parser reads a game's steps, if a renderer is added or missing, or
if a verdict or score is made outside the interpreter.

Prompts are content under the same rules as items: the vocabulary, the prohibited-terms
tripwire, boundary-keyword coverage, near-duplicates within a game, and coverage per level and
kind — including a whole game at SOFT with one to spare, so a released set is never the whole
permitted set (§5.5). The bundle carries `games` and `prompts` beside `items`; older bundles
without them still install. The Room cache is version 2 (a rebuildable cache, dropped and
refilled); deltas apply to prompts by the items' rule, and an item's delta never becomes a
prompt, nor a prompt's an item.

Deferred: VoicePrompt and PhotoPrompt (Phase 15), SecretMission, PartnerControl and Scenario
(Phases 13–14), Ranking, RandomSelection and Memory.

---

## D-073 — FLAG_SECURE is held by every private screen, not set and cleared by each
**Phase 11.** `SecureScreen` set FLAG_SECURE when a screen entered composition and cleared it
on dispose. Navigating between two private screens, the arriving screen sets the flag and the
leaving one — disposed after the transition — clears it: the screen now on top was left
capturable. Found on the device in Phase 11: the games hub after "Another game" screenshotted
in full, and the night player did the same after Build Our Night handed over to it.

The flag is now a hold: every `SecureScreen` in composition counts, and it is cleared only when
the last one leaves. `SecureScreenTest` composes the handover — the next screen arrives, then
the last one leaves — and fails against the old code.

---

## D-074 — A phone each, only for the games both partners answer at once
**Phase 12.** With one phone the hand-off *is* the turn: the phone changes hands, and the
screen covers what came before. With a phone each, "Truth or dare" would ask the other partner
to react to a dare they cannot see yet — the server reveals a round only once both phones have
answered, so a phone that waits for the other before answering waits for ever.

So the choice is offered only for games whose every step is `EACH` or `TOGETHER`
(`Game.bothAtOnce`): Would You Rather, Me or You, Predict My Answer, Who Knows Who. A game of
turns is played the way it was always meant to be, on one phone. The reading of a round on a
phone follows the same rule: this phone takes its own steps first, and waits after them
(`RoundReading.ownSlots`), whichever seat it plays.

## D-075 — A round's answers are never written down; the reveal gives them back
**Phase 12.** §12.3 asks for a session restored after process death. What a partner answered
never leaves the phone (D-069), so caching answers locally to restore a half-played round would
be the one place the app wrote them down — and the one place a forensic look would find them.

Instead: the session says which round it is on and whether this phone has already answered
(`submittedBy`), and the server's reveal carries *both* answers. A phone restarted mid-round
rejoins at the round, waits rather than answering twice (`GameRun.awaitingReveal`), and takes
its own answer back from the reveal along with the partner's. Nothing about a round is stored
on the phone at all. Verified on the device: killed mid-round, the app came back to the same
reveal with both answers.

## D-076 — The heartbeat's beat belongs to the repository, not the ViewModel
**Phase 12.** A `while (true) { send(); delay(20s) }` in `viewModelScope` is the obvious way to
send a heartbeat, and it makes every `advanceUntilIdle()` in a ViewModel test hang for ever:
the loop always has another task scheduled, so the test scheduler never goes idle.

`GameRepository.keepPresent(sessionId)` is a flow that sends while it is collected, and owns
how often (`PRESENCE_EVERY_MS`). The ViewModel collects it while a session is live. The fake
emits one beat and completes, so tests stay honest about what was sent without a virtual clock
racing a real loop.

---

## D-077 — A Mystery Night is an ordinary night with its shape hidden
**Phase 13.** The obvious reading of "Mystery Night" is a second kind of experience with its own
pipeline. That would mean a second way to build a night, and every rule that matters — the
chokepoint, PAUSE and STOP, a limit set mid-night, "Not this one" — would have to be taught
twice.

So it is one flag, `mystery`, stored on the experience and carried to the phone. The engine, the
session, the player and the content are the same; only the screens change. The shape screen says
"You'll find out as you go." instead of "Tonight has 6 chapters", shows no chapter count, no
length, and no "Make it more…" — that would be a dial on a thing whose whole point is not having
one.

## D-078 — The server chooses what a surprise is; the phone sends nothing
**Phase 13.** Surprise Us could have been the phone picking random settings and calling
`generateExperience`. Two reasons it is not. The app is meant to be the third participant (§2.4):
the mood should come from what the couple's nights have actually been, which is the couple's own
data and lives on the server. And a phone that sends nothing cannot probe: `parseSurprise` refuses
every field a client could steer with — moods, intensity, duration, a seed — where
`generateExperience` accepts them.

`surpriseNight` picks one mood that is not the last night's, the couple's own ceiling, and however
long their recent nights have run, then hands that to the same `buildExperience` every night goes
through. It returns the settings it chose, so the phone can still remix what it was given — a
surprise is not a night the couple is stuck with.

## D-079 — Apart says it is not built yet, rather than showing controls that do nothing
**Phase 13.** Home's switch is meant to drive the whole surface (§14.2), and Apart is a
first-class mode built in Phase 14 (§2.6). Rather than hide the switch until then, or fill Apart
with buttons that lead nowhere — §25's dead ends — the Apart side shows one card naming what is
coming and where everything currently works. The switch is real, the state is kept, and nothing
on it pretends.

## D-080 — A remix is agreed with the server, because a session counts chapters
**Phase 13.** "Make it more…" is a local rearrangement of the pool the server released (D-048),
and the first wiring of it kept everything on the phone. On the device, the first remix showed
the flaw: the modifier changed how many chapters the night had, but `createSession` takes its
`chapterCount` from the stored experience, so the player read "Chapter 1 of 4" for a five-chapter
session — and the night would have ended with the server still waiting for two more.

The remix is still planned on the phone, instantly and from what it already holds. It is then
written back through `remixNight`, which accepts **ids only** — every one already released for
that night — re-applies the chokepoint (a limit set since the night was built still rules its
items out), bounds the count to what a night of that length may have, and refuses once the night
has started. A remix the server will not take is not kept on the phone either: the couple plays
the night their record describes.

---

## D-081 — A planned night is closed to the partner who planned it
**Phase 14.** "Your partner planned tonight 👀" only works if the planner cannot look. The
experience document is the night, and both partners could read every experience — so a planner
could have read exactly what they were supposed to be keeping secret.

A night with `plannedFor` set is now readable by that partner alone, and listing experiences is
closed for everyone (a list would hand the planner every document the rule denies). What both may
see lives in `couples/{cid}/plans/{experienceId}`: who planned it, how many chapters, and whether
it has been opened — never a content id. `planNight` returns the same three things and nothing
else, so the planner's own phone never holds what it planned.

It is built for the partner who will play it, not for the planner: their boundaries decide what it
may hold, their ask-first themes are the ones flagged, and the planner's are left out — the mirror
image of D-060, and for the same reason.

## D-082 — ASYNC is a session kind, not a degraded live one
**Phase 14.** §12.4 asks for async as a first-class session type. The states it needs are the ones
two phones already use, so ASYNC is a kind rather than a second machine: what changes is time and
patience.

A session for a planned night is ASYNC. The planner's "I'm in" is recorded when the session opens,
because they chose how far the night goes when they planned it, so only the partner's is still to
give. Presence is not expected, and the sweep gives it a week instead of ten minutes — an async
night that nobody is coming back to still frees the couple's place eventually, but not while they
are asleep.

---

## D-083 — Every object has a document beside it, and deleting the document is the burn
**Phase 15.** A burn-after-view voice note has to be *gone*, not hidden (§5.8). Two mechanisms
had to agree on that: the object in Storage, and whatever the rules consult to allow a read.

So media is reserved before it exists. The server writes the document first — PENDING, with the
one path the phone may upload to — the Storage rules allow a write to that path only for the
author of a PENDING document, and the server confirms the object landed before marking it SENT.
Reading asks the same document. A burn deletes the object and then the document; an expiry does
the same on a schedule; and either way the path stops serving, because the rule that allowed it
asked a question that now has no answer (§7.3 row P).

That also removes the orphan problem in both directions: no object without a document, and any
upload that never finished is swept an hour later.

## D-084 — The emulator's default bucket is not the app's
**Phase 15.** The first voice note sent on the device uploaded fine and then failed to send. The
functions runtime in the Emulator Suite defaults to `<project>.appspot.com`, while the app's
`google-services.json` uses `<project>.firebasestorage.app` — so `confirmMedia` looked for the
object in a bucket nothing had ever been uploaded to, and the phone was told, correctly but
uselessly, that nothing had been uploaded.

The server now names the bucket explicitly when it runs in the emulator (`FUNCTIONS_EMULATOR`),
and uses the project's default everywhere else. The device check found it; no unit test would
have, because both halves were right on their own.

---

## D-085 — What is left for later is guarded by a document, not by the screen
**Phase 16.** A scheduled surprise has to be visible and unreadable at the same time: the partner
should know something is coming — that is the whole feeling — and must not be able to read it
early. §5.7 forbids "hidden in the UI but present in the payload", so the two halves live in two
documents.

`couples/{cid}/surprises/{id}` is the fact: type, title, when, state. Both partners read it.
`…/payload/content` is the thing itself, under one rule: its author may read it whenever, and
anyone in the couple may read it once the server has written `deliveredAt`. A client can write
neither. Delivery is a scheduled function for a time, and a callable for "Open when…", which hands
over exactly one — the oldest kept for that moment.

So the phone asks for the payload and is refused until it is meant to have it. The screen says
"not yet" because the server said so, not because the screen decided to hide something.

---

## D-086 — An arc is a plan; its nights are built one day at a time
**Phase 16.** A fourteen-day arc could have been fourteen experiences written at once. It is not.
`couples/{cid}/arcs/{id}` holds only the plan — which beat each day is, when it opens, and the
night it was given once it has one — and `openArcDay` builds that day's night through the same
`buildExperience` as every other night, at the moment the day is opened.

Three reasons. Boundaries move: a night built today for day eleven would be filtered against
limits that may not be theirs by then, and the chokepoint is the one place that decides what a
couple may see (D-048). Most arcs will not be finished, and content generated for a day nobody
reaches is a night's worth of writing that leaks nothing only because nobody asked for it. And an
arc is a sequence, not a menu: a day opens on the clock *and* only once the day before it has been
played, which is a fact the server checks against the experience's own status rather than
something a phone reports.

One arc runs at a time, so "where are we" has one answer.

---

## D-087 — The first day of an arc does not wait for the phone's clock
**Phase 16.** Each day's `unlockAt` is a server timestamp, and day one's is the moment the arc
started. The phone decides what to offer by comparing those against its own clock — and on a
device whose clock sits a second or two behind the server's, the arc they just started says
"nothing due yet" until the phone catches up. The emulator showed exactly that.

So `Arc.dayDue` treats the first day as due whenever it has not been played: starting an arc *is*
opening its first day, and that needs no clock. Later days still wait for their time, and the
screen asks again every five seconds so a day that comes round while the screen is open says so
without being reopened. The server's own check is unchanged — it refuses a day it has not reached
— and day one always passes it, because its unlock time is the moment the server wrote it.

---

## D-088 — A push payload carries ids, never words
**Phase 17.** §16.2 forbids private content in a notification, and the cheapest way to keep a
promise like that is to have nothing to leak. Every message this server sends is **data-only** —
no `notification` block at all, so nothing is ever displayed on the app's behalf — and its data
map holds four fields: the kind, its channel, the time, and at most one document id.

`buildMessage` is the single place a payload is built, and it refuses a reference that is not a
Firestore document id: twenty characters of its alphabet, which is what every reference actually
is. A single word — a title, a name, a mood — is not one, and nothing with a space in it comes
close. So a caller cannot smuggle a surprise's title into a payload even by accident, and the
payload inspection (`notify.unit.test.ts`, Phase 17's exit criterion) proves it for every kind.

The words come from the phone: `NotificationText` is a fixed table in `:core:notifications` with
no placeholders in it, and `NotifierTest` reads the table back and fails if a notification ever
says anything that is not in it. Tapping one opens the app rather than a deep link with an id in
it — whatever it was about is on the home surface once the app is unlocked, fetched there as the
signed-in user, which is exactly what §16.2 asks for.

The cost is real and accepted: a data-only message is not shown by the system while the app is
force-stopped, so a notification can be missed. A notification that was never delivered is a
smaller harm than one that reads "Your partner selected 'blindfold'" on a shared lock screen.

---

## D-089 — Whether to send is the server's decision, not the phone's
**Phase 17.** A phone that simply declined to show what it had been sent would be the same
mistake as hiding content in the UI (§7.1): the message would still have travelled, still have
been delivered, and still be sitting in a push service's logs. So `notificationSettings` lives in
`users/{uid}` where the server reads it — on, off, and quiet hours — and `notifyUser` checks it
before it sends anything at all.

Quiet hours are evaluated on the *user's own* clock, from their `timezone`: 22:00 means ten at
night where they are, and a window that wraps midnight is read the way people mean it. A window
the server cannot parse is ignored rather than obeyed — a malformed setting must not become a
phone that silently receives nothing — and the rules hold the shape so it stays parseable.

One switch, not two: `privacySettings.hideNotificationPreviews` was defined in Phase 3 and never
read by anything, and two switches for one idea is how they end up disagreeing. What a
notification may say is `notificationSettings.showContent`, and the phone keeps its own copy in
DataStore because a message can arrive at three in the morning, before anything has loaded.

---

## D-090 — A memory holds their words, and never a content id
**Phase 18.** The library is the one screen in this app that lists freely: everything else a
couple sees comes through the engine's boundary filter, and experiences cannot be listed at all
(D-081). A memory can be listed because of what it does not contain.

What the server writes is the shape of an evening — its moods, how long it ran, how many chapters
they played — and what the couple wrote about it: a title, a note, the moment they want to keep, a
rating, and the ids of any voice note or photo they attached. Not one content id. That matters for
one case in particular: a night one partner planned for the other is readable only by the partner
it was planned for, and a memory both may read would otherwise be the way around that rule.

The phone sends only its own words. The date, the length, the moods and the fact that the night
was *played* are read on the server from the night itself, so a memory says what happened rather
than what a phone claimed — and a night that belongs to another couple, or that nobody played, is
refused rather than quietly kept.

One memory per night, keyed by the experience: saving again edits what they kept rather than
filling the library with the same evening four times, and neither the favourite mark nor who first
kept it survives being overwritten by the edit. Marking a favourite is the single field a client
may write; keeping and forgetting are callables. Either partner may forget one — the library is
theirs together — and what it pointed at is not deleted with it: a voice note has its own life,
and its own burn (D-083).

Pages of twenty, newest first. A library is one of the few things here that only grows, and a
screen that read it whole would cost a read per night every time it opened. The Room cache of the
first page (§6.3) is deliberately not built yet; it belongs with the cost work in Phase 22.

---

## D-091 — The store is a flavour; what a purchase is worth is the server's
**Phase 19.** Play Billing cannot run against the Emulator Suite, and `androidpublisher` needs a
real Play account, a real package and a real purchase — none of which exist locally. The brief
already says what to do about that (§4.3: `dev` uses fake billing), so the split is by flavour:
`app/src/dev` binds a `FakeBillingClient` that returns a token beginning `dev-`, and
`app/src/play` — shared by `staging` and `prod` — binds the real `PlayBillingClient`. The Play
Billing library is a `stagingImplementation`/`prodImplementation` dependency, so a `dev` build
never loads it at all.

What makes the fake safe is that it is *only* a token. Everything under it is the production
path: the `verifyPurchase` callable, the server's verifier, the record on the user document, the
couple's derived tier, and every gate. The dev verifier accepts a `dev-` token **and refuses to
run at all outside the Emulator Suite** — `FUNCTIONS_EMULATOR !== 'true'` throws
`billing-not-configured` — so the fake cannot become a way in if it is ever deployed by mistake.

A phone never decides entitlement, and never writes one: §7.3 row I denies a client write to
`users/{uid}.entitlement`, the couple's copy lives on the couple document which no client may
write at all, and neither carries a purchase token. Play's later changes — renewal, grace period,
on hold, cancellation, a refund — arrive as notifications and go through the same
`applyPlayNotification`; a six-hourly sweep writes back terms that have simply run out, so a
missed notification cannot leave anyone premium for ever.

The library is pinned at 7.1.1, which is what resolves offline here. Nothing about the real
purchase flow has been exercised: there is no Play account, and the staging and prod variants are
disabled on this machine for want of a `google-services.json`, so `PlayBillingClient` is not even
compiled by a local build. That is recorded in PROGRESS.md and HUMAN_SETUP.md §3.1 rather than
implied to work — the first thing to do once a Play account exists is compile it.

---

## D-092 — Free is worth using, and a gate never stands inside a night
**Phase 19.** §19's free/premium table is a shape, not a specification, and the two rules that go
with it are hard: never paywall inside an active experience, and gate at selection time with the
limit said before anyone invests effort.

So the gates are four, and each sits at the one chokepoint its feature already has:
`guardNewNight` inside `buildExperience` (three built nights a week on free, and Mystery Nights
premium — every night in the app goes through that function, so a surprise, a planned night and a
day of an arc are all counted the same way), `guardArc` in `startArc`, and `guardSurprise` in
`createSurprise` (three waiting at once). Nothing else is gated: every game, voice notes and
photos, the library, planning a night for a partner, feedback and boundaries are all free, and a
free couple can use this app for a long time without meeting a limit.

Nothing that moves a night on calls any of them — consent, advance, submit, reveal, remix, end —
and `billing.int.test.ts` proves it by spending a free couple's whole week, watching the fourth
build be refused, and then playing the night they already have from start to chapter one.

A refusal is `premium-required:GATE`, one shape the phone reads everywhere. It becomes
`PremiumNudge` where the tap happened, with the limit in plain words, "See what's included" and
"Not now" — and the paywall itself states what is included, what it costs and that everything
they already have stays theirs. No countdown, no scarcity, no disguised close: the screenshot test
asserts the absence of exactly those words.

Gating games by id was considered and refused: code must never name a game (D-072), so "limited
games" is a count if it is ever needed at all, never a list.

---

## D-093 — Deleting an account takes the couple with it
**Phase 20.** The obvious reading of "delete my account" is that one person's row disappears and
the other's stays. It does not work here, because almost nothing in this app belongs to one
person. A memory is what the two of them wrote about a night they played together. A reveal holds
both their answers side by side. A voice note is one partner's words sitting in the other's hands.
A surprise is something one of them left for the other to find.

One person cannot consent on the other's behalf to keeping any of that, and a "deleted" account
whose words survive inside a shared document is not deleted. So `deleteAccount` ends the couple
and removes everything under it — experiences, memories, media, surprises, countdowns, arcs,
plans, feedback, sessions with their submissions, rounds and presence — along with every object in
Storage beneath both prefixes, every document under the user, and the Auth user last, so a phone
cannot be left signed in to records it can no longer reach.

The partner keeps what was only ever theirs: their account, their own private answers, their own
limits, their own affinity. They are simply not in a couple any more.

The screen says all of this before the first tap, and the button that does it sits behind a second
one. "If you only want to leave the couple, unpair instead — that keeps your account" is on the
same screen, because that is what most people asking this question actually want.

An export is the other half of the same promise, and is bound by §5.1 in the opposite direction:
it holds the requester's own documents and the things the two of them share and can both already
see — never a field of the partner's private record, and never the inside of a surprise that has
not been delivered, not even to the person who wrote it.

---

## D-094 — A cascade that cannot finish still finishes the account
**Phase 20.** The device check found this one. Deleting an account while the phone was still on
screen threw `CANCELLED: call already cancelled` part-way through: the app had live snapshot
listeners on the couple's documents, and deleting what a listener is watching made the emulator's
listen stream fail the write that caused it. The couple's data went; the account itself did not.
Alex's user document, his objects in Storage and his Auth user were all still there, and the
screen simply said it had not worked.

That is the wrong failure mode for this operation. Somebody who asks for their account to be
deleted must not be left with a half-deleted one because a listener dropped at the wrong moment.

So every delete in the cascade is attempted up to three times, transient gRPC failures
(`CANCELLED`, `ABORTED`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`) are retried rather than raised, and a
delete that still will not go is **counted and stepped over** instead of abandoning the rest. The
account's own documents and its Auth user are deleted whatever happened to the couple's data, the
count comes back as `failures` for the log, and because every step is idempotent the next call
finishes whatever was left. `deleteAccount` returning is the promise that the account is gone.


---

## D-095 — R8 is on, and the keep list is short on purpose
**Phase 22.** §18 asks for "R8 enabled with a verified-shrunk release build", so the release build
type now enables optimization and carries `app/proguard-rules.pro`. The shrink is real: 31.9 MB
debug against 5.5 MB release, with `mapping.txt` produced for Crashlytics.

Almost nothing needed keeping, and that is a consequence of earlier decisions rather than luck.
Documents are mapped field by field in `:core:firebase` rather than reflected into data classes, so
R8 renaming a model cannot break a read. Hilt, Room and Compose all generate code R8 understands.
What is kept is named in the file with the reason:

- the `kotlinx.serialization` generated serializers and their companions, because the content
  bundle and the taxonomy are parsed reflectively by the library's own lookup;
- `CplMessagingService`, because the manifest names it and nothing in Kotlin references it;
- `SourceFile` and `LineNumberTable`, so a deobfuscated stack trace still has line numbers.

The release build is signed with the debug key here. That is deliberate and local: a release APK
has to be signable to be installed and run at all, which is what the exit criterion asks for, and
the real upload key is a human step (HUMAN_SETUP.md §3.3). The alternative — a release variant that
cannot be verified on a device until someone generates a keystore — would have left the exit
criterion untestable.

---

## D-096 — The accessibility audit is a test suite, not a walkthrough
**Phase 22.** §20 lists seven accessibility requirements. Three of them are arithmetic on the
tokens and already had `ContrastTest`. The rest were checked by eye in earlier phases, which is
exactly the kind of check that stops happening.

So they are tests now (`AccessibilityTest` in `:core:designsystem`): the specimens composed at
`fontScale = 2.0` with the 200% goldens recorded, every clickable node asserted at 48 dp or more in
both directions, and every clickable node asserted to carry text or a content description. The
goldens matter more than the assertions — a change that starts clipping at the largest type shows
up as an image diff, which is the failure mode §20 actually worries about.

`fontScale` is provided through `LocalDensity`, not through Robolectric qualifiers, because the
qualifier applies to the device configuration and the composition reads the density local; setting
the local is what a real user's font-size setting ends up doing anyway.

What this does not cover, and what remains a human check: TalkBack's actual reading order, and
switch navigation. A semantics assertion proves a label exists, not that the order it is read in
makes sense.

---

## D-097 — The leak test counts listeners at the seam
**Phase 22.** §18 says "every listener is lifecycle-scoped and verified released (write a leak
test)". LeakCanary answers a different question — whether an `Activity` is retained — and it cannot
be asserted in CI.

The leak that matters here is a Firestore snapshot listener that outlives the screen collecting it:
it keeps costing reads, and on a shared phone it keeps a live view of a document open. So
`ListenerReleaseTest` counts registrations at the seam where they are created — a data source whose
`callbackFlow` increments on subscribe and decrements in `awaitClose` — and asserts the count
returns to zero when the collector goes away, that two collectors are two listeners and not a
shared one, and that nothing registers at all before something collects or while signed out.

A repository that cached a flow in a scope of its own, or held a `stateIn` on anything but the
caller's scope, fails it. LeakCanary still runs in debug builds for the run-through §18 asks for;
the test is what keeps holding after the run-through is over.

---

## D-098 — A session that is gone is signed out on open, and no write waits for ever
**Phase 22.** The device check found this one, and it is the failure a person actually hits first.

After the Firebase emulators were restarted, the app still held Alex's session. Firebase kept
reporting a signed-in user while every Firestore call came back
`UNAUTHENTICATED ... [ INVALID_REFRESH_TOKEN ]`, and the age gate — whose attestation is a Firestore
write — sat on "One moment…" for ever, because an offline Firestore write is queued and retried
rather than failed. There was no way forward and no way back: no error, no sign-in screen, no tap.

That is not only a `dev` problem. A refresh token stops working in production too: the account is
deleted, it is disabled, the password is changed on another device, or the session is revoked. In
every one of those cases the app would behave exactly as it did here.

Two changes, because there are two faults:

- **The session is checked on open.** `AuthDataSource.checkSession()` refreshes the stored token and
  reports `VALID`, `SIGNED_OUT`, `DEAD` or `UNVERIFIABLE`; `validateSession()` signs out only on
  `DEAD`, and the splash routes to sign-in. A failure that means "not now" — no network — keeps the
  session, because signing someone out for being on a train is worse than asking again later. Dead
  is read from the reasons Firebase actually reports (`INVALID_REFRESH_TOKEN`, `TOKEN_EXPIRED`,
  `USER_DISABLED`, `USER_NOT_FOUND`, `CREDENTIAL_TOO_OLD_LOGIN_AGAIN`), and anything unrecognised is
  treated as "could not tell".
- **The attestation write is bounded.** `confirmAge` gives the write ten seconds and then reports
  `AppError.Network`, so the gate says "That didn't save. Try again." and shows the button again
  instead of a spinner with nothing behind it.

The gate still refuses to advance optimistically. The point is not to let someone through when the
record did not save; it is to stop pretending the save is still in progress when it is not.

---

## D-099 — Chance is seeded, and a dial has no direction
**Redesign R1.** The rebuild needs games where nobody has to ask for anything — a wheel, a roll,
a pool of six — and games that ask "how much" without making anyone say a number out loud. Two new
primitives, and each raised a question worth answering properly.

**`DRAW` does not roll a die.** The obvious implementation is `Random.nextInt(options.size)` in the
renderer, and it breaks three things this app already promises. A test could not assert where a
round lands, so nothing would hold the feature. Two phones playing the same round would land
differently, and §12.2 sends answers between phones, never draws. A phone restarted mid-round would
re-spin, which contradicts D-075's rule that nothing about a round lives on the phone.

So the interpreter decides, from the round itself: the seed is
`hash("gameId|round|step|promptId")`, the pick is `mulberry32`'s, and the screen animates towards a
result that was already taken. `engine/SPEC.md` §10 states it, because the spec governs every random
number in this engine (D-047). The spin is honest — it is just not the thing doing the choosing.

**A `DIAL` reveals a gap, never a direction.** `DISTANCE` publishes `|a − b|` and the lower of the
two numbers as a floor, and nothing else. Showing who wanted it more would teach the partner who
wanted it less to answer differently next time, which destroys the only thing the dial is for. A gap
of zero is the interesting result and reads as a match; every other gap is symmetrical.

`MEDIA` — a voice note or photo inside a round — is the third primitive the apart games need. It is
deliberately not here: the media pipeline is server-side and asynchronous (D-083), and wiring it
into a round belongs with the apart games rather than with two pure additions to the interpreter.

---

## D-100 — Playing without an account, and why that is not a hole in §5
**Redesign R2.** The app's architecture assumes two accounts: a night is filtered on the server from
both partners' private limits, and the device only ever plays what it was handed
(`PermittedPool.fromServer`, D-048). That is right, and none of it changes. But it cannot produce a
card for two people who installed the app four minutes ago, and making them sign up, pair and answer
twenty questions first is what made the app unusable.

So there is a second path, `InstantDeck`, and its safety rests on a different fact rather than on a
weaker rule: **there is no partner yet.** Everything it reads belongs to the person holding the
phone — the heat they chose, and the cards they have refused. No private document is consulted
because none exists, so nothing about anyone can leak through it. The §5 promises are about what one
partner can learn about the other; with one account and one phone there is no other.

What the deck may contain is bounded rather than filtered: published items only, at or below this
phone's heat, in `TOGETHER` mode, needing no media and no mutual match. The last two matter — an
item that needs a recording needs someone to send it to, and an item that needs a mutual match needs
two people who have answered something.

The moment a couple is paired and signed in, this stops being the source. The deck comes from the
server through the chokepoint as it always has, and what is here becomes a second, **narrower** gate
on top of it. `InstantDeck.narrow` can only ever remove, and `InstantDeckTest` asserts exactly that,
because it is the property that keeps this from becoming a way around the filter.

**The attestation still gates the app.** It is recorded locally when there is no account and
reconciled when one appears; on a signed-in device the splash still checks the **server** record, so
a local flag has never been enough to pass the gate (§3.1, unchanged).

**A refusal is kept where it can be honoured.** "Never show me these again" writes the card's
boundary themes to this phone immediately, so the next card already respects it. When an account
exists those themes belong in the person's real limits — that reconciliation is R5's, and until then
the local copy is the only record and the only thing consulted.

---

## D-101 — What a phone learned before the account, and why it only tightens
**Redesign R5.** Someone can play for a week before they make an account. In that week they will
have tapped "never show me these again" to things they meant, and the app kept those refusals on the
phone because there was nowhere else to put them (D-100). The moment there is an account, they
belong in the person's real limits — otherwise the app forgets what it was told at exactly the
moment a couple pairs, which is the worst possible moment to forget it.

`LocalPlayReconciler` runs after a successful sign-in or sign-up, and two rules keep it honest.

**It only ever tightens.** A refused theme becomes `NEVER`. A theme the person has already set
themselves is left exactly as it is — including when their own setting is *more permissive*. What
somebody chose deliberately on a limits screen outranks what a tap during play implied, and silently
hardening a deliberate `ASK_FIRST` into a `NEVER` would be the app overruling them. That also makes
it idempotent: a second run writes nothing.

**It carries no content.** A refusal travels as a boundary theme, never as the card that prompted
it. The refused card ids stay on the phone. A content id on a server-readable path is precisely what
§5 forbids, and the test asserts that nothing but the theme is written.

The heat comes across as the person's own content level, which is the same quantity in the engine's
units. It is a ceiling for them alone; the couple's is still the lower of the two, computed on the
server (§3.2).

**The account is offered where it first buys something**, not on the way in: at the end of a run
("keep tonight, and find each other"), and at Apart and "plan a whole evening", which genuinely
cannot work without a partner. The line under the button says what it buys, including that what they
have said no to comes with them.

The seam is `ContentLevelWriter`, one method, which `AuthRepository` implements. The reconciler has
no business being able to sign anyone in or out, and the call happens in the view model that signs
people in rather than inside the repository, so nothing has to depend on anything circularly.

---

## D-102 — A recording is an answer, not a thing that travels
**Redesign R10.** The catalogue promised games played apart, and the honest finding is that most of
them already exist. "Send a dare with a clock" is a surprise and a timer. "Voice only" is voice
notes. "Unlock me" is a surprise gated by questions. Wrapping those as games would add names, not
mechanics — the same reduction R9 made for six other ideas.

What was genuinely missing is a round answered by **recording something** rather than typing it, and
that is worth having whether or not the couple is apart: two people on a sofa handing a phone back
and forth is the app's main case.

So `MEDIA` records into a round, and the recording is an answer like any other: it stays on the
phone that made it, it is played back for the people in the room, and it goes with the app's cache.
It is emphatically **not** the media pipeline. Nothing is reserved, uploaded or confirmed; no path
crosses to another device. `Answer.Recorded` encodes to null in the two-phone exchange for the same
reason the rest of §12.2 exists — sending a file path would say where something is without letting
anyone hear it, which is the worst of both.

A phone that will not record still finishes the round. The microphone can be refused, a preview has
no recorder at all, and "say it out loud instead" is a first-class answer rather than a failure
path. The recorder reaches the renderer through a composition local that is allowed to be null,
because a renderer must not know which screen it is inside.

The recorder and the player moved to `:core:ui` to make that possible: two players draw these
primitives now, a feature may not import another feature, and duplicating either would put two
MediaRecorders in the app. The architecture rule that keeps the app silent followed them, and still
names every file allowed to make a noise.

---

## D-103 — One home, and signing in adds rather than replaces

The rebuild left the app with two of everything. A phone with no account opened on
`InstantHomeScreen`: a heading, one Start, a list of games, a heat. A signed-in couple opened on
`HomeScreen`: a TOGETHER/APART switch, five buttons of which two were loud, a Mystery Night card, a
curiosities card and four quiet links. Two homes meant two game lists — `GamesScreen`, with its own
card style and a setup form asking for an intensity and two names, beside the tiles on the instant
home — and two ways into a game, and two vocabularies for the same dial.

The effect was worse than the duplication. **Signing in swapped the app.** Someone who had learned
the simple one, made an account so they could send their partner a voice note, and came back found a
screen they had never seen, with none of the words they had learned on it. Nothing about the app was
learnable, because there was nothing stable to learn.

So there is one home, `:feature:home`, for all three audiences, and `HomeUiState.company` says which
one is looking at it: `ALONE`, `SIGNED_IN`, `PAIRED`. It never chooses a layout. The heading, the one
filled button, the heat and the list of games are identical in all three; what an account adds is
that rows already on the screen start working. Apart and the library are visible from the first
minute, greyed, with "Set up" where their action word goes — so the thing an account buys is named
at the moment someone reaches for it, rather than being invisible until they have one.

Three things follow from that, and they are the parts worth keeping:

- **Two goldens hold it.** `home-alone.png` and `home-paired.png` are captured from the same
  composable with one field changed. A change that pulls them back into two different layouts is an
  image diff, not a review comment.
- **A game is one tap for everyone, and the difference is not a person's problem.** `HomeViewModel.play`
  asks the server for a paired couple, because a couple's rounds are chosen behind *both* partners'
  boundaries and both phones follow the session (§3.2, D-070); and deals on the phone for anyone
  else, from shipped content inside this phone's heat, because there is no partner whose limits
  could leak (D-100). One list, one tap, two sources — never two screens.
- **Not paired is no longer a wall.** The splash sent a signed-in, unpaired person to `CoupleSetup`
  and left them there. It sends them home; pairing is one row inside it, reached by someone who
  wants the half of the app that needs a partner.

What this retires: `Route.InstantHome`, `Route.Games`, `InstantHomeScreen`, `GamesScreen`,
`GamesViewModel`, the TOGETHER/APART switch, the five-button wall, the games setup form with its
intensity dial and its two name fields, and the `StartDestination.InstantPlay` branch. The dealt
game player moved to `:feature:games` as `DealtGameScreen`, beside the session player and its
goldens, because the two are meant to be indistinguishable to a person and a diff between them is
how anyone would notice they had drifted.

What the games setup form asked for, and where it went: the intensity dial is the heat on home, one
dial rather than two scales for one thing; the two names default to "You" and "Them" and are still
remembered when a couple gives them, because a form standing between a couple and a game at eleven
at night costs more than the names are worth; and the phone-each choice belongs with the game rather
than in a hub, so it sits in the player's own intro, where the game it applies to is on screen.

---

## D-104 — One way to start, and the five shapes behind it

Home had five filled or outlined buttons — Build tonight, Surprise us, Play a game, We have 15
minutes, Send something — and a Mystery Night card underneath that was a sixth. Every one of them
started an evening. A person opening the app at eleven at night had to rank five options before
anything had happened, which is exactly the decision §2.4 says the app exists to take away.

There is one filled button now, "Start now", and the five are what it opens: deal us cards, surprise
us, a mystery, fifteen minutes, plan the evening. The choices are drawn in place rather than in a
modal sheet — the same pattern `ReportSheet` already uses — so a screenshot test captures them and
no Material3 experimental API is in the way.

Two details are deliberate. The choice that needs nothing at all is first, for everyone, because it
is the shortest path from opening the app to doing something. And for a phone with no account the
other four are still listed, greyed, saying what they need — never hidden, because a person cannot
want something they have not been shown.

`BuildNight`'s six-field form is not gone; it is demoted. It is one line inside Start, called "Plan
the evening", for the couples who want to plan one.

---

## D-105 — One word for one thing, and the form that asked six questions

Two vocabularies had grown up beside each other. A person set a **heat** — Playful, Hot, Wild — on
first launch and on home, and then met an **intensity** — Gentle, Flirty, Naughty, Bold, Wild — on
the build form, which is the same idea on a different scale with a different name. An evening was a
**night** on one screen and an **evening** on another. What was inside it was a **chapter**, which
is a word from a book, for a thing that is one instruction on one card.

So, in every string a person reads:

- **heat** — Playful, Hot, Wild. `Intensity`'s five levels are the engine's and stay in the engine,
  where `Heat.ceiling` maps onto them. `Heat.covering` maps back, so the picker can show a couple's
  server-side ceiling in the words they set it in.
- **evening** for the thing, **tonight** for the time. "Night" survives nowhere as a noun for a
  built experience.
- **card** for one of its parts. `NightChapter` keeps its name in the model and on the wire, because
  renaming a Firestore field is a migration and this is a vocabulary change, not a data change.

Class and route names were left alone on purpose: `Route.Night`, `NightRepository`,
`chapterCount`. They are not read by anyone using the app, and renaming them would have buried the
change that matters in a rename nobody could review.

**The form.** `BuildNightScreen` asked six questions — time, mood, intensity, who leads, novelty, and
implicitly "are you sure" — and it was the main way into an evening. Home now calls it "Plan the
evening. Three questions, then we build it around them", so it asks three: how long, how far, what
mood. Who leads and how novel it is keep `NightSettings`' own defaults, `RANDOM` and `MIXED`, which
is roughly what those two dials produced anyway; the engine still takes both and nothing about §10
changed.

One real fix came with it. The old `IntensityDial` took `maxSelectable = ceiling`, so a couple could
not drag past their shared level. `HeatPicker` has no such parameter, so `setHeat` caps against the
shared level itself — `Intensity.effective(heat.ceiling, ceiling)` — and a test holds it. The server
would have refused anything past it regardless (§3.2); what this prevents is a screen that says Wild
to a couple who agreed to stop at Hot.

---

## D-106 — Nothing is a wall, and the account says what it buys

Three screens could be reached and not left, and each was reached by someone doing the right thing.

**The account screen.** Tapping a row on home that needs an account landed on `AuthScreen`: an
eyebrow saying "MAKE IT YOURS", two fields, and no statement of what an account was for or any way
back to what they had been doing. The one thing they had actually asked for — sending their partner
a voice note, keeping the evening — was never mentioned.

`Route.Auth` now carries `offer`, an `AccountOffer` name and nothing else. It is deliberately not a
content id, not a screen name and not what they were doing: three cases, `APART`, `MEMORIES`,
`EVENING`, so nothing about the couple can ride along in a route argument. The screen says what that
one buys — including that what they have already said no to comes with them (D-101) — and ends with
"Not now, keep playing", which goes back to what they were doing. The words live with the screen;
the enum is only which of them to say.

**Pairing.** `ChooseStep` offered "Invite my partner" and "I have a code" and nothing else. It used
to be the screen a signed-in person was dumped on, so there was nowhere to go back to; it is
reached from a row on home now (D-103), so "Not now" is an answer, and the rest of the app works
without a partner.

**The empty library.** `EmptyState` has taken an action since it was written, and the library was not
passing one. It says what fills it and offers the way to do it; the favourites filter offers the
full list instead, which is what somebody who has kept nothing as a favourite actually wants.

The rule this leaves behind: **a screen that can be reached must be leavable, and a screen that asks
for something must say what it is for.** `EmptyState`'s optional action exists precisely so the
first half is one argument, not a new component.

---

## D-107 — The background is a scene, and a surface is lit rather than outlined

The app looked like a prototype, and three quarters of the reason was not layout.

**The ground was a colour.** `Palette.Ink` painted edge to edge, with nothing on it. A dark app
is a light app with its colours inverted — flat panels, hairline borders, one gap between
everything. A dim room has a source of light, things that catch it, texture, and shadow where the
light does not reach. `Modifier.scene()` composes the second one once, in `AfterhoursSurface`,
under every screen: a warm key wash off the top-left corner, a plum bounce off the bottom-right,
a vignette, and grain.

Two details of it are worth keeping:

- **The grain is deterministic.** A seeded LCG rather than `Random()`, so the tile is byte-identical
  on every run. `Random()` would have failed every Roborazzi comparison in the repo on noise, which
  is a very expensive way to learn that lesson.
- **Plum finally has a job.** It was in the palette from Phase 2 and used essentially nowhere, so
  the app read as one pink on black. It is the bounce light now, at 20% alpha, off-screen.

**Five grounds became three.** `Ink → InkRaised → InkElevated → InkHigh → InkHighest` spanned under
five percent of luminance in total: in practice the app had one surface colour, and a card, a row
and a chip were the same object. `Scene #0B0708`, `Raised #171115`, `Card #1F1820` are about seven
percent apart each, which is the smallest step that survives an OLED at low brightness with grain
over it. `ContrastTest` measures the steps rather than trusting them.

**A border became a rim.** `Modifier.rim` draws a 1 dp *vertical gradient* — `Cream @ 16%` at the
top edge, `@ 3%` at the bottom — instead of a uniform hairline. It is one modifier and it is most
of the difference between a panel and an object, because it is what a lit thing looks like.
`Modifier.raised` applies fill, rim and, from `floating` up, a contact shadow, so the three can
never drift apart.

### The halo, and why it banded

`Modifier.glow` drew **ten** concentric rounded rectangles of equal alpha. At button size it
passed. At card size the equal steps banded into a hard-edged pink rectangle, and three of them in
a row — the heat picker — smeared into one continuous band across the bottom of the screen. Both
are in the committed goldens.

The fix was not fewer layers. It was the **falloff**: each ring's alpha now falls with the square
of its distance, so the near rings carry nearly all the light and the far ones fade to nothing,
which is how light behaves. Twelve layers with a quadratic falloff is smooth where three with a
flat one is not.

The first attempt at that was wrong in an instructive way: alphas *accumulate* where layers
overlap, so summing twelve rings at the intended strength produced roughly three and a half times
it — a solid pink band, worse than what it replaced. Dividing by the series total fixed it, and
`FALLOFF_TOTAL` is computed rather than guessed.

`Modifier.blur` is deliberately not used even on API 31+. It blurs a node's own content rather
than painting light behind it, it is a no-op below 31, and a primitive that renders differently on
different API levels would make every golden in the repo unstable.

Two rules keep this from recurring: **at most one bloomed element per screen**, and **never one
inside a row of siblings**. The heat picker is a segmented track now, and nothing in it blooms.

---

## D-108 — A heat is a colour, and Wild is as dark as contrast allows

All three heats were the same burgundy, so the one thing this product is about — escalation — was
invisible. A Playful card and a Wild card were the same object with different words.

`heatColors(Heat)` gives each one an accent, a container, a rim and the two text tones it needs,
and `LocalHeatColors` carries it, so a component asks `AfterhoursTheme.heat` rather than taking a
colour as a parameter. A screen that knows the heat wraps its content in `ProvideHeat`, and
changing the heat re-tints the card's rim, the primary action, the eyebrow and the bloom at once.

Wild goes **deeper and redder** rather than brighter, because escalation here is intimacy, not
volume. Two things fell out of that, and both are the honest consequence rather than a compromise:

- **Oxblood cannot carry dark text.** At `#B02A42` a label in ink measures 2.73:1. Wild's filled
  state takes cream instead, which is what a genuinely deep accent needs.
- **Wild is as dark as the threshold allows.** The first choice, `#A3243B`, measured **2.74:1**
  against the scene — a filled button a thumb could not find. `#B02A42` is the deepest red that
  still clears 3:1. Contrast is the real constraint on how far Wild can go, and the test says so
  rather than a preference.

Burgundy needed a second tone for the same reason in reverse: `#C75C7A` as *text* on the card
measures 4.29:1 and fails. `BurgundyBright` is the same hue lifted until it passes, and is used for
accent text while the original stays the fill.

---

## D-109 — One gap for everything is why every screen looked unfinished

`ScreenColumn` spaced every child by `spacing.md`. A section heading therefore sat exactly as far
from its own content as from the section above it, so nothing on any screen was visibly grouped
with anything else. It is the single highest-value change in the visual rebuild and the hardest to
see in a screenshot of a screen — which is why there is a specimen for it.

`Spacing` gains semantic gaps: `tight 4`, `item 12`, `group 20`, `section 36`. The rule is that a
heading takes `section` above and `tight` below, never equal gaps on both sides.

Type gained the three styles it was missing: `CardBodyTextStyle` (19/30 at zero tracking, for the
one thing a couple actually reads, which had been using a 17/26 *list* style), `NumeralTextStyle`
with `tnum` so a counting clock stops changing width under the reader, and `CARD_MEASURE` so body
copy stops running 52 characters to the edge of the phone.

Optical sizing is **not** wired, despite being in the brief: the shipped `playfair_display_variable`
has a `wght` axis and no `opsz`. Claiming it in code would have been a lie in a comment.

---

## D-110 — Emoji were the icon set, and a rule now keeps them out

🔥 🎲 🌙 💌 👀 📖 ⏱ ✨ ❤️ 😏 🎭 💬 🙂 🙅 📎 📷 🎙 — twenty-seven call sites and three domain enums.
They render differently on every platform and OS version, cannot be coloured or sized to a grid,
cannot be animated, and no two share an optical weight. They were the clearest signal the app had
no designer.

`AfterhoursIcons` is twenty-six vectors built by one private factory, so the 24 dp box, the 1.5 dp
stroke and the round caps cannot drift between icons — which is how every hand-assembled icon set
goes wrong. `GlowButton.leadingEmoji` became `leadingIcon`.

`Mood`, `CloseCheckIn` and `ChapterReaction` each carried an emoji in `:core:model`, which put a
presentation decision inside the domain. The id and the label stay, because content is tagged by
them; `DomainIcons.kt` maps each to an icon, as an exhaustive `when`, so adding a case to any of
those enums is a compile error until somebody decides what it looks like.

`DesignLanguageTest` is the gate, and it caught two things review had not:

- `"★".repeat(rating)` — a star rating built from a dingbat. It is `AfterhoursIcons.Star` now.
- Two literal colours in `QrCode.kt`. They turned out to be legitimate: a camera reads a pairing
  code, so its plate and modules cannot follow a theme. They are named tokens now (`codePlate`,
  `codeModule`) and `ContrastTest` measures them, which is better than a comment saying why.

The rule was proved by planting an emoji and watching the build fail, per the phase's own exit
criterion. A second rule fails the build on a literal `Color(` outside `:core:designsystem` — the
convention has been in CLAUDE.md since Phase 2 and was held by review alone until now.
