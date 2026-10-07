# Two phones, one round — the mirrored turn

A build brief. Everything in it is about one sentence:

> **Whatever the round is, both phones show it. Only the phone whose turn it is can act on it.**

Written against the app as it stands at commit `6fb5e88`. Sections marked **DONE** are already in
the build and are here so the brief reads as one whole; everything else is the work.

---

## 1. What a couple should see

Truth or Dare, Alex's phone and Sam's phone, paired, in different rooms.

| | Alex's phone (whose turn it is) | Sam's phone (watching) |
|---|---|---|
| Round opens | **"Truth or dare?"** with two buttons | **"Alex's turn."** — *They're choosing.* |
| Alex picks Dare | The dare, and **Done** / **Not this one** | The same dare, and **no buttons** — *Alex is doing it now.* |
| Alex taps Done | Round 2 opens; now it is Sam's turn | Round 2 opens; now it is Sam's turn |
| Round 2 | **"Sam's turn."** — *They're choosing.* | **"Truth or dare?"** with two buttons |

Rules that fall out of that table, and are the acceptance criteria:

1. **The task is on both screens, at the same time.** Not a summary afterwards.
2. **Exactly one phone has the buttons** — the one whose turn it is. The other has nothing to press
   except **Pause** and **Stop**, which are on every playing screen and always will be.
3. **Both phones say whose turn it is**, by name when the couple gave names, and never ambiguously
   ("you" on both phones was the original bug, D-130).
4. **The round advances on both phones at once**, driven by the active phone finishing it. Nobody
   taps "next" on the watching phone.
5. **Nothing a partner answers in secret is ever mirrored.** Would You Rather, Fantasy Match, Win It
   and Hot or Not keep their answers hidden until both are in; only an *open* round is mirrored.

---

## 2. Why it does not work yet

Two blockers, neither of them in the Kotlin that was just written.

### 2.1 The server half is not deployed

The reveal rule changed on the server: a round is now revealed when **everyone it asked** has
answered, rather than when both have. Until the functions are deployed, `copl-staging` still runs
the old rule, so a turn-based round never completes and the second phone never learns anything.

```bash
npm --prefix functions run build
firebase deploy --only functions --project copl-staging
```

`createSession` also writes a new field, `answeredBy`, onto the session. Sessions created before the
deploy do not have it and behave as they always did (they wait for both), so **start a fresh game**
after deploying rather than resuming one.

### 2.2 The session is still being created as `ONE_DEVICE`

`GameStages.forSession` sends a phone to the "elsewhere" screen exactly when
`session.kind != TWO_DEVICE && !isHost` — which is what the second phone is showing. The session's
kind is decided in `HomeViewModel.play()`, and today it is `TWO_DEVICE` only when the home screen's
**Together / Apart** toggle is set to **Apart**.

So the game was started in Together mode, which means "one phone, passed between us" — and that is
exactly what it did.

That is a bad way for this to be decided, and §3 replaces it.

---

## 3. Deciding how a game is played — **DONE, and not the way this brief first said**

**What this brief proposed:** pairing decides it outright, with a "Just this phone" escape on the
game's first screen.

**What was built instead, and why.** The escape hatch cannot work as described: ending a session
marks its experience `ABANDONED`, so the same game cannot be reopened in the other mode — "just
this phone" would have meant throwing the dealt game away and starting another. And the toggle is
not wrong, it was only unexplained and defaulted the wrong way: Together/Apart is exactly the
question of whether they are in the same room, which is the thing the app cannot know.

So:

- **Pairing leaves the app in Apart.** `PairingViewModel` sets `Mode.APART` the moment a couple
  exists. Pairing's only purpose is playing on two phones; starting there is what the couple just
  asked for, and it is the default they were never given.
- **The toggle says what it does to a game**, under it, in a line: *"A phone each. Both of you see
  every round."* / *"One phone, passed between you."* Two words with no consequence attached were
  the whole of the confusion — nothing on home said that Together meant the second phone would be
  shut out.
- `HomeViewModel.play()` keeps reading the mode. The same-room evening is one tap, not a lost game.

## 4. The round, mirrored

### 4.1 What the watching phone is shown — **DONE**

`GameMoment.Waiting(round, seat, watching: Watched?)` already carries the prompt in play once the
reveal has arrived, and `WatchingThem` already draws it as a card with no buttons. **DONE.**

