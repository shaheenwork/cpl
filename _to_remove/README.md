# Holding area

Everything the rebuild took out of scope, kept rather than deleted so the audit can be reviewed
before anything is lost. Nothing here is built: none of these modules is in `settings.gradle.kts`,
and no source set points at these paths.

Kotlin files carry a `.kt.txt` suffix. That is not decoration — the project's Konsist rules walk the
filesystem rather than Gradle's source sets, so a `.kt` here is a file they will judge, and
`_to_remove/firebase-memories/MemoryRemoteDataSource.kt` failed "only :core:firebase imports
firebase" while sitting in a folder nothing compiles. A file in a holding area is not source, and
the extension now says so. Restoring one is a rename.

| Folder | What it is | Why it is here |
|---|---|---|
| `feature-night` | The chapter-based night player and its builder | Not in the new screen list; Game Night replaces the idea in a simpler form |
| `feature-preferences` | The twenty-question private curiosity deck | The friction the zero-friction principle forbids |
| `feature-discovery` | Mutual reveals over those answers | Follows the deck |
| `feature-memories` | The kept-evenings library | Not in the new screen list |
| `feature-send-arc` | The multi-day arc screen | Arcs are not in the fifteen games |
| `games-later/` | Seven game prompt files, each with a `.why` note | Each becomes one of the brief's later games as a content edit |
| `content-prompts/` | Nine retired games' prompts | Out of scope entirely |
| `data-out-of-scope/` | The memories, preferences and mutual halves of `:core:data`, `:core:firebase` and the fakes | Nothing surviving reaches them |
| `model/Heat.kt.txt` | The three-level tier ceiling | One enum now: `Intensity` carries all five (M2) |
| `feature-boundaries-old/` | The twenty-seven-row limits list, its slider and its notes | Replaced by the eight-question profile (M3) |
