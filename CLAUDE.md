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
| `npm --prefix tools run export -- --out cards.tsv` | The catalogue as a spreadsheet: `--pack`, `--game`, `--level` narrow it (CONTENT_GUIDE.md §2) |
| `npm --prefix tools run import -- cards.tsv --write` | The edited spreadsheet back into the pack and prompt files, if the whole catalogue validates |
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
:feature:home         Home: the Together/Apart toggle, the heat, and the list of ways to play
:feature:instant      The card deck and Game Night — the whole of Together mode
:feature:games        The seven games' player, over the one interpreter
:feature:boundaries   The limits profile: eight questions over twenty-seven themes
:feature:store        The three packs, and what each costs (V2 rule 5)
:feature:chat         The conversation, drawn over everything else by :app (CHAT_PROMPT.md)
:feature:*            Added by the phase that introduces them (DECISIONS.md D-006)

Out of scope since the rebuild, and moved to `_to_remove/` rather than deleted:
:feature:night, :feature:preferences, :feature:discovery, :feature:memories, and the
memories / preferences / mutual halves of :core:data and :core:firebase.

Out of scope since v2: :feature:send — the old inbox. Media still exists inside a round as a
dare's proof (`GamePrimitive.MEDIA`, rendered by `:core:ui`), and since the chat there is also a
conversation beside the games — a layer `:app` draws over the nav host, never a route (D-138).
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
- **`open()` is not a read.** It calls `viewMedia`, and a burn-after-view item is deleted as it is
  read. Nothing may fetch media because it came into view: `ChatMedia.wanted` loads what is safe to
  load, `look` is the tap, and a once-only item is drawn from what its record already says (D-139).
- A voice note's length and waveform are on the **media record**, sent with `reserveMedia` and
  written by the server — never on the message, whose rules are held to five keys (§7.3 row U).
  `VoiceLevels.of` makes the forty bars; both phones draw them without decoding the audio.
- Media sent from the chat confirms with `quiet: true`. The message announces it, and the chat's
  own trigger is rate-limited; without it one photo is two pushes.

### The conversation, drawn

- The chat is a layer `:app` hangs over the nav host — never a route (D-138). `ChatViewModel` is
  one instance for the whole app.
- **The ticking is the collaborator's.** `ChatVoice` polls the microphone and the player,
  `ChatMedia` fetches and decodes; both are interfaces, and their fakes do not tick. A ViewModel
  with a loop of its own hangs `advanceUntilIdle()` for ever (D-076).
- `Timeline.of` is pure: day separators in the phone's own zone, runs of five minutes, the unread
  mark. `TimelineTest` is where a grouping change is proved, not a golden.
- **A bubble wraps its content**, up to `BUBBLE_SHARE` of the list's width measured once by the
  `BoxWithConstraints` around it — never `fillMaxWidth(share)`, which gives two words a banner.
  The gap inside a run is `xxs` and between runs `item`; a timestamp sits *under* the bubble, so a
  bubble holds only what was said.
- **Every photo is the same size**: `wide × PHOTO_RATIO`, cropped. Sizing a tile to its own image
  made a portrait into a column and a panorama into a sliver.
- **Media is on screen before it leaves the phone** (`ChatUiState.outgoing`). The upload comes
  before the message (D-083), so until it finishes there is nothing to draw and a tap on send looks
  like a tap on nothing. `ChatMedia.preview` decodes the local file for it, beside the upload.
- The panel is a **sheet**: `surfaceContainer` with a rim, not `surface`, which is the same black as
  everything behind it. Anything drawn on it goes a step higher — their bubbles and the composer
  field on `surfaceContainerHigh`, a covered plate on `surfaceContainerLowest`.
- It lands on the newest when it opens (`scrollToItem`, not animated — the panel is sliding at the
  same moment), and follows anything of this phone's own wherever the list had been scrolled to.

### One control, every bar

