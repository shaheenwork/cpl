# Afterhours

A private intimacy, chemistry and desire-exploration app for **two consenting adults in a
relationship**. Native Android, Firebase backend, no third-party services.

It is not a dating app, a social network, a questionnaire or a content feed. It is a
private playground for exactly two people — one that learns the couple over time and
builds personalised nights around what they are both actually curious about, whether
they are in the same room or a continent apart.

> **18+.** Age-gated, consent-gated, and built so that neither partner can ever see the
> other's private answers. See [SECURITY.md](SECURITY.md) once Phase 21 lands, and
> [BUILD_PROMPT.md §3](BUILD_PROMPT.md) for the safety architecture.

---

## Status

**Phase 0 of 22 complete** — project skeleton, build system, module graph, DI, static
analysis and architecture enforcement. See [PROGRESS.md](PROGRESS.md).

---

## Running it

The Gradle daemon must run on **JDK 21** ([why](DECISIONS.md)):

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
```

```bash
./gradlew assembleDevDebug
```

```bash
./gradlew check
```

The `dev` flavor targets the **Firebase Emulator Suite** and needs no Firebase project or
credentials. `staging` and `prod` do — see [HUMAN_SETUP.md](HUMAN_SETUP.md).

---

## Architecture

```
Compose UI  →  ViewModel  →  UseCase  →  Repository  →  Firebase | Room | DataStore
```

Roughly 16 Gradle modules today, growing per phase. `:core:model`, `:core:common` and
`:core:engine` are **pure JVM** — the experience engine has no Android or Firebase
dependency, so the most important logic in the product is also the fastest to test.

Shared build configuration lives in convention plugins under `build-logic/`, not
duplicated across module build files.

The module graph is enforced, not merely documented: `:architecture` runs Konsist rules
that fail the build if a feature imports another feature, if Firebase leaks outside
`:core:firebase`, if a `@Composable` touches Firestore, or if anything logs analytics
outside the typed allowlist.

See [CLAUDE.md](CLAUDE.md) for the module map and conventions.

---

## Documents

| File | What it is |
|---|---|
| [BUILD_PROMPT.md](BUILD_PROMPT.md) | The full product and engineering brief |
| [PROGRESS.md](PROGRESS.md) | Live phase status — start here |
| [CLAUDE.md](CLAUDE.md) | Build commands, module map, conventions, gotchas |
| [DECISIONS.md](DECISIONS.md) | Every non-obvious call, and why |
| [HUMAN_SETUP.md](HUMAN_SETUP.md) | Firebase, Play Console and toolchain steps only a human can do |

---

## Privacy, in one paragraph

Individual preference answers and boundary settings are owner-only for life — enforced by
Firestore rules, not by hiding things in the UI. Mutual matches are computed server-side
and **only positives are ever materialised**, so a non-match tells the other partner
nothing. Reveals are batched and jittered so that timing cannot be used to infer what a
partner just answered. The combined boundary set never reaches a device, because a user
could otherwise diff it against their own answers to derive their partner's hard limits.
`NEVER` from either partner is an absolute exclusion that no mood, parameter, random draw
or personalisation score can override.
