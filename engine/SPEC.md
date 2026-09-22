# The experience engine — algorithm spec, version 1

BUILD_PROMPT.md §10 says what the engine must do. This file says exactly how, precisely enough
that two implementations produce **byte-identical** output from the same inputs and seed:

- `functions/src/engine/` (TypeScript) — the server: hard filter, scoring, sampling, and the
  first assembly, inside `generateExperience`.
- `core/engine` (Kotlin, pure JVM) — the client: assembly, variety, duration fitting and
  remixing, only ever within the pool the server returned (§5.5).

`engine-fixtures.json` beside this file holds shared test vectors. Both test suites read it; a
case that one implementation passes and the other fails is a bug in one of them or here.
Regenerate it with `npm --prefix functions run engine:fixtures` after a deliberate change,
and raise `ENGINE_VERSION` whenever output changes for identical inputs (§10.6).

Numbers: all arithmetic is IEEE-754 double precision using only `+ - * /`, comparisons and
`floor`. No `pow`, `log`, `exp` or `sqrt`: their last bit is not guaranteed to agree across
runtimes. Sorting is stable; ties are always broken by item id, compared by UTF-16 code unit.

---

## 1. Inputs

**Item** — a published content item (BUILD_PROMPT.md §9.1): `id`, `intensity` 1–5, `modes`,
`interactionType`, `durationMin`, `moods`, `chapterKinds`, `category`, `noveltyWeight`,
`repeatCooldownDays`, `requiresMedia`, `status`, `excludedByBoundaries`,
`requiredMutualPreferences`, `boostedByPreferences`.

**Request** — `mode` (TOGETHER | APART), `durationMin` (10–240), `moods` (a set of the eight
mood ids), `intensity` 1–5, `leadership` (ME | YOU | SWITCH | RANDOM), `novelty` (FAMILIAR |
MIXED | SURPRISE_ME), `seed` (unsigned 32-bit integer), `media` — whether this device can
record VOICE and take a PHOTO (both default true) — and optionally `focus`: a revealed mutual
match (taxonomy item id) to build the night around. The server checks it is one of the
couple's revealed matches before it gets here. An item is **about the focus** when `focus` is
in its `boostedByPreferences` or its `requiredMutualPreferences`.

**Couple context** — server only, never sent to a client (§5.4):
- `filters`: the couple's `engineFilters` — `maxIntensity`, `excludedThemes`,
  `askFirstThemes` (theme → uids), `curiousThemes`, `excludedItems`.
- `mutual`: revealed mutual matches, taxonomy item id → match level. Only *revealed* matches
  count: a pending one steering a night would date the answer behind it (§5.3).
- `lastPlayed`: item id → epoch milliseconds, and `nowMs`.
- `affinity`: dimension → score in [-1, 1] (both partners averaged), keys `category:<id>`,
  `interaction:<TYPE>`, `mood:<id>`.
- `weights`: w1–w7 (Remote Config; defaults 3.0, 2.0, 2.0, 1.5, 1.5, 2.0, 3.0).

**Effective intensity** `E = min(request.intensity, filters.maxIntensity)`. The client never
computes E; the server returns it.

---

## 2. Random numbers — mulberry32

State is a 32-bit two's-complement integer, initialised to `seed` (reinterpreted). Each call:

```
state = (state + 0x6D2B79F5)            wrapping to 32 bits
t = state
t = imul(t ^ (t >>> 15), t | 1)         32-bit multiply, wrapping
t = t ^ (t + imul(t ^ (t >>> 7), t | 61))   + wraps to 32 bits
return ((t ^ (t >>> 14)) as unsigned) / 4294967296
```

`>>>` is an unsigned shift. `nextInt(n) = floor(next() * n)`. Draws happen in exactly the
order this spec lists them, and nowhere else.

Retry `a` (0–4) of a generation uses seed `(seed + a * 0x9E3779B9) mod 2^32`.

---

## 3. The hard filter — the chokepoint (server)

`permits(item, request, context)` returns the **first** failing reason, or nothing:

| # | Reason | Removed when |
|---|---|---|
| 1 | `NOT_PUBLISHED` | `status` is not `published` (after deltas) |
| 2 | `MODE` | `request.mode` is not in `modes` |
| 3 | `ABOVE_CEILING` | `intensity > E` |
| 4 | `EXCLUDED_THEME` | any `excludedByBoundaries` theme is in `filters.excludedThemes` |
| 5 | `EXCLUDED_ITEM` | any boosted or required preference is in `filters.excludedItems` |
| 6 | `NOT_MUTUAL` | any `requiredMutualPreferences` entry is not in `mutual` |
| 7 | `COOLDOWN` | `nowMs - lastPlayed[id] < repeatCooldownDays * 86400000` |
| 8 | `MEDIA` | `requiresMedia` is set and `request.media` says the device cannot |

