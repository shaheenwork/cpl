# CONTENT GUIDE

How to write and change what this app says. Content is data, not code: an item, a game or a prompt is
a line of JSON that the validator checks, the bundler packs and the engine chooses from. Nothing here
needs a new screen.

The rules below are enforced. `npm --prefix tools run validate` fails the build on every one of them,
so a careless edit is caught before anyone sees it — the policy is kept by the writing, and the
validator is the tripwire under it (BUILD_PROMPT.md §3.3, §9.4).

---

## 1. Where things live

```
content/packs/*.json          the items — twelve packs, 626 published items
content/prompts/<game>.json   a game's prompts, under the same rules as items
content/games.json            the games themselves: steps over primitives
content/taxonomy.json         themes, categories and the private preference library
content/vocabulary.json       the closed vocabulary: moods, tags, kinds, types, statuses
content/policy/prohibited-terms.json   the §3.3 tripwire
content/content.json          contentVersion — raise it to publish changed content
content/dist/bundle.json      the built bundle. Committed, and `check` fails on a stale one
```

After **any** content edit:

```bash
npm --prefix tools run validate      # or ./gradlew validateContent
npm --prefix tools run bundle        # rebuild content/dist/bundle.json, and commit it
npm --prefix functions run build     # the server keeps its own copy of the bundle
```

A published bundle version is immutable. Raise `contentVersion` in `content/content.json` before
publishing changed content, or devices keep the copy they have.

---

## 2. An item

```json
{
  "id": "afterdark_one_layer_one_word",
  "version": 1,
  "title": "One Layer, One Word",
  "subtitle": "Choose the word carefully.",
  "body": "Agree a word in advance. Each time one of you says it, the other removes one layer. Say it slowly, and make them wait for it.",
  "pack": "AFTER_DARK",
  "category": "teasing",
  "tags": ["teasing", "game", "anticipation"],
  "intensity": 3,
  "modes": ["TOGETHER"],
  "interactionType": "CHALLENGE",
  "durationMin": 10,
  "moods": ["naughty", "unpredictable"],
  "requiredMutualPreferences": [],
  "boostedByPreferences": ["teasing_verbal", "mood_naughty"],
  "excludedByBoundaries": ["teasing_words", "mood_charged"],
  "chapterKinds": ["TEASE"],
  "noveltyWeight": 0.5,
  "repeatCooldownDays": 30,
  "requiresMedia": null,
  "status": "published",
  "locale": "en",
  "createdAt": "…",
  "updatedAt": "…"
}
```

Every field is required and no other field is allowed. One item per line in the file, so a diff shows
what changed.

| Field | Rule |
|---|---|
| `id` | `^[a-z0-9_]{3,64}$`, starts with the pack's prefix, unique across items **and** prompts |
| `title` | ≤ 40 characters |
| `subtitle` | ≤ 70 characters |
| `body` | 25–320 characters. The instruction itself, in the second person plural |
| `pack` | the file's own pack |
| `category`, `tags`, `moods`, `interactionType`, `chapterKinds` | from `vocabulary.json` only |
| `intensity` | 1–5, inside the pack's declared range (Appendix A) |
| `modes` | `TOGETHER`, `APART`, or both — at least one |
| `durationMin` | 1–30 |
| `noveltyWeight` | 0–1 |
| `repeatCooldownDays` | 0–365 |
| `requiresMedia` | `VOICE`, `PHOTO`, or null |
| `status` | `draft`, `published`, `disabled`, `archived`. Only `published` is ever shown |

---

## 3. The rules that exist because of the boundary engine

These are the checks worth understanding, because they are the ones that fail on a plausible-looking
item:

1. **Everything a partner leans on must be refusable.** An item boosted by, or requiring, a
   preference must list a boundary that covers that preference's theme in `excludedByBoundaries`.
   Otherwise a partner's NEVER could not reach the item (§3.2), and the whole promise breaks.
2. **An item must be reachable at its own intensity.** If it requires a mutual preference that is
   only ever asked at level 4, it cannot sit at intensity 2 — it would be excluded by its own
   requirement and never appear.
