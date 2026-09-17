# PROGRESS

Live build status. Updated at the end of every phase (BUILD_PROMPT.md §0.2).
Phase list and exit criteria: BUILD_PROMPT.md §22.

**Current position:** Phase 0 complete. Next action: **Phase 1 — Firebase wiring.**

---

## Phase status

| # | Phase | Status |
|---|---|---|
| 0 | Repo & tooling | ✅ **Complete** |
| 1 | Firebase wiring + emulators | ⬜ Next |
| 2 | Design system | ⬜ |
| 3 | Auth + age gate + app lock | ⬜ |
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
