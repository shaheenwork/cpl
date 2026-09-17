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