Nothing is ever *reordered* by the filter and nothing it removes reaches scoring (§10.3).
ASK_FIRST is not a reason: an item with a theme in `askFirstThemes` is kept and flagged with
the uids to ask, which are stored only where each of them alone can read (§3.2).

---

## 4. Score (server)

For each permitted item, with `d = (nowMs - lastPlayed[id]) / 86400000` and
`fresh = lastPlayed has no entry for id`:

```
M  = mean over boostedByPreferences of level(mutual[p])      (0 when there are none)
     level: BOTH_YES 1, BOTH_SECRET 1, BOTH_CURIOUS 0.8, MIXED_POSITIVE 0.6, absent 0
     then + 0.25 if any excludedByBoundaries theme is in curiousThemes; at most 1
A  = mean of (clamp(affinity[k], -1, 1) + 1) / 2 over the keys present:
       category:<category>, interaction:<interactionType>, mood:<m> for each mood
     0.5 when none is present
Md = 0.5 if request.moods is empty, else |moods ∩ request.moods| / |request.moods|
N  = FAMILIAR    0.5 * (1 - noveltyWeight) + 0.5 * (fresh ? 0 : 1)
     MIXED       0.5
     SURPRISE_ME 0.5 * noveltyWeight + 0.5 * (fresh ? 1 : 0)
I  = 1 - (E - intensity) / 4
R  = fresh ? 0 : max(0, 1 - d / 90)

F  = 1 if the item is about the focus, else 0 (always 0 without a focus)

score = w1*M + w2*A + w3*Md + w4*N + w5*I - w6*R + W_FOCUS*F       W_FOCUS = 4.0
```

Evaluated left to right in exactly that order. Without a focus the last term adds zero, so
every night without one is exactly what it was before focus existed. The repetition penalty (w7) depends on the
night being built, so it is applied during assembly (§7).

---

## 5. Plan

**Chapter count** from `request.durationMin` — range `[lo, hi]`: ≤ 20 → [3, 3]; ≤ 37 →
[4, 5]; ≤ 52 → [5, 6]; ≤ 75 → [6, 7]; otherwise [8, 10]. `count = lo + nextInt(hi - lo + 1)`
(the first draw, even when lo = hi).

**Kinds.** `available` is the set of chapter kinds carried by at least one item in the pool
being planned for (the permitted set on the server, the returned pool on a client). Slot 0 is
WARM_UP and the last slot FINALE. If WARM_UP is not available, slot 0 is planned like a middle
slot. If FINALE is not available the plan fails (`NO_FINALE`).

Middle slots, in order, each draw one kind from
`CURIOSITY TEASE CHALLENGE CONTROL CONFESSION ROLEPLAY WILDCARD YOUR_CHOICE` (this order).
Each kind's weight is 1.0, plus 1.0 for every requested mood that favours it, plus leadership:

| Mood | Favours | | Leadership | Adds |
|---|---|---|---|---|
| romantic | CONFESSION, CURIOSITY | | ME, YOU, SWITCH | CONTROL +1.0 |
| naughty | TEASE, CHALLENGE | | RANDOM | WILDCARD +0.5, YOUR_CHOICE +0.5 |
| bold | CONTROL, CHALLENGE | | | |
| unpredictable | WILDCARD, YOUR_CHOICE | | | |
| mysterious | TEASE, WILDCARD | | | |
| roleplay | ROLEPLAY | | | |
| talk | CONFESSION, CURIOSITY | | | |
| experimental | CHALLENGE, WILDCARD | | | |

A kind is a candidate when it is available, is not the previous slot's kind, and is not
YOUR_CHOICE once YOUR_CHOICE has been used. If any candidate is still unused tonight, only
unused candidates remain. Draw: `r = next() * total weight`; walk the candidates in the order
above, adding weights; the first whose running total exceeds `r` is chosen (the last
candidate if rounding leaves none). No candidate → the plan fails (`NO_KINDS`).

**Targets.** `s = max(1, E - 2)`; `target[i] = s + floor((E - s) * i / (count - 1))`. They
never fall, never rise by more than one per chapter, and the finale's target is E.

---

## 6. Sample the pool (server)

`budget = D / count`, and every permitted item's **value** is
`score + W_DURATION * fit(durationMin, budget)`, where
`fit(d, b) = 1 - |d - b| / max(d, b)` and `W_DURATION = 2.0`. A pool of perfect but long items
cannot make a short night, so length counts from the start.

For each slot in order, `eligible` = permitted items not yet in the pool whose `chapterKinds`
contain the slot's kind and whose intensity is at most the slot's target, sorted by value
descending, then id. Add the first. Then draw two more from the rest of `eligible`, one at a
time without replacement: weight = `value - min + 0.1`, `min` being the lowest value still in
the list; `total` is the sum of the weights in list order; `r = next() * total`; walk in list
order adding weights; the first running total above `r` is drawn (the last item if none).

