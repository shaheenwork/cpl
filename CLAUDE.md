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
| `./gradlew check` | detekt + ktlint (via detekt-formatting) + all unit tests + architecture rules |
| `./gradlew :core:engine:test` | The experience-engine suite — the highest bar in the repo |
| `./gradlew :architecture:test` | Konsist layering rules |
| `./gradlew :core:designsystem:recordRoborazziDebug` | Re-record screenshot goldens after an intentional visual change |
| `./gradlew projects` | Verify the module graph configures |
| `./gradlew connectedDevDebugAndroidTest` | Instrumented tests - needs an emulator **and** the Firebase emulators |

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
:core:database        Room
:core:datastore       DataStore settings
:core:security        App lock, FLAG_SECURE, App Check glue
:core:analytics       Typed, allowlisted analytics facade
:core:notifications   FCM + privacy-aware builders
:core:testing         Fakes, fixtures, rules
:architecture         Konsist rules that fail the build on violations
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

**Never assume a flow's first value.** `collectAsStateWithLifecycle(initialValue = …)` renders
that guess on the first frame; the lock screen once trapped every user that way (D-027).

**Kotlin/KSP versions are paired.** Kotlin 2.2.10 ↔ KSP `2.2.10-2.0.2`. KSP changed to
standalone versioning at 2.3.0; do not mix the schemes.

---

## Working agreement

Phase by phase (BUILD_PROMPT.md §22). Verify before advancing — a phase is not done
until its commands have been *run* and seen to pass. Update `PROGRESS.md` and commit at
the end of every phase. Record non-obvious calls in `DECISIONS.md`, and anything a human
must click in `HUMAN_SETUP.md`.
