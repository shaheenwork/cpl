# MASTER BUILD PROMPT — "Afterhours"

**A private 18+ intimacy, chemistry & kink-exploration app for two consenting adults in a relationship.**
**Native Android (Kotlin / Jetpack Compose) + Firebase only.**

> **How to use this document:** This is your complete brief. Read it fully before writing code. Execute it phase by phase per §22. Do not ask for permission to start — start at Phase 0 and work forward. Ask the human only when §0.5 says to.

---

## 0. YOUR MISSION & WORKING AGREEMENT

### 0.1 Mission
Build a shippable MVP of a premium, private, adult-oriented couples app. Two real adults must be able to install it, pair privately, discover mutual desires, set boundaries, and play personalized intimate experiences together — both in the same room and across distance — with no developer intervention, no mocked multiplayer, and no data leaks.

### 0.2 This is a long build. Work like it.
- Work **phase by phase** (§22). Do not start Phase N+1 until Phase N's exit criteria pass.
- Maintain **`PROGRESS.md`** at the repo root. After every phase (and before any long-running compaction risk) update it with: phase number, status, what shipped, what's stubbed, known issues, exact next action. Treat it as your handoff note to yourself.
- Maintain **`CLAUDE.md`** at the repo root from Phase 0 onward: build commands, module map, conventions, gotchas. Keep it current.
- **Commit after every phase** with a conventional-commit message (`feat(pairing): ...`). Initialize git in Phase 0. Never commit secrets or a real `google-services.json` for production.
- After each phase, print a short report: what was built, verification commands run + their results, what's deferred, what's next. Then continue.

### 0.3 Verify, don't assume
A phase is not done until you have **run** the verification and seen it pass. Never report "should work." The commands are in **Appendix C**.

### 0.4 When blocked
If something genuinely requires a human (a real Firebase project, a Play Console account, a signing key), do **not** stop the build:
1. Implement against the **Firebase Emulator Suite** and/or a fake implementation behind an interface.
2. Record the exact human step in **`HUMAN_SETUP.md`** (console clicks, field values, file paths).
3. Keep going.

### 0.5 The only things worth asking the human about
Ask (batched, once) only if: the app/package name is disputed, a legal/distribution decision is needed (see §3.6), or a requirement in this document contradicts itself in a way you cannot resolve. Everything else: make the call, document it in `DECISIONS.md`, move on.

### 0.6 Naming
- Working product name: **Afterhours** (single string resource `app_name`, so it is a one-line rename).
- Application ID: `app.afterhours.android` unless the human says otherwise.
- Keep the name out of code, layouts and content — only in `strings.xml`.

---

## 1. HARD CONSTRAINTS

### 1.1 Stack — use exactly this
**Android**
- Kotlin (latest stable, 2.x), **Jetpack Compose** + **Material 3** (Compose BOM), Compose Compiler plugin
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`
- Gradle **version catalog** (`gradle/libs.versions.toml`) — no hardcoded versions in build files
- **KSP** (never kapt)
- **Hilt** for DI
- **Navigation Compose** with type-safe routes (`@Serializable` route objects)
- ViewModel, Lifecycle, Coroutines, Flow (`StateFlow` for UI state — no `LiveData`)
- **Room** (local cache / offline), **DataStore Proto or Preferences** (settings)
- **WorkManager** (local scheduling, deferred uploads, content-bundle refresh)
- **kotlinx.serialization**, **Coil 3** (images), **Media3/ExoPlayer** or `MediaRecorder`+`MediaPlayer` (voice)
- **AndroidX Biometric** (app lock), **CameraX** only if a camera capture flow is needed
- Testing: JUnit4, **Turbine**, **MockK**, **Robolectric**, `androidx.compose.ui.test`, **kotlinx-coroutines-test**
- Static analysis: **ktlint** + **detekt**, wired into `./gradlew check`

**Firebase (the entire backend)**
- Authentication, Cloud Firestore, Cloud Storage, **Cloud Functions (TypeScript, Node 20, firebase-functions v2)**, Cloud Messaging, Analytics, Crashlytics, Remote Config, **App Check** (Play Integrity in prod, debug provider in dev), Performance Monitoring
- Firebase BOM for all Android Firebase deps
- **Firebase Emulator Suite** is the default dev backend

**Monetization**
- Google Play Billing Library (latest), entitlement **verified and written server-side** by a Cloud Function via Real-time Developer Notifications / `androidpublisher`. Client never decides entitlement.

### 1.2 Absolutely prohibited
- Any backend that is not Firebase: no Node server outside Functions, no Supabase, no AWS, no Postgres/Mongo, no custom REST API, no custom WebSocket server, no third-party BaaS.
- No LLM / AI call anywhere in the MVP. The experience engine is **deterministic** (§10). AI is a post-1.0 consideration only (§10.7).
- No Firebase SDK calls inside Composables. No Firebase types crossing into `:feature:*` modules.
- No `GlobalScope`, no blocking calls on the main thread, no `!!` on nullable Firebase results.
- No god ViewModels (>300 lines is a smell — split), no god Activities (single `MainActivity`, everything else Compose destinations).
- No duplicated game logic — games compose primitives (§11).
- No client-side security decisions (§7.1).

---

## 2. PRODUCT DEFINITION

### 2.1 What this is
A private, secret, addictive playground for **exactly two consenting adults in a relationship**. It should feel naughty, steamy, provocative, mysterious, playful, kinky, intimate, premium and private — like a **luxury after-hours lounge**, never like an adult novelty store or a cheap content dump.

The feeling to engineer: *"Nobody else knows what happens in here. It's just ours."*

### 2.2 What this is NOT
Not a dating app. Not a social network. Not a public community. Not a questionnaire. Not a random-dare list. Not a Truth-or-Dare clone. Not pornography. Not an endless feed.

### 2.3 The core loop
```
Private curiosity → Preference discovery → Mutual discovery → Boundaries
 → Anticipation → Personalized experience → Play → Surprise → Feedback
 → Memory → Better future experiences
