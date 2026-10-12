# SECURITY

What this app protects, how, where each promise is enforced, and what it does **not** protect.
Written in Phase 21 (BUILD_PROMPT.md §22); the matrix at the end is the one in §7.3, row by row,
with the test that holds it.

The governing rule is §7.1: **client-side hiding is not security**. If a rule or a callable does
not enforce something, it is not enforced — a screen that declines to show a thing is a courtesy,
not a boundary.

---

## 1. Who this defends against

In order of how much the design is shaped by them:

1. **A partner with the phone in their hand.** The most likely adversary by far, and the one the
   privacy architecture is built around: they know the passwords, they share the room, and they
   have every reason to be curious about what the other one answered. Everything in §5 of the
   brief exists because of this person.
2. **A partner using their own phone, honestly.** Not an attacker, but the one whose curiosity the
   data model must not accidentally satisfy — by inference, by timing, by absence.
3. **Someone else who picks the phone up.** A flatmate, a guest, a child, a repair shop.
4. **A signed-in stranger.** Another account on the same project, poking at documents and paths
   that are not theirs.
5. **An unauthenticated caller on the network.** Anyone who can reach the callables and the rules.
6. **Us.** What the operators of the app can see is deliberately limited, because the private
   parts of this data set should not be readable by a support engineer having a bad day.

Explicitly **not** in the threat model: a rooted device under an attacker's control, a compromised
Google account, or a party with the couple's Firebase credentials. Nothing here survives those, and
claiming otherwise would be dishonest.

---

## 2. What each promise rests on

| Promise | Where it lives | What holds it |
|---|---|---|
| A partner's private answers are never readable by the other | `users/{uid}/preferences/*`, `users/{uid}/boundaries/*` | Firestore rules: `isSelf(uid)` and no rule anywhere grants a partner read (matrix A, B) |
| A non-match reveals nothing | `couples/{cid}/mutualPreferences/*` | The server materialises **positives only**; a client cannot create or delete one (matrix R) |
| The moment of an answer reveals nothing | the reveal queue | Batched with jitter by a scheduled function; the queue is server-only, so a client cannot date an answer |
| The combined boundary set never reaches a device | `couples/{cid}/engineFilters/*` | Rules deny it to **both** members, and only the server reads it (matrix L) |
| A night is filtered before it is assembled | `functions/src/experience/generate.ts` | The chokepoint, plus an architecture test proving no other path selects content |
| A phone cannot assemble from a list of its own | `PermittedPool.fromServer` | Konsist rule in `:architecture`; the server sends a bounded sampled pool, never the permitted set |
| What each partner answers in a session stays theirs | `sessions/{sid}/submissions/{chapter}_{uid}` | `allow read: if false` for everyone, forever; the reveal document is the only shared copy (matrix D, E, F, G) |
| A surprise is unreadable until it is delivered | `surprises/{id}/payload/content` | Rule: its author, or anyone once the **server** wrote `deliveredAt` (matrix S) |
| Media is never public, and a burn really burns | Storage `couples/{cid}/...` | Storage rules keyed on the media document; deleting the document denies the path (matrix O, P) |
| A notification never carries content | `functions/src/notify/notify.ts` | Data-only payloads of ids and enums; `buildMessage` refuses anything that is not a document id, and the payload-inspection test proves it for every kind |
| A memory cannot be used to read a night | `couples/{cid}/memories/*` | The server writes them and never puts a content id in one |
| Entitlement is not a client decision | `users/{uid}.entitlement` | Server-written only; rules deny the field to every client (matrix I) |
| An export is one person's own record | Storage `users/{uid}/exports/*` | Written by the server, readable by that user alone, writable by no client |
| Deleting an account really deletes | `deleteAccount` | Auth + Firestore + Storage + the couple, retried and idempotent (D-093, D-094) |
| Identity comes from the token | every callable | `callerUid(request)` before anything else; a uid or coupleId in a payload is never trusted |
| Only our app may call the server | every callable | App Check (`enforceAppCheck`) outside the Emulator Suite, Play Integrity in staging and prod |
| Someone holding the phone cannot browse the app | app lock | PIN or biometric, `FLAG_SECURE` on every private screen |

---

## 3. How the layers fit together

**Rules are the floor.** Every privacy claim above has a rules test, because rules are the only
thing that holds when a client is not ours. `firebase/tests/rules.test.mjs` (121 tests) and
`firebase/tests/storage.test.mjs` are run against the emulator.

**Callables are the only writers of anything that matters.** Sessions move through a transition
table inside transactions; couples are created and ended by functions; entitlement, reveals,
memories, media records, arcs and surprise deliveries are all server-written. A client writes
exactly five things: its own profile fields, its own private answers and limits, its own
submission for the chapter it is on, its own presence heartbeat, and a countdown.

**The chokepoint is the boundary.** No night, game or day of an arc is assembled without passing
`hardFilter` first; an architecture test reads the sources and fails the build if another path
appears. `:core:engine` cannot filter — it only ever assembles from what the server released.

**Nothing private is logged.** Analytics has no free-form API: if an event is not in the
`AnalyticsEvent` sealed hierarchy it cannot be logged, and a Konsist rule keeps `FirebaseAnalytics`
out of every module but one. No preference answer, boundary, session answer, memory, surprise or
media note is an analytics parameter or a log line.

**Rate limits** sit on pairing, generation, surprises, media, reports, exports and purchase
verification — the endpoints where repetition is the attack.

---

## 4. Residual risks, honestly

