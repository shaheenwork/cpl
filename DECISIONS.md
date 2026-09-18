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
- `ContentSyncWorker` runs on every process start and every 12 hours with a network. It
  attempts the bundle and the deltas independently — a global disable (§9.6) must land even
  when a download fails — and does not retry while signed out or on an invalid bundle.

---

## D-041 — Against the emulators, the content pointer is a Firestore document
**Phase 8.** Remote Config has no emulator, so in the `dev` flavor the app reads the content
version from `contentMeta/pointer` instead (signed-in read, no client write), and
`publish-content --emulator` writes it there. Staging and prod read Remote Config
`content_version`, as §9.2 says. Publishing uploads the bundle first and moves the pointer
second, only ever forwards; a published version is immutable (identical bytes are a no-op,
different bytes are refused).

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