```
The app should progressively learn the couple.

### 2.4 The one UX law
> Never make the user think **"which question should we pick?"**
> Make them think **"what is the app going to surprise us with?"**

The app is the third participant: not a person, not a chatbot — a **mysterious host**.

### 2.5 The three signature experiences (get these right above all else)
1. **BUILD OUR NIGHT 🔥** — a fully personalized, chaptered night generated from mood/duration/intensity/leadership/novelty + mutual interests + boundaries + history. Must feel *made specifically for us*.
2. **YOUR PARTNER PLANNED TONIGHT 👀** — one partner secretly plans; the other only knows something is coming; revealed chapter by chapter. The strongest long-distance retention hook.
3. **YOU BOTH CHOSE THIS 👀** — private preference selection → mutual match → suspenseful reveal → one tap to *"Build a night around this."* The bridge from discovery to play.

### 2.6 Modes
**TOGETHER** (same room): anticipation, teasing, games, challenges, roleplay, partner control, mystery, playful competition, shared exploration.
**APART** (separated): delayed reveals, voice, async games, synchronized sessions, surprises, scheduled experiences, countdowns, multi-day arcs.

**Apart is not a reskin of Together.** Different information architecture, different interaction primitives, different pacing. Build it as a first-class mode.

---

## 3. SAFETY, CONSENT & LEGAL GUARDRAILS — NON-NEGOTIABLE

These override every other requirement including "make it feel adventurous." Implement them as **code chokepoints**, not guidelines.

### 3.1 Consent architecture
- **Age gate** at first launch: explicit 18+ attestation, stored with timestamp in `users/{uid}.ageAttestation`. No adult content path is reachable without it. Re-prompt if the record is missing.
- **Both-parties consent to escalate.** Intensity 4 (BOLD) and 5 (WILD) sessions require *both* partners to tap an explicit "I'm in" before the first chapter renders. Store both acknowledgements on the session.
- **Never silently escalate intensity.** Any intensity change is user-initiated and visibly announced.
- **Stop is always one tap away.** Every experience/session screen carries a persistent, always-visible control:
  - **PAUSE** → freezes the session, both sides see "Paused."
  - **STOP** → ends the session immediately, no confirmation gauntlet, no shaming copy. Partner sees a neutral, warm message ("Ending here. All good."). Never surfaces who stopped it as a failure.
- **Per-item opt-out:** every content item shows a low-friction "Not this one" that (a) swaps it instantly and (b) offers one tap to move that theme to `NOT TONIGHT` or `NEVER` in the user's boundaries.
- **Check-in at close.** Every completed session ends with a short, warm check-in before feedback: *"How are we doing?"* → simple options. This is aftercare and it is a product feature, not a legal box.

### 3.2 The Boundary Engine is a hard chokepoint
- There is exactly **one** function in the codebase that decides whether a content item may be shown to a couple. Everything — Build Our Night, Surprise Us, games, scheduled surprises, remixes, partner-planned nights, multi-day arcs, "Make it more…" — routes through it. Enforce with an architectural test.
- Hard exclusion: if **either** partner marked a theme `NEVER`, the content is removed. `NEVER` cannot be overridden by any parameter, any mood, any random selection, any personalization score, any partner-planned choice.
- **Filter before rank, always.** Scoring never sees excluded content (§10.3).
- `ASK FIRST` items require an explicit in-session prompt to the boundary-holder before appearing.

### 3.3 Content policy for all seed content you generate
All content items must involve **two consenting adult partners** and nothing else. Hard-prohibited themes, to be rejected by the content validation script (§9.4) and never authored:
- Anyone under 18, or any age-ambiguous framing
- Non-consent, coercion, intoxication-as-consent, "surprise" acts done *to* a partner without prior agreement
- Incest, bestiality, anything illegal
- Self-harm, degradation framed as harm, or anything that requires medical/safety knowledge the app cannot supply (breath play, bondage-suspension, electricity, etc.)
- Third parties or anyone outside the couple
- Anything requiring a substance
- Public-exposure acts that would involve non-consenting bystanders

Provocative, kinky, power-dynamic and fantasy content **is** in scope — expressed through anticipation, teasing, secrets, roleplay, negotiated power play, sensory themes, fantasy conversation, challenges, partner control and escalating intensity. Intensity 5 is adventurous and explicit in *language and suggestion*; it is not an instruction manual for anything unsafe.

### 3.4 Privacy is the product
- Individual preference answers and individual boundary settings are **never** exposed to the partner, ever, by any surface: UI, notification, analytics, export, support, or inference (§5).
- No sensitive content in notifications (§16.2).
- No private content ever reaches Analytics (§17).
- `FLAG_SECURE` on all experience, preference, boundary, memory and media screens (blocks screenshots + recents preview). Make it a single `SecureScreen()` composable effect.
- App lock (biometric + PIN fallback) is available from MVP.

### 3.5 Reporting & moderation
- Report control on every content item and every partner-authored media item: broken / inappropriate / unsafe / technical.
- Reports write to a collection unreadable by either partner; reporter identity is never exposed.
- Remote Config / admin flag can disable a content item globally without an app update.

### 3.6 Distribution reality (flag to the human, then build accordingly)
Google Play prohibits apps whose primary purpose is sexual gratification or that contain pornographic content. Comparable couples apps ship on Play by staying **text- and suggestion-based**: no app-supplied explicit sexual imagery, no depicted sex acts, user-generated media private and never shared publicly, Content Rating set to the highest maturity tier, mandatory age gate, in-app reporting.

Build to that line by default:
- App-supplied content is **text, prompts, scenarios and typography-led visuals** — no illustrated or photographic sexual content.
- User-generated photos/voice are private, couple-scoped, never public, never indexed, reportable.
- Ship-blocking assumption to record in `HUMAN_SETUP.md`: Play Console content rating must be completed as an adult/mature app; if the human wants content beyond this line, that is a distribution decision (direct APK / web) they must make — surface it, don't decide it.

---

## 4. ARCHITECTURE

### 4.1 Layering
```
Compose UI  →  ViewModel  →  UseCase  →  Repository  →  DataSource (Firebase | Room | DataStore)
```
- Unidirectional data flow. One immutable `UiState` data class per screen, exposed as `StateFlow`.
- One-shot events via `Channel`/`SharedFlow`, never in `UiState`.
- Repositories return `Result<T>` (or a sealed `Outcome`) — no exceptions across layer boundaries.
- Domain models in `:core:model` are pure Kotlin; Firestore DTOs live in `:core:firebase` and are mapped at that boundary.

### 4.2 Gradle module graph
```
:app                          — MainActivity, nav host, Hilt app, flavors

:core:model                   — pure Kotlin domain models + enums (JVM module)
:core:common                  — Result/Outcome, dispatchers, time, ids, extensions (JVM)
:core:engine                  — EXPERIENCE ENGINE (pure JVM, zero Android/Firebase deps)
:core:designsystem            — theme, tokens, motion, components, icons
:core:ui                      — shared stateful composables, previews, SecureScreen
:core:navigation              — route definitions, deep links
:core:data                    — repositories + use cases (Android)
:core:firebase                — Firestore/Storage/Auth/Functions/Messaging data sources, DTOs
:core:database                — Room entities, DAOs, migrations
:core:datastore               — settings, session-local flags
:core:security                — app lock, boundary chokepoint client wrapper, App Check glue
:core:analytics               — typed, allowlisted analytics facade
:core:notifications           — FCM service, channels, privacy-aware builders
:core:testing                 — fakes, fixtures, rules, emulator harness

:feature:auth        :feature:onboarding   :feature:pairing
:feature:preferences :feature:boundaries   :feature:discovery
:feature:home        :feature:together     :feature:apart
:feature:experience  :feature:games        :feature:surprises
:feature:memories    :feature:profile      :feature:settings
:feature:paywall

