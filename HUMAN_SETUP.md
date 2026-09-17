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
```

### ⏳ 1.2 SDK command-line tools and an emulator

The machine had no `cmdline-tools`, no system image and no AVD, so nothing could be
launched or instrumented. Being installed automatically; if it needs redoing by hand:

> **Android Studio → Settings → Languages & Frameworks → Android SDK**
> · **SDK Tools** tab → tick **Android SDK Command-line Tools (latest)**
> · **SDK Platforms** tab → tick **Show Package Details** → under **Android 16 (API 36)**
>   tick a **Google APIs Intel x86_64 Atom System Image**
> · **Device Manager → Create Virtual Device** → Pixel 8 → that image → Finish

Two AVDs are needed from Phase 12 onward, because the synchronized-session test drives
two clients at once.

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

In each project: **Authentication** (Email/Password, and Google if wanted),
**Firestore**, **Storage**, **Cloud Functions** (needs the Blaze plan),
**Cloud Messaging**, **Crashlytics**, **Remote Config**, **App Check**,
**Performance Monitoring**.

### ⬜ 2.4 App Check

Register the **Play Integrity** provider for `staging` and `prod`. For local debug
builds, run the app once and copy the debug token it logs into
**App Check → Apps → Manage debug tokens**.

### ⬜ 2.5 SHA certificate fingerprints

Needed for Google Sign-In and Play Integrity. Add the debug and upload-key SHA-1 and
SHA-256 to each Firebase app registration.

```bash
./gradlew signingReport
```

---

## 3. Google Play — needed from Phase 19

### ⬜ 3.1 Play Console app + billing products

Create the app, then the subscription products the paywall expects. Until then the `dev`
flavor uses a fake `BillingRepository` and entitlement can be set directly in the
emulator.

### ⬜ 3.2 Content rating and policy

BUILD_PROMPT.md §3.6 — **read it before submitting.** Google Play prohibits apps whose
primary purpose is sexual gratification. The app is built to stay on the compliant side
of that line (text and suggestion only, no app-supplied explicit imagery, private
user media, in-app reporting, mandatory age gate).

You must complete the content-rating questionnaire at the highest maturity tier. If you
decide you want content beyond that line, that is a **distribution decision** — it means
leaving Play for direct APK or web distribution. Flagging it, not deciding it.

### ⬜ 3.3 Upload key

Create and back up the upload keystore. Add its SHA fingerprints per §2.5.

---

## 4. Legal — before any public release

### ⬜ 4.1 Privacy policy and terms

Required by Play, and this app handles unusually sensitive data. The policy must reflect
the actual architecture in `SECURITY.md` — in particular that individual preference and
boundary answers are never shared with the partner, and that no private content is sent
to analytics.

### ⬜ 4.2 Data safety form

Play's Data Safety declaration must match what the app really collects. `:core:analytics`
is a closed allowlist precisely so this stays answerable and honest.
