# CLAUDE.md

Working notes for this repository. Read `BUILD_PROMPT.md` for the product brief and
`PROGRESS.md` for where the build currently stands.

---

## Build commands

**The Gradle daemon must run on JDK 21.** Not the JDK 25 that Android Studio bundles —
see DECISIONS.md D-005. `gradle/gradle-daemon-jvm.properties` pins `toolchainVersion=21`,
but the wrapper still needs a `JAVA_HOME` to bootstrap:

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
export PATH="/c/Users/shahe/.jdks/jbr-21.0.11/bin:$PATH"   # the Firebase CLI needs `java` on PATH
```

Note the PATH entry uses a **Unix-style** path. In Git Bash a `C:/...` entry on PATH is
not searched, and `firebase emulators:start` then dies with *"Could not spawn `java
-version`"* even though `JAVA_HOME` is set.

| Command | What it does |
|---|---|
| `./gradlew assembleDevDebug` | Build the dev variant (emulator-backed Firebase) |
| `./gradlew check` | detekt + ktlint (via detekt-formatting) + all unit tests + architecture rules + the content gate |
| `./gradlew :core:engine:test` | The experience-engine suite — the highest bar in the repo |
| `./gradlew :architecture:test` | Konsist layering rules |
| `./gradlew :core:designsystem:recordRoborazziDebug` | Re-record screenshot goldens after an intentional visual change |
| `./gradlew projects` | Verify the module graph configures |
| `./gradlew connectedDevDebugAndroidTest` | Instrumented tests - needs an emulator **and** the Firebase emulators |
| `npm --prefix tools run validate` | The content gate on its own (also `./gradlew validateContent`) |
| `npm --prefix tools run bundle` | Rebuild `content/dist/bundle.json` after editing content — `check` fails until you do |
| `npm --prefix functions run engine:fixtures` | Regenerate `engine/engine-fixtures.json` after a deliberate engine change |
| `node functions/scripts/dev-couple.js seed` | A paired couple in the emulators for device checks; `ready`/`stop`/`state` play the partner (TESTING.md) |

### Firebase emulators

The `dev` flavor talks to the local Emulator Suite and needs no Firebase project:

```bash
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
```

Add `functions` to `--only` when testing callables; build first with
`npm --prefix functions run build`. Server tests: `npm --prefix functions run
test:unit|test:int|test:e2e` and `npm --prefix firebase/tests test` (see TESTING.md).

Ports (mirrored in `firebase.json` and `FirebaseEnvironment`): auth 9099, firestore 8080,
storage 9199, functions 5001, UI 4000. From inside an Android emulator the host is
`10.0.2.2`, which is what `FirebaseEnvironment.ANDROID_EMULATOR_LOOPBACK` resolves to.

Compiled bytecode targets **Java 17** regardless of the daemon JVM.

---

## Module map

```
:app                  MainActivity, nav host, Hilt app, product flavors
:core:model           Domain models + enums          (pure JVM)
:core:common          Outcome/AppError, dispatchers  (pure JVM)
:core:engine          THE EXPERIENCE ENGINE          (pure JVM — keep it that way)
:core:designsystem    Theme, tokens, motion, components
:core:ui              Shared composables, SecureScreen
:core:navigation      Type-safe routes + deep links
:core:data            Repositories + use cases
:core:firebase        Firebase data sources — the ONLY module allowed to import Firebase
:core:database        Room: the content cache (content.db)
:core:datastore       DataStore settings
:core:security        App lock, FLAG_SECURE, App Check glue
:core:analytics       Typed, allowlisted analytics facade
:core:notifications   FCM + privacy-aware builders
:core:testing         Fakes, fixtures, rules
:architecture         Konsist rules that fail the build on violations
:feature:home         The home surface and the TOGETHER / APART switch
:feature:*            Added by the phase that introduces them (DECISIONS.md D-006)
```

Layering: `UI → ViewModel → UseCase → Repository → DataSource`.

---

## Conventions

- Sources live in `src/main/kotlin`, not `src/main/java`.
- Module build files declare **only** their plugins and their own dependencies. Shared
  config lives in `build-logic/convention/src/main/kotlin/afterhours.*.gradle.kts`.
- New Android library: `id("afterhours.android.library")` (+ `.compose` if it renders UI).
- New feature module: `id("afterhours.android.feature")`, then register it in
  `settings.gradle.kts` and add its route to `:core:navigation`.
- New pure-JVM module: `id("afterhours.jvm.library")`.
- One immutable `UiState` data class per screen, exposed as `StateFlow`. No `LiveData`.
- Repositories return `Outcome<T>`; exceptions do not cross layer boundaries.

### Content

- Game prompts are authored in `content/prompts/*.json` under the same rules as items.
- Authored in `content/packs/*.json`, one item per line; the closed vocabulary is
  `content/vocabulary.json`, the §3.3 tripwire `content/policy/prohibited-terms.json`.
- After any content edit: `npm --prefix tools run bundle`, and commit the bundle with it.
- The app reads content only through `ContentRepository` (`:core:data`). No product screen
  lists content: what a couple sees goes through the engine's boundary filter (§3.2). The
  inspector in `app/src/debug` is the one exception, and it has no release twin.
- A new mood, interaction type, chapter kind, media kind or status changes
  `content/vocabulary.json` **and** the enum in `:core:model` together —
  `ContentBundleFileTest` fails if they differ.

### Experience engine

- One algorithm, `engine/SPEC.md`, implemented twice: the server's TypeScript
  (`functions/src/engine/`) filters, scores and samples; `:core:engine` (Kotlin) assembles
  and remixes. Change the spec, both implementations and the shared vectors together, and
  raise `ENGINE_VERSION` and `Engine.VERSION` when output changes for the same inputs (D-046).
- Determinism is a rule of the code, not a hope: only `+ - * /`, comparisons and `floor`;
  stable sorts with ties broken by id; random draws only where the spec lists them (D-047).
- The device never runs the boundary filter and never assembles from a list of its own
  choosing — only from `PermittedPool.fromServer` (D-048). Konsist enforces it.
- On the server only `experience/generate.ts` runs the pipeline, and the pipeline filters
  before anything else (§10.3); the catalogue is loaded only there and in `feedback/affinity.ts`.
  A game's rounds are chosen only by `engine/game.ts`, called only from `experience/game.ts`,
  filtering first too (SPEC §10). The TypeScript architecture test enforces both.

### Home, and the nights nobody configured

- Every night in the app is built by `buildExperience` in `functions/src/experience/generate.ts`.
  A surprise chooses its own shape in `experience/surprise.ts` and hands it to that; the
  TypeScript architecture test holds both rules.
- A phone sends nothing at all for a surprise (`parseSurprise` refuses anything it could steer
  with), and the server returns the settings it chose so the phone can still remix them.
- A Mystery Night is an ordinary night with `mystery: true`: only the screens hide the shape
  (D-077). Nothing about how it is built or played differs.
- "Make it more…" is `Remixer` run on the phone over the released pool — never past the night's
  ceiling (D-048) — and then written back with `remixNight`, because a session counts a night's
  chapters (D-080). A remix the server refuses is not played here either.
- Express ("We have 15 minutes") is one route argument, `Route.BuildNight(express = true)`: the
  same builder with fifteen minutes, the couple's own level and one question.
- A night one partner plans for the other is built **for them** (`plannedFor`), readable by them
  alone, and summarised for both in `couples/{cid}/plans/{experienceId}` — which never holds a
  content id (D-081). Experiences cannot be listed by anyone, for the same reason.
- Its session is `ASYNC` (D-082): the planner's consent is recorded when it opens, nobody is
  expected to be present, and the expiry sweep gives it a week.
- `node functions/scripts/dev-couple.js plans` shows what is planned, for device checks.

### Voice notes and photos

- Media is reserved, uploaded, then confirmed, in that order (D-083). The server names the path;
  the Storage rules allow it only to the author of a PENDING record; reading asks for the record.
- Deleting the record *is* the burn: the object goes first, the record second, and the rule stops
  serving the path. Expiry and unfinished uploads are swept hourly by `expireMedia`.
- Nothing mints a download URL. Bytes are fetched with the signed-in user's own credentials, into
  this app's cache.
- In the Emulator Suite the functions runtime's default bucket is `<project>.appspot.com` while
  the app uses `<project>.firebasestorage.app` — `bucketOf()` names it explicitly (D-084).
- `node functions/scripts/dev-couple.js media` lists what has been sent and whether the bytes are
  still there.

### Things left for later

- A surprise is two documents: `couples/{cid}/surprises/{id}` (the fact, both partners read it)
  and `…/payload/content` (the thing, its author until the server writes `deliveredAt`) — D-085.
- Delivery is the server's: `deliverSurprises` every five minutes for a time, `openWhen` for a
  moment. A client cannot write a surprise, deliver one, or mark one opened.
- Countdowns are the couple's own documents, held to their shape by the rules; no server logic.
- `node functions/scripts/dev-couple.js leave|openwhen|deliver|waiting` plays the partner and the
  clock for a device check.
- An arc is a plan, not content (D-086): `couples/{cid}/arcs/{id}` names each day's beat, and a
  day's night is built by `buildExperience` the moment the day is opened — never before, so
  nothing exists for a day they may never reach. One arc at a time; `openArcDay` refuses a day the
  clock has not reached and one whose day before was not played.
- The phone holds none of that. It shows the plan, asks for today's day, and says what the server
  said. The first day is due the moment the arc starts, without waiting for the phone's clock to
  agree with the server's (D-087).

### Subscription

- The client never decides entitlement. A phone hands `verifyPurchase` a token; the server asks
  the store what it is worth and writes `users/{uid}.entitlement` — which no client may write
  (§7.3 row I) — then publishes the couple's derived tier onto the couple document (D-091).
- Entitlement is couple-scoped: `coupleEntitlement` takes the better of the two members' records.
  A grace period still entitles; on hold, paused, revoked and expired do not.
- The store is a flavour: `app/src/dev` binds `FakeBillingClient` (tokens beginning `dev-`),
  `app/src/play` binds the real one for staging and prod. The dev verifier refuses to run outside
  the Emulator Suite.
- Four gates, each at the chokepoint its feature already has (D-092): `guardNewNight` inside
  `buildExperience`, `guardArc` in `startArc`, `guardSurprise` in `createSurprise`. **Nothing that
  moves a night on may ever call one.**
- A refusal is `premium-required:GATE`; `AppError.gate()` turns it into a `Gate`, and the screen
  shows `PremiumNudge` where the tap happened.
- `node functions/scripts/dev-couple.js tier` shows what the couple is entitled to and how much of
  the free tier they have used; `free` takes premium away again for checking a gate.

### Memories

- A memory is the shape of an evening plus their own words — never a content id (D-090), which is
  why the library may list freely when nothing else may.
- The phone sends its own words; `saveMemory` reads the date, length, moods and "was it played"
  from the night itself, and refuses one that is not theirs or was never played.
- One memory per night (the experience id *is* the memory id): saving again edits it. `favorite`
  is the one field a client writes; keeping and forgetting are callables.
- Pages of twenty, newest first (`MemoryRepository.page(beforeMillis)`), because the library only
  grows.

### Notifications

- One place sends: `functions/src/notify/notify.ts`. Every message is **data-only** and carries
  `kind`, `channel`, `at` and at most a document id — `buildMessage` refuses a reference that is
  not a Firestore id, so a title cannot ride along (D-088).
- The words are the phone's: `NotificationText` in `:core:notifications` is a fixed table with no
  placeholders. `NotifierTest` fails if a notification ever says something that is not in it.
- Whether to send at all, and whether it is the middle of that user's night, is decided on the
  server from `users/{uid}.notificationSettings` and their `timezone` (D-089). A phone filtering
  what it was already sent would be the §7.1 mistake again.
- `users/{uid}/fcmTokens/{tokenId}` is owner-only: a token in someone else's list would forward
  their notifications to the writer's phone.
- A new trigger is a new kind in `NOTIFICATION_KINDS`, `PushKind` and `NotificationText` together,
  plus one `notifyUser`/`notifyPartner` call *after* the transaction that caused it.

### Games

- A game is content, not code: steps over primitives in `content/games.json`, prompts in
  `content/prompts/<game>.json`. A new game, or a changed one, is a content edit plus
  `npm --prefix tools run bundle` — never a new screen or a branch. `GameLogicTest` fails the
  build if code names a game (D-072).
- `GameRun` (`:core:engine`) is the only interpreter. `:feature:games` renders primitives —
  one renderer each, in `PrimitiveRenderers.kt` — and only ever sees what the interpreter hands
  it. A new primitive changes the vocabulary, `GamePrimitive`, `RoundPrimitive` and gets exactly
  one renderer.
- The device plays only a `ReleasedGame` built by `fromServer` from what `startGame` released
  (D-070). What partners answer never leaves the phone (D-069); a game never writes anyone's
  limits (D-071).
- A night's session moves only through the callables and the transition table in
  `functions/src/session/transitions.ts` (D-059). The phone follows the session document; it
  never assumes where a night is.

### Sessions on two phones

- A phone writes only its own `submissions/{chapter}_{uid}`, its own `presence/{uid}` and a
  PAUSE/STOP request; everything else is a callable or the `onSubmissionCreated` trigger, which
  is the only thing that ever reads a submission. It writes one `rounds/{chapter}` reveal that
  both may read (§12.2).
- "A phone each" is offered only for games whose every step is `EACH` or `TOGETHER`
  (`Game.bothAtOnce`, D-074), and a phone always answers before it waits — the reveal exists
  only once both have answered.
- Nothing about a round is stored on the phone (D-075): a restarted phone rejoins at the
  session's round, and takes its own answer back from the reveal rather than from a cache.
- The heartbeat's beat is the repository's (`keepPresent`), never a `while (true)` in a
  ViewModel — that hangs `advanceUntilIdle()` for ever (D-076).
- `node functions/scripts/dev-couple.js answer|seen|present` plays the partner's own phone for a
  device check.

### Design system

- Reach tokens through `AfterhoursTheme.colors / .spacing / .motion / .elevations`, and
  Material slots through `MaterialTheme`. Never hardcode a colour, a dp or a duration.
- **Dark only.** `AfterhoursTheme` takes no `darkTheme` flag and ignores the system
  setting (DECISIONS.md D-009).
- Decorative movement goes through `decorativeTween(..., reduceMotion)` so it collapses
  under the system reduce-motion setting. Reveals keep their timing on purpose.
- Component state enums live together in `component/ComponentState.kt`; domain types
  (`Intensity`, `Mood`, `BoundaryLevel`, `PreferenceValue`) come from `:core:model` and
  are never redefined.
- New component → add it to `gallery/ComponentGallery.kt` and to `GallerySnapshotTest`,
  then `recordRoborazziDebug`.
- Any screen showing private content calls `SecureScreen()` from `:core:ui`.
- The taxonomy is authored in `content/taxonomy.json` and copied into the APK by
  `:core:data`'s bundle task. Edit the JSON, never a copy; `TaxonomyFileTest` holds it to
  the strict standard (DECISIONS.md D-020).
- Private answers (`PreferenceAnswer`) never reach analytics, logs or a partner-readable
  path. `secret` only ever accompanies `CURIOUS` (D-021).
- Feature screenshot tests use `@Config(sdk = [35])` and wrap content in
  `AfterhoursTheme { AfterhoursSurface { ... } }`; `verifyRoborazziDebug` runs in `check`.
- Screens build on `ScreenColumn`, `ScreenHeading`, `BackRow` and `ErrorBanner` from
  `:core:ui` rather than re-declaring them (D-034).
- Anything that decides what a couple may see is computed on the server from both partners'
  private documents and stored only where no client can read it (`engineFilters`, D-032).
- ViewModels depend on interfaces (`AuthRepository`, `AppPreferencesStore`); tests use the
  real fakes in `:core:testing`, never mocked suspend functions.

---

## Rules the build enforces for you

`:architecture` (Konsist) fails the build on:
- a `:feature:*` module importing another `:feature:*`
- a Firebase import outside `:core:firebase`
- `Firebase`/`Firestore` referenced inside a `@Composable`
- `FirebaseAnalytics` used outside `:core:analytics` / `:core:firebase`
- any `LiveData` or `GlobalScope`
- a `ViewModel` subclass not named `*ViewModel`, or a `*UiState` that is not an
  immutable data class

Analytics has no free-form API by design: if an event is not in the
`AnalyticsEvent` sealed hierarchy, it cannot be logged (BUILD_PROMPT.md §17.1).

---

## Gotchas

**JDK 21, not 25.** detekt 1.23.8 embeds the Kotlin 2.0.21 compiler, whose bundled
IntelliJ `JavaVersion.parse` throws on `"25.0.2"`. It inspects the *running* JVM, so no
task configuration works around it.

**AGP 9 has built-in Kotlin support.** Modules do **not** apply
`org.jetbrains.kotlin.android`. Most AGP-8-era docs and NowInAndroid-style convention
plugins found online will mislead you here.

**`android.disallowKotlinSourceSets=false` is required.** KSP 2.2.10-2.0.2 still
registers generated sources through the `kotlin.sourceSets` DSL, which AGP 9 rejects by
default. Remove the flag once KSP ships an AGP-9-aware release.

**detekt's `jvmTarget`/`languageVersion` are pinned in an `afterEvaluate`.** detekt
auto-wires them from the Kotlin plugin inside its own `afterEvaluate`; overriding earlier
loses the race.

**Convention plugins use `implementation`, not `compileOnly`,** for plugin markers.
They are precompiled *script* plugins, so each `id("…")` in their `plugins { }` block
must resolve from build-logic's own classpath.

**Gradients must fade a colour to zero alpha, not to `Color.Transparent`.**
`Color.Transparent` is RGBA(0,0,0,0), so a gradient towards it drags the hue to black as
well as the alpha — on this dark theme that shows up as a dirty halo. Use
`color.copy(alpha = 0f)` for the far stop. `Modifier.drawBehind` also does not clip.

**Don't mock suspend functions with mockk inside `runTest`.** `coEvery` records through
an internal `runBlocking` that blocks `runTest`'s thread, so its timeout never fires — one
test here hung for an hour. Use the fakes in `:core:testing` (DECISIONS.md D-013).

**Tests that read files Gradle can't see must declare them as inputs,** or the task goes
UP-TO-DATE and silently stops running. That is exactly what happened to `:architecture`
for three phases (D-015). Prove a gate by watching it fail on a *normal* build.

**Screens render inside `AfterhoursSurface`** — in the app and in every screenshot test.
Drawing a screen bare shows the default window colour through it.

**Anything that can race runs in a Cloud Function transaction,** with its core logic
taking `(db, uid, nowMs)` so it is tested directly against the emulator. Callables stay
thin: auth from the token, delegate, map errors. Never trust a uid or coupleId in a payload.

**Robolectric's default screen is 320×470dp.** A screenshot test without explicit
qualifiers renders narrow and silently clips anything taller — the gallery did both for
three phases (D-024). Feature tests pass `qualifiers = "w392dp-h840dp-xhdpi"`; the design
system sets its size in `robolectric.properties`.

**The dev flavor needs cleartext to the emulators** — `src/dev/res/xml/network_security_config.xml`,
dev only (D-026). Without it every Firebase call on a device fails.

**The nav host is inset at the root** (`safeDrawingPadding()`, including the keyboard). Screens
do not add status-bar or IME padding of their own (D-028).

**Read route arguments by property name, not with `toRoute()`,** in ViewModels:
`savedStateHandle.get<String>(Route.Night::experienceId.name)`. `toRoute()` goes through
Android's Bundle, which JVM unit tests stub, so it silently returns defaults there (D-067).

**Never assume a flow's first value.** `collectAsStateWithLifecycle(initialValue = …)` renders
that guess on the first frame; the lock screen once trapped every user that way (D-027).

**Memory is tight with everything running.** This machine has 7.4 GB; the Android emulator,
the Firebase emulators and a 4 GB Gradle daemon together can fail a build with "Native
memory allocation failed". With the emulator up, build with
`--max-workers=2 "-Dorg.gradle.jvmargs=-Xmx2560m -XX:MaxMetaspaceSize=768m"`.

**After an airplane-mode toggle, wait for the network before a cold start.** The emulator's
Wi-Fi returns in stages; until `adb shell ping -c 1 10.0.2.2` answers, the app cannot reach
the server, fails closed on the age check and shows the age gate, and WorkManager stops any
running sync. Neither is a bug.

**The functions e2e suite wipes the dev emulator project.** Seed manual device checks again
after running it. Integration tests use their own project and touch nothing (D-037).

**`check` needs Node.** `:validateContent` and `:testContentTools` run the TypeScript tools in
`tools/` (and `npm ci` there the first time). The app build itself (`assemble*`) does not.

**The content bundle is committed and must be current.** `check` compares
`content/dist/bundle.json` with a fresh build from the packs; a stale bundle fails it. A
published bundle version is immutable: raise `contentVersion` in `content/content.json`
before publishing changed content.

**Kotlin/KSP versions are paired.** Kotlin 2.2.10 ↔ KSP `2.2.10-2.0.2`. KSP changed to
standalone versioning at 2.3.0; do not mix the schemes.

**The server's engine has its own copy of the content.** `npm --prefix functions run build`
copies `content/dist/bundle.json` into `functions/lib/`. After a content edit, rebuild the
functions too, or the emulator's engine keeps choosing from the old bundle.

---

## Working agreement

Phase by phase (BUILD_PROMPT.md §22). Verify before advancing — a phase is not done
until its commands have been *run* and seen to pass. Update `PROGRESS.md` and commit at
the end of every phase. Record non-obvious calls in `DECISIONS.md`, and anything a human
must click in `HUMAN_SETUP.md`.
