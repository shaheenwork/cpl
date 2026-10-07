# COST

What one active couple costs on Firebase, counted from the code rather than guessed — reads,
writes and function invocations per session — and the three biggest drivers with what is already
done about them. Written in Phase 22 (BUILD_PROMPT.md §18).

Counts are per **couple**, and a "session" means one night played together end to end: built,
played through four chapters, reacted to, and kept as a memory. Where a number depends on how the
night is played, both shapes are given: **one phone** passed between them, and **a phone each**.

---

## 1. A night, operation by operation

### Opening the app (cold)

| Step | Reads | Writes | Invocations | Notes |
|---|---|---|---|---|
| Auth restore | 0 | 0 | 0 | The SDK's own token refresh |
| Profile listener | 1 | 0 | 0 | `users/{uid}`, one listener |
| Couple listener | 1 | 0 | 0 | `couples/{cid}`, one listener |
| Content pointer | 1 | 0 | 0 | `contentMeta/pointer` in `dev`; Remote Config in staging and prod, so **0 reads** there |
| Content deltas | 1+ | 0 | 0 | One query; only changed items come back, and browsing is served from Room |
| Reveal release | 0 | 0 | 1 | `releaseRevealsNow`, throttled in the repository |
| **Total** | **~4** | **0** | **1** | Browsing content, limits and the library costs nothing more until something is opened |

### Building a night

| Step | Reads | Writes | Invocations |
|---|---|---|---|
| `generateExperience` | 7–9 | 2–3 | 1 |
| — inside it | user, couple, `engineFilters/current`, `engineState/history`, revealed matches, both members' affinity, the rate-limit doc | the experience, the rate-limit doc, and `askFirst` when the caller has ask-first items | |
| Catalogue | 0 | 0 | 0 | Loaded once per instance and cached; a published bundle is one Storage read per version, not per call |

A surprise, an express night, a planned night and a day of an arc all go through the same builder,
so they cost the same. A free couple is capped at three a week by the gates (§19), which is also a
cost ceiling.

### Playing it

| Step | Reads | Writes | Invocations | One phone | A phone each |
|---|---|---|---|---|---|
| `createSession` | 2 | 2 | 1 | ×1 | ×1 |
| `markReady` | 2 | 1 | 1 | ×2 | ×2 |
| `advanceSession` | 2 | 1–2 | 1 | ×5 (open + 4 chapters) | ×5 |
| Submissions (client write) | 0 | 1 each | 0 | — | ×8 (2 per chapter) |
| `onSubmissionCreated` trigger | 3–4 | 1–2 | 1 per submission | — | ×8 |
| `markSeen` | 2 | 1 | 1 | ×4 | ×8 |
| Presence heartbeat | 0 | 1 per beat | 0 | ×~135 (45 min) | ×~270 |
| Session listener | 1 + 1 per change | 0 | 0 | ~15 | ~25 |
| `endSession` | 2 | 2 | 1 | ×1 | ×1 |

### Afterwards

| Step | Reads | Writes | Invocations |
|---|---|---|---|
| Feedback (client write, per chapter) | 1 each (the rule reads the night) | 1 each | 0 |
| `onFeedbackCreated` trigger | 2 each | 1–2 each | 1 each |
| `saveMemory` | 3 | 1 | 1 |
| Library, first page | 20 | 0 | 0 |

### The bill for one night

| | One phone | A phone each |
|---|---|---|
| Reads | ~70 | ~120 |
| Writes | ~150 | ~300 |
| Invocations | ~20 | ~30 |

At Firestore's published rates (reads $0.06 / 100k, writes $0.18 / 100k) and Cloud Functions'
(invocations $0.40 / million, free tier aside), a night costs a fraction of a cent: roughly
**$0.0003 on one phone and $0.0006 on two**. A couple playing twice a week for a year is about
**$0.06 of Firestore** plus function time. The cost of this app is not its database.

---

## 2. The three biggest drivers

### 1. The presence heartbeat — half of all writes

`keepPresent` writes `sessions/{sid}/presence/{uid}` every **20 seconds** while a session is on
screen. On two phones for 45 minutes that is ~270 writes, more than every other write in the night
put together.

