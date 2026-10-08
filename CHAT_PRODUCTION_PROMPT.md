# Chat, made real — a build brief

The chat works end to end and looks like a prototype. This is what it takes to be the thing the
two of them actually use: a photo you can see, a camera you can open, a voice note that records,
and a screen that belongs to the rest of the app instead of being a list of grey boxes.

Written against the app at commit `d2bd1ce`. Each of the three broken things has one named cause,
and they are §1; the design is §2. Every file and token named below was checked to exist.

---

## 0. What is wrong, in one line each

| What was seen | What it actually is |
|---|---|
| A photo arrives as the words "A photo" | Nothing ever loads the bytes. There is no image anywhere in the chat UI. |
| Tapping photo gives no camera | Only the gallery picker is wired, and the app has no `FileProvider` for a camera to write into. |
| The mic does nothing | `LocalVoiceRecorder` is null outside a game screen, and nothing asks for the microphone. |
| It looks bad | It was built to prove the pipe works. §2 is the design. |

Found while reading for this brief, and in scope because it is the same feature: **a photo sent in
the chat sends two pushes.** `confirmMedia` calls `notifyPartner(..., 'THEY_SENT_SOMETHING', ...)`
unconditionally (`functions/src/media/media.ts:144`), and `onMessageCreated` then sends its own.
`notifyPartner` has no dedupe. Fix in §1.4.

---

## 1. The three that are broken

### 1.1 A photo has to be fetched before it can be shown

**Cause.** `Said()` in `ChatOverlay.kt` draws an eyebrow that says "A photo" and stops. The bytes
live in Storage and are reachable only through `MediaRepository.open(itemId, into)`, which fetches
them with the signed-in user's own credentials — nothing in this app mints a download URL (D-083),
so there is no URL an image loader could take. That is why there is no Coil in the project and why
adding one would not help.

**The trap.** `open()` is not a read. It calls `viewMedia`, which marks the item seen and, for a
once-only item, **deletes it as it is read**. Auto-loading every photo in the list would burn every
burn-after-view photo the moment it scrolled past. So:

- **An ordinary photo of theirs** loads inline, once, and is cached by media id for the session.
- **A once-only photo** draws a covered tile that says so — *"Once only. Tap to look."* — and is
  fetched on the tap. After that it is gone, and the tile says that instead.
- **Your own photo** is already on this phone: keep the file that was uploaded, keyed by media id,
  and draw it with no round trip. Never call `open()` on your own — `mine` media does not burn, but
  spending a Storage read to see a photo you just took is still wrong.

**What the message needs to know.** A `Message` carries a `mediaId` and nothing else, so the screen
cannot tell a once-only photo from an ordinary one, or a seven-second note from a one-second one.
The facts already exist on `MediaItem` (`kind`, `mine`, `burnAfterView`, `viewedAtMillis`), which
`MediaRepository.inbox` already exposes. Join them in `DefaultChatRepository` —
`combine(remote.observe(...), media.inbox)` — and widen the model:

```kotlin
data class Message(
    …,
    val media: MessageMedia? = null,
)

data class MessageMedia(
    val id: String,
    val kind: MediaKind,
    val burnAfterView: Boolean,
    /** Already looked at: a once-only one is gone, an ordinary one is just read. */
    val opened: Boolean,
    /** A voice note's length, for the bar. Null for a photo. */
    val seconds: Int?,
    /** 40 bars, 0..100, sampled at record time. Empty for a photo. */
    val levels: List<Int>,
)
```

A message whose media id is not in the inbox yet keeps `media = null` and draws as loading, not as
broken: the message and the media record arrive on two listeners and either may be first.

**Where seconds and levels live.** Not on the message — `validMessage` in `firestore.rules:268`
holds it to `hasOnly(['from','at','kind','text','mediaId'])`, and that tightness is a §7.3 row
worth keeping. They describe the media, so they go on the media record, written by the server:
`confirmMedia` already takes `input: unknown` and reads only `idOf(input)`, so add an optional
`seconds: number` and `levels: number[]` (length 40, each 0–100, anything else dropped) and include
them in its `ref.update`. Clients cannot write that collection, so no rule changes.