The pool is therefore about three times the chapter count — the best candidate for every slot
plus a weighted random tail — and never the permitted set itself (§5.5): if it holds every
permitted item while there are more permitted items than chapters, the item that was not a
slot's first pick and comes last in score-then-id order (score descending, then id) is
dropped.

---

## 7. Assemble (server and client)

Inputs: the pool, a base score per item, the plan, `request.durationMin` (D), E, and w7.
`W_TARGET = 1.0`; `W_DURATION` and `fit` as in §6.

**Slot rules.** Item `x` may stand in slot `i` of a night only if:
- `x.chapterKinds` contains `kinds[i]`, and `x.intensity ≤ targets[i]`;
- on the FINALE slot, no other chapter of the night is above `x`;
- elsewhere, `x.intensity ≤ cap` and, once a finale is in the night, `≤` the finale's
  intensity — where `cap` is the highest intensity among pool items that carry FINALE and are
  at most `targets[last]`. No chapter may outrank what the finale can reach;
- `x` is at most one level above the chapter before it, and the chapter after it (if any) is
  at most one level above `x`;
- `x` does not make three consecutive chapters of one interaction type, counting the chapters
  on either side of it.

**Fill** is a depth-first search over the slots in order. At slot `i`, with `m` minutes
already chosen:
- `budget = max(1, (D - m) / (count - i))`
- `value = score + W_TARGET * (x.intensity == targets[i] ? 1 : 0) + W_DURATION * fit(x.durationMin, budget) - w7 * (chapters already of x's type) / count`
- candidates are the unused pool items that keep the slot rules, ordered by: first a *rank*
  (lower first) = `2` if the slot is FINALE and `x.intensity ≠ E`, plus `1` if
  `x.durationMin` is outside `[0.8 D - m - r * 30, 1.2 D - m - r * 1]` with `r = count - i - 1`
  (the lengths that keep the ±20% window reachable); then value descending; then id.
- Each candidate tried counts one step; past `MAX_FILL_STEPS = 2000` the search stops.
- A complete night whose total is within `[0.8 D, 1.2 D]` ends the search. The first complete
  night reached at all is kept as the fallback.

Result: the in-window night if one was found, else the fallback, else `UNFILLABLE`.

**Variety.** While the night has 6+ chapters and fewer than 4 interaction types: take the most
frequent type (ties: the type whose last chapter is later). Walk its chapters from the last to
the first, skipping the FINALE; for the first that has one, replace it with the unused pool item
of a type not yet in the night that keeps the slot rules and has the highest
`score + W_TARGET * (intensity == target ? 1 : 0)`, ties by id. If no chapter could be
replaced, stop.

**Duration.** While the total is outside `[0.8 D, 1.2 D]`, for at most `2 * count` rounds: for
every slot (in order) and every unused pool item (in pool order) that keeps the slot rules —
and, if the finale was at E, keeps it at E; and, on a night of 6+ chapters that has 4+ types,
keeps 4+ — compute the distance from D of the total with that swap. Take the smallest distance;
ties go to the lower slot, then the higher score, then the lower id. Apply it only if it is
strictly closer than the current total; otherwise stop.

---

## 8. Validate (server and client)

An assembled night is rejected with every rule it breaks:

| Code | Rule |
|---|---|
| `CHAPTER_COUNT` | count outside the range for D |
| `DUPLICATE_ITEM` | an item appears twice |
| `KIND_MISMATCH` | an item does not carry its chapter's kind |
| `TYPE_RUN` | three consecutive chapters share an interaction type |
| `TOO_FEW_TYPES` | 6+ chapters with fewer than 4 interaction types |
| `MULTIPLE_YOUR_CHOICE` | more than one YOUR_CHOICE chapter |
| `DURATION` | total outside ±20% of D |
| `ESCALATION` | a chapter more than one level above the one before |
| `ABOVE_CEILING` | a chapter above E |
| `FINALE_NOT_PEAK` | the finale is below another chapter, or the last chapter is not a FINALE |
| `MODE` | an item not playable in the request's mode |

The server also re-runs the hard filter (§3) over the night and the alternates.

**Retries.** Attempts 0–4 each plan, sample, assemble and validate with their own seed (§2).
A night *holds the focus* when a chapter is about it, or when nothing permitted is about it
(or there is no focus). A night that passes, holds the focus and has its finale at E is
returned at once. Otherwise the best night that passed is kept while the remaining attempts
look: the first to pass, replaced only by a later one that holds the focus when the kept one
does not. After five attempts it is returned. With no passing night at all,
`generateExperience` fails with `not-enough-content`.

---

## 9. Output

`chapters` (index, kind, content id, intensity), `alternates` (the pool minus the night, by
score then id), `effectiveIntensity`, `seed`, `engineVersion`, and `focusIncluded` — whether a
chapter is about the focus. The client resolves ids against
its content cache; nothing here depends on a device.