- An app-wide control is **offered**, never placed: `LocalBarExtra` in `:core:designsystem` holds
  it and each bar draws it where that bar's own controls go — `ScreenScaffold`'s top bar beside the
  screen's `trailing`, or `SafetyRail` beside Stop on a screen that plays (a screen with a `rail`
  does not draw it twice). The chat is the only thing in it.
- A floating affordance lands on whatever the screen already had in that corner; it is how the
  settings button disappeared on home for a while. `ScreenScaffoldTest` holds both in place.
- `rememberNudge()` in `:core:ui` is one buzz, for one thing: the *other* phone started an evening.
  Never for this phone's own taps. `VIBRATE` is install-time, so nothing is ever asked for.
- `CinematicCard(pulsing = true)` breathes instead of simply being lit — also only for something
  the other phone started. Two pulsing things on a screen is none.
- Renderers that two features would need live in `:core:ui`; these are the chat's own, so they stay
  in `:feature:chat`.
- The camera needs `:feature:chat`'s `FileProvider` (`${applicationId}.files`). `CAMERA` is
  deliberately not declared — with it, `ACTION_IMAGE_CAPTURE` would need a runtime permission.

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

### The account itself

- Reporting writes `reports/{id}`, which **no client may read** (§3.5). Four reasons, no free text,
  and the partner is never told. `ReportSheet` in `:core:ui` is the same card everywhere.
- An export is written by the server into `users/{uid}/exports/…` — readable by that user alone,
  never writable by a client — and holds their own documents plus what the couple shares. Never a
  field of the partner's private record, and never an undelivered surprise (D-093).
- `deleteAccount` ends the couple and takes everything under it, both Storage prefixes, every
  document under the user, and the Auth user last (D-093). The screen says so before the first tap,
  and the real button is behind a second one.
- Every delete in that cascade is retried and, if it still will not go, **stepped over** — deleting
  documents a phone is watching throws `CANCELLED`, and an account must not survive its own
  deletion because a listener dropped (D-094). `failures` comes back for the log; the next call
  finishes what was left.
- Settings (`:feature:settings`) is the hub for all of it (§14.9); home keeps only memories, limits
  and a way in.
- **Settings is built from `SettingsGroup` / `SettingsRow` / `StatusCard`** in `:core:ui` (D-146),
  not from headings and lines. Unpairing is a question, then "Unpairing…", then a card that says it
  is done — never a tap that does the whole job and shows nothing.
- **The app lock has an off switch** (`AppLockSettingsScreen`), and turning it off or changing the
  PIN asks for the current PIN. The component gallery and content inspector are rows in a debug-only
  "Developer" group at the foot of Settings, never on home, and their routes do not exist in a
  product build (`DEV_TOOLS_AVAILABLE`).

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
- Chance is not free: a `DRAW` lands on a seed made from the round itself (`engine/SPEC.md` §10), so
  a test can assert it, two phones agree without sending anything, and a restarted phone lands the
  same way. A `DIAL` is a number that never leaves the phone — only a `DISTANCE` reveal's gap is
  shared, and a gap has no direction.
- A `DICE` step is a `DRAW` with more than one pool, rolled at once (D-130). Each die takes its faces
  from the step or from the round's prompt, at most one carries a clock (`seconds` parallel to its
  faces, zero meaning none), and **which die it is goes through the seed's hash** — an offset leaves
  neighbouring streams correlated, which two dice showed on a device.
- **A game of turns hands the phone over at the start of each round** (`Game.takesTurns`, D-130).
  That is where the names question appears, and it is the only thing that ever said whose turn it is.
- A round's last step is its end: the dealt player never shows "that's the round" (D-130).
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
- "A phone each" is offered for any game the other phone can follow (`Game.playableApart`, D-136):
  both seats are asked, **or** the round is open. A chapter is revealed once everyone it *asked*
  has answered — `answeredBy` on the session — so a game of turns no longer waits for an answer
  nobody was ever asked for.