3. **Sensitive text must be covered by a boundary.** If the body mentions blindfolds, strangers,
   instructions or any theme in `boundaryKeywords`, the item must list a boundary covering it. This
   is what stops a careless rewrite from slipping past somebody's limit.
4. **No prohibited term, anywhere.** `content/policy/prohibited-terms.json` is matched
   case-insensitively, whole word, over every authored string: anyone under 18 or age-ambiguous,
   non-consent or coercion, intoxication or anything requiring a substance, incest, bestiality,
   anything illegal. A word on that list fails the build even in an innocent sentence — rewrite the
   sentence.
5. **No near-duplicates.** Bodies are compared by word-trigram Jaccard similarity, and anything at
   0.4 or above is a duplicate. Two items that say the same thing in different words are one item.
6. **Coverage, so the engine can always build a night.** Per mode: enough items in every chapter
   kind, enough `FINALE` items at every intensity, and enough `WARM_UP` items at intensity 1–2.
   Adding a mood or a kind without items to fill it fails here, which is the point.

---

## 4. Voice

Write as if one adult is telling two adults something worth trying tonight.

- **Second person plural, present tense.** "Agree a word in advance", not "The couple should agree".
- **An instruction, not a description.** Say what to do, in the order to do it. One idea per
  sentence.
- **Confident, never coy.** No winking, no euphemism treadmill, no exclamation marks. The app is for
  adults; write like it.
- **No pet names, no assumptions.** Never "boyfriend", "wife", "he", "she". Never assume who leads,
  who is home, whose body is whose. `you` and `they` carry everything.
- **Specific beats intense.** "Say it slowly, and make them wait for it" is better than an adjective.
  Intensity comes from the `intensity` field and the content itself, not from the prose straining.
- **An out, where one is needed.** An item that asks for something new can say how to stop, and must
  not shame anyone who does.
- **No brand, no place, no time.** Content outlives a release and is read anywhere.

Titles are two to four words and name the thing. Subtitles are one short line that adds something —
never a restatement of the title.

---

## 5. Games

A game is `content/games.json` plus `content/prompts/<game>.json`: steps over the primitives
(`QUESTION`, `CHOICE`, `PREDICTION`, `VOTE`, `REVEAL`, `TIMER`, `CHALLENGE`, `REACTION`), each step
saying who acts (`ACTIVE`, `OTHER`, `EACH`, `TOGETHER`) and how they answer.

- A new game, or a changed one, is a **content edit**. Never a new screen and never a branch:
  `GameLogicTest` fails the build if code names a game (D-072).
- "A phone each" is offered only for a game whose every step is `EACH` or `TOGETHER`
  (`Game.bothAtOnce`, D-074).
- A new **primitive** is the one exception: it changes `vocabulary.json`, `GamePrimitive`,
  `RoundPrimitive`, and gets exactly one renderer in `PrimitiveRenderers.kt`.
- Prompts follow every item rule above, including the prohibited terms and the near-duplicate check.

---

## 6. The taxonomy, and adding to the vocabulary

`content/taxonomy.json` is the private preference library and the boundary themes: what a person is
asked about, at which intensity floor, and which theme a limit covers. It is authored once and copied
into the APK by `:core:data`'s bundle task — edit the JSON, never a copy, and `TaxonomyFileTest` holds
it to the strict standard (D-020).

A new mood, interaction type, chapter kind, media kind or status changes **`content/vocabulary.json`
and the enum in `:core:model` together**. `ContentBundleFileTest` fails if they differ. Then write
enough items to satisfy coverage, or the validator fails on the gap you just made.

---

## 7. What content never does

- **It never names a person, a place, a product or a date.**
- **It never carries anyone's answers.** An item cannot reference what either partner said; the
  engine does that, on the server, from documents no client can read.
- **It never appears in a notification, a memory or an export.** A notification carries a kind and an
  id (D-088); a memory holds the shape of an evening and the couple's own words, never a content id
  (D-090). That is why the memory library may list freely when nothing else may.
- **It is not private data.** The bundle ships in the APK, so anyone can read every item the app might
  ever show. What is private is which items a given couple sees — and that never leaves the server
  except as the night itself (SECURITY.md §4.10).
