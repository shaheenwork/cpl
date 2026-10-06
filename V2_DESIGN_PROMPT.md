# Afterhours v2 — HTML design prompt

Paste everything below into an AI that can produce a single HTML file. It is written to be
self-contained: the palette, type scale and copy are the real ones from the Android app, so what
comes back can be compared against the build rather than admired on its own.

---

## WHAT TO PRODUCE

A **single self-contained HTML file** — no build step, no external JS, styles in one `<style>` block.
Load only Google Fonts (Playfair Display, Inter) and nothing else from the network.

Lay the screens out as a **gallery of phone frames** on one scrolling page: each frame 390×844 CSS
pixels, rounded 44px, dark page background behind them, a small caption above each naming the screen
and its state. Group them under headings that match the sections below. Every frame must be a real
rendering of that screen — not a wireframe, not a description — so it can be read as a design review.

Make the frames *look like the app*: the full dark scene, the grain, the rim-lit cards, the glow on
the primary button. If a screen has more than one state (locked, waiting, sent) draw each state as
its own frame rather than annotating one.

Include a short design-rationale line under each group, one sentence, in the voice of a designer
explaining the decision — not marketing copy.

---

## THE PRODUCT, IN ONE PARAGRAPH

Afterhours is a private game app for one couple. It is a deck of games with five levels of heat.
Two people play it either **on one phone, together in a room**, or on **two paired phones when they
are apart**. There are no accounts, no profiles, no feed, no social anything. It opens and it plays.

## THE SIX RULES THAT SHAPE EVERY SCREEN

1. **No login. No sign-up. No email, no password, no account.** The only identity in the product is
   "this phone" and, once paired, "the other phone". Nothing on any screen may ask who someone is.
2. **Pairing is the only setup, and only for playing apart.** A six-digit code or a QR, under twenty
   seconds. A pair, once made, lasts until one of them taps **Unpair** in Settings. It survives
   reinstalls, reboots and months of not playing. Nothing expires it.
3. **The app is the games.** No separate messaging, no inbox, no "send a photo" feature. The only
   place a photo or a voice note exists is *inside a round*, when a dare asks one partner to prove
   they did it. It is a game mechanic, not a chat.
4. **Heat is five levels** — Mild, Flirty, Spicy, Hot, Extreme — and it is a ceiling, not an average.
   Picking Hot means the cards are hot.
5. **Mild and Flirty are free for ever. Spicy, Hot and Extreme are paid packs.** For the first month
   after install, everything is unlocked, and the app says so plainly with a date.
6. **Stop is always one tap away** on any screen that is playing something, and it is never styled
   like the heat accent.

---

## THE GAMES

All seven exist already and keep their names and taglines:

| Game | Tagline | Shape |
|---|---|---|
| Game Night | A guided evening: warm up, build, peak, then land | 30/45/60 min, four phases |
| Heat Dice | Three dice. Do what they say | Three dice roll together: How / Which / How long |
| Truth or Dare | The classic, with better questions | Turns alternate, phone passes each round |
| Fantasy Match | Only what you both said yes to | Each answers privately, only a mutual yes is shown |
| Would You Rather | Two choices. No sitting on the fence | Both answer in secret, revealed together |
| Win It / Owe It | Stake it in private. Match, and it is on | Both stake, a match makes it real |
| Hot or Not Quiz | Guess what they said. Score the ones you knew | One answers, the other guesses |

---

## THE SCREENS

Draw every one of these. States listed are separate frames.

### 1. Opening
- **Splash** — the wordmark on the scene, nothing else.
- **Age gate** — one line, one big button: *"We're both 18 or over — start"*. A quiet second line for
  *"I'm not 18"*. No checkbox, no form. Below the button, the five-level dial so the first choice is
  made here.

### 2. Home
The whole product in one screen.
- A large primary action: **Play Now**.
- A **Together / Apart** toggle. Together needs nothing. Apart shows, in place of the toggle's
  description, either *"Paired with their phone"* or *"Not paired yet"* with a quiet **Pair** action.
