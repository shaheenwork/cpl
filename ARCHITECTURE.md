# ARCHITECTURE

How this app is put together, and why each seam is where it is. `CLAUDE.md` is the working
reference; this is the shape behind it. `engine/SPEC.md` is the algorithm itself, `SECURITY.md` is
what is enforced where, and `BUILD_PROMPT.md` is the product brief every section here answers to.

---

## 1. The shape in one paragraph

An Android app in Kotlin and Compose, a set of Cloud Functions in TypeScript, and one algorithm
implemented on both sides of that line. The phone owns the experience of a night; the server owns
every decision that depends on what both partners privately said. Firestore holds the data, and its
rules are the floor under every privacy promise — not the screens, which are only a courtesy.

---

## 2. Modules

```
:app                  MainActivity, the nav host, Hilt, product flavors (dev / staging / prod)

:core:model           Domain models and enums                       pure JVM
:core:common          Outcome / AppError, dispatchers, Clock        pure JVM
:core:engine          Assembly, variety, remix, the game runner     pure JVM
:core:designsystem    Tokens, theme, motion, components, gallery
:core:ui              Shared composables, SecureScreen, ReportSheet
:core:navigation      Type-safe routes and deep links
:core:data            Repositories and use cases
:core:firebase        Firebase data sources — the ONLY Firebase importer
:core:database        Room: the content cache (content.db)
:core:datastore       DataStore settings and routing hints
:core:security        App lock, FLAG_SECURE, App Check glue
:core:analytics       A typed, allowlisted analytics facade
:core:notifications   FCM service and the fixed notification text
:core:testing         Fakes, fixtures, rules
:architecture         Konsist rules that fail the build on violations

:feature:onboarding   Age gate, welcome        :feature:night      Build a night, play it
:feature:auth         Sign in, sign up, reset  :feature:games      Games over primitives
:feature:applock      PIN and biometric        :feature:home       Together / Apart
:feature:pairing      Code, QR, approval       :feature:send       Apart: send, later, arcs
:feature:preferences  Private curiosities      :feature:memories   The library
:feature:boundaries   Limits and levels        :feature:settings    The account hub
:feature:discovery    Mutual matches           :feature:paywall     Premium, and the gates
```

Rules the build enforces (`:architecture`, Konsist):

- a `:feature:*` module may not import another `:feature:*` — they meet at `:app` and at `:core:*`;
- no Firebase import outside `:core:firebase`, and no `Firebase`/`Firestore` inside a `@Composable`;
- `FirebaseAnalytics` only in `:core:analytics` and `:core:firebase`;
- no `LiveData`, no `GlobalScope`;
- a `ViewModel` subclass is named `*ViewModel`, and a `*UiState` is an immutable data class.

---

## 3. Layering, and what each layer may do

```
Compose screen  →  ViewModel  →  UseCase / Repository  →  DataSource  →  Firebase
   state only       StateFlow        Outcome<T>            typed errors
```

- **Screens** render one immutable `UiState` and send events up. They never touch Firebase, never
  decide what a couple may see, and call `SecureScreen()` when private content is on them.
- **ViewModels** depend on interfaces (`AuthRepository`, `NightRepository`, `AppPreferencesStore`),
  which is what lets every test use a real fake instead of a mocked suspend function (D-013).
- **Repositories** return `Outcome<T>`. Exceptions do not cross a layer boundary: `:core:firebase`
  is the exception boundary, and `firestoreCall`, `functionsCall` and `storageCall` are where a
  Firebase failure becomes an `AppError`.
- **Data sources** are the only Firebase importers, and the only place a listener is registered.

---

## 4. Data flow of one night

```
phone: BuildNightViewModel            server: generateExperience
   mood, length, intensity  ──────▶     callerUid from the token
   (ceiling clamped in UI)              engineFilters/current  (server-only: both partners' limits)
                                        hardFilter            ← the chokepoint, first, always
                                        score → plan → sample
                                        write experiences/{id} + a sampled alternates pool
   PermittedPool.fromServer  ◀──────     the night, and nothing wider than it
   Remixer, Assembler                   (no catalogue, no permitted set, no filter on the phone)
   "make it more teasing" ───────▶     remixNight: refuses anything past the ceiling
   createSession ────────────────▶     sessions/{id}, transitions.ts, in a transaction
   the phone follows the session document; it never assumes where a night is
```

The rules of that picture, each with a decision behind it:

- **One builder.** Every night — asked for, surprise, express, planned, a day of an arc — is built by
  `buildExperience` in `functions/src/experience/generate.ts`. A TypeScript architecture test reads
  the sources and fails the build if anything else assembles one.
- **Filter first.** `hardFilter` runs before scoring, sampling or assembly, and the catalogue is
  loaded in exactly two places. A game's rounds come only from `engine/game.ts`, called only from
  `experience/game.ts`, which filters first too.
- **The device cannot widen a night.** `PermittedPool.fromServer` is the only way to build a pool on
  the phone, and Konsist enforces it. A remix the server refuses is not played.
- **Sessions move only through the table.** `functions/src/session/transitions.ts` in a transaction;
  a phone writes its own submission, its own presence and a PAUSE/STOP request, nothing else.

---

## 5. The engine, on both sides of the line

One algorithm (`engine/SPEC.md`), two implementations:

