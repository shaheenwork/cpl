# Chat — a build brief

A conversation between the two paired phones that can be opened **over anything**, including a
round in progress, and closed again without the game noticing.

Written against the app at commit `2c8b2d4`. It is a brief for an implementer who has the
repository open: it names files, collections and rules rather than describing a chat app in the
abstract.

---

## 0. One rule

> **The chat is a layer, not a destination.** It opens over whatever is on screen, keeps its own
> state, and when it closes the thing underneath has not moved.

Everything below follows from that, and most of the hard parts are about making it true.

---

## 1. What changes, and what does not

This reverses one line of the v2 brief — *"No separate messaging, no inbox"* (V2 rule 3) — and the
reason is concrete: a dare done apart needs proof sent and something said about it, and a round is
the wrong place to hold a conversation. The rest of rule 3 stands: **the app is still the games**,
and the chat is the thing beside them, not a second product.

Three rules that do not move, and that the implementation must be checked against:

1. **A notification never says what was said.** Data-only messages, a kind and at most a document
   id (D-088); the words come from `NotificationText`, which has no placeholders. Reuse the
   existing `THEY_SENT_SOMETHING` → *"They sent you something."* Do not add a kind that carries
   content, and do not put a message body in a push payload "just for the preview".
2. **Nothing a partner answers in a game reaches the chat.** What is answered in a round stays on
   the phone that answered it (D-069). A "share my answer to the chat" button is the one feature
   this brief forbids outright.
3. **Media keeps its existing lifecycle.** Reserve → upload → confirm, in that order; the server
   names the path; the rules allow writing only to the author of a `PENDING` record; reading asks
   for the record; deleting the record *is* the burn (D-083). None of that is rewritten — the chat
   is a new *reference* to media, not a new way of moving bytes.

---

## 2. Where the overlay lives

This is the decision the feature stands or falls on.

**It is drawn above the NavHost, in `:app`.** `CplNavHost` already composes a `NavHost` inside the
root scaffold; the chat goes in a `Box` *around* it, as a sibling drawn on top:

```kotlin
Box(modifier = modifier) {
    NavHost(navController, startDestination) { /* unchanged */ }
    ChatOverlay()          // :feature:chat, hosted here and nowhere else
}
```

Why there and not anywhere else:

- **The game must not be recomposed away.** If the chat were a route, opening it would move the
  back stack and `GameViewModel` would leave the composition — the run, the round, the answer in
  flight. Drawn above the NavHost, the route underneath is untouched and the game cannot tell.
- **A feature may not import another feature** (Konsist, §4.2). The overlay has to appear over
  `:feature:games`, `:feature:instant` and home alike, and only `:app` may depend on all of them.
- **It needs a real ViewModel**, so it cannot live in `:core:ui` the way the primitive renderers
  do. `:feature:chat` with `ChatViewModel`, hosted by `:app`.

Consequences to get right:

- **Back closes the chat before it leaves the screen.** A `BackHandler(enabled = open)` inside the
  overlay. Mind `PanicGesture` — two back presses already mean panic (M8); the chat's handler must
  not swallow the second one. Rule: chat open → first back closes the chat and the panic counter
  resets.
- **Insets.** The root is already inset including the keyboard (D-028), so the composer sits above
  the IME without any padding of its own. Do not add `imePadding()` on top of it — that was a bug
  the nav host already fixed once.
- **`SecureScreen()`.** The overlay shows private content, so it calls it like every other such
  screen. It is drawn over screens that already call it; calling it twice is harmless and leaving
  it out is not.
- **The safe word stays reachable.** M7 says Stop is on every screen that plays anything. The chat
  covers one. So the overlay carries its own **Stop**, with the same behaviour as the bar's — it
  ends the evening and closes the chat.

**How it is opened.** A small affordance in the top bar — an icon with a dot when there is
something unread — on home and on every playing screen. It is never the primary action, never
styled with the heat accent, and it does not appear at all when the couple is not paired.

---

## 3. The data

### 3.1 Messages

```
couples/{coupleId}/messages/{messageId}
  from:     string      the author's uid
  at:       timestamp   request.time, server-set
  kind:     'TEXT' | 'VOICE' | 'PHOTO'
  text:     string?     the words, or the note that came with the media
  mediaId:  string?     -> couples/{coupleId}/media/{id}, for VOICE and PHOTO
```

