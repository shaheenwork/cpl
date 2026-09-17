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
```

| Command | What it does |
|---|---|
| `./gradlew assembleDevDebug` | Build the dev variant (emulator-backed Firebase) |
| `./gradlew check` | detekt + ktlint (via detekt-formatting) + all unit tests + architecture rules |
| `./gradlew :core:engine:test` | The experience-engine suite — the highest bar in the repo |
| `./gradlew :architecture:test` | Konsist layering rules |
| `./gradlew projects` | Verify the module graph configures |

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

**Kotlin/KSP versions are paired.** Kotlin 2.2.10 ↔ KSP `2.2.10-2.0.2`. KSP changed to
standalone versioning at 2.3.0; do not mix the schemes.

---

## Working agreement

Phase by phase (BUILD_PROMPT.md §22). Verify before advancing — a phase is not done
until its commands have been *run* and seen to pass. Update `PROGRESS.md` and commit at
the end of every phase. Record non-obvious calls in `DECISIONS.md`, and anything a human
must click in `HUMAN_SETUP.md`.