1. **The sampled alternates pool leaks statistically** (§5.5). A determined user who calls
   `generateExperience` many times can build a picture of what never appears, and infer something
   about their partner's limits. The pool is re-sampled per call, bounded at roughly 3× what a
   night needs, and never the permitted set; cooldowns and rate limits make the attack slow. It is
   not closed, and it cannot be while the device does local remixes at all. The mitigation for the
   person this actually threatens — a partner with the phone — is the app lock.
2. **A partner who knows the passwords can sign in as the other person.** Nothing in an app can
   prevent that. App lock, `FLAG_SECURE` and per-account private paths reduce what a shared or
   borrowed phone gives away; they do not defend against someone who *is* the other account.
3. **`FLAG_SECURE` blocks screenshots and the recents preview, not a camera.** Someone in the room
   can photograph a screen.
4. **Notification content is a category, not nothing.** With previews turned on, a lock screen can
   say "They left you something 👀". That is by design and is the user's choice; the default hides
   even that. What no setting can produce is a notification carrying a word either of them wrote.
5. **Push delivery is unverified.** FCM has no emulator, so delivery has been tested only in the
   sense that the payloads, the settings gate and the phone's rendering are tested. The first real
   message will be sent from a real project (HUMAN_SETUP.md §2.3a).
6. **Play Billing is unverified.** There is no Play account; `PlayBillingClient` has not been
   compiled or run (D-091). Entitlement itself is server-decided and tested with a fake store, so
   the risk is a broken purchase flow rather than a forged entitlement.
7. **App Check is off in `dev`** and uses the debug provider in debug builds. Enforcement is on
   everywhere else, but a debug build's token is a shared secret in the emulator's world.
8. **Deletion can leave unreachable data behind** if a step fails three times (D-094). The account
   and its Auth user go regardless; what is left belongs to a couple that no longer exists and is
   reachable by nobody, and the next call removes it. It is not a leak, but it is not tidy either.
9. **Moderation is one-way.** A report reaches a collection no client can read; there is no
   in-app follow-up, and no tooling yet on the other side of it.
10. **The content bundle ships in the APK.** Anyone can read every item this app might ever show.
    That is not private data — what is private is which items a given couple sees, which never
    leaves the server except as the night itself.

---

## 5. The §7.3 matrix

Every row, and where it is asserted. `firebase/tests/rules.test.mjs` holds a suite named
**"§7.3 — the mandatory matrix"** with one test per row, so this table maps one-to-one onto test
names; the deeper coverage lives in the per-collection suites above it.

| # | Scenario | Expected | Where |
|---|---|---|---|
| A | A reads B's `users/{B}/preferences/*` | DENIED | matrix A; `private, owner-only for life` |
| B | A reads B's `users/{B}/boundaries/*` | DENIED | matrix B; `private, owner-only for life` |
| C | A writes a different `coupleId` on their user doc | DENIED | matrix C; `users/{uid}` |
| D | A writes B's submission | DENIED | matrix D; submissions suite |
| E | A reads B's submission before the reveal | DENIED | matrix E; submissions suite |
| F | A reads B's submission after the reveal | DENIED | matrix F; submissions suite |
| G | A sets `round.state = REVEAL` from a client | DENIED | matrix G; submissions suite |
| H | A writes `couples/{cid}.contentLevelEffective` | DENIED | matrix H; `couples/{coupleId}` |
| I | A writes `users/{A}.entitlement` | DENIED | matrix I; `users/{uid}` |
| J | Unauthenticated read of any `couples/**` | DENIED | matrix J; `default deny` |
| K | A third user reads `couples/{cid}/**` | DENIED | matrix K; `couples/{coupleId}` |
| L | Any client read of `engineFilters/**` | DENIED | matrix L; server-only collections |
| M | A joins a couple that already has two members | DENIED | matrix M; `couples/{coupleId}` |
| N | A reuses an expired or consumed pairing code | DENIED | matrix N; `pairing.int.test.ts` for the callable's own refusal |
| O | A reads a Storage object from another couple | DENIED | `storage.test.mjs` row O |
| P | A reads a deleted media path | DENIED | `storage.test.mjs` row P |
| Q | A writes to `reports/**` | DENIED | matrix Q; server-only collections |
| R | A creates a `mutualPreferences` doc | DENIED | matrix R; mutual discovery suite |
| S | A reads a surprise payload before delivery | DENIED | matrix S; experiences suite row S |
| T | Both members read and write their own permitted paths | ALLOWED | matrix T |
| V | A writes B's `packs` doc, a level that is not a pack, a forged time, or deletes one | DENIED; A writing their own pack level, and B reading it, ALLOWED | matrix V (D-149) |
| U | A writes a message as B, back-dates one, or edits one after it was read | DENIED | chat suite |
| V | A marks the chat read on B's behalf | DENIED | chat suite |

**The server's own doors** are covered by `security.e2e.test.ts`, which reads the callable surface
out of `index.ts` so a callable added later is tested the moment it exists: every one of them
refuses a caller with no token and a caller with a forged one, says nothing about itself while
refusing, is declared with the shared options (App Check included), and takes identity only from
`callerUid`. That suite found two real flaws on its first run — `respondToPairing` and
`unpairCouple` validated their payloads before checking who was calling — which is exactly what it
is for.

---

## 6. Running it

```bash
firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator

npm --prefix firebase/tests test          # rules + storage rules, including the §7.3 matrix
npm --prefix functions run test:unit      # payload inspection, entitlement, engine
npm --prefix functions run test:int       # every callable against the emulator
npm --prefix functions run test:e2e       # over HTTP, including the auth sweep
./gradlew check                           # architecture rules, Konsist, the content gate
```

TESTING.md says what each tier covers and why.