- The **heat dial**: five segments, named above, dots filled to the level. Locked levels (Spicy, Hot,
  Extreme, after the free month) carry a small lock and open the store rather than selecting.
- The **game grid**, Game Night first, then the six, each a card with its title and tagline.
- **States to draw**: free month active (with the banner *"Everything unlocked until 14 November"*),
  free month over with Spicy/Hot/Extreme locked, and paired-apart.

### 3. Pairing
- **Start pairing** — two choices: *Show my code* and *Enter their code*.
- **Showing a code** — six big characters, a QR beneath, and a line saying it works until used.
- **Entering a code** — six boxes, a numeric keypad, and nothing else.
- **Paired** — a confirmation that names what it means: *"These two phones are a pair until one of you
  unpairs. Nothing is stored anywhere else."*
- **State to draw**: a failed code, with a calm one-line error.

### 4. Playing on one phone
- **Hand-off** — the chromeless screen: one line, *"Pass the phone to Alex"*, one button. Nothing of
  what comes next is visible.
- **Who's playing** — asked once, at the first hand-off: two name fields, *That's us* / *Not now*.
- **A card** — the hero card, its heat eyebrow, the one-line game rule above it on the first round,
  the progress rail under the bar, **Too far** and **Stop** in the bar, and the primary action docked
  at the bottom.
- **Heat Dice rolling** — three dice mid-tumble, and a second frame with them landed, pips under each
  face, and a clock ring for the duration the third die gave.
- **Truth or Dare** — the choice, then the dare.
- **A reveal** — two answers side by side and what they add up to.
- **Session end** — what it came to, the aftercare card for a Hot or Extreme run, and three ways out.

### 5. Playing apart, on two phones
Same games, different waiting.
- **Your turn** — the card, with a small persistent note that the other phone is with you.
- **Waiting for them** — the one screen in the app that is allowed to be still: *"They're answering."*
  Never a spinner with no words.
- **A dare that asks for proof** — the dare, and the two ways to answer it: **Record a voice note** or
  **Take a photo**. Draw the capture state (recording, with a level meter) and the sent state.
- **Their proof arrived** — the card, their photo or a voice bar to play, and the reaction row. Draw
  a frame showing a photo that is deliberately only viewable once, with the words that say so.
- **Partner is away** — what the screen says when the other phone has not answered in a while.

### 6. Money
- **A locked level tapped** — not a wall, a card: what Spicy contains, what it costs, and a way back.
  One tap to buy, one to dismiss.
- **The store** — three packs (Spicy, Hot, Extreme) and a bundle of all three. Price, what is inside,
  and nothing else. No countdown timers, no fake urgency, no crossed-out prices.
- **The free month ending** — the banner's late state, at three days left.

### 7. Settings
One list, grouped.
- **You**: your limits, app lock, discreet icon (Afterhours / Notes / Calculator as three choosable
  tiles), panic.
- **The two of you**: paired status, and **Unpair and wipe** — which states in one sentence that it
  clears the pair on both phones and everything shared.
- **Packs**: what is owned, and restore.
- **About**: version, and nothing that looks like an account.

### 8. Safety
- **Stop pressed** — the evening pauses: *"Stopped."* and two ways on — turn it right down, or end.
- **The Extreme gate** — asked once a session: *"Both of you want this?"*, two taps to confirm.
- **Limits** — eight questions, each a short line with Yes / Maybe / No, progress along the top.

---

## THE VISUAL SYSTEM — USE THESE EXACT VALUES

Dark only. There is no light theme; do not generate one.

### Grounds
```
--scene:  #0B0708   the room, one per screen
--raised: #171115   rows, chips, fields, sheets
--card:   #1F1820   the hero card, and only the hero card
```
Each is about seven percent of luminance from the next. Lay a very faint grain over the scene and a
soft radial key light from the upper left, with a plum bounce from the lower right.