**Sampling the levels.** `VoiceRecorder` has `start/stop/cancel` and no way to read the signal, so
add one:

```kotlin
/** The loudest sample since the last call, 0..32767, or 0 when not recording. */
fun amplitude(): Int
```

`AndroidVoiceRecorder` delegates to `MediaRecorder.getMaxAmplitude()`. The ViewModel polls it every
100ms while recording, keeps the peaks, and reduces them to 40 buckets on stop. The live meter in
the composer reads the same poll, so one mechanism serves both.

**Decoding.** No new dependency: the bytes are a local `File`, so `BitmapFactory.decodeFile` with
`inJustDecodeBounds` first, then `inSampleSize` for the tile's width, on `Dispatchers.IO`, held in
a small LRU keyed by media id. Decoding a full-resolution photo on the main thread is the one sure
way to make this feature janky.

### 1.2 The camera needs a provider to write into

**Cause.** The composer launches `PickVisualMedia` only. `ActivityResultContracts.TakePicture` needs
a `content://` URI the camera app may write to, and this app has **no `FileProvider`** — the only
provider in `app/src/main/AndroidManifest.xml` is androidx-startup, and `app/src/main/res/xml/`
holds only the two backup rule files.

**The work.**

```xml
<!-- app/src/main/AndroidManifest.xml, inside <application> -->
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.files"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

```xml
<!-- app/src/main/res/xml/file_paths.xml -->
<paths><cache-path name="shared" path="." /></paths>
```

`${applicationId}` matters: the flavours are `com.shnapps.couple.dev` and `…staging`, and a
hardcoded authority makes two installed builds collide at install time.

The photo button then opens a two-line sheet — **Take one** / **Choose one** — because they are
different intentions and guessing is worse than asking. `TakePicture` writes into a cache file this
app made and hands over through the provider; `PickVisualMedia` returns a URI that `copyIn` already
copies into one.

Declaring `CAMERA` is **not** required and must not be added: `ACTION_IMAGE_CAPTURE` needs no
runtime permission as long as the permission is absent from the manifest. Adding it would make the
app ask for something it does not use.

### 1.3 The recorder is null outside a game

**Cause.** `LocalVoiceRecorder` (`core/ui/.../voice/VoiceRecorder.kt:116`) is
`staticCompositionLocalOf<VoiceRecorder?> { null }`, and the one place that provides it is
`DealtGameScreen.kt:57`. `ChatLayer` is drawn above the nav host, outside every screen, so
`LocalVoiceRecorder.current` is null and `recorder?.start()` is a no-op that reports nothing. There
is also no `RECORD_AUDIO` request anywhere in the chat, so even with a recorder the first `start()`
would be refused by the platform.

**The work.** Stop reaching for a composition local from a layer that has no provider:

- `ChatViewModel` takes `VoiceRecorder` by constructor injection. It is already `@Singleton` and
  already bound in `VoiceModule`, so this is one parameter.
- The composer holds a `RequestPermission` launcher for `RECORD_AUDIO` and starts on grant, exactly
  as `VoiceProof` in `PrimitiveRenderers.kt` already does. Copy that shape; it works.
- A refusal is said once, in the composer, and the mic stays where it is. Never a dialog.
- `onCleared` calls `cancel()`. A recorder left running is a microphone left on.

**While recording** the composer *becomes* the recording: a pulsing dot, a running `0:07`, the live
level meter from §1.1, a cancel on the left and a send on the right. Hold-to-record and
release-to-send is wrong here — a dare's proof is spoken, not shouted in two seconds — and the
recorder already caps itself with `setMaxDuration`.

### 1.4 One thing said, one push

`confirmMedia` notifies and `onMessageCreated` notifies, so a photo in the chat pushes twice.
The media notification exists for media sent **inside a round**, where there is no message. Give
`confirmMedia` an optional `quiet: true` and have `ChatRepository.send` pass it, so the chat's own
trigger — which is already rate-limited by `QUIET_MS` on `chatState` — is the only thing that
speaks. `NotifierTest` keeps its promise that the words come from the fixed table either way.

---

## 2. The design

The rest of the app is a dark, lit room with one accent that moves with the tier. The chat has to
belong to it. Every value below is a token that exists: `AfterhoursTheme.colors` /
`.spacing` / `.motion`, `MaterialTheme.colorScheme` / `.shapes`, and `tierColors(tier)` for the
accent. Nothing is a literal (and `shapes.extraLarge` stays reserved for the hero card).

### 2.1 The frame

- **Not a full-bleed sheet.** It rises from the bottom over `colorScheme.scrim` at ~60%, its top
  corners on `shapes.large`, a grab handle above the bar. Tapping the scrim closes it, as back does.
  What is underneath stays dimly visible, which is the point: the evening did not stop.
- The bar is one line: **close** left, *"The two of you"* in `EyebrowTextStyle` centre, **Stop**
  right. Stop never takes the accent (M7) — it keeps the quiet styling it has on every other screen.
- About 85% of the height, so the game still shows above it.
- It animates with `AfterhoursTheme.motion` and collapses under reduce-motion via
  `decorativeTween` — the panel's entrance is decoration, not a reveal.

### 2.2 The bubbles

| | Theirs | Yours |
|---|---|---|
| Side | left | right |
| Fill | `colorScheme.surfaceContainerHigh` | `tierColors(tier).container` |
| Text | `colorScheme.onSurface` | `tierColors(tier).onContainer` |
| Corners | `shapes.medium`, the corner nearest its own side at 4dp | the same, mirrored |

The tier is the session's if one is running, otherwise this phone's own — the chat picks up the
colour of the evening it is covering.

- **Grouped**: consecutive messages from the same person within five minutes close to `spacing.xxs`
  from `spacing.sm`, and only the last of a group carries a time — `bodySmall` in `colors.textFaint`,
  inside the bubble, trailing the last line.
- **Day separators**: a centred `EyebrowTextStyle` line — TODAY, YESTERDAY, then "14 NOVEMBER", in
  the phone's own zone, formatted as `onDay` in `:core:ui` already does.
- **An unread divider**: a `colors.edgeFaint` hairline and the word NEW above the first message this
  phone has not read, placed when the chat opens and not moving while it is open.
- **Max width 80%**, so which side a bubble is on is always legible and a bubble never reads as a
  banner.
- `rimTop`/`rimBottom` on a bubble's edge, as every other raised thing in the app has.

### 2.3 A photo in a bubble

- A rounded tile at the bubble's width, `aspectRatio` from the decoded bitmap, clipped to
  `shapes.medium`, the caption under it inside the same bubble.
- **Loading**: the tile at 4:3 with a shimmer, not a spinner on a grey box.
- **Once only**: no tile — a plate with a lock and *"Once only. Tap to look."* After it is gone:
  *"Seen. It's gone now."*, and no tap target.
- **Tapping an ordinary photo** opens it full-screen over the chat: pinch to zoom, back to close.
  Nothing is saved to the gallery, and there is no share — the bytes stay in this app's cache.

### 2.4 A voice note in a bubble

Not a play button and a filename:

- Play/pause on the left, then the **waveform** from §1.1 — 40 bars, drawn with `Canvas`, the same
  shape on both phones because the levels travelled with the record rather than being measured
  from the audio.
- The played part takes `tierColors(tier).accent`; the rest is `colors.textFaint`.
- The duration on the right: counting up while it plays, the total when idle.
- One at a time — starting one stops the other.
- A once-only note is a covered bar with the same words as a once-only photo.

### 2.5 The composer

- One rounded field on `colorScheme.surfaceContainerLow`, `shapes.small`, growing to four lines then
  scrolling.
- Left of it, a single **+** that opens Take one / Choose one / Record. Three icons in a row is the
  clutter the rest of this app does not have.
- Right of it, a send button that appears only when there is something to send.
- **While recording**, the field is replaced, as §1.3 says.
- No `imePadding()`: the nav host is already inset including the keyboard (D-028), and adding it
  again double-pads.

### 2.6 The states that get forgotten

- **Empty**: not "Nothing here yet." in grey. A line in the app's own voice — *"Say something to
  them."* — with the field focused.
- **Sending**: the bubble at 60% with no timestamp, exactly where it will settle.
- **Failed**: a quiet "Didn't send" under the bubble with one **Retry**, and the text never lost.
- **Long press on your own**: *Delete for both of us*, and nothing else.
- **Scrolled up**: a small jump-to-latest pill with the unread count, bottom right.
- **Partner away**: nothing at all. The chat is not a presence indicator and must not become one —
  presence is a session's business (`keepPresent`), not a conversation's.

### 2.7 Accessibility

`AccessibilityTest` and `ContrastTest` (D-096) are the audit, and this screen joins it:

- A bubble's content description says who and what: *"Them: did you do it, 9:42pm"*, *"You sent a
  photo"*, *"Them, voice note, 7 seconds"*. Build those with `buildString`, never a template
  literal inside a plain string — that mistake has now shipped twice here.
- 48dp targets on play, send, +, close and Stop.
- At 200% type the bubbles grow and the waveform does not; a golden proves it.
- The waveform is decorative: `contentDescription = null`, the duration carries the meaning.
- The recording state announces itself once when it starts, and the elapsed time is not a live
  region — a screen reader reading every second is unusable.

---

## 3. What this must not break

1. **A push still says nothing.** `THEY_SENT_SOMETHING` and the fixed table; no preview, no name,
   no count in the body (D-088).
2. **Nothing mints a download URL.** Bytes are fetched with the user's own credentials into this
   app's cache (D-083).
3. **Deleting the record is still the burn**, object first, record second (D-083). A cached bitmap
   is dropped from the LRU when its record goes.
4. **A game answer never reaches the chat.** No "share this to the chat", anywhere (D-069).
5. **Stop stays on the bar**, styled quietly (M7), and back still closes the chat and resets the
   panic counter.
6. **The layer stays a layer.** No `Route`, ever: a route takes the player's ViewModel out of the
   composition and the round is lost.
7. **`validMessage` keeps its five keys.** Everything new goes on the media record.

---

## 4. Tests

| What | Where |
|---|---|
| A once-only photo is not fetched until it is tapped | `ChatViewModelTest` |
| Your own photo renders with no call to `open()` | `ChatViewModelTest` |
| A message whose media record has not arrived yet draws as loading, not broken | `ChatRepositoryTest` |
| `seconds` and `levels` survive confirm, and a malformed `levels` is dropped | `media.unit` (functions) |
| A photo sent in the chat produces exactly one push | `media.int` or `chat.unit` (functions) |
| The mic asks for permission; a refusal is said once and not repeated | `ChatViewModelTest` |
| 40 buckets come out of a poll of any length | `VoiceLevelsTest` (pure) |
| Messages group, and only a group's last carries a time | `ChatGroupingTest` (pure) |
| Day separators land on day boundaries in the phone's zone | same |
| Goldens: conversation, photo loading, photo loaded, once-only covered, once-only gone, voice idle, voice playing, recording, failed send, empty, 200% type | `ChatSnapshotTest` |

The three existing suites (`ChatRepositoryTest`, `ChatViewModelTest`, `ChatSnapshotTest`) are kept
and widened, not replaced; `rules.test.mjs` needs nothing new.

---

## 5. Two-device acceptance

Added to `ACCEPTANCE.md`:

1. A sends a photo from the camera; B sees the image, not the word "photo".
2. A sends a once-only photo; B sees the plate, taps, sees it, and on scrolling back finds it gone
   with the right words. The object is gone from Storage —
   `node functions/scripts/dev-couple.js media` says so.
3. B records about seven seconds; A sees a waveform and `0:07`, plays it, and the bar fills as it goes.
4. A sends a photo in the chat: **one** notification on B's phone, not two.
5. A opens the chat mid-round, sends something, closes it: **the round has not moved.**
6. Airplane mode: three messages sit at 60%, then land in order when it comes back.
7. 200% type: every bubble readable, nothing clipped.
