# Privacy Policy — Afterhours

**Draft. Not legal advice, and not yet reviewed by a lawyer.** It is written from the app's actual
architecture (`SECURITY.md`, `ARCHITECTURE.md`), so every claim below is one the code already
enforces and a reviewer can check. Fill in the bracketed details, have it reviewed, and publish it at
a stable URL before the first release — Play requires the URL, and HUMAN_SETUP.md §4.1 tracks it.

**Last updated:** [date] · **Who we are:** [legal entity, address] · **Contact:** [privacy@domain]

---

## The short version

Afterhours is a private app for two adults in a relationship. It works because each of you can answer
honestly without the other seeing your answers.

- **Your private answers are yours.** What you say about your own curiosities and your own limits is
  readable by you alone. Your partner cannot see them. We do not show them to your partner in any
  form, at any time, and there is no screen anywhere in the app that displays them.
- **Only agreement is shared.** When you have both said yes to the same thing, you both see that. A
  no, a maybe, or no answer at all produces nothing your partner can see — not an empty state, not a
  hint, not a change in timing.
- **Nothing you write or record is ever put in a notification.** Notifications carry a category and
  an identifier. They never carry a word either of you wrote.
- **Nothing private goes to analytics.** Analytics can only record events from a fixed list; there is
  no way for the app to send free-form data, by design.
- **Deleting your account deletes it,** including everything the two of you made together.
- **We are 18+.** You confirm your age before the app will do anything else.

---

## 1. What we collect

### You give us

| Data | Why | Who can see it |
|---|---|---|
| Email address and password | To sign you in. The password is held by Google Firebase Authentication and never by us in readable form | You |
| Your age confirmation | Legal requirement for adult content | You, and us as a record |
| Your private preference answers | To find what you and your partner both want | **You only** |
| Your private limits and notes | To keep the app inside them | **You only** |
| Your reactions after an evening | To improve what the app suggests to you | **You only** |
| Voice notes and photos you send | To send them to your partner | You and your partner. Deleted when you say, and swept automatically |
| Your own words in a saved memory | So you can keep it | You and your partner |
| Reports you make | Safety | Us only. Your partner is never told |

### The app produces

| Data | Why |
|---|---|
| Which evenings were built and played, and their shape — length, how many chapters, mood | So the app does not repeat itself, and so you can see your own history |
| A pairing record for the two of you | So the app knows you are a couple |
| Your device's notification token | To deliver notifications you asked for |
| Your time zone | So we never notify you in the middle of your night |
| Subscription status | To know what you are entitled to. Purchases are verified with Google Play; we never see your card |
| Diagnostics and crash reports | To fix crashes (Firebase Crashlytics and Performance) |
| A fixed list of product events | To know which features are used at all (see §4) |

### We do not collect

Your name, your address, your contacts, your photos beyond the ones you deliberately send, your
location, your advertising identifier, or anything from other apps. We do not sell data. We do not use
your data to train models. There is no advertising in Afterhours and no third-party ad SDK in it.

---

## 2. What your partner can and cannot see

This is the part the app is built around, so it is stated exactly.

**They can see:** that you are paired; things you have **both** said yes to; the shape of an evening
you built or played together; what you send them on purpose; a memory you saved; a countdown you
added; that something is waiting for them.

**They cannot see:** any individual answer you gave about your curiosities; any limit you set or any
note on it; your reactions after an evening; whether you declined something; whether you answered at
all; the contents of a surprise before it is delivered; anything you reported.

This is enforced on the server and in database rules, not in the app's screens. A modified app, a
script, or someone reading the network cannot get around it. The one thing no software can defend
against is someone who knows your password and signs in as you — which is why the app has its own PIN
or fingerprint lock, and why we suggest using it.

---

## 3. Where your data lives, and for how long

Data is held by Google Firebase (Authentication, Cloud Firestore, Cloud Storage, Cloud Functions,
Cloud Messaging, Crashlytics, Performance Monitoring) in [region]. Google processes it on our behalf as
a data processor under their terms.

| Data | Kept until |
|---|---|
| Your account and your private answers | You delete your account |
| Voice notes and photos | Their expiry, the moment they are opened if you chose that, or when either of you deletes them — whichever comes first |
| Undelivered surprises | Delivered, or you delete them |
| Evenings and memories | You delete them, or you delete your account |
| An export you requested | 7 days, then it is deleted automatically |
| Crash reports and analytics | Per Google's retention for those products |
| A report you made | Kept while it is needed for safety, separately from your account |

---

## 4. Analytics, exactly

Analytics events are a closed list in the app's source. Each event carries at most a count, a
duration, an intensity number, a mode, or a game identifier. No event carries a preference answer, a
limit, a reaction, a memory, a message, a photo, a voice note, or any text either of you wrote — and
the app has no API that could send one, which is checked by a test rather than by a promise.

---

## 5. Notifications

Notifications are data-only: a category, a channel, a timestamp, and at most one identifier. Your
device turns that into wording from a fixed table inside the app. A notification can say something has
arrived; it can never say what. Whether a notification is sent at all, and whether it is the middle of
your night, is decided from your own settings and your time zone. You can turn any category off, and
you can turn them all off.

---

## 6. Your choices and your rights

- **See your data:** Settings → export. The server writes a file of your own documents plus what the
  two of you share, readable only by you.
- **Delete your data:** Settings → delete account. This ends the couple and removes everything under
  it, both partners' shared content, everything under your account, and finally your sign-in itself.
  Your partner keeps their own account and their own private answers; they are simply not in a couple
  any more. If you only want to leave the couple, unpair instead.
- **Correct your data:** change any of it in the app at any time.
- **Withdraw consent:** turn off notifications, change your limits, or delete your account.
- **Under GDPR/UK GDPR** you also have the right to object, to restrict processing, to portability
  (the export), and to complain to your supervisory authority — [name the lead authority]. Our legal
  bases are: performance of a contract (running the app), consent (notifications, adult content),
  legitimate interests (safety, diagnostics, preventing abuse), and legal obligation (age assurance,
  responding to lawful requests).
- **Under CCPA/CPRA:** we do not sell or share personal information as those terms are defined, and we
  do not use sensitive personal information for anything but providing the app.

To exercise any of these, use the app or write to [privacy@domain]. We reply within [30] days.

---

## 7. Children

Afterhours is for adults only. You confirm you are 18 or over before the app will do anything, and the
record of that confirmation is kept. We do not knowingly hold data about anyone under 18; if we learn
of an account belonging to someone under 18 we delete it. To report one, write to [privacy@domain].

---

## 8. Security

Passwords are handled by Google, not by us. Private answers are readable only by their owner, enforced
by server-side database rules. Anything derived from both partners' private data is stored where no
device can read it and is used only on the server. Private screens are marked so Android blocks
screenshots and the recent-apps preview. Only our own app can call our servers (Play Integrity /
Firebase App Check). The app can be locked with a PIN or your fingerprint.

No system is perfect. `SECURITY.md` in the source lists what this design does **not** protect against,
and we would rather say so than imply otherwise.

---

## 9. Changes

If we change this policy in a way that affects what we do with your data, we will tell you in the app
before it takes effect. The date at the top always says when it last changed.
