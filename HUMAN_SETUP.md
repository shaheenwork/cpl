# HUMAN_SETUP

Things only a human can do — console clicks, accounts, keys. Everything else is
automated. Items are listed in the order they become blocking.

Status legend: ⬜ not done · ✅ done · ⏳ in progress

---

## 1. Local toolchain

### ⬜ 1.1 Point Android Studio's Gradle JDK at JDK 21

The Gradle daemon must run on **JDK 21**, not the JDK 25 that Android Studio bundles.
See `DECISIONS.md` D-005 for why (detekt cannot parse JDK 25's version string).

`gradle/gradle-daemon-jvm.properties` already pins `toolchainVersion=21`, so command-line
builds are handled. For the IDE:

> **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK**
> Select **21** (`C:\Users\shahe\.jdks\jbr-21.0.11` is already installed).

For a terminal build:

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
export PATH="/c/Users/shahe/.jdks/jbr-21.0.11/bin:$PATH"
```

The PATH entry must be **Unix-style** (`/c/...`, not `C:/...`) or the Firebase CLI cannot
find Java, and `firebase emulators:start` fails with *"Could not spawn `java -version`"*
even though `JAVA_HOME` is set.

### ✅ 1.2 SDK command-line tools, system image and AVD

Done automatically. Installed: `cmdline-tools` (16111833), the
`system-images;android-36;google_apis;x86_64` image, and an AVD named **`afterhours_a`**.

### ✅ 1.3 Install a hypervisor — **done** (WHPX, confirmed by `emulator -accel-check`)

**This is the one thing blocking the app from being run and instrumented.** The emulator
refuses to start:

```
ERROR | x86_64 emulation currently requires hardware acceleration!
CPU acceleration status: Android Emulator hypervisor driver is not installed on this machine
```

The hardware is fine — virtualization is enabled in firmware, and SLAT and DEP are both
available. Only the hypervisor *driver* is missing, and installing one requires an
elevated shell, which this session does not have.

Pick either option, in an **Administrator** terminal:

**Option A — Android Emulator hypervisor driver (lighter, recommended)**

In Android Studio: **Settings → Languages & Frameworks → Android SDK → SDK Tools** →
tick **Android Emulator hypervisor driver (installer)** → Apply. Then, as Administrator:

```
cd %LOCALAPPDATA%\Android\Sdk\extras\google\Android_Emulator_hypervisor_driver
silent_install.bat
```

**Option B — Windows Hypervisor Platform**

As Administrator, then reboot:

```
dism /Online /Enable-Feature /FeatureName:HypervisorPlatform /All
```

Verify afterwards:

```
%LOCALAPPDATA%\Android\Sdk\emulator\emulator -accel-check
```

Once this is done, everything else is already in place:

```bash
$LOCALAPPDATA/Android/Sdk/emulator/emulator -avd afterhours_a -no-window -no-audio &
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator &
./gradlew connectedDevDebugAndroidTest
```

### ⬜ 1.4 A second AVD, before Phase 12

The synchronized-session test drives two clients at once:

```bash
$LOCALAPPDATA/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n afterhours_b -k "system-images;android-36;google_apis;x86_64" -d pixel_8
```

---

## 2. Firebase — needed from Phase 1

Until these are done, the `dev` flavor runs entirely against the **Firebase Emulator
Suite** and needs none of them. `staging` and `prod` do.

### ⬜ 2.1 Create the Firebase projects

Create two at <https://console.firebase.google.com>:
- `afterhours-staging`
- `afterhours-prod`

### ⬜ 2.2 Register the Android apps

The flavors use suffixed application ids, so each environment is a separate app
registration:

| Flavor | Application ID | Firebase project |
|---|---|---|
| `dev` | `com.shnapps.couple.dev` | emulator only — registration optional |
| `staging` | `com.shnapps.couple.staging` | `afterhours-staging` |
| `prod` | `com.shnapps.couple` | `afterhours-prod` |

Download each `google-services.json` and place it at:

```
app/src/staging/google-services.json
app/src/prod/google-services.json
```

**Do not commit the production file.** `.gitignore` will be updated to exclude it in
Phase 1.

### ⬜ 2.3 Enable the services

In each project: **Authentication**, **Firestore**, **Storage**, **Cloud Functions** (needs the
Blaze plan), **Cloud Messaging**, **Crashlytics**, **Remote Config**, **App Check**,
**Performance Monitoring**.

Under **Authentication → Sign-in method**, enable **Anonymous**. This is not optional and it is
new in v2: there are no accounts any more, so pairing mints an anonymous uid for the server to
hang the pair on, and nothing else in the app ever signs anybody in. A project that only has
Email/Password enabled will fail at the first tap of *Show my code* — the screen now says so by
name rather than "something went wrong".

Email/Password is only needed for a build that still has the old account screens.

### ⬜ 2.3a Cloud Messaging — needed from Phase 17

Push has **no emulator**. Everything about a notification can be tested locally except the
delivery itself: the payloads are inspected by `notify.unit.test.ts`, the flows that send them by
`notify.int.test.ts`, and what a phone shows by `NotifierTest` — but a message only reaches a real
device from a real project.

So, in `staging` and `prod`:
- Enable **Cloud Messaging** (2.3 above) and keep the default service account, which is what the
  functions use to send. Nothing else is configured: the app sends **data-only** messages, so
  there is no notification template, no icon and no channel to set in the console (D-088).
- On the device, notifications need the runtime permission on Android 13+; the app asks on first
  start and never again — a user who said no changes it in Android's own settings.
- To check delivery by hand, sign in on a staging build, look at `users/{uid}/fcmTokens` for this
  phone's token, and send a **data** message to it from *Cloud Messaging → Send test message*.
  A payload of `kind=SURPRISE_LEFT` alone is enough; anything the app does not recognise is
  dropped rather than shown.

### ⬜ 2.4 App Check

Register the **Play Integrity** provider for `staging` and `prod`. For local debug
builds, run the app once and copy the debug token it logs into
**App Check → Apps → Manage debug tokens**.

The token is **per app install**: a reinstall, a second phone, or clearing app data each produce
a new one, and an unregistered token means every callable is refused — which looks exactly like
pairing being broken. The line to copy is in logcat at startup:

```bash
adb logcat -s DebugAppCheckProvider:D
```

(In `dev` there is no App Check provider at all and nothing to register — D-134.)

### ⬜ 2.5 SHA certificate fingerprints

Needed for **Play Integrity**, which is how App Check attests a real install. This app has
no Google Sign-In — `AuthDataSource` is email and password only — so that is the only
reason these matter here.

There are three keys, and the checklist used to read as if there were one:

| Key | Where its SHA comes from | When |
|---|---|---|
| Debug | `./gradlew :app:signingReport` (store `~/.android/debug.keystore`) | any time; optional, since `dev` uses the emulators and the App Check debug provider |
| Upload | `keytool -list -v -keystore afterhours-upload.jks -alias upload` | once §3.3 has created it |
| Play App Signing | Play Console → Setup → App signing. **Google's key, not yours** | after the first upload |

Play re-signs your upload with its own key, so the **Play App Signing** fingerprint is the
one Play Integrity checks on a real install. Leaving it out is the failure that works
perfectly on your machine and fails for everyone who installs from Play.

Paste each SHA-1 and SHA-256 into Firebase Console → Project settings → Your apps → the
Android app → Add fingerprint, in **both** projects. Then re-download each
`google-services.json` into `app/src/staging/` and `app/src/prod/` — the fingerprints are
embedded in that file, so an old copy silently keeps the old set.

```bash
./gradlew signingReport
```

### ⬜ 2.6 Email-enumeration protection

In each project: **Authentication → Settings → User actions → Email enumeration
protection** → enable. The app already gives identical answers on sign-in and password
reset; this closes the matching gaps on Firebase's side. Sign-up still reveals an existing
address — see DECISIONS.md D-012 for why, and for the email-link fix recommended before
launch.

### ⬜ 2.7 A domain for App Links (recommended before launch)

Invite links currently use `afterhours://`, which many messengers show as plain text
(DECISIONS.md D-017). With a domain serving `/.well-known/assetlinks.json` for the app's
signing certificate, links become `https://` and open the app directly. Firebase Hosting on
the same project is the simplest host.

### ⬜ 2.8 Functions region, and the Blaze plan

Cloud Functions need the **Blaze** plan. Deploy them in the **same region as Firestore** —
the Firestore location is chosen once, at project creation, and cannot be changed. The code
uses the default `us-central1`; if Firestore lives elsewhere, set the region in
`functions/src/index.ts` to match before the first deploy.

### ⬜ 2.9 Publishing content to a real project (Phase 8)

The app ships its content and needs nothing from here to work. To publish a newer bundle or
disable an item on staging or prod, the tools need an identity with Storage, Firestore and
Remote Config admin rights on that project:

1. Create a service account with the roles **Storage Object Admin**, **Cloud Datastore
   User** and **Firebase Remote Config Admin**, and download a key. Store it outside the
   repository; only files named `serviceAccount*.json` are gitignored.
2. `export GOOGLE_APPLICATION_CREDENTIALS=/path/to/key.json` — or use
   `gcloud auth application-default login` with an account that has those roles.
3. Publish with the project id and, if it differs from `<project>.firebasestorage.app`, the
   bucket:
   ```bash
   npm --prefix tools run publish-content -- --project afterhours-staging
   ```
   The first publish creates the Remote Config parameter `content_version` and the Firestore
   document `contentMeta/pointer`, which the server's engine reads.

Raise `contentVersion` in `content/content.json` for every release after the first; a
published version can never be replaced (DECISIONS.md D-041).

---

### ⬜ 2.10 The free period: one date, in Remote Config (D-151)

Everything is open until a date you set, then Spicy, Hot and Extreme are one-time packs. To set or
move it, with no release:

1. Firebase console → the project (`copl-staging` or the production one) → **Remote Config**.
2. **Add parameter** `free_until`, type **String**. (If it is there already, edit it.)
3. Set the default value, then **Publish changes**:

   | You type | It means |
   |---|---|
   | `2026-11-06` | Open through the end of 6 November, on each phone's own clock. |
   | `2026-11-06T18:00:00Z` | Open until exactly that moment (`+01:00` style offsets work too). |
   | *(empty)* | No free period: the packs are required now. This is how you end it early. |
   | anything unreadable | Ignored. The app falls back to `2026-11-06`, the date it ships with. |

A phone hears about a change within about an hour of being open, and checks the clock every minute,
so the end of the period arrives on its own while the app is running. A phone that is offline keeps
the last date it heard. Nothing is cut off mid-card: the level is applied when the next card is
chosen, and packs already bought stay bought.

## 3. Google Play — needed from Phase 19

### ⬜ 3.1 Play Console app + the three packs (D-152)

What is sold is **three one-time products**, not a subscription: Spicy, Hot and "Everything"
(Extreme). There is no server verification, no service account and no Pub/Sub topic to set up:
the phone asks Play what the account holds, and a pack opens content that is already inside the
app (see `BillingRepository`). The `dev` flavor has a fake store and never needs any of this.

**1. Decide which package the Play app is.** A Play app has exactly one package name, and the
build you install on your two phones has to match it:

| Build | Package | Use it when |
|---|---|---|
| `staging` | `com.shnapps.couple.staging` | You run on the `copl-staging` Firebase project, which is what you do today. **Simplest for a private two-phone app.** |
| `prod` | `com.shnapps.couple` | You have created a separate production Firebase project (§2.1). |

The package is fixed forever once the first bundle is uploaded, so choose before step 3.

**2. Create the app.** Play Console → *Create app* → name, default language, *App*, *Free*
(the packs are in-app products, so the app itself is free). Enter the package from step 1 when it
asks for it with the first upload.

**3. Build and upload a bundle** (after §3.3 has created the upload key):

```bash
./gradlew :app:bundleStagingRelease -PversionCode=1
```

The file is `app/build/outputs/bundle/stagingRelease/app-staging-release.aab`. Upload it under
*Testing → Internal testing → Create new release*, and accept **Play App Signing** when asked.
Every later upload needs a higher `-PversionCode` than any before it. Play may ask you to finish
some of *App content* (privacy policy, content rating, data safety, target audience) before it
lets a release roll out; §3.2 and §4 cover those.

**4. Create the three products.** *Monetize → Products → In-app products → Create product*.
The ids must be exactly these, and each must be **Active**:

| Product id | Name to show | Opens |
|---|---|---|
| `afterhours.pack.spicy` | Spicy pack | Mild, Flirty, Spicy |
| `afterhours.pack.hot` | Hot pack | up to Hot |
| `afterhours.pack.everything` | Everything pack (Extreme) | every level |

Give each a one-time **Buy** purchase option and a price. Do not make them consumable: the app
never consumes a purchase, because that is what makes a restore on a new phone work. Products only
become buyable once a build that includes the billing permission is on a track — step 3 does that.

**5. Install from Play on both phones.** *Testing → Internal testing → Testers*: make a list with
both of your Google accounts, then open the *opt-in link* on each phone and install from Play. A
build sideloaded with `adb` cannot buy anything; the app says *"The store doesn't have this pack
yet"* instead.

**6. License testers, so testing is free.** *Settings → License testing*: add both Google
accounts. Their purchases are test purchases, charged to a test card that always approves, and can
be refunded from *Order management* so you can buy again. Also pick the slow test card at least
once: it produces the *pending* payment, and the app should say *"Your payment is waiting to
clear"* and open the pack by itself when it clears.

**7. End the free period, or nothing is for sale.** While the free period is on, the store sells
nothing (§2.10). To test a purchase, set Remote Config `free_until` to an empty value and publish.
Then check, on phone A: a pack shows its real price (not "(dev)"), buying it opens the level, and
phone B, once paired, has the same level without buying. Delete the app and install it again: the
pack must be back without pressing anything.

### ⬜ 3.2 Content rating and policy

BUILD_PROMPT.md §3.6 — **read it before submitting.** Google Play prohibits apps whose
primary purpose is sexual gratification. The app is built to stay on the compliant side
of that line (text and suggestion only, no app-supplied explicit imagery, private
user media, in-app reporting, mandatory age gate).

You must complete the content-rating questionnaire at the highest maturity tier. If you
decide you want content beyond that line, that is a **distribution decision** — it means
leaving Play for direct APK or web distribution. Flagging it, not deciding it.

### ⬜ 3.3 Upload key

Create the upload keystore once, in the repository root (`*.jks` and `keystore.properties` are
git-ignored; never commit either), and **back it up outside this machine**:

```bash
keytool -genkeypair -v -keystore afterhours-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties
```

Put the passwords you chose into `keystore.properties`. From then on `./gradlew
:app:bundleStagingRelease` signs with it by itself; without the file a release build is signed with
the debug key and Play will refuse it. Add the SHA fingerprints per §2.5.

---

## 4. Legal — before any public release

### ⬜ 4.1 Privacy policy and terms

Required by Play, and this app handles unusually sensitive data. The policy must reflect
the actual architecture in `SECURITY.md` — in particular that individual preference and
boundary answers are never shared with the partner, and that no private content is sent
to analytics.

`RELEASE.md` puts every step in this file into the order it has to happen, with the gates
between them.

**Drafts are written**, from the architecture rather than from a template:
`legal/PRIVACY_POLICY.md` and `legal/TERMS.md`. Both have `[bracketed]` gaps — the entity,
the address, the region, the jurisdiction, the contact addresses — and both need a lawyer
to read them before they go up. Then publish each at a stable URL and put the privacy URL
in Play Console and in the app's Settings screen.

### ⬜ 4.2 Data safety form

Play's Data Safety declaration must match what the app really collects. `:core:analytics`
is a closed allowlist precisely so this stays answerable and honest.

**The answers are written out** in `legal/DATA_SAFETY.md`, row by row in the order Play asks
them, including the account-deletion declaration. Read it against the code if you want to
check it, then type it into the console — nothing there is a guess.