| | Server (`functions/src/engine/`) | Device (`:core:engine`) |
|---|---|---|
| Filter | yes — the chokepoint | **never** |
| Score, plan, sample | yes | no |
| Assemble | yes | yes, from the released pool |
| Variety and remix | — | yes |
| Games | chooses the rounds | `GameRun` interprets them |

Determinism is a rule of the code, not a hope: only `+ - * /`, comparisons and `floor`; stable sorts
with ties broken by id; `mulberry32` for the draws the spec lists, and nowhere else. Shared fixture
vectors (`engine/engine-fixtures.json`) are asserted by both suites, so the two implementations
cannot drift. `ENGINE_VERSION` and `Engine.VERSION` are raised together whenever output changes for
the same inputs (D-046).

A game is content, not code: steps over primitives in `content/games.json`, prompts in
`content/prompts/<game>.json`. `GameLogicTest` fails the build if code names a game (D-072).

---

## 6. Where the data lives

```
users/{uid}                     profile, entitlement (server-written), notificationSettings, timezone
  preferences/*                 private answers            owner-only, for life
  boundaries/*                  private limits             owner-only, for life
  affinity/*                    what they liked            owner-only
  fcmTokens/{tokenId}           push tokens                owner-only
  exports/*  (Storage)          their own export           owner reads, no client writes

couples/{cid}                   membership, derived tier, the couple's ceiling
  members/*                     who is in it
  mutualPreferences/*           positives only, server-written
  engineFilters/current         the combined boundary set  NO CLIENT MAY READ
  experiences/{id}              a night                    never listable
  sessions/{sid}                the state machine
    submissions/{chapter}_{uid} a phone's own answer        no client may read, ever
    rounds/{chapter}            the reveal                 both may read
    presence/{uid}              the heartbeat
  feedback/*                    private reactions
  memories/{experienceId}       the shape of an evening, and their words — never a content id
  media/{id}                    a voice note or photo record; the bytes are in Storage
  surprises/{id}                the fact
    payload/content             the thing — its author until the server writes deliveredAt
  arcs/{id}                     a plan, not content
  plans/{experienceId}          what one planned for the other — never a content id
  countdowns/{id}               the couple's own documents

reports/{id}                    server-only. No client may read it, and nobody is told
pairingCodes/{code}             single-use, transactional
contentMeta/pointer             which content bundle is current (dev; Remote Config elsewhere)
```

The boundary that matters: anything computed from **both** partners' private documents is written
where no client can read it, and read only by the server (`engineFilters`, D-032). Absence is never
shown as information — a non-match produces nothing, not an empty state.

---

## 7. The server

Cloud Functions v2, TypeScript, one region. Callables are thin by rule: identity from the token
(`callerUid(request)`, **before** any payload validation), delegate to a function taking
`(db, uid, nowMs)`, map errors with `toHttpsError`. Anything that can race runs in a transaction, and
the core logic is tested directly against the emulator.

Triggers and schedules: `onSubmissionCreated` (the only reader of a submission), `onFeedbackCreated`,
and five sweeps — reveals every 15 minutes, surprises and sessions every 5, media hourly,
entitlements every 6 hours. Each is a bounded query with `maxInstances: 1`; `COST.md` counts them.

One place sends push: `functions/src/notify/notify.ts`. Every message is data-only and carries
`kind`, `channel`, `at` and at most a document id; the words live on the phone in a fixed table
(D-088).

---

## 8. Content

Authored as JSON under `content/` — packs, prompts, games, the taxonomy, the closed vocabulary, and
the prohibited-terms tripwire — validated and bundled by the TypeScript tools in `tools/`. The bundle
is committed, and `check` fails on a stale one. The app reads content only through
`ContentRepository`, serves it from Room, and no product screen lists it: what a couple sees comes
through the engine. The server has its own copy of the same bundle, copied into `functions/lib/` at
build time.

`CONTENT_GUIDE.md` is the authoring guide; `content/vocabulary.json` and the enums in `:core:model`
change together or `ContentBundleFileTest` fails.

---

## 9. Build and flavors

Gradle 9 with AGP 9 (which has built-in Kotlin support — modules do **not** apply the Kotlin Android
plugin), convention plugins in `build-logic/`, a version catalog, and a JDK 21 daemon because
detekt's embedded compiler throws on newer ones (D-005). Bytecode targets Java 17.

Three flavors on one dimension: `dev` talks to the Firebase Emulator Suite and needs no project or
Play account (a fake billing client, cleartext to `10.0.2.2`); `staging` and `prod` talk to real
projects and share the real billing client, and both disable themselves until their
`google-services.json` is present. R8 is on for release (D-095).

---

## 10. Testing tiers

| Tier | What | Where |
|---|---|---|
| Unit (JVM) | ViewModels with real fakes, the engine, parsers, the app-lock | every module |
| Screenshot | Roborazzi goldens, including 200% type | `:core:designsystem`, `:feature:*` |
| Architecture | Konsist, plus TypeScript source-reading tests | `:architecture`, `functions/src/test` |
| Rules | every §7.3 row against the emulator | `firebase/tests` |
| Server unit / int / e2e | payloads, callables against the emulator, over HTTP | `functions/src/test` |
| Content | the validator and the bundle gate | `tools/` |
| Device | the two-device script | `ACCEPTANCE.md` |

`TESTING.md` says how to run each one and what it is for.