### Heat accents — one per level
```
Mild     plum        #C49BC4   container #3A1F36
Flirty   burgundy    #C75C7A   container #4A1426   text tone #DE8AA1
Spicy    burgundy deep #8E2A47 container #4A1426   text tone #DE8AA1
Hot      oxblood     #B02A42   container #3D0F1A   text tone #E0566F
Extreme  ember       #E2603F   container #4A1A0F
```
The ramp goes **darker and redder** as it climbs — escalation here is intimacy, not volume — and then
Extreme breaks the pattern by running hot, because the fifth level must not look like the fourth with
more of it. The whole screen takes the level's accent: the card's rim, the primary button, the
eyebrow.

### Metal, type and lines
```
--brass:        #D9B978   locks, codes, dice pips, eyebrows
--cream:        #F2E6DA   primary text
--cream-muted:  #CBBAAE   secondary text
--cream-faint:  #A2938A   hints and timestamps
--outline:      #7D6C74   edges that should register
--outline-faint:#3A2F35   hairlines that should not
--signal:       #FFB4A8   STOP and destructive only — never decorative
```

### Type
- **Display — Playfair Display.** Card titles, screen headlines, countdown numerals.
  52/58, 40/46, 32/38, 24/30.
- **Body — Inter.** Everything else. 16/24 body, 14/20 secondary, 13/18 small.
- **Eyebrows** are Inter, 11px, uppercase, letter-spacing 0.14em, in brass.

### Space and shape
A 4px scale: 2, 4, 8, 16, 24, 32, 48, 64. Screens use a 16px side gutter and 24px between groups.
Corners: 12px chips and fields, 20px rows, 28px the hero card, full-round for the primary button.

### The three things that make it look like this app and not a template
1. **The rim light.** A card's border is a vertical gradient — brighter along the top edge, nearly
   gone at the bottom. That one detail is most of the difference between a panel and an object.
2. **The glow.** The primary button carries a soft bloom of the level's accent beneath it.
3. **One hero per screen.** Exactly one surface is at `--card`. Everything else is raised or scene.
   A screen where everything is a card has no hierarchy at all.

---

## REAL COPY — USE THIS, DO NOT INVENT FILLER

Card bodies, at their real lengths:

- *Mild* — "Finish the evening with ten minutes where neither of you has to decide anything. Sit
  close, phones somewhere else, and name one small thing you liked about today."
- *Flirty* — "Kiss their neck slowly, working your way up to their ear. Whisper what you want to do
  to them. Go back to kissing. Make them shiver with anticipation."
- *Spicy* — "They stay completely still. You don't."
- *Hot* — "One hand only, and once it lands it does not leave until the clock does."
- *Extreme* — "Their eyes stay shut. Yours don't."

Interface words that already exist and should be kept exactly: **Play Now**, **Too far**, **Stop**,
**Not this one**, **I've got it**, **That's us**, **Not now**, **Start the clock**, **Keep going**,
**That's us for tonight**, **We're both in**, **Pass the phone over**.

Heat Dice labels: **How** / **Which** / **How long**, with faces like *Slowly*, *Their way*,
*2 minutes*, *Until they say stop*.

---

## TONE

Warm, direct, unembarrassed, never coy and never clinical. Short sentences. The app never giggles at
its own content and never lectures. Nothing is described as naughty, spicy or steamy in the interface
copy — the levels have names, and that is enough.

---

## WHAT NOT TO INCLUDE

No sign-in, sign-up, email, password, social login, profile, avatar, friends, feed, streak-shaming,
leaderboards, ads, cookie banners, onboarding carousels, tutorial overlays, or any screen whose only
job is to explain the app. No light theme. No stock photography of couples. No emoji in interface
copy. No fake urgency in the store. No separate messaging surface of any kind — media exists only
inside a round.
