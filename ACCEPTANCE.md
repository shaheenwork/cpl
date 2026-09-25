# ACCEPTANCE

The two-device script (BUILD_PROMPT.md §21.2, §24). Every step says what to do and what you should
see; a step that does not match is a bug, not a note.

Run it end to end before calling a release done. It takes about an hour on two devices, or about
forty minutes on one device and the dev script standing in for the partner (§0 below says how, and
each step marks where that differs).

---

## 0. What you need

**Two devices** (or one device and the dev script), and the emulators:

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
export PATH="/c/Users/shahe/.jdks/jbr-21.0.11/bin:$PATH"
firebase emulators:start --only auth,firestore,storage,functions --project afterhours-dev-emulator
npm --prefix functions run build      # before starting, so the callables are current
./gradlew installDevDebug             # on each device
```

With one device, `node functions/scripts/dev-couple.js <command>` plays the partner:
`seed`, `ready`, `answer`, `seen`, `present`, `leave`, `openwhen`, `deliver`, `waiting`, `plans`,
`media`, `tier`, `free`, `state`. Each step that needs the partner names the command.

Start from nothing: `adb shell pm clear com.shnapps.couple.dev` on each device, and clear the
emulator's Firestore (the UI at http://127.0.0.1:4000, or the REST delete in TESTING.md).

---

## 1. Install and first launch

| # | Do | Expect |
|---|---|---|
| 1.1 | Launch the app on device A | "BEFORE WE START" — the age gate, before anything else |
| 1.2 | Read what it says | It names what is inside, that limits are yours, and that nothing happens you have not agreed to |
| 1.3 | Tap **I'm not** | The app says goodbye and does not continue. Nothing is created |
| 1.4 | Relaunch, tap **I'm 18 or over** | The welcome, then the sign-in screen. Android asks about notifications once — allow it |

## 2. Accounts

| # | Do | Expect |
|---|---|---|
| 2.1 | **Create an account** on A with an email and password | Straight in; no email verification wall in `dev` |
| 2.2 | Create a second account on B | Same |
| 2.3 | On A, sign out and back in | The same account, the same state |
| 2.4 | On A, **Forgot your password** with A's email | It says a link was sent, and says the same for an address that does not exist (no account enumeration) |

## 3. Pairing, privately

| # | Do | Expect |
|---|---|---|
| 3.1 | A: **Pair with your partner** → a code appears | Six digits, and a countdown |
| 3.2 | B: enter A's code | B sees three symbols and "waiting" |
| 3.3 | A: the same three symbols appear | **They match.** This is the verification; if they differ, stop |
| 3.4 | A: approve | Both land on home within a second or two |
| 3.5 | B: try A's code again | Refused: a code is used once |
| 3.6 | One device: `dev-couple.js state` | One couple, two ACTIVE members |

## 4. Private preferences

| # | Do | Expect |
|---|---|---|
| 4.1 | A: **Your private curiosities** | One card at a time, five answers, and a way to say "secretly curious" |
| 4.2 | A: answer ten or so, including one **secretly curious** and one **NEVER** | Progress moves; nothing is shown about B |
| 4.3 | B: answer a set that overlaps A's on at least two items | Same |
| 4.4 | A: open **What you both chose** | Only things you both said yes to. Nothing about what either of you declined |
| 4.5 | A: look for anything that says what B answered | There is nothing. Absence is not shown as information |

## 5. Boundaries

| # | Do | Expect |
|---|---|---|
| 5.1 | A: **Your limits** | Themes, each with five levels and a private note |
| 5.2 | A: set one theme to **NEVER** and one to **ASK FIRST** | Saved as you go |
| 5.3 | B: set a different theme to **NOT TONIGHT** | Same |
| 5.4 | Either: try to see the other's limits anywhere | You cannot. There is no screen that shows them |

## 6. A night, built

| # | Do | Expect |
|---|---|---|
| 6.1 | A: **Build tonight** | Mood, length, intensity, leadership, novelty — and the ceiling is the lower of your two content levels |
| 6.2 | A: try to set intensity above that ceiling | The dial will not go past it |
| 6.3 | A: build it | A shape: how many chapters, about how long, what it rises to — and **not** what is in them |
| 6.4 | Check the chapters against §5 | Nothing from either NEVER theme. Nothing above the ceiling |
| 6.5 | A: **Make it more teasing** | The shape changes in place, still inside the ceiling |

## 7. Played together

| # | Do | Expect |
|---|---|---|
| 7.1 | A: **Begin** | If it rises to BOLD or WILD, both must say they are in — on their own phones |
| 7.2 | B: say you are in (`dev-couple.js ready`) | The night starts on A |
| 7.3 | A: open chapter 1 | One thing at a time. PAUSE and STOP on screen |
| 7.4 | A: **PAUSE** | Everything stops; B's phone says so too |
| 7.5 | A: resume, finish chapter 1 | "Something interesting is coming…" between chapters |
| 7.6 | A: **Not this one** on a chapter | A different one, still inside the ceiling |
| 7.7 | On an ask-first chapter | It asks A first, and offers to keep that theme out |
| 7.8 | A: **STOP** mid-chapter | Instant. No confirmation, no reason asked, and nothing anywhere says who stopped it |
| 7.9 | A: start again and play to the end | "How are we doing?", then private reactions, then NIGHT COMPLETE ❤️ |

## 8. A phone each

| # | Do | Expect |
|---|---|---|
| 8.1 | A: **Play a game**, choose one, pick **a phone each** | Offered only for games whose every step is for both |
| 8.2 | Both: answer the same round | Each phone waits after answering, not before |
| 8.3 | Both: the reveal | Both answers, side by side, at the same moment |
| 8.4 | B: force-stop and reopen the app mid-game | B rejoins at the round the session is on, with their own answer already in |
| 8.5 | Turn B's network off for a minute | B says the connection went; A says B dropped. Neither pretends |
| 8.6 | Turn it back on | B rejoins where the session is |

## 9. Surprise Us, and a Mystery Night

| # | Do | Expect |
|---|---|---|
| 9.1 | A: **Surprise us** | A night with no questions asked, inside the ceiling |
| 9.2 | A: **Mystery night** (premium) | Free: the premium card, saying which limit and what is included. Premium: a night that never shows its shape — not how long, not how many |
| 9.3 | A: `dev-couple.js tier` | What the couple is entitled to, and how much of the free tier is used |

## 10. Feedback and a memory

| # | Do | Expect |
|---|---|---|
| 10.1 | A: react to each chapter, **Save** | "Your reactions are yours alone" |
| 10.2 | B: look for A's reactions | Nowhere. They are private to whoever gave them |
| 10.3 | A: **Save to Our Memories**, write a title and a moment | It lands in the library |
| 10.4 | A: **Our memories** | The shape of the evening — moods, length, chapters — and your words. **Never what was in it** |
| 10.5 | A: **Love it**, then filter to **We love** | The mark sticks, and the filter shows it |

## 11. Apart

| # | Do | Expect |
|---|---|---|
| 11.1 | A: switch to **Apart** | A different surface: send something, leave something, plan something, a few days of this |
| 11.2 | A: **Send something** → record a voice note, mark it **gone after they open it** | "Nothing leaves this phone until you send it" |
| 11.3 | B: open it (`dev-couple.js media` to see it) | It plays once, then says it is gone — for both |
| 11.4 | A: check it is really gone | The record and the bytes are both gone (`dev-couple.js media`) |
| 11.5 | B: **Report this** on something A sent | Four reasons, no text box, "It goes to us, not to them" |
| 11.6 | A: look for any sign of that report | There is none, and there never will be |

## 12. Planned, waiting, and scheduled

| # | Do | Expect |
|---|---|---|
| 12.1 | A: **Plan something for them** | A: "you never see it either". B: "something is coming" |
| 12.2 | B: open it | B plays it alone; A is told it landed, never what happened |
| 12.3 | A: **Leave something for later**, due in a minute | "Something is coming" on B — the title only once delivered |
| 12.4 | Wait, or `dev-couple.js deliver` | B: "🎁 IT'S HERE". Opening shows what A wrote |
| 12.5 | A: leave an **Open when… you can't sleep** | B sees the moment, not the contents |
| 12.6 | B: pull that moment | Exactly one, the oldest kept for it |
| 12.7 | A: add a **countdown** | Both see it |
| 12.8 | A: **A few days of this** (premium) | The whole plan from the start; only today's day opens |

## 13. Notifications

| # | Do | Expect |
|---|---|---|
| 13.1 | A: **Settings → Notifications** | Three switches, and the promise: a notification never carries anything either of you wrote |
| 13.2 | Leave previews hidden; have B send something | On a real project: "Something's waiting for you." and nothing else. In `dev` there is no delivery (FCM has no emulator — HUMAN_SETUP §2.3a) |
| 13.3 | Turn previews on, repeat | "They sent you something 👀" — the kind of thing, never the thing |
| 13.4 | Android's own app settings | Four categories: pairing, surprises, your nights, voice notes and photos |

## 14. Settings, export, and the end

| # | Do | Expect |
|---|---|---|
| 14.1 | A: **Settings** | One place for everything about the account and the couple |
| 14.2 | A: **Export my data** | A file, its size, and "our copy is deleted after a week" |
| 14.3 | Read the file (`adb shell run-as … cat`) | A's own account, answers and limits, and what the couple shares. **Nothing of B's private record**, and nothing inside a surprise B has not delivered |
| 14.4 | A: **App lock** → set a PIN, background the app, return | It asks for the PIN. Screenshots of private screens are blocked |
| 14.5 | A: **Unpair** | The couple ends. A keeps their own answers and limits |
| 14.6 | A: pair again, then **Delete my account** | It says what goes — including the couple and everything in it — before the first tap, and the real button is behind a second |
| 14.7 | A: delete | Back to the beginning. Check the server: A's documents, objects and Auth user are gone; B still has their account, unpaired (`dev-couple.js state`) |

## 15. The promises, checked

| # | Check | How |
|---|---|---|
| 15.1 | No unauthorized data access | `npm --prefix firebase/tests test` — every row of §7.3 |
| 15.2 | No boundary bypass | `./gradlew :core:engine:test` and `npm --prefix functions run test:int` |
| 15.3 | No content in a notification | `npm --prefix functions run test:unit` (the payload inspection) |
| 15.4 | No client-controlled security decision | `npm --prefix functions run test:e2e` (the callable sweep) |
| 15.5 | STOP works from every state | Step 7.8, and `session.int.test.ts` |
| 15.6 | No crashes | `adb logcat -d \| grep -i "FATAL EXCEPTION"` — empty; Crashlytics clean on a real project |
| 15.7 | Accessibility | `./gradlew :core:designsystem:test` — contrast, 200% type, 48 dp, semantics |
| 15.8 | A shrunk release build runs | `./gradlew :app:assembleDevRelease`, install it, and play a night |

---

## Run log — 2026-09-25

Run on the headless AVD `afterhours_a` with the dev script as the partner, against the emulators,
on `d800c2e` plus Phase 22's changes. What follows is what was seen in **this** run, with a note
where a section was walked by the phase that built it instead — those walks are recorded step by
step in PROGRESS.md under their own phases.

- **1 Install and first launch** — walked today, twice from a cleared install: the age gate came
  first, and the notification prompt appeared once.
- **2–3 Accounts and pairing** — **not walked today.** The couple is seeded by
  `dev-couple.js seed`, which creates both accounts and pairs them through the real callables. The
  handshake itself — code, matching symbols, approval, a reused code refused — was walked on the
  device in Phase 4 and is covered by `pairing.int.test.ts` and `callables.e2e.test.ts`.
- **4–5 Preferences and limits** — **not re-walked today**; walked on the device in Phases 5 and 6.
  The privacy claims behind them are asserted by rows A and B of the §7.3 matrix and by
  `reveals.int.test.ts`.
- **6–7 Built and played** — walked today: a night's shape shown without its contents, and a
  15-minute express night played to NIGHT COMPLETE ❤️ through the check-in and private reactions.
  STOP from a live chapter was walked in Phase 10; `session.int.test.ts` holds it from every state.
- **8 A phone each** — covered by `realtime.int.test.ts` and `session.e2e.test.ts` rather than by
  hand: this run had one device, and the dev script plays the partner's callables but not a second
  phone's listeners. **Not walked on two real devices.**
- **9 Surprise Us and Mystery Night** — a free couple was refused a Mystery Night with the premium
  card; after a dev purchase the same tap built one, and dropping back to free refused a fourth
  night that week.
- **10 Feedback and a memory** — a night kept as "The Tuesday one"; the library showed
  "TALK · 15 MIN · ALL 3" and the couple's words, and the stored memory carried none of that
  night's content ids.
- **11 Apart** — a photo from the partner was reported as "This feels unsafe"; the report landed
  where neither partner can read it, and nothing about it appeared on either side.
- **12 Planned and waiting** — **not re-walked today**: a planned night, a scheduled surprise
  delivered by the sweep, an "Open when…" pulled and a three-day arc whose later days stayed shut
  were each walked on the device in Phases 14–16, with what was seen recorded there.
- **13 Notifications** — the settings screen wrote what the rules allow, and the four channels were
  on the phone from first launch. **Delivery not walked**: FCM has no emulator (HUMAN_SETUP §2.3a).
- **14 Settings, export, deletion** — an export of 606 bytes with the right contents; then a
  deletion after which the account's documents, objects and Auth user were gone, the couple with
  them, and the partner's account intact and unpaired.
- **15 The promises** — rules 121/121, functions unit 132 / int 158 / e2e 35, `./gradlew check`
  green, `:core:designsystem:test` green including contrast and the 200% goldens, and the R8
  release build (5.5 MB, down from 31.9 MB) installed, signed in and built a night with no
  keep-rule breakage.

### What this run found

- **A dead session trapped the app** (DECISIONS.md D-098). With the app signed in, the Auth emulator
  was restarted; Firebase still reported a signed-in user, every Firestore call failed
  `UNAUTHENTICATED [ INVALID_REFRESH_TOKEN ]`, and the age gate's write — queued offline and retried
  for ever — left "One moment…" on screen with no way forward and no way back. Fixed: the session is
  refreshed before anything is routed and a dead one is signed out, and the write is bounded. Checked
  again by deleting every Auth account under a signed-in app: the next cold start reached sign-in in
  8 seconds.
- **No retained objects.** LeakCanary ran through build-a-night, games and send-something, then seven
  activity recreations by rotation. It watched all seven `MainActivity` instances and reported no
  retained objects: no heap dump, no leak trace.
- **Cold start on the headless AVD**: 1.9 s and 2.4 s for the release build, 5.5 s for the debug
  build (not debuggable, plus LeakCanary installing itself). §18's target is under 2 s on a mid-range
  device; a software-rendered emulator on a 7.4 GB machine is not that device, so the release numbers
  are encouraging rather than proof. The target stays unverified until it runs on real hardware.

**Still to walk on real hardware**, and stated as such rather than ticked: section 8 on two physical
devices, notification delivery, and the Play purchase flow. Each needs something this machine does
not have — a second device, a real Firebase project, a Play account — and each is named in
PROGRESS.md and HUMAN_SETUP.md.