What is missing is the *first half* of the turn — before the active player has chosen, the watching
phone falls through to `WaitingOnThem` and says "Over to them.", which is the old two-phone copy for
a private round and reads as a dead end.

**Change** `RoundReading.watching()` so it returns something before the answer lands, and
`WatchingThem` so it draws both states:

```kotlin
// what the other seat is on right now, in an open round
data class Watched(
    val kind: PromptKind?,     // TRUTH / DARE, once chosen
    val text: String?,         // the prompt, once chosen
    val choosing: Boolean,     // true while they are still picking
)
```

Built. The watching phone shows **"Alex is choosing."** through the first half of the turn and the
dare itself through the second, with nothing to press in either.

- Open round, nothing answered yet → `Watched(kind = null, text = null, choosing = true)` →
  **"Alex's turn." / *They're choosing: truth or dare.*"**
- Open round, answered → what it does today.
- Private round (any step `isPrivate`) → `null`, and the existing "Over to them." stands. That is
  the rule that keeps Would You Rather honest, and it must not be relaxed.

### 4.2 Who may press anything — **DONE**

The interpreter already decides this and the UI cannot get it wrong: on a phone with a seat
(`GameRun.phone`), `ownSlots` only ever produces `Slot.Take` for that seat's own steps. The watching
phone's moment is `Waiting`, which has no submit path at all. Nothing to add — but the acceptance
script below checks it, because it is the promise the whole feature rests on.

### 4.3 Moving the round on — **DONE**

- The active phone finishes the round and calls `finishRound`, which advances the session.
- `onWatcherFollows` moves the watching phone to the session's new chapter.
- `nextRound()` no longer waits for `CHAPTER_COMPLETE` on a round that asked only one of them.

### 4.4 Whose turn it is

Seat 0 is the phone that opened the session; turns alternate by chapter. Both phones and the server
compute it the same way from the chapter number, so nobody has to be told. **DONE** — but note the
consequence for §3: whoever taps the game first is seat 0 and therefore goes first.

---

## 5. The protocol, as it now stands — **DONE**

For reference while reading the above.

```
Alex picks DARE
  → phone writes sessions/{sid}/submissions/{chapter}_{alexUid}   (as soon as it has an answer)
  → applySubmission: expectedAnswerers(chapter) = [alex]  (answeredBy = ACTIVE)
      → everyone expected has answered → write rounds/{chapter} → state REVEAL
  → Sam's phone reads the reveal, feeds it into its own run, shows the dare
Alex taps Done
  → finishRound → ADVANCE → chapter + 1 → both phones follow
```

`answeredBy` is `ACTIVE` when only one seat is asked each round, `BOTH` otherwise, and is read off
the game's steps when the experience is built (`functions/src/session/answerers.ts`).

---

## 6. Tests

| What | Where |
|---|---|
| The watching phone shows "choosing", then the dare | `TwoPhoneRunTest` (`:core:engine`) |
| A private round shows nothing at all | `TwoPhoneRunTest` — exists, must keep passing |
| Pairing alone makes a `TWO_DEVICE` session | `HomeViewModelTest` (`:feature:home`) |
| The watching phone has no submit path | `GameViewModelTest` — submit on a `Waiting` moment is a no-op |
| Both goldens: choosing, and watching the dare | `GameSnapshotTest` |
| One-sided reveal | `session.unit.test.ts` — exists |
| A chapter that asks both still waits for both | `session.unit.test.ts` — exists |

---

## 7. Two-device acceptance

Deploy first (§2.1). Then, on two paired phones:

1. Both open the app. Phone A taps **Truth or Dare**.
2. **A:** "Truth or dare?" with two buttons. **B:** "A's turn — they're choosing."
3. A picks **Dare**. **Both** show the same dare text. Only A has **Done**.
4. A taps **Done**. **Both** move to round 2. Now **B** has the question and **A** is watching.
5. Repeat to the end. At no point do both phones have a button for the same round.
6. Press **Stop** on B mid-round: the evening ends on both.
7. Kill B's app mid-round and reopen it: it rejoins at the round the session is on, still watching.
8. Start **Would You Rather**: both answer in secret, neither sees the other's answer before the
   reveal. This is the test that the mirroring did not leak anything.

Record the run in `ACCEPTANCE.md`.