- The watching phone is never shown a blank: `GameMoment.Waiting.watching` carries whose turn it
  is, that they are choosing, and then what they drew. Chance needs nothing sent — the dice are
  seeded by the round, so both phones compute the same faces (§10). A round with any **private**
  step shows the watcher nothing at all, and that is what keeps the mirroring safe.
- **A finished round moves itself on.** "Round done." with a Next button was a screen whose only
  content was the tap that left it. The one end-of-round screen left is a round that asked them
  both where the other phone has not looked yet.
- Nothing about a round is stored on the phone (D-075): a restarted phone rejoins at the
  session's round, and takes its own answer back from the reveal rather than from a cache.
- The heartbeat's beat is the repository's (`keepPresent`), never a `while (true)` in a
  ViewModel — that hangs `advanceUntilIdle()` for ever (D-076).
- `node functions/scripts/dev-couple.js answer|seen|present` plays the partner's own phone for a
  device check.

### Design system

- Reach tokens through `AfterhoursTheme.colors / .spacing / .motion / .elevations`, and
  Material slots through `MaterialTheme`. Never hardcode a colour, a dp or a duration.
- The values come from `design/redesign/tokens.json` (D-145); change a colour there and in
  `theme/Color.kt` together, and `ContrastTest` decides whether it may ship.
- **The room is lit in tonight's heat.** `ScreenScaffold` calls `LightTheRoom(tier.accent)`; pass
  `calm = true` after Stop. A screen never draws its own background or its own key light.
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

## Playing with no account (the rebuild)

- `REDESIGN_PROMPT.md` is the brief for the front half; `BUILD_PROMPT.md` still governs everything
  it does not cover, and §3, §5, §7 and §10.3 are not up for renegotiation.
- **One tier enum.** `Intensity` is the five a couple sees — Mild, Flirty, Spicy, Hot, Extreme — with
  the label and the dots on it. `Heat` is gone (M2): three words over five levels meant Wild and
  Bold were the same word and no screen could say which tier a card was. A tier is still a
  **ceiling**, and a paired couple's real ceiling is still the server's.
- `InstantDeck` (`:core:engine`) is the deck for a phone with no account — shipped content only,
  bounded by this phone's tier, its lane and its refusals. It is safe because there is no partner
  yet, not because it filters (D-100). `narrow` may only ever remove, and a test holds that.
- **The deal is not the pool.** `cards` is everything this phone may be shown; `next()` is what
  comes up, and it climbs to the tier and never repeats (D-124). Dealing from index zero over a
  pool in bundle order is what made 790 items look like six.
- **A game is played at the heat its starter chose** (D-148): the request carries the heat on the
  starting phone, `forSharedPhone` lifts the couple's own level so it is `min(request, 5)`, and both
  phones show it (`HeatTag`, from what the server released). Exclusions and ask-first themes still
  apply; only the number is lifted. A server change: redeploy the functions after touching it.
- **Home has no Play Now** (D-150). Every way to start is a named row under "Play": Cards (the
  deck, lit in the heat, first), Game Night, then the games. Cards and Game Night are one-phone, so
  Apart (paired) shows only the games, and Apart with nobody paired shows only "Pair your phones".
  A game a paired couple starts is asked of the server, so home says "Getting it ready…" where the
  tap was. The deck's own bar says "Cards".
- **Apart needs a pair** (D-146): with Apart selected and nobody paired, home offers no games and no
  heat — only "Pair to play apart" — and `HomeViewModel.play` refuses. Together never needs one.
- The packs are a **lane** a couple can ask for by name (D-125): only ones with cards at this tier
  are offered, and a lane is only ever narrower than the open deck.
- A refusal is captured mid-play by `Too far` and honoured from the next card. The **limits
  profile** is the same statement made in a form: eight `LimitTag`s over the twenty-seven taxonomy
  themes, a partition, with every theme either owned or explicitly governed by the tier (M3).
  `TaxonomyFileTest` fails the build on a theme nobody owns.