**Written by the client, not by a callable.** A message is a couple's own words to each other:
there is nothing to compute, nothing to authorise beyond "is this your couple", and a function
invocation per message would be latency and cost for nothing. Precedent exists — `presence/{uid}`
and `submissions/{chapter}_{uid}` are client-written under tight rules.

Rules (`firebase/firestore.rules`, beside `media/{itemId}`):

```
match /messages/{messageId} {
  allow get, list: if isActiveMember(coupleId);
  allow create: if isActiveMember(coupleId)
    && request.resource.data.from == request.auth.uid
    && request.resource.data.at == request.time
    && request.resource.data.kind in ['TEXT', 'VOICE', 'PHOTO']
    && request.resource.data.text is string
    && request.resource.data.text.size() <= 2000
    && (!('mediaId' in request.resource.data) || request.resource.data.mediaId is string);
  allow update: if false;                                   // a message is not edited
  allow delete: if isActiveMember(coupleId)
    && resource.data.from == request.auth.uid;              // your own, for both of you
}
```

Add a row to the **§7.3 matrix** in `SECURITY.md` and a named test in the
`"§7.3 — the mandatory matrix"` suite in `firebase/tests/rules.test.mjs`. At minimum: a member
reads; a non-member cannot read or write; a member cannot write `from` as the other partner; a
member cannot edit; a member cannot delete the other's message; `at` cannot be back-dated.

### 3.2 What has been read

```
couples/{coupleId}/chatState/{uid}
  lastReadAt: timestamp
```

Owner-only write, both may read — the partner seeing "read" is the point. Rules mirror
`fcmTokens`: `allow write: if request.auth.uid == uid`.

The unread badge is the count of messages after this phone's `lastReadAt`, taken from the page
already in memory. Do not add a counter document; do not run a count query per open.

### 3.3 Media

No new collection and no new Storage rules. A photo or voice note is sent exactly as
`MediaRepository.send` already sends one — `reserveMedia` → upload to the reserved path →
`confirmMedia` — and the message is written **after** `confirmMedia` returns, carrying its id.

Order matters and the failure mode has to be chosen deliberately: a confirmed media record with no
message is invisible and gets swept by `expireMedia`; a message pointing at an unconfirmed record
would render as a permanent spinner. So: **confirm first, then write the message.**

`burnAfterView` stays on the existing record and is offered on send for a photo — "once only" is
the right default for proof and the wrong one for a conversation, so it is a toggle and it is off.

---

## 4. The screen

```
┌─────────────────────────────┐
│  ← Chat            ■ Stop   │   the overlay's own bar
├─────────────────────────────┤
│                             │
│         yesterday           │   a day separator, not a timestamp per line
│   ┌───────────────────┐     │
│   │ did you do it     │     │   theirs: left
│   └───────────────────┘     │
│         ┌──────────────┐    │
│         │ [photo]      │    │   yours: right
│         │ proof 😏      │    │
│         └──────────────┘    │
│   ┌───────────────────┐     │
│   │ ▶ 0:07            │     │   a voice note plays in place
│   └───────────────────┘     │
├─────────────────────────────┤
│ 🎤  📷   [ say something ]  ▶│   the composer
└─────────────────────────────┘
```

- **Pages of fifty, newest last**, `orderBy('at', desc).limit(50)` reversed for display, "load
  older" above. The listener watches only the newest page; older pages are fetched once.
- **A failed send stays in the list, marked, with one retry.** A Firestore write queues offline and
  retries for ever, so the composer must never block on it (the `confirmAge` lesson) — the message
  appears immediately from the local write and the pending state comes from Firestore's own
  metadata.
- **A voice note is recorded by the existing recorder** (`:core:ui`'s `VoiceRecorder`, which now
  owns `RECORD_AUDIO`), capped at the recorder's existing maximum, with a level meter while it
  records and a scrub-free play button after.
- **A photo comes from the system picker or the camera.** The picker needs no permission; the
  camera contract needs none either as long as `CAMERA` stays undeclared.
- **Nothing is marked read until the overlay is actually open and that message is on screen.**

---

## 5. Notifications