functions/                    — TypeScript Cloud Functions (own package.json, Jest)
content/                      — authored JSON content packs + taxonomy + validator
tools/                        — seed/upload/bundle scripts (Node, run locally)
```

Rules:
- `:feature:*` may depend on `:core:*`. **`:feature:*` must never depend on another `:feature:*`.** Cross-feature navigation goes through `:core:navigation`.
- `:core:engine`, `:core:model`, `:core:common` are **pure JVM modules** — they must compile without the Android SDK. Enforce it; it makes the crown-jewel logic fast to test.
- Write a **dependency/architecture test** (Konsist or a custom Gradle task) that fails the build on feature↔feature deps, Firebase types in `:feature:*`, or Firebase calls inside `@Composable` functions.

### 4.3 Build flavors
| Flavor | Backend | App Check | Billing | Notes |
|---|---|---|---|---|
| `dev` | Firebase Emulator Suite (auto-wired at startup) | Debug provider | Fake billing | default for all local work |
| `staging` | real Firebase project (staging) | Play Integrity | Play test track | |
| `prod` | real Firebase project | Play Integrity | Play Billing | R8 + Crashlytics mapping upload |

`dev` must work with **zero human setup** beyond `firebase emulators:start`.

---

## 5. PRIVACY ARCHITECTURE (read this twice — it's the hardest part)

The promise is: *"Your individual answers are never exposed to your partner."* Naive implementations break this by **inference**, not by direct reads. Implement all of the following.

### 5.1 Owner-only data never leaves the owner's scope
`users/{uid}/preferences/*` and `users/{uid}/boundaries/*` are readable **only** by `uid`, enforced in Firestore rules. There is no admin-readable mirror, no couple-level copy, no "derived" doc containing raw values.

### 5.2 Mutual matches are computed server-side and only positives are ever materialized
A Firestore trigger (or callable) on preference writes computes matches:
- A `couples/{cid}/mutualPreferences/{prefId}` doc is created **only when both sides are positive** (`YES`/`CURIOUS`/`MAYBE`).
- The doc contains **only**: `prefId`, `matchLevel` (derived: BOTH_YES / BOTH_CURIOUS / MIXED_POSITIVE), `revealedAt`, `seenBy` map, `sourceVersion`. It **never** contains either partner's raw answer.
- A non-match produces **nothing**. The UI never says "you asked, they passed." Absence is never surfaced as information.

### 5.3 Defeat timing inference
If a match doc appears the instant B answers, A learns exactly what B just answered. Therefore:
- **Batch + jitter reveals.** Matches are queued and released by a scheduled function in randomized batches (jitter window configurable via Remote Config, default 30–180 min, plus a "release now" on a couple's next app open if the queue is non-empty).
- Never expose `answeredAt` for the partner.
- A reveal batch always contains ≥1 item or is not sent.

### 5.4 Boundary intersection never leaves the server
The combined (A ∩ B) allow/deny set is **not** readable by any client. Storing it client-side would let a user diff it against their own boundaries and derive their partner's `NEVER` list.
- Hard filtering happens **only** in Cloud Functions.
- The client never receives the permitted set in full (§5.5).

### 5.5 The sampled-alternates rule
`generateExperience()` returns:
1. the assembled experience (chapter list + content item IDs), and
2. a **bounded, randomly sampled alternates pool** — roughly 3× the items needed, sampled from the permitted set, **never the complete permitted set**, re-sampled on every call.

This lets the client do fast, free local remixes ("Make it more…", swap an item, enforce variety) without ever exposing the exclusion set by omission. Document the residual risk in `SECURITY.md` honestly: a determined user making many calls can build a statistical picture; the sampling + cooldown + rate limit make it impractical, and the app's threat model is a partner with physical device access, for whom app lock is the mitigation.

### 5.6 Session answers
- `couples/{cid}/sessions/{sid}/rounds/{rid}/submissions/{uid}` is **readable only by `uid`, forever**. No rule ever grants the partner read access to this path.
- At reveal, the **server** copies the mutually-agreed payload into `rounds/{rid}/reveal` (a single doc both partners may read). The client learns the partner's answer only from the reveal doc.
- Every answer carries an explicit `visibility`: `PRIVATE` | `PARTNER_AFTER_SUBMISSION` | `MUTUAL_REVEAL` | `SHARED`. The reveal function honors it.

### 5.7 Secret mechanics stay secret
Secret Missions, Tonight's Director choices and partner-planned content live in server-guarded docs where the rule is `request.auth.uid == ownerUid` until the server flips `revealedAt`. Never ship "hidden in the UI but present in the payload."

### 5.8 Media
Storage objects are couple- or user-scoped, never public, no download URLs minted into shared documents, access via authenticated `getBytes`/`getFile` with rules verifying couple membership. Voice notes and photo challenges support TTL/burn-after-view where the feature calls for it, with server-side deletion (scheduled function) — not client-side "hide."

---

## 6. DATA MODEL

Field lists are the minimum; add what you need but do not move private fields into shared documents.

### 6.1 Firestore
```
users/{uid}
  displayName, photoPath, createdAt, lastActiveAt, timezone, locale
  ageAttestation { confirmed: bool, at: ts }
  contentLevel: 1..5
  coupleId: string|null
  notificationSettings { enabled, showContent, quietHours }
  privacySettings { appLock, hidePreviews, analyticsOptOut }
  entitlement { tier, source, expiresAt, updatedAt }   // SERVER-WRITTEN ONLY
  fcmTokens/{tokenId}

users/{uid}/preferences/{prefId}        // OWNER-ONLY, FOREVER
  value: YES|CURIOUS|MAYBE|NOT_FOR_ME|NEVER
  secret: bool                          // "secretly curious"
  updatedAt, taxonomyVersion

users/{uid}/boundaries/{themeId}        // OWNER-ONLY, FOREVER
  level: ALWAYS_OK|CURIOUS|ASK_FIRST|NOT_TONIGHT|NEVER
  note: string|null, updatedAt

users/{uid}/affinity/{dimensionId}      // OWNER-ONLY
  score: float, samples: int, updatedAt

couples/{cid}
  status: PENDING|ACTIVE|UNPAIRED
  memberUids: [uidA, uidB]              // denormalized for rules
  nickname, anniversary, createdAt
  contentLevelEffective: int            // min(A,B) — server-written
  currentMode: TOGETHER|APART
  stats { nightsPlayed, mutualCount, lastPlayedAt }

couples/{cid}/members/{uid}
  role, joinedAt, status, lastSeenAt

couples/{cid}/mutualPreferences/{prefId}     // POSITIVES ONLY (§5.2)
  matchLevel, revealedAt, seenBy{uid:bool}, exploredAt|null

couples/{cid}/engineFilters/current           // SERVER-ONLY, NO CLIENT READ (§5.4)
  allowedTags[], excludedTags[], maxIntensity, computedAt

couples/{cid}/experiences/{expId}
  mode, durationMin, mood[], intensity, leadership, novelty
  chapters[] { index, kind, title, contentIds[], state }
  createdBy, plannedBy|null, scheduledFor|null
  status: DRAFT|SCHEDULED|READY|IN_PROGRESS|COMPLETED|ABANDONED
  seed, engineVersion, createdAt

couples/{cid}/sessions/{sid}
  experienceId, state (§12), currentChapter, currentRound
  participants { uid: {ready, lastHeartbeat, connected} }
  consentAcks { uid: ts }               // required when intensity >= 4
  startedAt, endedAt, endedBy|null, endReason

couples/{cid}/sessions/{sid}/rounds/{rid}
  primitive, contentId, state, openedAt, deadline|null
  reveal: {...}|null                    // SERVER-WRITTEN AT REVEAL ONLY

couples/{cid}/sessions/{sid}/rounds/{rid}/submissions/{uid}   // OWNER-ONLY FOREVER
  payload, visibility, submittedAt

couples/{cid}/memories/{memId}
  date, durationMin, mood[], experienceType, title, note
  mediaPaths[], favoriteMoment, rating, createdBy, createdAt

couples/{cid}/surprises/{sid}
  type, createdBy, createdAt, scheduledFor, timezone
  payloadRef, state: PENDING|SCHEDULED|DELIVERED|OPENED|EXPIRED
  // payload itself lives in a server-guarded doc until state=DELIVERED (§5.7)

couples/{cid}/openWhen/{itemId}         // trigger, kind, payloadRef, openedAt|null
couples/{cid}/futureCapsules/{capId}    // unlockAt|unlockEvent, ownerUid, payloadRef, openedAt
couples/{cid}/countdowns/{cdId}         // title, targetAt, dailyUnlocks[], timezone
couples/{cid}/arcs/{arcId}              // multi-day: lengthDays, dayStates[], startedAt
couples/{cid}/feedback/{fbId}           // contentId, reaction, byUid, at   (drives affinity)

content/{contentId}                     // PUBLIC-READ TO AUTHED USERS (§9.2)
pairingCodes/{code}                     // SERVER-ONLY; short TTL
reports/{reportId}                      // SERVER-ONLY; never readable by users
```

### 6.2 Storage
```
users/{uid}/avatar/{file}
users/{uid}/outbox/{itemId}/{file}           // pre-delivery, owner-only
couples/{cid}/memories/{memId}/{file}
couples/{cid}/voice/{itemId}/{file}
couples/{cid}/media/{itemId}/{file}
content/bundles/v{n}/{pack}.json             // read: authed; write: admin only
```

### 6.3 Room (cache only — never the source of truth for anything security-relevant)
Cache: current user + couple, content bundle (items, tags, taxonomy), current/recent experiences and their chapters, in-flight session snapshot, memories page 1, pending outbox actions. Everything else is fetched. Room must survive process death and restore a session view instantly while the listener reattaches.

---

## 7. FIRESTORE & STORAGE SECURITY RULES

### 7.1 The rule
**Client-side hiding is not security.** If a rule does not enforce it, it is not enforced. Every privacy claim in §5 must be provable by an emulator rules test.

### 7.2 Required properties
- Every read/write requires `request.auth != null` **and** App Check.
- Couple-scoped access requires `request.auth.uid in get(/couples/$(cid)).data.memberUids`, with `status == 'ACTIVE'`.
- `users/{uid}/preferences/**`, `users/{uid}/boundaries/**`, `users/{uid}/affinity/**`, and `**/submissions/{uid}`: read+write **only** where `request.auth.uid == uid`. No exceptions, no reveal-state escape hatch.
- `couples/{cid}/engineFilters/**`: **no client access at all** (rules deny all reads and writes; Admin SDK only).
- `couples/{cid}.memberUids`, `contentLevelEffective`, `users/{uid}.entitlement`, session `state`, round `reveal`, and every `revealedAt`: **server-written only** — rules reject client writes to those fields.
- `pairingCodes/**` and `reports/**`: no client read; writes only through callable functions.
- Field-level write validation: reject unknown fields, wrong types, out-of-range intensity, and any attempt to change `ownerUid`/`coupleId` on an existing doc.
- Storage rules mirror Firestore membership checks and enforce content-type + size limits (images ≤ 10 MB, audio ≤ 20 MB).

### 7.3 Mandatory rules tests (emulator, must all pass)
| # | Scenario | Expected |
|---|---|---|
| A | Partner A reads B's `users/{B}/preferences/*` | **DENIED** |
| B | Partner A reads B's `users/{B}/boundaries/*` | **DENIED** |
| C | A writes a different `coupleId` onto their user doc | **DENIED** |
| D | A writes to B's submission doc | **DENIED** |
| E | A reads B's submission before reveal | **DENIED** |
| F | A reads B's submission **after** reveal | **DENIED** (reveal comes from the reveal doc only) |
| G | A sets `round.state = REVEAL` from the client | **DENIED** |
| H | A writes `couples/{cid}.contentLevelEffective` | **DENIED** |
| I | A writes `users/{A}.entitlement` | **DENIED** |
| J | Unauthenticated read of any `couples/**` | **DENIED** |
| K | Third user (not a member) reads `couples/{cid}/**` | **DENIED** |
| L | Any client read of `couples/{cid}/engineFilters/**` | **DENIED** |
| M | A joins a couple that already has 2 active members | **DENIED** |
| N | A reuses an expired/consumed pairing code | **DENIED** |
| O | A reads a Storage object from another couple | **DENIED** |
| P | A reads a deleted media path | **DENIED** |
| Q | A writes to `reports/**` directly | **DENIED** |
| R | A creates a `mutualPreferences` doc directly | **DENIED** |
| S | A reads a surprise payload before `state == DELIVERED` | **DENIED** |
| T | Happy path: both members read/write their own permitted paths | **ALLOWED** |

---

## 8. CLOUD FUNCTIONS SURFACE

TypeScript, v2, with Jest unit tests and emulator integration tests. Use transactions for anything with a race condition.

**Callable**
```
createPairingCode()                 → {code, expiresAt}       // 6-digit, TTL 15 min, rate-limited
acceptPairing({code})               → {coupleId}              // TRANSACTIONAL
unpairCouple({confirm})             → {}                      // handles shared-memory disposition
generateExperience(params)          → {experience, alternates} // §10, server-authoritative
remixExperience({expId, modifier})  → {experience, alternates}
createSession({experienceId})       → {sessionId}             // TRANSACTIONAL
joinSession({sessionId})            → {}
markReady({sessionId})              → {}
submitAnswer({sessionId, roundId, payload})  → {}             // TRANSACTIONAL, idempotent
advanceSession({sessionId})         → {}                      // server validates legality
endSession({sessionId, reason})     → {}                      // PAUSE/STOP path
scheduleSurprise(payload)           → {surpriseId}
createPartnerPlannedNight(payload)  → {experienceId}
openCapsule({capsuleId})            → {payload}               // server checks unlock condition
reportContent({contentId, reason})  → {}
requestDataExport()                 → {exportPath}
deleteAccount({confirm})            → {}
```

**Firestore triggers**
```
onPreferenceWrite     → recompute match candidacy, enqueue reveal (§5.3)
onBoundaryWrite       → recompute couples/{cid}/engineFilters/current
onSubmissionWrite     → if all required submissions present → write reveal doc, advance round
onFeedbackWrite       → update per-user affinity scores
onCoupleMemberChange  → recompute contentLevelEffective, engineFilters
onUserDelete (auth)   → cleanup cascade
```

**Scheduled**
```
releaseMutualRevealBatches   (every 15 min — jittered §5.3)
deliverScheduledSurprises    (every 5 min, timezone-aware)
advanceMultiDayArcs          (hourly)
expireStaleSessions          (every 10 min → ABANDONED)
purgeExpiredMedia            (daily — TTL/burn-after-view)
purgeExpiredPairingCodes     (hourly)
```

**Rules for all functions**
- App Check enforced (`enforceAppCheck: true`).
- Auth + couple-membership asserted at the top of every callable; never trust a `coupleId` from the client — resolve it from the caller's user doc.
- Rate-limit pairing, generation and report endpoints.
- Idempotency keys on submit/advance/complete to make retries safe.
- All state transitions validated against the legal transition table (§12) — reject illegal transitions with a typed error.

---

## 9. CONTENT SYSTEM

### 9.1 Authoring format
Content is authored as **JSON files in `content/packs/*.json`, committed to the repo**. Not hardcoded in Kotlin, not hand-entered in the console.

```jsonc
{
  "id": "tease_slow_burn_012",
  "version": 1,
  "title": "Three Words",
  "subtitle": "Say it slowly.",
  "body": "Tell them exactly what you've been thinking about — in three words. Then make them wait ten minutes before you explain.",
  "pack": "TEASE",
  "category": "TEASING",
  "tags": ["teasing", "anticipation", "verbal", "slowBurn"],
  "intensity": 3,
  "modes": ["TOGETHER", "APART"],
  "interactionType": "CHALLENGE",
  "durationMin": 5,
  "moods": ["naughty", "mysterious"],
  "requiredMutualPreferences": [],
  "boostedByPreferences": ["verbal_teasing", "anticipation"],
  "excludedByBoundaries": ["verbal_teasing"],
  "chapterKinds": ["TEASE", "WARM_UP"],
  "noveltyWeight": 0.6,
  "repeatCooldownDays": 21,
  "requiresMedia": null,
  "status": "published",
  "locale": "en",
  "createdAt": "...", "updatedAt": "..."
}
```

### 9.2 Distribution (this is the cost-control decision)
Content is **not** read per-item from Firestore. Instead:
1. `tools/build-content-bundle.ts` validates + compiles packs into a versioned bundle.
2. Bundle uploaded to Storage at `content/bundles/v{n}/`.
3. `contentVersion` pointer published via **Remote Config**.
4. Client downloads the bundle once, caches it in **Room**, and refreshes via **WorkManager** when `contentVersion` changes.
5. Firestore `content/{id}` holds only admin-edited deltas and disable flags.

Result: content browsing costs ~zero Firestore reads.

### 9.3 Seed content volume (Phase 8 deliverable)
Author real, usable content — this is not a stub. Minimum per pack, spread across intensity 1–5 and both modes:

| Pack | Min items |
|---|---|
| FLIRT | 60 |
| TEASE | 60 |
| CONFESSIONS | 50 |
| FANTASY_TALK | 50 |
| ROLEPLAY | 45 |
| POWER_DYNAMICS | 45 |
| SENSORY | 45 |
| SURPRISE | 40 |
| COUPLE_CHALLENGES | 50 |
| LONG_DISTANCE | 60 |
| AFTER_DARK | 60 |
| DEEP_TALK | 40 |
| **Total** | **≥ 600** |

Plus: ≥ 300 game prompts across the 7 core games, ≥ 40 roleplay scenarios, ≥ 30 secret missions, ≥ 25 Open When templates, ≥ 15 multi-day arc templates.

Quality bar: every item is specific, evocative and playable in the stated duration. No filler, no near-duplicates, no "talk about something intimate." Write in the app's voice — confident, warm, a little wicked, never clinical, never crude for its own sake.

### 9.4 Validation script (must exist and run in CI/`check`)
`tools/validate-content.ts` fails the build on: schema violations, duplicate IDs, unknown tags/moods/packs, intensity out of range, missing intensity coverage per pack, near-duplicate bodies (similarity threshold), prohibited-theme keyword hits (§3.3), items with no valid mode, or items excluded by their own required preferences.

### 9.5 Taxonomy
Taxonomy lives in `content/taxonomy.json`, versioned, shipped in the bundle, **remotely updatable**. Structure: `category → theme → preference item`, with display copy, mode applicability, intensity floor, and the boundary theme it maps to. Categories (do **not** collapse these into one list called "Kinks"):

- **MOOD** — romantic, flirty, naughty, playful, adventurous, mysterious, spontaneous, intense, slow, experimental
- **POWER & DYNAMICS** — taking the lead, giving up control, switching roles, partner decides, being surprised, directing the evening, following instructions, negotiated power play
- **TEASING** — verbal teasing, anticipation, delayed reveal, secret instructions, playful challenges, surprise choices, countdown experiences
- **SENSORY** — music, lighting, scent, texture, massage, blindfold-style sensory play, atmosphere, temperature play
- **ROLEPLAY** — strangers, characters, mystery date, costume scenario, adventure scenario, power-dynamic scenario, fantasy scenario
- **COMMUNICATION** — confessions, fantasies, secrets, flirty conversation, voice, compliments, "tell me something…", storytelling
- **EXPLORATION** — trying something new, switching roles, partner chooses, mutual curiosity, surprise, adventure, trust exercises

All descriptions are broad, consensual and non-graphic. The taxonomy is a map of *curiosities*, not a catalogue of acts.

### 9.6 Content admin
Ship a minimal admin path (no full CMS in MVP): content lifecycle `draft → published → disabled → archived`, editable via the Firebase console + the delta collection, plus `tools/` scripts for bulk import/validate/publish. Global disable must take effect without an app release.

---

## 10. THE EXPERIENCE ENGINE

The crown jewel. Deterministic, testable, boundary-safe.

### 10.1 Split of responsibility
- **Server (`functions/src/engine/`, TypeScript):** boundary intersection, hard filtering, mutual-preference weighting, affinity scoring, alternates sampling. Anything that touches private partner data.
- **Client (`:core:engine`, pure Kotlin):** chapter assembly, interaction-type variety enforcement, pacing, duration fitting, local remix within the returned alternates pool, repeat-cooldown against local history.

Both sides share the same **algorithm spec below** and both are unit-tested. Mirror the test vectors across the two implementations (a shared `engine-fixtures.json` used by Jest and JUnit).

### 10.2 Inputs
```
mode, durationMin, moods[], intensity, leadership(ME|YOU|SWITCH|RANDOM),
novelty(FAMILIAR|MIXED|SURPRISE),
mutualPreferences[], bothBoundaries (server only), contentLevelEffective,
history (last N experiences + per-item last-played), affinity scores, seed
```

### 10.3 Pipeline — order is mandatory
```
1. HARD FILTER  (server only, never skippable)
     - either partner NEVER on any tag                    → remove
     - intensity > min(contentLevel A, B)                 → remove
     - intensity > requested intensity                    → remove
     - mode mismatch                                      → remove
     - status != published, or globally disabled          → remove
     - requiredMutualPreferences not satisfied            → remove
     - repeat cooldown not elapsed                        → remove
     - media requirement unsupported by device/mode       → remove
2. SCORE (only survivors — ranking never sees excluded content)
     score = w1*mutualPreferenceMatch
           + w2*affinity(category,mood,interaction,duration,intensity)
           + w3*moodMatch
           + w4*noveltyFit
           + w5*intensityFit
           - w6*recencyPenalty
           - w7*repetitionPenalty(interactionType already used tonight)
     ASK_FIRST items carry a flag, not a penalty.
3. SAMPLE ALTERNATES (§5.5) — top-K plus weighted random tail, 3× needed size
4. ASSEMBLE (client or server) — chapter plan → fill → variety pass → duration fit
5. VALIDATE — re-run the hard filter over the final assembly. If anything fails, rebuild.
        (Belt-and-braces: the chokepoint runs twice.)
```

Weights live in Remote Config with sane defaults so they can be tuned without a release.

### 10.4 Chapter assembly
A night is **chapters**, not a card list. Chapter kinds:
```
WARM_UP · CURIOSITY · TEASE · CHALLENGE · CONTROL · CONFESSION
· ROLEPLAY · WILDCARD · YOUR_CHOICE · FINALE
```
Plan by duration:
| Duration | Chapters |
|---|---|
| 15 min | 3 |
| 30 min | 4–5 |
| 45 min | 5–6 |
| 60 min | 6–7 |
| 90 min+ | 8–10 |

Escalation curve: intensity rises across chapters and never jumps more than one level between consecutive chapters. `FINALE` is always the requested intensity, never above.

### 10.5 Variety enforcement (hard requirement)
Reject any assembly that:
- uses the same `interactionType` more than twice in a row,
- uses fewer than 4 distinct interaction types in a 6+ chapter night,
- repeats a content item within the night,
- contains more than one `YOUR_CHOICE` chapter,
- exceeds ±20% of the requested duration.

A night must mix: choice · reveal · conversation · prediction · challenge · surprise · control · memory · wildcard. Five questions in a row is a bug, not a night.

### 10.6 Determinism
Every generation takes an explicit `seed`. Same inputs + same seed + same content version = byte-identical output. This makes the engine unit-testable, reproducible in bug reports, and replayable. Store `seed` + `engineVersion` on every experience.

### 10.7 No AI in the MVP
The deterministic engine ships first: cheaper, predictable, safer, debuggable, moderatable. If AI is added later, it must (a) receive only `{mode, duration, mood, intensity, mutualPreferences, boundaries, history}` — never raw private answers, never arbitrary Firestore access; (b) run **after** the deterministic boundary layer; and (c) have its output re-validated through the same chokepoint before display. Do not build this now. Do leave the seam.

---

## 11. GAME ENGINE

### 11.1 Primitives
Implement once, in `:core:engine` + `:feature:games`, as composable interaction units:
```
Question · Choice · Prediction · Reveal · Timer · Challenge · Vote · Ranking
Reaction · SecretMission · PartnerControl · RandomSelection · Scenario
Memory · VoicePrompt · PhotoPrompt
```
Each primitive = a `sealed interface RoundPrimitive` + one renderer composable + one submit/reveal contract. **Games are declarative compositions of primitives loaded from content** — a new game must be addable without new Kotlin classes wherever possible.

### 11.2 Core games (MVP)
`Predict My Answer` · `Me or You` · `Would You Rather` · `Truth or Dare` (intensity- and preference-gated) · `Who Knows Who` · `Two Truths & A Lie` · `Couple Battle` · `Deep Talk`

### 11.3 Cross-cutting mechanics (reusable anywhere)
- **Secret Mission 👀** — one partner gets a hidden objective (steer a choice, set a mood, make them laugh, influence the next activity); revealed at the end as `MISSION REVEALED`.
- **Tonight's Director** — one partner secretly picks from the mutually approved pool; the other sees only *"Your partner is planning tonight."* Reveal at the right moment; offer role swap next session.
- **Partner Control** — a chapter whose choice belongs entirely to one partner, with a "someone is choosing…" waiting state on the other device.

---

## 12. REALTIME SESSION STATE MACHINE

### 12.1 States
```
CREATED → WAITING_FOR_PARTNER → READY → COUNTDOWN → CHAPTER_ACTIVE
  → AWAITING_SUBMISSIONS → BOTH_SUBMITTED → REVEAL → CHAPTER_COMPLETE
  → (CHAPTER_ACTIVE | COMPLETED)
Any state → PAUSED → (previous state)
Any state → ABANDONED   (timeout / explicit STOP)
```
`RECONNECTING` is a **client-only view state** — it is never persisted; the server's state is the truth.

### 12.2 Rules
- **Server-authoritative.** The client may write only: its own `ready` flag, its own `submissions/{uid}` doc, its own heartbeat, and a STOP/PAUSE request. Every other transition is performed by a Function or trigger.
- Illegal transitions are rejected with a typed error, not silently ignored.
- Transactions (§8) prevent: duplicate submissions, double completion, premature reveal, double advancement, score manipulation.
- Every submit/advance carries an idempotency key.

### 12.3 Reconnection & resilience
Handle Wi-Fi↔cellular switch, transient loss, backgrounding, **process death**, listener interruption, device sleep, partner disconnect. On resume: show `Reconnecting…`, restore from Room instantly, reattach the listener, then reconcile to the server state (server always wins). Heartbeats every 20 s; a partner silent for >90 s shows `Your partner dropped out — waiting…`; >10 min → session expires to `ABANDONED` via the scheduled function.

### 12.4 Async (no simultaneity required)
A large share of Apart mode must work with only one partner online:
```
A prepares something → A completes their part → (later) B is notified:
"Someone has been planning something… 👀" → B opens → B completes → REVEAL
```
Model this as a first-class session type (`ASYNC`), not a degraded sync session.

---

## 13. APART MODE — REQUIRED FEATURE SET

Home (`TOGETHER, APART`): 🔥 Start Tonight · 🎲 Surprise Us · 💌 Send Something · 🎧 Voice · 📸 Challenge · ⏳ Countdown · 🎁 Something Waiting · 🌙 Multi-Day

- **Remote Date Night** — guided flow: prepare → ready? → countdown → warm-up → choice → reveal → challenge → partner control → secret mission → surprise → finale. Supports synchronized and async states, voice, scheduled start, and an optional hand-off to an external video call (deep link out; do not build video).
- **Partner-Planned Night** — §2.5 #2. Optional scheduled start. Pre-start teaser: *"Something is waiting for you tonight."*
- **Scheduled Surprise** — date + time + timezone + type. Notification ladder: *"Something interesting is waiting for you tonight 👀"* → *"Almost time."* → *"Your surprise is ready."* Content never appears in the notification.
- **Multi-Day Arcs** — 1 / 3 / 7 / 14 days. Example 7-day AFTER DARK: Curiosity → Teasing → Secret → Challenge → Fantasy → Surprise → Final Night. Days unlock on schedule and only when prerequisites are met.
- **Voice** — record / play / delete / send / receive. Private Storage, short-lived authorized access, optional burn-after-listen.
- **Photo Challenges** — private, couple-scoped, never public URLs, optional expiry. Prompts like *"send something that represents your mood."*
- **I MISS YOU ❤️** — one tap → one tiny interaction (memory, compliment, voice note, question, challenge, micro-surprise, future-date idea) in under 30 seconds.
- **Open When…** — templates: you miss me · you need a laugh · you can't sleep · you want a surprise · you're thinking about me · you need attention · you want to remember us. Payload: text / voice / photo / memory / challenge / surprise.
- **Future Capsule** — locked content unlocking tomorrow / next week / anniversary / birthday / custom date / *next time we meet*.
- **Countdown** — `UNTIL WE MEET` with big typographic days/hours, and a daily unlock (memory, question, mini-challenge, surprise, future plan).
- **Send Something** — ❤️ Compliment · 👀 Tease · 💭 Question · 🎁 Surprise · 📸 Photo · 🎙 Voice · 🔥 Challenge. Must be *fast* — ≤ 2 taps to send.

---

## 14. SCREENS & NAVIGATION

### 14.1 First-run
```
Splash → Age Gate (18+) → Auth → Welcome (4 beats) → Create/Join Couple
  → Preference Discovery → Mutual Discovery → Home
```
Welcome copy beats, in order:
1. *This is your private space. Just you two.*
2. *Explore what you're both curious about.*
3. *Discover things you didn't know about each other.*
4. *Build nights around your chemistry. And make distance feel less distant.*

### 14.2 Home
Never opens with "Welcome to our couples questionnaire." It opens with **"Tonight could get interesting."**

Primary: 🔥 **BUILD TONIGHT** · 🎲 **SURPRISE US** · 👀 **DISCOVER TOGETHER** · 💌 **SEND SOMETHING**
Secondary: Mystery Night · Our Favorites · Memories · Our Curiosities · Open When · Countdown
A single prominent **TOGETHER / APART** switch drives the whole surface.

### 14.3 Together
`Games · Build Tonight · Mystery · Surprise · History`

### 14.4 Apart
`Start Tonight · Surprise · Async · Voice · Send Something · Countdown · Multi-Day · Scheduled`

### 14.5 Build Our Night (the signature input screen)
**"WHAT ARE WE IN THE MOOD FOR?"**
- **Time:** 15 · 30 · 45 · 60 · 90+ min
- **Mood:** ❤️ Romantic · 😏 Naughty · 🔥 Bold · 🎲 Unpredictable · 👀 Mysterious · 🎭 Roleplay · 💬 Talk · ✨ Experimental
- **Intensity:** 1 SOFT → 2 FLIRTY → 3 NAUGHTY → 4 BOLD → 5 WILD
- **Who leads:** Me · You · Switch · Random
- **Novelty:** Familiar · Mixed · Surprise me
→ **BUILD OUR NIGHT**

Also required: a **"We have 15 minutes"** express path that produces a valid night from one tap plus at most one choice. Nobody should configure five dials to play something short.

### 14.6 The experience player
```
Tonight  →  Chapter  →  Interaction  →  Action  →  Reaction  →  Next
```
- Show the **shape** up front (*"Tonight has 7 chapters"*) but never the contents.
- Reveal one chapter at a time. Between chapters: *"Something interesting is coming…"*
- Persistent PAUSE / STOP (§3.1). Persistent chapter progress. Never a scrollable list of everything.
- **"MAKE IT…"** after generation: 🔥 more intense · 👀 more mysterious · 😏 more teasing · 🎲 more unpredictable · ❤️ more romantic · 🎭 more roleplay · 💬 more conversational · ⚡ shorter · 🌙 slower · ✨ more adventurous. Recalculates within fixed boundaries (§10), locally from the alternates pool where possible.

### 14.7 Discovery
- **👀 MUTUAL CURIOSITY** — one card at a time, private selection, no partner attribution ever. Reveals arrive as suspenseful, **one-at-a-time** moments — never a dump of ten results.
- **SECRETLY CURIOUS** — privately flagged items; if only one picks, it stays private forever; if both pick → *"WAIT… YOU BOTH PICKED THIS 👀"*. This is the most addictive mechanic in the app — give it the best animation in the app.
- Every reveal ends with one tap: **BUILD A NIGHT AROUND THIS**.

### 14.8 Memories & library
Post-session: **NIGHT COMPLETE ❤️** → date, duration, mood, general experience type, favorite moment, optional note/media → *Save to Our Memories*.
Library: ❤️ We Love · 👀 Curious · 🔥 Want Again · 🎲 Try Something New · 📖 Our Nights · 💌 Open When · ⏳ Future Us.

### 14.9 Profile & settings
Couple: nickname, anniversary, favorite moods, favorite experiences, memories.
Personal (private): preferences, boundaries, privacy, notifications, app lock.
Settings: Account · Couple · Preferences · Boundaries · Content Level · Notifications · Privacy · App Lock · Data Export · Delete Account · Unpair · Help & Report.

### 14.10 No fake compatibility scores
Never display "92% compatible." Display truth:
> *18 mutual interests · 6 new things discovered · Your favorite mood lately: Naughty · 4 experiences you've both replayed*

---

## 15. DESIGN SYSTEM & MOTION

### 15.1 Look
Dark-first. Deep near-black backgrounds (not pure `#000`), burgundy/plum/oxblood accents, warm cream typography, subtle gradients and soft glow, editorial type (a high-contrast display face for headings, a clean humanist for body), large cinematic cards, generous negative space, restrained ornament.

**Reference feeling:** a luxury after-hours lounge. **Not:** pink gradients, hearts everywhere, cartoon flames, or an adult novelty store.

Define everything as tokens in `:core:designsystem` (color, type, spacing, elevation, radius, motion duration/easing). Material 3 as the substrate, fully re-themed — it must not read as stock M3.

### 15.2 Motion — the anticipation budget goes here
| Moment | Treatment |
|---|---|
| Mutual reveal | blurred card → slow glow bloom → reveal |
| Mystery chapter | locked card → countdown → unlock |
| Partner control | *"Someone is choosing…"* — ambient, breathing |
| Secret mission | envelope / sealed card |
| Scheduled surprise | vault, locked until the moment |
| Chapter transition | slow cross-dissolve, never a snap |

Rules: all animation respects the system **reduce-motion** setting with meaningful (non-jarring) fallbacks. Every animation must hold 60 fps on a mid-range device (test on a Pixel 6a-class profile / emulator with animations enabled). No animation blocks input for more than 400 ms. Never animate a required control out of reach.

### 15.3 Component inventory (build in Phase 2, with a gallery screen)
`CinematicCard` · `RevealCard` · `LockedCard` · `ChapterCard` · `GlowButton` · `IntensityDial` · `MoodChipRow` · `DurationPicker` · `PreferenceSwipeCard` · `BoundarySlider` · `WaitingForPartner` · `CountdownRing` · `VoiceRecorderBar` · `StopPauseBar` · `EmptyState` · `SecureScreen`.

---

## 16. NOTIFICATIONS

### 16.1 Triggers
partner invitation · partner joined · surprise created · surprise unlocked · partner completed an activity · both ready · scheduled experience starting · partner sent something · multi-day day unlocked · session expiring.

### 16.2 Privacy (mandatory)
- ❌ *"Your partner selected 'blindfold'"*
- ✅ *"Your partner left you something 👀"*

**No private content ever enters an FCM payload — not in `title`, `body`, or `data`.** Payloads carry IDs only; the app fetches content after auth. Notification channels per category, quiet hours, and three user settings: *Show notification content* / *Hide sensitive previews (default)* / *Disable notifications*.

---

## 17. ANALYTICS

### 17.1 Enforce the allowlist in code
`:core:analytics` exposes a **sealed class of events only**. There is no `log(String, Bundle)` API anywhere. If an event isn't in the sealed hierarchy, it cannot be logged. Add a lint/architecture test forbidding direct `FirebaseAnalytics` usage outside that module.

### 17.2 Allowed events
```
app_open · onboarding_complete · couple_created · partner_joined
preferences_started · preferences_completed · mutual_interest_found (count only)
experience_started · experience_completed · experience_skipped
game_started · game_completed · surprise_created · surprise_opened
apart_session_started · apart_session_completed · memory_created
subscription_started · paywall_viewed
```

### 17.3 Never send
Private preference values · private answers · boundary settings · any user-authored text · voice or photo content or paths · content item bodies · partner-identifying selections. Parameters are limited to enums, counts, durations and buckets.

---

## 18. OFFLINE, PERFORMANCE & COST

- **Offline:** cached content, cached current couple, cached recent experiences, cached in-flight session snapshot. Async actions queue in an outbox and sync via WorkManager. Realtime multiplayer requires connectivity — say so clearly rather than faking it. **Offline actions never bypass server authorization** — they replay through the same callables.
- **Listeners:** attach only to the active session, the current couple doc, and the unseen-reveal queue. Never listen to an entire collection. Every listener is lifecycle-scoped and verified released (write a leak test).
- **Reads:** paginate memories and history (page size 20). Serve content from Room. Debounce preference writes. Batch where possible. Do not write UI interactions to Firestore.
- **Targets:** cold start < 2 s to first meaningful frame on a mid-range device; no dropped frames on the reveal animations; no `LeakCanary` leaks in a full run-through; R8 enabled with a verified-shrunk release build.
- **Cost audit (Phase 22):** produce `COST.md` estimating reads/writes/function-invocations per active couple per session, and list the three biggest cost drivers with their mitigations.

---

## 19. SUBSCRIPTION

| Free | Premium |
|---|---|
| basic preference discovery | full preference library |
| limited games | advanced experiences, Mystery Nights |
| basic Build Tonight | unlimited nights |
| limited surprises | Partner Control, Secret Missions |
| basic memories | advanced long-distance + multi-day arcs |
| | advanced memories, premium content packs, advanced personalization |

Rules:
- **Never paywall inside an active experience.** If a session has started, it finishes. Gate at selection time, with the limit communicated before the user invests effort.
- Entitlement is **couple-scoped** (one subscription covers both partners) and written **server-side only**.
- Abstract behind `BillingRepository` with a fake for `dev`. Handle: purchase, restore, grace period, on-hold, cancellation, refund revocation.
- The paywall must state clearly and honestly what's included. No dark patterns, no fake countdowns, no disguised close buttons.

---

## 20. ACCESSIBILITY

Dynamic type up to 200% without clipping · TalkBack labels and semantics on every interactive element (including the reveal/lock animations, which need meaningful content descriptions) · touch targets ≥ 48 dp · WCAG AA contrast against the dark palette (verify — cream-on-plum is the risky pair) · respect reduce-motion · no information conveyed by color alone · full keyboard/switch navigability.

Accessibility must not flatten the visual design. If a token fails contrast, fix the token.

---

## 21. TESTING

### 21.1 Required coverage
- **`:core:engine` — the highest bar in the repo.** Unit tests for: boundary filtering (including every `NEVER` bypass attempt), intensity gating, mutual-preference weighting, ranking, novelty, repeat cooldown, chapter assembly, variety enforcement, duration fitting, determinism (same seed ⇒ same output), and remix-within-boundaries. Use `engine-fixtures.json` shared with the TS implementation.
- **Boundary chokepoint test:** an architectural test proving no code path selects content without passing through the chokepoint.
- **Functions:** Jest unit tests + emulator integration tests for every callable and trigger, including the transaction races (two simultaneous `acceptPairing` on the same code; two simultaneous submits; double advance).
- **Rules:** the full table in §7.3, via `@firebase/rules-unit-testing`.
- **Compose UI tests:** onboarding, age gate, pairing, preference flow, Build Tonight, experience player (including STOP), Together, Apart, settings.
- **Two-client sync test:** an emulator-backed test driving two authenticated clients through a full synchronized session, asserting state transitions and that neither client can read the other's submission.
- **Screenshot tests** for the design system gallery (light/dark, default/large font).

### 21.2 Manual acceptance script
Write `ACCEPTANCE.md` as a step-by-step two-device script covering §24, with expected results at each step. Run it yourself against the emulator before declaring done.

---

## 22. BUILD PHASES

Each phase: implement → verify → update `PROGRESS.md` → commit → report → continue.

| # | Phase | Exit criteria |
|---|---|---|
| **0** | Repo & tooling — git init, version catalog, module skeleton, Hilt, Compose, ktlint/detekt, architecture tests, `CLAUDE.md`, `PROGRESS.md`, `DECISIONS.md` | `./gradlew assembleDevDebug check` green; empty app launches |
| **1** | Firebase wiring — flavors, emulator auto-connect, App Check, Crashlytics, Performance, Remote Config defaults, typed Analytics facade, `firebase.json` | app reads/writes an emulator doc; `firebase emulators:start` documented in `HUMAN_SETUP.md` |
| **2** | Design system — tokens, theme, motion, full component inventory + gallery screen | gallery renders; screenshot tests pass; contrast verified |
| **3** | Auth + age gate + app lock | sign up / in / out / reset; attestation persisted; biometric + PIN lock works; `FLAG_SECURE` applied |
| **4** | Pairing — code, deep link, QR; transactional `acceptPairing`; unpair | two emulator users pair; rules tests C, K, M, N pass |
| **5** | Taxonomy + private preference discovery | answers owner-only; rules tests A, B pass; swipe flow feels good |
| **6** | Boundaries + content level + server `engineFilters` | boundaries stored owner-only; rules test L passes; intersection unit-tested |
| **7** | Mutual discovery + batched/jittered reveals + Secretly Curious | two-user test yields a match; non-match leaks nothing; timing jitter verified |
| **8** | Content system — packs, validator, bundle build/upload, Remote Config pointer, Room cache, **≥600 seed items** (§9.3) | validator passes; content loads and browses fully offline |
| **9** | Experience engine — server filter/score/sample + client assembly/variety | full engine test suite green on both implementations; determinism proven |
| **10** | Build Our Night + experience player + chapters + anticipation + STOP/PAUSE + check-in + feedback | a full night is generated and played end-to-end on one device |
| **11** | Game primitives + 7 core games | each game playable; no duplicated game logic (architecture test) |
| **12** | Realtime sessions — state machine, triggers, transactions, reconnection, process-death restore | two-client sync test green; rules tests D–G pass |
| **13** | Together home + Surprise Us + Mystery Night + "Make it more…" + 15-minute express | all Together entry points reach a playable session |
| **14** | Apart mode + async sessions + Partner-Planned Night + Remote Date Night | async flow completes with the partners never online simultaneously |
| **15** | Voice + photo experiences + Storage rules + TTL/burn | rules tests O, P pass; media never public |
| **16** | Scheduled surprises, countdowns, Open When, Future Capsules, multi-day arcs | scheduled function delivers on time in the emulator; rules test S passes |
| **17** | FCM + notification privacy settings | payload-inspection test proves no content in any notification |
| **18** | Memories + private library | save, browse (paginated), favorite, media attached |
| **19** | Subscription — Play Billing, server entitlement, paywall placement | fake billing entitles in `dev`; no paywall interrupts an active session |
| **20** | Settings, data export, delete account, unpair, reporting | delete cascades across Auth/Firestore/Storage/schedules; export produces a real file |
| **21** | Security hardening — full §7.3 table, function auth tests, App Check enforcement | every row of §7.3 passes; `SECURITY.md` written |
| **22** | Performance, cost, accessibility, polish | `COST.md`, a11y audit clean, no leaks, R8 release build installs and runs, `ACCEPTANCE.md` executed |

---

## 23. DELIVERABLE FILES

Beyond the app itself:
```
README.md          — what it is, how to run, architecture at a glance
CLAUDE.md          — build commands, module map, conventions, gotchas
PROGRESS.md        — live phase status (keep current)
DECISIONS.md       — every non-obvious call you made and why
ARCHITECTURE.md    — module graph, layering, data flow, engine design
HUMAN_SETUP.md     — exact console steps: Firebase project, App Check, Play Console,
                     signing, FCM, Remote Config keys, content upload
SECURITY.md        — threat model, privacy architecture (§5), residual risks, rules test matrix
CONTENT_GUIDE.md   — content schema, voice/tone guide, authoring rules, prohibited themes
TESTING.md         — how to run every test tier, including the emulator suite
ACCEPTANCE.md      — the two-device manual acceptance script
COST.md            — Firebase cost model per active couple
```

---

## 24. DEFINITION OF DONE

The MVP is complete only when **two real adults on two real devices** can:

```
Install → create accounts → pair privately → complete private preferences
→ discover mutual interests → configure boundaries → build a personalized night
→ play it together → use Surprise Us → give feedback → save a memory
→ switch to Apart → start a remote experience → synchronize across two devices
→ complete an async surprise → schedule a future experience
```

with **all** of:
- no developer intervention
- no mocked multiplayer
- no broken navigation or dead ends
- **no unauthorized data access** (every §7.3 row passes)
- **no boundary bypass** (every engine test passes)
- no critical crashes (Crashlytics clean through a full acceptance run)
- **no sensitive content in any notification**
- no client-controlled security decisions
- STOP works, instantly, from every session state

---

## 25. EXPLICIT ANTI-PATTERNS — DO NOT SHIP THESE

1. A scrollable list of all tonight's content (kills anticipation — the entire product).
2. Five questions in a row (§10.5).
3. "Shaheen selected this" — **any** attribution of a private answer to a person.
4. Ranking content before hard-filtering it.
5. Client-side `if (isOwner) hide()` as a privacy mechanism.
6. A paywall inside a running experience.
7. "92% compatible."
8. Pink hearts / novelty-store aesthetics.
9. Apart mode built as Together mode with a label swap.
10. Private content in an FCM payload or an Analytics parameter.
11. An LLM anywhere in the MVP.
12. A god ViewModel, a second Activity, or Firebase inside a Composable.
13. Ten configuration dials before a 15-minute quickie.
14. A STOP button that asks "are you sure?" three times.
15. Reporting a phase complete without running its verification.

---

## APPENDIX A — INTENSITY LADDER

| # | Name | Character |
|---|---|---|
| 1 | **SOFT** | Romantic, warm, playful. Safe for a first night. |
| 2 | **FLIRTY** | Teasing, chemistry, light provocation. |
| 3 | **NAUGHTY** | Provocative, adventurous, explicit in language. |
| 4 | **BOLD** | Experimental, power dynamics, deeper fantasy. Requires both-party consent ack. |
| 5 | **WILD** | The highest configured adult intensity. Requires both-party consent ack, and is capped by `min(contentLevel A, B)`. |

Effective intensity = `min(requested, contentLevel_A, contentLevel_B, boundaryCeiling)`. Never exceed it. Never escalate without the user's explicit action.

---

## APPENDIX B — CONTENT TAG VOCABULARY

```
romantic · flirty · naughty · teasing · mystery · roleplay · sensory
conversation · challenge · surprise · longDistance · voice · photo
partnerControl · secretMission · competitive · anticipation · slowBurn
confession · fantasy · powerPlay · trust · verbal · nonVerbal · atmosphere
```
The engine combines **tags**, not just categories — tags are what make a "more teasing, more mysterious, shorter" remix possible.

---

## APPENDIX C — VERIFICATION COMMANDS

```bash
# Android
./gradlew assembleDevDebug
./gradlew testDevDebugUnitTest
./gradlew :core:engine:test          # the crown-jewel suite
./gradlew connectedDevDebugAndroidTest
./gradlew check                       # ktlint + detekt + architecture tests + content validator
./gradlew assembleProdRelease         # R8 verification

# Firebase
firebase emulators:start
firebase emulators:exec --only firestore,auth,functions,storage "npm --prefix functions test"
npm --prefix functions run test:rules
npm --prefix functions run build

# Content
npx tsx tools/validate-content.ts
npx tsx tools/build-content-bundle.ts
```

---

## THE FEELING TO ENGINEER

When the app opens it must never say *"Welcome to our couples questionnaire."*

It says:

> **Tonight could get interesting.**
>
> 🔥 Build Tonight   🎲 Surprise Us   👀 Discover Something   💌 Send Something

And the loop it creates is:

```
"What's new?" → "Wait… we both chose that?" → "Should we try it?"
→ "Let's build tonight." → "What's next?" → "Your partner chose this."
→ "Wait, there's another chapter?" → "That was fun." → "Again?"
→ "Let's make tomorrow interesting."
```

Success is not an app full of content. Success is **a private world for two** — where they come back not because they need another question, but because they want to know what happens tonight.

**Now start at Phase 0.**