- **Stop is not Too far.** Too far is about one card; the safe word stops the evening, asks nothing,
  and is on every screen that plays anything (M7) — always the `StopPill` at the start of
  `SafetyRail`, at the bottom, one-phone games and Game Night included (D-143). There is no Extreme
  gate on one phone: choosing the tier is the consent (D-144); the paired "I'm in" is the server's
  and stays. Aftercare after any Hot or Extreme run comes from cards written to land on.
- **The deck deals face down** (`TurnOverCard`, D-143): nothing of a card is composed until it is
  turned, and Done and the timer wait for it. The heat and the lanes are chips that open an
  `AfterhoursSheet` — in the screen's own window, never a dialog, so `SecureScreen` covers it.
- A game with no account is dealt by `ReleasedGame.forHeat`, not by the server, and `autoHeat`
  decides whether it climbs to the tier or opens at it (M4). `fromServer` remains the only way once
  a couple is signed in.
- **Game Night is not in `games.json`** (M5). A prompt-based game draws from one game's pool; an
  hour needs the whole catalogue, so it is a plan over `InstantDeck` in four phases. The length is a
  budget in minutes and the plan keeps to it: `next(within = …)` is how a phase asks for something
  that fits, and only a phase's first card may overrun (D-129).
- **The climb belongs to the start of an evening, not to every sixth card.** `keepGoing` and
  `setLane` reshuffle without resetting the deal index, or a run six Extreme cards deep opens two
  tiers down again (D-129).
- A `DRAW` may take its faces from the step rather than the prompt, and only a `DRAW` may: a die has
  the same faces every roll, and everywhere else a fixed list is a closed vocabulary the interpreter
  reads meaning from (M4).
- **The primitive renderers live in `:core:ui`**, not in `:feature:games`: two players draw them now,
  and a feature may not import another feature. A second renderer for a primitive is what §11.1
  forbids, so they belong to neither feature.
- **A couple buys a pack once** (D-149): `HomeViewModel` starts `BillingRepository.shareOwnership()`,
  which writes how far this phone's packs reach to `couples/{cid}/packs/{uid}`; the other phone reads
  it (`partnerPacks`) and `LevelAccess` takes the further of the two. A client may write only its own
  document, and only a level a pack can be — a rule that is allowed because the packs open content
  already inside the APK. Changing it is a rules change: deploy `firestore:rules` as well.
- **The money is the top of the range, not the volume** (V2 rule 5, D-135). Mild and Flirty are free
  for ever; Spicy, Hot and Extreme are one-time packs, and **a pack opens every level below it** —
  a ceiling with a hole in it would skip rungs on the climb. **Everything is open until a date set in
  Firebase** (Remote Config `free_until`, D-151): `2026-11-06` is the whole of that day, a full ISO
  moment is exactly then, blank ends it now, and a value nobody can read falls back to the date the
  app ships with. Nothing counts cards: there is no limit that can stop an evening that has started.
- **Notifications are for a phone that is not looking** (D-151). `Notifier` posts nothing for a kind
  whose `speaksWhileOpen` is false while the app is on screen — only a message from them speaks over
  an open app — and permission to notify is asked once, after pairing (`AskToNotifyAfterPairing`),
  never at first launch.
- The ceiling is applied in `InstantPlayRepository` — deck, guided evening and dealt game alike —
  never on a screen. A purchase is recorded on the phone (`ownedLevels`) because an unpaired phone
  has no account; Play is the authority and `restore` asks it, on demand and on opening the store.

---

## Performance, cost and accessibility

- A stored session is checked on open: `validateSession()` refreshes the token and signs out one
  that is definitively dead (deleted, disabled, revoked, password changed elsewhere, or the `dev`
  Auth emulator restarted). Offline keeps the session. Without this the app holds a signed-in user
  whose every call fails `UNAUTHENTICATED`, and a queued Firestore write never lands — D-098.
- **A Firestore write is queued offline and retried for ever**, so a screen that waits on one waits
  for ever. `confirmAge` is bounded at ten seconds for that reason; bound any write a screen blocks on.
