# RELEASE

The first release, in the order it has to happen. Everything here needs a human with accounts:
`HUMAN_SETUP.md` is the reference for each console step, and this is the sequence and the gates
between them. Nothing in this file can be done by the build.

Nothing below is done. The code side is finished (`PROGRESS.md`: all 23 phases), and what remains is
setup, review and two real devices.

---

## 0. Before anything

```bash
export JAVA_HOME="C:/Users/shahe/.jdks/jbr-21.0.11"
export PATH="/c/Users/shahe/.jdks/jbr-21.0.11/bin:$PATH"

./gradlew check                       # detekt, ktlint, 559 unit tests, architecture, content gate
npm --prefix functions run test:unit  # 133
npm --prefix functions run test:int   # 158  (needs the emulators)
npm --prefix firebase/tests test      # 121  (needs the emulators)
```

All green, from a clean tree, before a release branch exists.

---

## 1. Firebase — the real projects

`HUMAN_SETUP.md` §2.1–2.9, in that order. Two projects: staging and prod, never one.

- [ ] Create both projects, register the Android apps with their real application ids
      (`com.shnapps.couple.staging`, `com.shnapps.couple`), and drop each `google-services.json`
      into `app/src/staging/` and `app/src/prod/`. **Never commit them** — the build disables those
      variants until they are present, which is how you know it worked.
- [ ] Enable Authentication (email/password), Firestore, Storage, Functions on Blaze, Messaging,
      Crashlytics, Performance, Remote Config, App Check.
- [ ] Turn on **email-enumeration protection** (§2.6). The app deliberately cannot tell "no such
      account" from "wrong password"; this keeps the server from telling either.
- [ ] Register SHA-1 and SHA-256 for the upload key and the Play signing key (§2.5).
- [ ] App Check: Play Integrity for staging and prod, enforcement **on** for Firestore, Storage and
      Functions (§2.4).
- [ ] Deploy the rules and the functions to staging first:
      `firebase deploy --only firestore:rules,storage:rules,functions --project <staging>`
- [ ] Publish the content bundle and set Remote Config's `content_version` (§2.9).
- [ ] Gate: run the two-device script (`ACCEPTANCE.md`) against **staging**, on real devices, with
      the real project. This is the first time notifications and App Check are exercised for real.

---

## 2. Play Console

`HUMAN_SETUP.md` §3.

- [ ] Create the app. 18+ rating, content rating questionnaire answered honestly (§3.2).
- [ ] Create the subscription product and its base plan; the id must match `PREMIUM_PRODUCT` in
      `functions/src/billing/billing.ts`.
- [ ] Set up the Play service account so `verifyPurchase` can ask the store what a token is worth,
      and put its credentials in the functions' config — never in the repo.
- [ ] **Generate the upload key** (§3.3). This is the one irreversible step in this document: lose
      this keystore and you cannot update the app again. Store it in a password manager and a second
      place offline.
      ```bash
      keytool -genkeypair -v -keystore afterhours-upload.jks -alias upload \
        -keyalg RSA -keysize 4096 -validity 10000
      ```
      Then replace the local debug signing in `app/build.gradle.kts` (D-095) with a real
      `signingConfig` reading from `~/.gradle/gradle.properties` or the environment — the keystore
      path, its password and the key password never enter the repository.
- [ ] Enrol in Play App Signing and register the resulting SHA fingerprints back in Firebase (§2.5).
- [ ] Gate: a **closed test track** build, installed from Play by two real testers, with a real
      purchase made and `dev-couple.js tier` replaced by the real entitlement. This is the first time
      `PlayBillingClient` runs at all (SECURITY.md §4.6).

---

## 3. Legal, and the store listing

- [ ] Have a lawyer read `legal/PRIVACY_POLICY.md` and `legal/TERMS.md`, fill every `[bracketed]`
      gap, and publish both at stable URLs.
- [ ] Put those two URLs in `core/ui/.../LegalUrls.kt`. The Settings rows appear on their own once
      the constants are non-blank, and stay hidden while they are empty.
- [ ] Privacy policy URL into Play Console, and into the store listing.
- [ ] Data safety form: the answers are written out row by row in `legal/DATA_SAFETY.md`.
- [ ] Account deletion declaration: the in-app route plus a web page that explains it.
- [ ] Store listing: screenshots, description, and an honest content warning. No screenshot may show
      a real person's answers.

---

## 4. Build the release

- [ ] Raise `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] `./gradlew bundleProdRelease` (an `.aab` for Play; `assembleProdRelease` is the APK for a
      device check).
- [ ] Upload `app/build/outputs/mapping/prodRelease/mapping.txt` to Crashlytics, or let the Gradle
      plugin do it — an obfuscated crash report is worth very little.
- [ ] Install the **release** artifact on a device and walk `ACCEPTANCE.md` §1–7 once more. R8 breaks
      at runtime, not at build time, and only a real run finds it.

---

## 5. The gates that are not optional

A release does not go out unless, from BUILD_PROMPT.md §24:

- two real adults on two real devices complete the whole flow, with no developer intervention and no
  mocked multiplayer;
- every row of §7.3 passes against the **real** project's rules, not only the emulator's;
- every engine test passes, and no content appears above the couple's ceiling or inside a NEVER;
- Crashlytics is clean through a full acceptance run;
- no notification carries anything either partner wrote.

`ACCEPTANCE.md` is the script for the first, `SECURITY.md` §5 maps the second, and the test tiers in
`TESTING.md` cover the rest.

---

## 6. After the first release

- Watch Crashlytics and the functions' logs for the first week; the failure modes nobody has seen yet
  are in `deleteAccount`'s `failures` count, in media confirmation, and in Play notifications.
- `COST.md` says what one couple costs and where the money goes if it ever surprises you.
- The three things this repository knows are unverified — notification delivery, the Play purchase
  flow, and startup on real hardware — stop being unverified the first time this list is worked
  through. Update `SECURITY.md` §4 and `PROGRESS.md` when they do.