One new trigger, reusing an existing kind. In `functions/src/notify/notify.ts`, send
`THEY_SENT_SOMETHING` to the partner after a message is created — which means a small
`onDocumentCreated` trigger on `couples/{cid}/messages/{id}`, because nothing else sees the write.

- Rate-limit it: at most one push per partner per two minutes, or a conversation becomes a
  notification storm. Hold the last-sent stamp on `chatState/{uid}`.
- The quiet-hours and settings check is the server's, as it already is (D-089).
- Never include `text`. `buildMessage` already refuses a reference that is not a Firestore id;
  keep it that way.

---

## 6. Cost

`COST.md` has the per-night read/write counts, and this changes them. State the new numbers there:

- A listener on the newest fifty messages, attached while the app is in the foreground **and the
  couple is paired**, detached in `onStop`. Not a listener that lives for the process.
- One write per message, one per `lastReadAt` update — debounce the latter to at most one every
  few seconds, not one per message scrolled past.
- Media is unchanged: one reserve, one upload, one confirm.

The listener is also a `ListenerReleaseTest` concern (`:core:data` counts registrations at the data
source seam) — the chat's must be in that count.

---

## 7. The work, by module

| Module | What |
|---|---|
| `:core:model` | `Message`, `MessageKind`; nothing else — no new enum in the content vocabulary |
| `:core:firebase` | `ChatRemoteDataSource`: observe the page, send, delete, read-state. The only module that may touch Firestore |
| `:core:data` | `ChatRepository` over it, `Outcome<T>` at the boundary; reuse `MediaRepository` for bytes |
| `:feature:chat` | `ChatViewModel`, `ChatOverlay`, the composer, the bubbles. One `ChatUiState` as a `StateFlow` |
| `:core:ui` | The bar affordance with the unread dot, because home and the players all show it and a feature may not import a feature |
| `:app` | Hosts `ChatOverlay()` above the `NavHost`; adds the dependency on `:feature:chat` |
| `firebase/` | The two rule blocks above, their §7.3 rows and tests |
| `functions/` | The `onMessageCreated` trigger and its rate limit; `notify` unchanged otherwise |

---

## 8. Tests

| What | Where |
|---|---|
| A member reads and writes their own; the partner cannot forge `from`; nobody else reads | `firebase/tests/rules.test.mjs`, §7.3 suite |
| A message cannot be edited, and only its author deletes it | same |
| Sending a photo confirms the media **before** the message exists | `ChatRepositoryTest` (`:core:data`) |
| An unsent message shows as pending and retries, and the composer never blocks | `ChatViewModelTest` |
| Opening the chat over a game leaves the run exactly where it was | `GameViewModelTest` — assert the run is the same instance across an overlay open/close |
| Back closes the chat and does not trigger panic | `ChatViewModelTest` + the panic test it must not break |
| The push for a message carries no text | `notify.unit.test.ts` |
| Goldens: empty, a conversation with all three kinds, a failed send, the unread dot | `ChatSnapshotTest` |

---

## 9. Two-device acceptance

Add to `ACCEPTANCE.md`:

1. Both phones paired, A in a Truth or Dare round, mid-turn.
2. A opens the chat, sends "doing it now", closes it. **The round is exactly where it was** — same
   prompt, same step, nothing re-dealt.
3. B gets one notification saying nothing about the words. B opens the chat and reads it.
4. A does the dare, takes a photo in the chat, sends it with "proof".
5. B opens it. If "once only" was set, it is gone on the second look and the message says so.
6. B records a voice note; A plays it.
7. A presses **Stop** from inside the chat: the evening ends on both phones and the chat closes.
8. Airplane mode on B; B sends three messages; they sit pending. Airplane mode off: all three land,
   in order, once.
9. Unpair: every message and every media object goes with the couple.

---

## 10. Explicitly out of scope

- **Typing indicators.** Cost, noise, and they leak attention — "they started typing and stopped"
  is a thing this app should never say.
- **Read receipts beyond the existing one.** `lastReadAt` powers the unread badge; it is not drawn
  as a tick under each message.
- **Reactions, replies, threads, editing.** A message is a thing that was said.
- **Any route to the chat.** It is a layer. If it ever gets a `Route`, the game will start losing
  its state again and nobody will know why.
- **Sharing a game answer into the chat.** D-069. The answer stays on the phone that gave it.