What already holds it down:
- It beats only while a session screen is collecting it (`ListenerReleaseTest` proves it stops with
  the screen), and only for `TWO_DEVICE` sessions, where knowing the other phone dropped is the
  point.
- It writes one field — a timestamp — to a document nothing queries.
- The expiry sweep prefers the session's own `updatedAt` and reads presence only for sessions that
  already look stale, so the heartbeat is not read back on a schedule.

If it ever needs to be cheaper: widen the interval with the session's age (20 s for the first
minute, then 60 s), or drop it to a "last seen" field on the session document that `advanceSession`
already writes.

### 2. Scheduled sweeps — the floor under an idle project

Five schedules run whether or not anyone is using the app: reveals every 15 minutes, surprises
every 5, sessions every 5, media hourly, entitlements every 6 hours. Each is one invocation plus a
collection-group query.

What already holds it down:
- Every sweep is a **bounded query**, not a scan: `where('state', '==', …)`, `where('expiresAt',
  '<=', now)`, and the session sweep also bounds the window to 30 days.
- `maxInstances: 1` on each, so a slow run cannot fan out.
- A query that matches nothing costs one read, and most runs match nothing.

At ~600 invocations a day the whole schedule set sits inside the free tier, and its Firestore cost
is dominated by whatever it actually finds.

### 3. Listeners on a live session — reads that arrive by themselves

A session document changes on every transition, and both phones hold a listener on it, so one night
is ~15–25 reads per phone from changes alone, plus the reveal documents.

What already holds it down:
- Listeners are attached to **the active session, the couple document and the reveal queue only**
  (§18) — never to a collection. `ListenerReleaseTest` proves each one lasts exactly as long as its
  collector.
- Content is served from Room, so browsing the catalogue is free.
- The library is paged at twenty, and nothing listens to it.
- Nothing in the UI is written to Firestore: no taps, no screen views, no progress pings.

---

### 4. The chat — the only listener that is not about a night

The conversation (CHAT_PROMPT.md) adds a second standing listener for a paired couple:

| Operation | Reads | Writes |
|---|---|---|
| Opening the chat | 50 (the newest page, once) + 2 (`chatState`) | 1 (`lastReadAt`) |
| A message arriving | 1 | — |
| Saying something | — | 1 |
| A photo or voice note | — | 1 reserve + 1 confirm + 1 message (the upload is Storage, not Firestore) |
| The notification | 2 (couple, `chatState`) | 1 (`notifiedAt`), at most one per two minutes |

Three things keep it small:

- **The listener is on the newest fifty and nothing else.** Older pages are a one-off fetch when
  somebody scrolls, not a widening window.
- **It only exists for a paired couple**, and `ChatRepository.chat` emits an empty conversation
  with no listener at all until there is one. A phone that never pairs costs nothing here.
- **One notification per two minutes, however much is said.** A conversation is not twenty events.

What it is *not* bounded by is how much they talk, and that is the honest risk: a long evening of
messages is a read each on the other phone. At two people and a free tier's 50k daily reads, that
is not a number worth defending against.

## 3. What would change these numbers

- **Media.** A voice note is one Storage write, one read when it is opened, and a delete. Bytes
  dominate: a 20 MB cap per photo and 20 MB per voice note means storage and egress, not
  operations, are the cost of a couple who send a lot. The burn and the hourly sweep keep the
  stored total from growing without limit.
- **The content bundle.** One Storage read per published version per server instance, and one per
  device per version. Publishing content more often costs more than any couple does.
- **Push.** Free at this scale; the cost is the tokens' upkeep, which is one delete when a token
  goes stale.
- **A couple who never subscribe** are capped by the free gates at three built nights a week, which
  bounds the expensive part (building) without touching the cheap part (playing what they have).

---

## 4. How these were counted

By reading the code, not by instrumenting a run: every callable's reads and writes are visible in
`functions/src/`, every listener in `:core:firebase`, and the heartbeat interval in
`DefaultGameRepository`. The per-night totals assume four chapters, a 45-minute session, feedback
on every chapter, and one memory kept. Where the code can take either of two paths — one phone or
two — both are counted rather than averaged.
