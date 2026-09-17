# dev flavor

`google-services.json` here is a **placeholder with no real credentials**. It exists only
so the Google Services Gradle plugin can run and the Firebase SDK can initialise.

The `dev` flavor sets `USE_FIREBASE_EMULATOR=true`, so every Firebase client is redirected
to the local Firebase Emulator Suite before first use
(`core/firebase/.../di/FirebaseModule.kt`). No request ever reaches Google, which is why
this file is safe to commit and why a developer needs no Firebase project to run the app.

The real `staging` and `prod` files are gitignored — see HUMAN_SETUP.md §2.2.

Start the backend with:

```bash
firebase emulators:start
```
