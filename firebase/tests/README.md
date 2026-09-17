# Security-rules tests

These prove the privacy architecture in `BUILD_PROMPT.md` section 5 rather than assuming
it. The full pass/fail matrix is section 7.3; rows are added by the phase that introduces
the collection they cover.

Run against a live Firestore emulator:

```bash
firebase emulators:start --only auth,firestore,storage --project afterhours-dev-emulator
```

```bash
npm --prefix firebase/tests test
```

The single most important property under test: **no rule anywhere grants one partner read
access to the other's `preferences`, `boundaries` or round `submissions`** — not on match,
not on reveal, not ever. Mutual matches are computed server-side and only positives are
materialised, so a non-match leaks nothing.
