# Typefaces

Both are variable fonts, licensed under the SIL Open Font License 1.1. Full licence texts
ship in `assets/licenses/` so the attribution travels with the app.

| File | Family | Role |
|---|---|---|
| `playfair_display_variable.ttf` | Playfair Display | Display / headings. High-contrast editorial serif — the single biggest carrier of the "luxury after-hours lounge" feel in BUILD_PROMPT.md section 15.1. |
| `inter_variable.ttf` | Inter | Body / UI. Roboto was rejected deliberately: it reads as stock Android, which section 15.1 explicitly warns against. |

Variable rather than static instances: two files instead of twelve, the full weight range,
and less total weight. Weights are selected through `FontVariation.Settings(wght)`, which
needs API 26 — `minSdk` is 26.

Subsetting these to the glyphs actually used is a Phase 22 size optimisation, not a
correctness issue.
