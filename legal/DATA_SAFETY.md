# Play Data Safety — the answers

**Draft answers for Play Console → App content → Data safety.** Every row is taken from the code, so a
reviewer (or a future you) can check it: the collectors are in `:core:firebase` and `functions/src`, and
the analytics surface is the closed `AnalyticsEvent` hierarchy in `:core:analytics`. HUMAN_SETUP.md §4.2
tracks this step.

Three answers apply to everything below: data is **encrypted in transit**, users **can request
deletion in the app**, and none of it is **used for advertising or shared with third parties for
their own purposes**. There is no ad SDK in this app.

---

## 1. The top-level questions

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **Yes** — in the app (Settings → delete account), and by email |
| Is your app's data collection independently validated against a security standard? | **No** |
| Does your app have a publicly available privacy policy? | **Yes** — [URL] |
| Is any data **shared** with third parties? | **No**, in Play's sense. Google Firebase and Google Play are **processors** acting for us, not recipients using it for their own purposes |
| Does your app collect data from children? | **No** — 18+, age-gated |

---

## 2. Data types

For each: **Collected** yes/no · **Shared** yes/no · **Processed ephemerally** · **Required or
optional** · **Purposes**.

### Personal info

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Email address | Yes | No | Required | Account management |
| User IDs | Yes | No | Required | Account management, app functionality |
| Name, address, phone, race, political or religious beliefs, sexual orientation, other info | **No** | — | — | — |

> Note on "Sexual orientation": the app does **not** ask for it and does not infer it. The private
> preference answers are not orientation data and are never shared with anyone, including the partner.

### Financial info

| Type | Collected | Notes |
|---|---|---|
| Purchase history | Yes | Subscription state only, from Google Play's verification. Purposes: app functionality, account management |
| Payment info, credit score, other financial info | **No** | Payment is handled entirely by Google Play. We never see a card |

### Location, contacts, calendar, personal files, SMS, call logs, web browsing

**None collected.** No location permission, no contacts permission, no calendar, no file browsing, no
SMS or call access, no browsing history.

### Photos and videos

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Photos | Yes | No | **Optional** — only a photo the user deliberately sends | App functionality |
| Videos | **No** | — | — | — |

### Audio

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Voice or sound recordings | Yes | No | **Optional** — only a voice note the user deliberately records and sends | App functionality |
| Music files, other audio | **No** | — | — | — |

### App activity and other

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| App interactions | Yes | No | Required | Analytics, app functionality. A closed event list; no free-form data and no content |
| In-app search history | **No** | — | — | — |
| Installed apps, other user-generated content beyond the above, other actions | **No** | — | — | — |
| Other user-generated content: the user's own private answers, limits, reactions, memory notes | Yes | **No** | Required for the app to work; each item is the user's own choice to write | App functionality, personalisation. **Readable only by the user who wrote it** |

### App info and performance

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Crash logs | Yes | No | Required | Diagnostics (Firebase Crashlytics) |
| Diagnostics | Yes | No | Required | Diagnostics (Firebase Performance) |
| Other app performance data | **No** | — | — | — |

### Device or other IDs

| Type | Collected | Shared | Optional? | Purposes |
|---|---|---|---|---|
| Device or other IDs | Yes | No | Required | App functionality — an FCM token, so notifications can be delivered. Not an advertising ID; we do not request one |

---

## 3. What to say if Play asks about sensitive data

The honest description, and the one the architecture supports:

> The app holds answers about each user's own intimate preferences and limits. They are collected only
> from the user themselves, are readable by that user alone — enforced by server-side database rules,
> not by the app's screens — and are never shown to the user's partner, never sent to analytics, never
> put in a notification, and never included in another person's data export. Anything derived from both
> partners' answers is computed on the server and stored where no client can read it. All of it is
> deleted when the account is deleted.

---

## 4. Account deletion declaration (Play's separate form)

| Question | Answer |
|---|---|
| Can users request account deletion from within the app? | **Yes** — Settings → Delete account |
| Web link for account deletion requests | [URL — a page that explains the in-app route and gives an email address] |
| What is deleted | The account, its sign-in, every private answer and limit, the couple and everything under it (evenings, memories, media, surprises, plans, arcs, countdowns, feedback, sessions), both Storage prefixes |
| What is retained, and why | Safety reports, held separately and never linked back to the account for support purposes; anything the law requires us to keep. Nothing else |
| How long deletion takes | Immediately on request. The cascade is idempotent and finishes anything a transient failure left |