- The accessibility audit is `AccessibilityTest` in `:core:designsystem` (200% type with goldens,
  48 dp targets, a label on everything clickable) plus `ContrastTest`. Add to it when a component
  lands — D-096.
- Listeners are proved released by `ListenerReleaseTest` in `:core:data`, which counts registrations
  at the data-source seam. LeakCanary is in debug builds only — D-097.
- R8 is on for release with `app/proguard-rules.pro`; the release build is signed with the debug key
  locally, and the real upload key is HUMAN_SETUP.md §3.3 — D-095.
- `COST.md` holds the per-night read/write/invocation counts and the three cost drivers.
  `ACCEPTANCE.md` is the two-device script, with a run log that says what was actually walked.

---

## Security

- SECURITY.md is the map: threat model, where each promise is enforced, the residual risks, and the
  §7.3 matrix with the test that holds each row.
- Every row of §7.3 has a named test in the suite **"§7.3 — the mandatory matrix"** in
  `firebase/tests/rules.test.mjs` (rows O and P in `storage.test.mjs`). Add a row there when a
  phase adds a collection.
- `security.e2e.test.ts` reads the callable surface out of `index.ts`: a new callable is swept
  automatically. **Authenticate before validating a payload** — `callerUid(request)` first, always,
  or an unauthenticated caller learns the shape of a request it was never allowed to make.
- App Check is enforced outside the Emulator Suite (`enforceAppCheck`), and every callable uses the
  shared `options` — the sweep fails if one declares its own.

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

**`10.0.2.2` is the Android emulator's address for this machine, and means nothing on a real
phone.** For a device on the same network, build with its address and let the suite listen beyond
localhost (`firebase.json` binds `0.0.0.0`) — D-134:

```bash
./gradlew installDevDebug -PdevEmulatorHost=192.168.1.20
```

**An App Check error in a `dev` run is noise.** The dev `google-services.json` is a placeholder for
a project that does not exist, so the debug token can never be exchanged and the console link in the
error cannot help. Dev installs no App Check provider at all now (D-134); if you see one, the build
is not the dev flavour.

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

**The Firestore emulator builds indexes on demand; a real project does not.** Every
`collectionGroup` query needs an entry in `firebase/firestore.indexes.json`, and
`firebase deploy` needs `firestore:indexes` alongside `firestore:rules` — deploying rules alone
leaves the five scheduled sweeps failing with `9 FAILED_PRECONDITION: The query requires an
index`, which no test and no screen ever shows. Add the index in the same commit as the query.

**The functions e2e suite wipes the dev emulator project.** Seed manual device checks again
after running it. Integration tests use their own project and touch nothing (D-037).

**`check` needs Node.** `:validateContent` and `:testContentTools` run the TypeScript tools in
`tools/` (and `npm ci` there the first time). The app build itself (`assemble*`) does not.

**The content bundle is committed and must be current.** `check` compares
`content/dist/bundle.json` with a fresh build from the packs; a stale bundle fails it. A
published bundle version is immutable: raise `contentVersion` in `content/content.json`
before publishing changed content.

**A phone that already has the app keeps its cached content.** The cache installs the shipped
bundle when its version is higher, or when the same version arrives from a *different build*
(`ShippedContent.fingerprint()`, D-131). Reinstalling the same build over itself changes the
install time, so a device check picks the new content up — but if you are ever unsure what a phone
is playing, `adb shell pm clear com.shnapps.couple.dev` is the certain answer.

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

**The Firestore emulator keeps what the last run wrote, and it does not reload `firestore.rules`
reliably on Windows.** Two separate traps, and both look like a broken rule:

- A rules test that *creates* a document fails on its second run if the collection forbids updates —
  `setDoc` over an existing document is an update, not a create. A suite that writes must clear its
  own documents in `before`.
- Editing `firestore.rules` while the emulators are running can leave the old ruleset in place.
  Restart the suite after a rules change rather than trusting the watcher.
