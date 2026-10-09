'use strict';
/* =============================================================================================
   Afterhours, after dark — a working prototype of the redesign (MOOD_REDESIGN_PROMPT.md).

   It proves the design, not the engine: the logic below is the smallest thing that makes every
   control do what the app's control does, against real cards from content/dist/bundle.json.
   Where the real thing is the server's or the taxonomy's, a comment says so.
   ============================================================================================= */

const TIERS = ['MILD', 'FLIRTY', 'SPICY', 'HOT', 'EXTREME'];
const T = DATA.tiers;
const level = (tier) => T[tier].level;
const tierOfLevel = (n) => TIERS[Math.max(1, Math.min(5, n)) - 1];
const lower = (tier) => tier.toLowerCase();

const $ = (s, r = document) => r.querySelector(s);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const icon = (name, cls = '') => `<svg class="i ${cls}" aria-hidden="true"><use href="#i-${name}"/></svg>`;
const pick = (list, n) => list[((n % list.length) + list.length) % list.length];

/* ---------------------------------------------------------------------------------------------
   Drawn assets: flames, the wax seal, the card-back sigil, a QR
   --------------------------------------------------------------------------------------------- */

const FLAME = 'M6 .8C8.6 3.6 10.6 6.2 10.6 9.4a4.6 4.6 0 0 1-9.2 0C1.4 7.8 2.2 6.4 3.2 5.2c.4 1 1 1.6 1.6 1.9C4.6 4.6 5 2.6 6 .8z';
const CORE = 'M6 8.4c1.1 1.2 1.7 2.1 1.7 3.1a1.7 1.7 0 0 1-3.4 0c0-1 .6-1.9 1.7-3.1z';
function flames(n, { big = false, flicker = false } = {}) {
  const one = `<svg viewBox="0 0 12 16"><path d="${FLAME}"/><path class="core" d="${CORE}"/></svg>`;
  return `<span class="flames ${big ? 'big' : ''} ${flicker ? 'flicker' : ''}" aria-hidden="true">${one.repeat(n)}</span>`;
}

let sealPath = null;
function seal(size = 56) {
  if (!sealPath) {
    // Wax is never a perfect circle: a ring of points pushed in and out, joined smoothly.
    const n = 24;
    const pts = Array.from({ length: n }, (_, i) => {
      const a = (i / n) * Math.PI * 2;
      const r = 27.4 + Math.sin(i * 2.7) * 1.3 + Math.cos(i * 1.31) * 1.0;
      return [32 + Math.cos(a) * r, 32 + Math.sin(a) * r];
    });
    const mid = (p, q) => [(p[0] + q[0]) / 2, (p[1] + q[1]) / 2];
    const m0 = mid(pts[0], pts[1]);
    sealPath = `M${m0[0].toFixed(2)} ${m0[1].toFixed(2)}`;
    for (let i = 1; i <= n; i += 1) {
      const p = pts[i % n];
      const m = mid(p, pts[(i + 1) % n]);
      sealPath += ` Q${p[0].toFixed(2)} ${p[1].toFixed(2)} ${m[0].toFixed(2)} ${m[1].toFixed(2)}`;
    }
    sealPath += 'Z';
  }
  return `<svg class="seal" width="${size}" height="${size}" viewBox="0 0 64 64" aria-hidden="true">
    <path d="${sealPath}" fill="url(#wax)"/>
    <circle cx="32" cy="32" r="19.5" fill="none" stroke="rgb(0 0 0 / 0.32)" stroke-width="2.2"/>
    <circle cx="32" cy="31.2" r="19.5" fill="none" stroke="rgb(255 220 210 / 0.14)" stroke-width="0.8"/>
    <circle cx="32" cy="28.2" r="4.4" fill="#2A0510"/>
    <path d="M29.6 30.6 L27.8 40.4 H36.2 L34.4 30.6Z" fill="#2A0510"/>
    <ellipse cx="22" cy="17" rx="7" ry="3.4" fill="rgb(255 230 225 / 0.16)" transform="rotate(-28 22 17)"/>
  </svg>`;
}

function sigil() {
  return `<svg class="sigil breath" viewBox="0 0 150 200" fill="none" stroke="currentColor" stroke-width="1" aria-hidden="true">
    <path d="M75 26 L122 100 L75 174 L28 100 Z" opacity="0.75"/>
    <path d="M75 50 L106 100 L75 150 L44 100 Z" opacity="0.5"/>
    <circle cx="75" cy="100" r="27"/>
    <circle cx="75" cy="100" r="31" opacity="0.35"/>
    <circle cx="75" cy="94" r="6" fill="currentColor" fill-opacity="0.18"/>
    <path d="M72 99 L69.5 113 H80.5 L78 99" fill="currentColor" fill-opacity="0.18"/>
    <path d="M75 6 V26 M75 174 V194 M8 100 H28 M122 100 H142" opacity="0.6"/>
    <circle cx="75" cy="16" r="1.6" fill="currentColor"/><circle cx="75" cy="184" r="1.6" fill="currentColor"/>
    <circle cx="16" cy="100" r="1.6" fill="currentColor"/><circle cx="134" cy="100" r="1.6" fill="currentColor"/>
    <path d="M10 40 Q10 10 40 10 M140 40 Q140 10 110 10 M10 160 Q10 190 40 190 M140 160 Q140 190 110 190" opacity="0.55"/>
    <path d="M22 50 Q30 62 22 74 M128 50 Q120 62 128 74 M22 126 Q30 138 22 150 M128 126 Q120 138 128 150" opacity="0.35"/>
  </svg>`;
}

/** A decorative QR, drawn from the code. It is the shape of one, for the design — not a real one. */
function qr(code) {
  const n = 25;
  let seed = [...code].reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0;
  const rnd = () => { seed = (seed * 1664525 + 1013904223) >>> 0; return seed / 4294967296; };
  const cells = [];
  const finder = (x, y) => {
    cells.push(`<rect x="${x}" y="${y}" width="7" height="7" rx="1.6"/>`);
    cells.push(`<rect x="${x + 1}" y="${y + 1}" width="5" height="5" rx="1" fill="#F6D6CC"/>`);
    cells.push(`<rect x="${x + 2}" y="${y + 2}" width="3" height="3" rx="0.6"/>`);
  };
  const inFinder = (x, y) => (x < 8 && y < 8) || (x > n - 9 && y < 8) || (x < 8 && y > n - 9);
  for (let y = 0; y < n; y += 1) for (let x = 0; x < n; x += 1) if (!inFinder(x, y) && rnd() > 0.52) cells.push(`<rect x="${x}" y="${y}" width="1" height="1" rx="0.25"/>`);
  finder(0, 0); finder(n - 7, 0); finder(0, n - 7);
  return `<svg viewBox="0 0 ${n} ${n}" fill="#1A0710" aria-label="QR code for ${esc(code)}" role="img">${cells.join('')}</svg>`;
}

/* ---------------------------------------------------------------------------------------------
   State
   --------------------------------------------------------------------------------------------- */

const NAMES = ['Alex', 'Sam'];
const S = {
  screen: 'home',
  mode: 'APART',
  paired: true,
  tier: 'SPICY',
  ownedCeiling: 2,          // Mild and Flirty are free for ever; a pack raises this
  freeMonth: true,          // everything open for the first month (a date, never a countdown)
  freeUntil: '8 November',
  faceDown: true,
  extremeOk: false,         // the gate is asked once a session
  gateStep: 0,
  stoppedFrom: 'deck',
  limits: { KISSING: 'YES', TOUCH: 'YES', TEASING: 'YES', ROLE_PLAY: 'MAYBE', SENSORY: 'MAYBE', VERBAL: 'YES', POWER_PLAY: null, FANTASY: null },
  sheet: null,
  underWay: 'theirs',       // null | 'mine' | 'theirs'
  locked: null,
  toast: null,
  deck: null,
  night: null,
  game: null,
  apart: 'turn',
  pair: { stage: 'choose', entered: '', code: '482 913' },
  chat: 'conversation',
  store: 'packs',
  lockPin: '',
  states: {},               // loading / error toggles per screen
};

const isOpen = (tier) => S.freeMonth || level(tier) <= S.ownedCeiling;

/* ---------------------------------------------------------------------------------------------
   The deck — a prototype of InstantDeck: climbs to the tier, never repeats, honours refusals
   --------------------------------------------------------------------------------------------- */

const isAftercare = (c) => (c.tags || []).includes('aftercare');
// The real filter is the taxonomy's: each LimitTag owns themes, and a card's excludedByBoundaries
// names them. Here a "No" hides cards whose own tags carry the tag's word — close enough to show.
const LIMIT_WORDS = {
  KISSING: ['kiss', 'kissing'], TOUCH: ['touch', 'massage'], TEASING: ['teasing', 'tease'],
  ROLE_PLAY: ['roleplay', 'role-play', 'character'], SENSORY: ['sensory', 'blindfold', 'senses'],
  VERBAL: ['verbal', 'confession', 'words'], POWER_PLAY: ['power', 'control'], FANTASY: ['fantasy'],
};
function refusedByLimits(card) {
  const tags = (card.tags || []).map((t) => t.toLowerCase());
  return Object.entries(S.limits).some(([tag, v]) => v === 'NO' && LIMIT_WORDS[tag].some((w) => tags.includes(w)));
}
function pool() {
  const d = S.deck;
  return DATA.cards
    .filter((c) => !isAftercare(c) && c.intensity <= level(S.tier))
    .filter((c) => !d.lane || c.pack === d.lane)
    .filter((c) => !d.refused.has(c.pack + ':' + (c.tags || [])[0]))
    .filter((c) => !refusedByLimits(c))
    // The climb: gentler first, up to tonight's tier.
    .sort((a, b) => a.intensity - b.intensity || a.id.localeCompare(b.id));
}
function newDeck() {
  S.deck = { index: 0, seen: new Set(), lane: null, refused: new Set(), yeses: 0, played: 0, card: null, revealed: false, run: 6, timer: null, interstitial: null };
  dealNext();
}
function dealNext() {
  const d = S.deck;
  const next = pool().find((c) => !d.seen.has(c.id));
  d.card = next || null;
  d.revealed = !S.faceDown;
  if (next) d.seen.add(next.id);
}
function laneOptions() {
  const packs = new Map();
  for (const c of DATA.cards) if (!isAftercare(c) && c.intensity <= level(S.tier)) packs.set(c.pack, (packs.get(c.pack) || 0) + 1);
  return DATA.packs.filter((p) => packs.has(p.id)).map((p) => ({ ...p, count: packs.get(p.id) }));
}
const aftercareCard = () => DATA.cards.find(isAftercare);

/* ---------------------------------------------------------------------------------------------
   Shared pieces
   --------------------------------------------------------------------------------------------- */

function bar({ back = null, title = '', right = '', left = '' } = {}) {
  return `<header class="bar">
    ${back ? `<button class="iconbtn" data-act="go" data-arg="${back}" aria-label="Back">${icon('Back')}</button>` : left || '<span class="spacer"></span>'}
    <div class="title-slot"><span class="eyebrow">${esc(title)}</span></div>
    ${right || '<span class="spacer"></span>'}
  </header>`;
}

const chatButton = (cls = 'iconbtn') => (S.paired && S.mode === 'APART'
  ? `<button class="${cls}" data-act="sheet" data-arg="chat" aria-label="Chat, 2 unread">${icon('Message', cls === 'railbtn' ? 'sm' : '')}${cls === 'railbtn' ? '<span>Chat</span>' : '<span class="dot-badge"></span>'}</button>`
  : '');

/**
 * The safety rail: Stop, always; Too far where a card or a prompt can be refused; Pause in a game
 * played a phone each; the chat when paired. Bottom of the screen, in the thumb's reach, never the
 * accent. `proposed` marks a control the screen does not have today (ANALYSIS.md §4).
 */
function rail({ tooFar = false, pause = false, proposed = false, tooFarProposed = false } = {}) {
  return `<nav class="rail" aria-label="Safety">
    <button class="stop" data-act="stop" aria-label="Stop — ends everything, asks nothing">${icon('SafeWord')}<span>Stop</span>${proposed ? '<span class="proposed">proposed</span>' : ''}</button>
    <span class="grow"></span>
    ${tooFar ? `<button class="railbtn" data-act="sheet" data-arg="toofar" aria-label="Too far — skip this kind of thing">${icon('Skip', 'sm')}<span>Too far</span>${tooFarProposed ? '<span class="proposed">proposed</span>' : ''}</button>` : ''}
    ${pause ? `<button class="railbtn" data-act="apart" data-arg="paused" aria-label="Pause">${icon('Pause', 'sm')}<span>Pause</span></button>` : ''}
    ${chatButton('railbtn')}
  </nav>`;
}

function heatChip() {
  const t = T[S.tier];
  return `<button class="chip tier-chip" data-act="sheet" data-arg="heat" aria-label="Heat: level ${t.level} of 5, ${t.label}. Change it">${flames(t.level)}<span>${t.label}</span></button>`;
}

function heatSelector({ compact = false } = {}) {
  const t = T[S.tier];
  const pct = ((t.level - 0.5) / 5) * 100;
  return `<div class="heat" role="radiogroup" aria-label="How far tonight">
    <div class="heat-cells">
      ${TIERS.map((k) => {
        const x = T[k];
        const open = isOpen(k);
        const on = k === S.tier;
        const label = `Level ${x.level} of 5, ${x.label}${open ? '' : ', locked'}${on ? ', selected' : ''}`;
        return `<button class="heat-cell ${on ? 'on' : ''} ${open ? '' : 'locked'}" role="radio" aria-checked="${on}" aria-label="${label}"
            data-act="tier" data-arg="${k}" style="--cell-accent: var(--tier-${lower(k)}-accent)">
          ${open ? '' : `<svg class="i lock" aria-hidden="true"><use href="#i-Lock"/></svg>`}
          ${flames(x.level, { flicker: on })}
          <span class="name">${x.label}</span>
        </button>`;
      }).join('')}
    </div>
    <div class="heat-bar" aria-hidden="true"><span class="veil" style="width:${100 - (t.level / 5) * 100}%"></span><span class="ember" style="left:${pct}%"></span></div>
    ${compact ? '' : `<div class="heat-hint"><p class="tease" style="margin:0">${esc(t.hint)}</p><span class="eyebrow heat">${t.label}</span></div>`}
  </div>`;
}

function cardFace(card, { faceDown = true, revealed = false, number = 1, holder = "Whoever's holding the phone" } = {}) {
  const t = T[tierOfLevel(card.intensity)];
  const meta = [t.label, card.durationMin ? `${card.durationMin} min` : null].filter(Boolean).join(' · ');
  // The card wears its own tier, so a run that climbs visibly warms card by card.
  return `<div class="card-stage deal" data-tier="${lower(tierOfLevel(card.intensity))}">
    <div class="card3d ${!faceDown || revealed ? 'up' : ''}" ${faceDown && !revealed ? 'role="button" tabindex="0" data-act="reveal" aria-label="Face down. Tap to reveal the card"' : ''} id="card3d">
      <div class="face front">
        <span class="eyebrow gold">Card ${String(number).padStart(2, '0')}</span>
        ${sigil()}
        <p class="tease">tap to reveal</p>
      </div>
      <div class="face back" aria-live="polite">
        <div class="row between"><span class="eyebrow heat">${esc(meta)}</span>${flames(card.intensity)}</div>
        <h2 class="card-title">${esc(card.title)}</h2>
        ${card.subtitle ? `<p class="tease subtitle">${esc(card.subtitle)}</p>` : ''}
        <p class="card-body">${esc(card.body)}</p>
        <div class="meta">
          <div class="moods">${(card.moods || []).slice(0, 3).map((m) => `<span>${esc(m)}</span>`).join('')}</div>
          <span class="small">${esc(holder)}</span>
        </div>
      </div>
    </div>
  </div>`;
}

const sheetFrame = (body, { tall = false, label = '' } = {}) => `<div class="scrim" data-act="close-sheet"></div>
  <section class="sheet ${tall ? 'tall' : ''}" role="dialog" aria-modal="true" aria-label="${esc(label)}">
    <div class="handle" aria-hidden="true"></div>
    ${body}
  </section>`;

/* ---------------------------------------------------------------------------------------------
   Screens
   --------------------------------------------------------------------------------------------- */

const SCREENS = {};

SCREENS.start = () => ({
  html: `<div class="scroll"><div class="pad stack section top-space bottom-space" style="padding-top:72px">
    <div class="stack item center">${seal(84)}<span class="wordmark">Afterhours<b>FOR THE TWO OF YOU</b></span></div>
    <div class="stack tight center">
      <h1 class="headline">One question,<br><em>then we start.</em></h1>
      <p class="body">No account, no sign-up, no email. Nothing leaves this phone unless you pair it.</p>
    </div>
    <div class="stack item">
      <span class="eyebrow">How far tonight?</span>
      ${heatSelector()}
      <p class="small">Change it any time, mid-game, with one thumb.</p>
    </div>
  </div></div>
  <div class="dock">
    <button class="btn primary tall block" data-act="start">${icon('Flame')}We're both 18 or over — start</button>
    <button class="btn quiet block" data-act="go" data-arg="not-adult">I'm not 18</button>
  </div>`,
});

SCREENS.agegate = () => ({
  html: `<div class="fill pad stack section center">
    ${seal(96)}
    <div class="stack tight center">
      <span class="eyebrow gold">Before we start</span>
      <h1 class="headline">This is an app<br><em>for adults.</em></h1>
      <p class="body">Everything inside is made for two consenting adults in a relationship.</p>
    </div>
    <div class="velvet panel-card" style="text-align:left">
      <span class="eyebrow">What you're confirming</span>
      <p class="body strong">You are 18 or over, and you are happy to see adult content.</p>
    </div>
  </div>
  <div class="dock">
    <button class="btn primary tall block" data-act="go" data-arg="home">${icon('Check')}I'm 18 or over</button>
    <button class="btn quiet block" data-act="go" data-arg="not-adult">I'm not</button>
  </div>`,
});

SCREENS['not-adult'] = () => ({
  cls: 'calm',
  html: `<div class="fill pad stack center"><p class="title">Then this one isn't for you.</p><p class="body">The app closes here. Nothing was saved.</p></div>`,
});

SCREENS.home = () => {
  if (S.states.home === 'loading') return { html: homeLoading() };
  const apart = S.mode === 'APART';
  const games = DATA.games;
  const glyph = { HEAT_DICE: 'Dice', TRUTH_OR_DARE: 'Facets', FANTASY_MATCH: 'Rings', WIN_IT_OWE_IT: 'Scales', WOULD_YOU_RATHER: 'Fork', HOT_OR_NOT: 'FlameAsk' };
  const underWay = S.underWay && apart && S.paired ? `
    <section class="velvet lit under-way ${S.underWay === 'theirs' ? 'theirs' : ''}" aria-label="A game under way">
      <div class="row between"><span class="eyebrow gold">Under way</span><span class="who"><span class="av">${S.underWay === 'theirs' ? 'S' : 'A'}</span></span></div>
      <p class="title">${S.underWay === 'theirs' ? "They've started <em style=\"font-style:italic;color:var(--tier-on-dark)\">a game</em>. It's waiting for you." : 'Your game is waiting. Pick up where you left off.'}</p>
      <button class="btn primary block" data-act="apart" data-arg="turn">${icon('Play')}Pick it up</button>
    </section>` : '';
  const pairStrip = apart ? `
    <div class="velvet pair-strip ${S.paired ? 'on' : ''}">
      <span class="pair-dots" aria-hidden="true"><i></i><i></i></span>
      <div class="grow"><span class="label" style="display:block">${S.paired ? 'Paired with their phone.' : 'Not paired yet.'}</span>
        <span class="small">${S.paired ? 'Games open on both phones.' : 'Pair once, and every game opens on both.'}</span></div>
      ${S.paired ? `<button class="btn quiet" data-act="go" data-arg="settings" aria-label="Pairing settings">${icon('Link', 'sm')}</button>` : `<button class="btn gold" data-act="pair" data-arg="choose">${icon('Link', 'sm')}Pair</button>`}
    </div>` : '';
  const locked = S.locked ? `
    <div class="velvet panel-card gold-hairline" role="status">
      <div class="row between"><span class="eyebrow gold">${T[S.locked].label} is a pack</span><button class="iconbtn" data-act="unlock-dismiss" aria-label="Not now">${icon('Close', 'sm')}</button></div>
      <p class="body strong">It opens ${T[S.locked].label} and every level below it — yours to keep.</p>
      <button class="btn gold block" data-act="go" data-arg="store">${icon('Lock', 'sm')}See the packs</button>
    </div>` : '';
  return {
    html: `${bar({
      left: `<span style="padding-left:6px">${seal(34)}</span>`,
      // Chat and settings side by side, neither covering the other (D-141).
      right: `<div class="row" style="gap:0">${chatButton()}<button class="iconbtn" data-act="go" data-arg="settings" aria-label="Settings">${icon('Settings')}</button></div>`,
    })}
    <div class="scroll"><div class="pad stack" style="padding-bottom:120px">
      <div class="stack tight" style="padding-top:8px">
        <span class="eyebrow heat">${apart && S.paired ? 'Tonight, the two of you' : 'Tonight'}</span>
        <h1 class="display">${apart ? 'Two phones,<br><em>one evening.</em>' : 'One phone,<br><em>both of you.</em>'}</h1>
      </div>
      ${S.freeMonth ? `<div class="ribbon">${icon('Gift')}<span>Everything's open until <b>${S.freeUntil}</b>.</span></div>` : ''}
      ${underWay}
      <div class="stack item">
        <div class="mode-switch ${apart ? 'apart' : ''}" role="group" aria-label="Together or apart">
          <span class="thumb" aria-hidden="true"></span>
          <button aria-pressed="${!apart}" data-act="mode" data-arg="TOGETHER">${icon('Heart', 'sm')}Together</button>
          <button aria-pressed="${apart}" data-act="mode" data-arg="APART">${icon('Phones', 'sm')}Apart</button>
        </div>
        <p class="small">${apart ? 'A phone each. Both of you see every round.' : 'One phone, passed between you.'}</p>
        ${pairStrip}
      </div>
      <div class="stack item">
        <span class="eyebrow">How far tonight</span>
        ${heatSelector()}
        ${locked}
      </div>
      <div class="ornament">${icon('Keyhole')}</div>
      <div class="stack item">
        <span class="eyebrow">Or play something</span>
        <div class="games">
          <button class="velvet tile wide" data-act="night">
            <span class="glyph">${icon('Candle')}</span>
            <span class="grow"><span class="t" style="display:block">Game Night</span><span class="d">A guided evening: warm up, build, peak, then land somewhere gentle.</span></span>
            ${icon('Forward', 'sm')}
          </button>
          ${games.map((g) => `<button class="velvet tile" data-act="game" data-arg="${g.id}">
            <span class="glyph">${icon(glyph[g.id] || 'Spark')}</span>
            <span class="t">${esc(g.title)}</span><span class="d">${esc(g.tagline)}</span>
          </button>`).join('')}
        </div>
      </div>
      <div class="row" style="gap:8px;flex-wrap:wrap">
        <button class="chip" data-act="sheet" data-arg="limits">${icon('Limits')}Your limits</button>
        <button class="chip" data-act="go" data-arg="store">${icon('Gift')}Packs</button>
      </div>
    </div></div>
    <div class="dock" style="position:absolute;left:0;right:0;bottom:0">
      <button class="btn primary tall block" data-act="play">${flames(T[S.tier].level, { flicker: true })}<span>Play now</span></button>
    </div>`,
  };
};

function homeLoading() {
  return `${bar()}<div class="pad stack" aria-busy="true" aria-label="Loading">
    <div class="skeleton" style="height:96px;margin-top:8px"></div>
    <div class="skeleton" style="height:56px"></div>
    <div class="skeleton" style="height:110px"></div>
    <div class="games"><div class="skeleton" style="height:112px;grid-column:span 2"></div><div class="skeleton" style="height:132px"></div><div class="skeleton" style="height:132px"></div></div>
  </div>`;
}

/* --- the deck --- */

SCREENS.deck = () => {
  const d = S.deck;
  if (S.states.deck === 'loading') return deckFrame(`<div class="fill pad center"><div class="card3d skeleton" style="min-height:430px" aria-label="Shuffling"></div><p class="tease" style="margin-top:16px">Shuffling…</p></div>`, false);
  if (S.states.deck === 'error') return deckFrame(`<div class="fill pad stack center">${icon('CardBack', 'xl')}<p class="title">The cards didn't load.</p><p class="body">Nothing was lost. Try again.</p><button class="btn secondary" data-act="state" data-arg="deck:">${icon('Retry', 'sm')}Try again</button></div>`, false);
  if (d.interstitial === 'turnup') return deckFrame(turnUp(), false);
  if (d.interstitial === 'complete') return deckFrame(runComplete(), false, 'complete');
  if (!d.card) return deckFrame(nothingLeft(), false, 'nothing');
  return deckFrame(`<div class="fill" style="justify-content:center">${cardFace(d.card, { faceDown: S.faceDown, revealed: d.revealed, number: d.played + 1 })}</div>`, true);
};

function deckFrame(content, playing, kind = '') {
  const d = S.deck;
  const timed = playing && d.card && d.card.durationMin;
  const lane = d.lane ? DATA.packs.find((p) => p.id === d.lane) : null;
  return {
    html: `${bar({ back: 'home', title: 'Tonight', right: heatChip() })}
      <div class="progress" aria-label="Card ${Math.min(d.played + 1, d.run)} of ${d.run}">${Array.from({ length: d.run }, (_, i) => `<i class="${i < d.played ? 'done' : i === d.played ? 'now' : ''}"></i>`).join('')}</div>
      <div class="row pad" style="padding-top:12px;gap:8px">
        <button class="chip" data-act="sheet" data-arg="lanes">${icon('Lanes')}${lane ? esc(lane.title) : 'Everything'}</button>
      </div>
      ${content}
      ${playing ? `<div class="dock" style="padding-top:8px">
        ${timed ? `<button class="btn gold block" data-act="timer" ${d.revealed ? '' : 'disabled'}>${icon('Clock', 'sm')}Start the ${d.card.durationMin} minutes</button>` : ''}
        <div class="row">
          <button class="btn secondary" data-act="skip" aria-label="Skip — pass, no questions asked">${icon('Skip', 'sm')}Skip</button>
          <button class="btn primary" style="flex:1.6" data-act="done" ${d.revealed ? '' : 'disabled'} id="done-btn">${icon('Check', 'sm')}Done</button>
        </div>
        <p class="small center" style="text-align:center">Skip is always free — pass, no questions asked.</p>
      </div>` : ''}
      ${kind === 'complete' ? `<div class="dock"><button class="btn primary tall block" data-act="keep-going">${icon('Flame', 'sm')}Keep going</button><button class="btn quiet block" data-act="go" data-arg="home">That's us for tonight</button></div>` : ''}
      ${kind === 'nothing' ? `<div class="dock">${level(S.tier) < 5 ? `<button class="btn primary block" data-act="tier-up">${icon('Up', 'sm')}Turn it up</button>` : ''}<button class="btn quiet block" data-act="go" data-arg="home">Back home</button></div>` : ''}
      ${rail({ tooFar: playing })}`,
  };
}

function turnUp() {
  const next = tierOfLevel(level(S.tier) + 1);
  return `<div class="fill pad stack section center">
    <div class="gate-lock warm">${flames(T[next].level, { big: true, flicker: true })}</div>
    <div class="stack tight center"><span class="eyebrow heat">Five in a row</span>
      <h2 class="headline">Turn it <em>up?</em></h2>
      <p class="body">You've both said yes to five in a row. ${esc(T[next].label)} is next — or stay right here.</p></div>
    <div class="stack item" style="width:100%">
      <button class="btn primary tall block" data-act="turn-up" data-arg="${next}">${icon('Up', 'sm')}Yes — ${esc(T[next].label.toLowerCase())}</button>
      <button class="btn secondary block" data-act="keep-here">Keep it here</button>
    </div>
  </div>`;
}

function runComplete() {
  const care = level(S.tier) >= 4 ? aftercareCard() : null;
  return `<div class="scroll"><div class="pad stack" style="padding-top:20px">
    <div class="stack tight center"><span class="eyebrow gold">That's ${S.deck.played}</span><h2 class="headline">Stop here, <em>or keep going?</em></h2>
      <p class="body">There is plenty left, and an evening does not end on its own.</p></div>
    ${care ? `<div class="stack item">
      <div class="ornament">${icon('Aftercare')}</div>
      <span class="eyebrow heat center" style="text-align:center">Before you put it down</span>
      <div class="card-static lit deal"><span class="eyebrow">Aftercare · ${care.durationMin || 5} min</span><h3 class="card-title">${esc(care.title)}</h3>${care.subtitle ? `<p class="tease">${esc(care.subtitle)}</p>` : ''}<p class="card-body">${esc(care.body)}</p></div>
      <p class="tease center" style="text-align:center">Check in with each other.</p>
    </div>` : ''}
  </div></div>`;
}

function nothingLeft() {
  return `<div class="fill pad stack center">
    <div style="color:var(--color-gold);opacity:0.4;width:110px;height:146px">${sigil().replace('class="sigil breath"', 'class="sigil" style="width:110px;height:146px"')}</div>
    <p class="title">Nothing left at this tier.</p>
    <p class="body">Turn it up, or come back tomorrow — there is more at the other levels.</p>
  </div>`;
}

SCREENS.timer = () => {
  const d = S.deck;
  const total = (d.card?.durationMin || 5) * 60;
  return {
    html: `${bar({ back: 'deck', title: d.card ? d.card.title : 'Timer', right: heatChip() })}
    <div class="fill pad stack section center">
      <p class="tease" style="max-width:300px">${esc(d.card?.subtitle || 'Take your time.')}</p>
      <div class="ring" id="ring">
        <svg viewBox="0 0 180 180"><circle class="track" cx="90" cy="90" r="80" fill="none" stroke-width="6"/>
          <circle class="arc" id="ring-arc" cx="90" cy="90" r="80" fill="none" stroke-width="6" stroke-dasharray="502.65" stroke-dashoffset="0"/></svg>
        <span class="numeral" id="ring-num" role="timer" aria-live="off">${fmt(total)}</span>
      </div>
      <p class="small">The last stretch warms to flame.</p>
    </div>
    <div class="dock"><div class="row">
      <button class="btn secondary" data-act="timer-pause" id="timer-pause">${icon('Pause', 'sm')}Pause</button>
      <button class="btn primary" style="flex:1.6" data-act="done">${icon('Check', 'sm')}Done</button>
    </div></div>
    ${rail({ tooFar: true })}`,
    mount: () => startTimer(total),
  };
};
const fmt = (s) => `${Math.floor(s / 60)}:${String(Math.max(0, s) % 60).padStart(2, '0')}`;
let ticking = null;
function startTimer(total) {
  stopTimer();
  let left = total;
  let paused = false;
  const tick = () => {
    const arc = $('#ring-arc');
    const num = $('#ring-num');
    if (!arc) return stopTimer();
    arc.style.strokeDashoffset = String(502.65 * (1 - left / total));
    num.textContent = fmt(left);
    $('#ring').classList.toggle('last', left <= total * 0.2);
  };
  tick();
  ticking = setInterval(() => { if (!paused && left > 0) { left -= 1; tick(); } }, 1000);
  ticking.jump = () => { left = Math.min(left, Math.ceil(total * 0.18)); tick(); };
  ticking.toggle = () => { paused = !paused; return paused; };
}
function stopTimer() { if (ticking) clearInterval(ticking); ticking = null; }

/* --- safety --- */

SCREENS.stopped = () => ({
  cls: 'calm',
  tier: 'MILD',
  html: `${bar({ title: 'Stopped' })}
  <div class="fill pad stack section center">
    <div class="shield-mark">${icon('SafeWord')}</div>
    <div class="stack tight center">
      <h1 class="headline">Everything's stopped.</h1>
      <p class="body">Nothing is waiting on either of you. Take as long as you like — turn it right down and carry on gently, or end here.</p>
    </div>
    <p class="tease">Breathe. Check in with each other.</p>
  </div>
  <div class="dock">
    <button class="btn secondary tall block" data-act="gentle">${icon('Down', 'sm')}Turn it down to Mild</button>
    <button class="btn secondary block" data-act="go" data-arg="home">End here</button>
  </div>`,
});

SCREENS.gate = () => ({
  tier: 'EXTREME',
  html: `${bar({ back: S.gateBack && S.gateBack !== 'gate' ? S.gateBack : 'home', title: 'Extreme' })}
  <div class="fill pad stack section center">
    <div class="gate-lock ${S.gateStep ? 'warm' : ''}" id="gate-lock">${icon(S.gateStep > 1 ? 'LockOpen' : 'Lock')}</div>
    <div class="gate-steps" aria-hidden="true"><i class="${S.gateStep >= 1 ? 'on' : ''}"></i><i class="${S.gateStep >= 2 ? 'on' : ''}"></i></div>
    <div class="stack tight center">
      <span class="eyebrow heat">Once, tonight</span>
      <h1 class="headline">Both of you <em>want this?</em></h1>
      <p class="body">Extreme is the furthest this goes. Nothing here is a dare and nothing is a measure of anything — the stop is on every screen, and it works mid-anything.</p>
    </div>
  </div>
  <div class="dock">
    <button class="btn primary tall block" data-act="gate-confirm">${icon('Check', 'sm')}${S.gateStep ? 'Yes — both of us' : "We're both in"}</button>
    <button class="btn quiet block" data-act="gate-down">${icon('Down', 'sm')}Not tonight — turn it down</button>
  </div>
  ${rail()}`,
});

/* --- Game Night --- */

const PHASES = [['Warm-up', 0.25], ['Building', 0.35], ['Peak', 0.4], ['Aftercare', 0]];
function planNight(minutes) {
  const top = level(S.tier);
  const pool = DATA.cards.filter((c) => !isAftercare(c) && c.intensity <= top).sort((a, b) => a.intensity - b.intensity);
  const low = pool.filter((c) => c.intensity <= Math.max(1, top - 2));
  const mid = pool.filter((c) => c.intensity === Math.max(1, top - 1) || c.intensity === Math.max(1, top - 2));
  const peak = pool.filter((c) => c.intensity === top);
  const per = minutes >= 60 ? 3 : minutes >= 45 ? 2 : 2;
  const cards = [
    ...low.slice(0, per).map((c) => [0, c]),
    ...mid.filter((c) => !low.slice(0, per).includes(c)).slice(0, per).map((c) => [1, c]),
    ...peak.slice(-per).map((c) => [2, c]),
    [3, aftercareCard()],
  ];
  return { minutes, cards, at: 0 };
}

SCREENS['night-length'] = () => {
  const n = S.night || { minutes: 30 };
  return {
    html: `${bar({ back: 'home', title: '', right: heatChip() })}
    <div class="fill pad stack section">
      <div class="stack tight">
        <span class="eyebrow heat">Game Night</span>
        <h1 class="display">How long have <em>you got?</em></h1>
        <p class="body">It warms up, builds, peaks, and lands somewhere gentle. Nothing in it goes past ${esc(T[S.tier].label)}.</p>
      </div>
      <div class="stack tight">
        <span class="eyebrow">The evening</span>
        <div class="lengths" role="radiogroup" aria-label="The evening's length">
          ${[[30, 'minutes'], [45, 'minutes'], [60, 'an hour']].map(([m, u]) => `<button class="length" role="radio" aria-checked="${n.minutes === m}" aria-pressed="${n.minutes === m}" data-act="night-length" data-arg="${m}"><span class="n">${m === 60 ? '1' : m}</span><span class="u">${m === 60 ? 'hour' : u}</span></button>`).join('')}
        </div>
      </div>
      <div class="phases" aria-hidden="true" style="padding:0">${PHASES.map(([p]) => `<div><i></i><span>${p}</span></div>`).join('')}</div>
    </div>
    <div class="dock"><button class="btn primary tall block" data-act="night-start">${icon('Candle', 'sm')}Start the evening</button></div>`,
  };
};

SCREENS['night-phase'] = () => {
  const n = S.night;
  const [phase, card] = n.cards[n.at];
  const name = PHASES[phase][0];
  const last = n.at === n.cards.length - 1;
  return {
    html: `${bar({ back: 'home', title: 'Game Night', right: heatChip() })}
    <div class="phases" aria-label="${name}">${PHASES.map(([p], i) => `<div class="${i < phase ? 'done' : i === phase ? 'now' : ''}"><i></i><span>${p}</span></div>`).join('')}</div>
    <div class="fill pad">
      <div class="card-static ${phase === 2 ? 'lit' : ''} deal">
        <div class="row between"><span class="eyebrow heat">${name} · ${card.durationMin || 5} min</span>${phase < 3 ? flames(card.intensity) : icon('Aftercare', 'sm')}</div>
        <h2 class="card-title">${esc(card.title)}</h2>
        ${card.subtitle ? `<p class="tease subtitle">${esc(card.subtitle)}</p>` : ''}
        <p class="card-body">${esc(card.body)}</p>
        <div class="meta row between" style="margin-top:8px"><span class="small">${last ? 'The last one' : `${n.cards.length - n.at - 1} to go`}</span>${phase === 2 ? '<span class="eyebrow heat">The peak</span>' : ''}</div>
      </div>
    </div>
    <div class="dock"><button class="btn primary tall block" data-act="night-next">${icon('Check', 'sm')}${last ? 'Finish' : 'Done'}</button></div>
    ${rail({ tooFar: false, proposed: true })}`,
  };
};

SCREENS['night-end'] = () => ({
  html: `${bar({ back: 'home', title: 'Game Night' })}
  <div class="fill pad stack section center">
    ${seal(72)}
    <div class="stack tight center"><span class="eyebrow gold">That's the evening</span><h1 class="headline">Nicely done, <em>both of you.</em></h1>
      <p class="body">Plan another when you want one, or go back to a card at a time.</p></div>
  </div>
  <div class="dock"><button class="btn primary block" data-act="night">${icon('Dice', 'sm')}Plan another</button><button class="btn quiet block" data-act="go" data-arg="home">That's us for tonight</button></div>`,
});

/* --- a game on one phone, through its primitives --- */

const GAME = (id) => DATA.games.find((g) => g.id === id);
function startGame(id) {
  const g = GAME(id);
  S.game = { id, names: [...NAMES], phase: 'names', round: 1, rounds: Math.min(g.rounds, 4), active: 0, step: 0, each: 0, answers: [], prompt: null, kind: null, landed: null, rolling: false, revealed: false };
  S.screen = 'game';
}
function gamePrompt(kind = null) {
  const g = S.game;
  const list = DATA.prompts[g.id].filter((p) => p.intensity <= level(S.tier) && (!kind || p.kind === kind));
  const any = list.length ? list : DATA.prompts[g.id].filter((p) => !kind || p.kind === kind);
  return pick(any, g.round * 3 + g.active + (g.reroll || 0));
}
function nextRound() {
  const g = S.game;
  if (g.round >= g.rounds) { g.phase = 'done'; return; }
  g.round += 1; g.step = 0; g.each = 0; g.answers = []; g.prompt = null; g.kind = null; g.landed = null; g.revealed = false; g.reroll = 0;
  if (GAME(g.id).turns === 'ALTERNATE') g.active = 1 - g.active;
  g.phase = GAME(g.id).turns === 'ALTERNATE' ? 'handover' : 'step';
}

SCREENS.game = () => {
  const g = S.game;
  const game = GAME(g.id);
  const head = `${bar({ back: 'home', title: game.title, right: heatChip() })}
    <div class="progress" aria-label="Round ${g.round} of ${g.rounds}">${Array.from({ length: g.rounds }, (_, i) => `<i class="${i < g.round - 1 ? 'done' : i === g.round - 1 ? 'now' : ''}"></i>`).join('')}</div>`;
  const step = game.steps[g.step];
  const me = g.names[g.active];
  const other = g.names[1 - g.active];
  let body = '';
  let dock = '';

  if (g.phase === 'names') {
    body = `<div class="fill pad stack section"><div class="stack tight"><span class="eyebrow heat">Before you pass it</span><h1 class="display">Who's <em>playing?</em></h1><p class="body">${esc(game.description)}</p></div>
      <div class="stack item">${g.names.map((n, i) => `<label class="velvet" style="display:flex;align-items:center;gap:12px;padding:6px 16px;min-height:56px"><span class="who"><span class="av">${n[0]}</span></span><input value="${esc(n)}" aria-label="Player ${i + 1}'s name" style="flex:1;background:none;border:0;font-size:17px;min-height:48px" data-names="${i}"></label>`).join('')}</div>
      <p class="small">So the phone can say whose turn it is. It stays on this phone, and you can skip it — nothing needs a name.</p></div>`;
    dock = `<button class="btn primary tall block" data-act="game-names">${icon('Play', 'sm')}Start</button><button class="btn quiet block" data-act="game-names">Skip names</button>`;
  } else if (g.phase === 'handover') {
    body = `<div class="fill pad stack section center"><div class="who" style="transform:scale(2.2);margin:24px 0"><span class="av">${me[0]}</span></div>
      <div class="stack tight center"><span class="eyebrow heat">Round ${g.round} · ${esc(me)}'s turn</span><h1 class="display">Pass the phone <em>to ${esc(me)}.</em></h1><p class="body">${esc(other)} can watch.</p></div></div>`;
    dock = `<button class="btn primary tall block" data-act="game-go">I've got it</button>`;
  } else if (g.phase === 'pass') {
    const next = game.steps[g.step];
    const to = next.who === 'EACH' ? g.names[g.each] : next.who === 'OTHER' ? other : me;
    const from = to === g.names[0] ? g.names[1] : g.names[0];
    body = `<div class="fill pad stack section center"><div class="gate-lock">${icon('Keyhole')}</div><div class="stack tight center"><span class="eyebrow gold">Just for them</span><h1 class="headline">Pass the phone to ${esc(to)}.</h1><p class="body">Don't look. ${esc(from)}'s answer stays hidden until the reveal.</p></div></div>`;
    dock = `<button class="btn primary tall block" data-act="game-go">I've got it</button>`;
  } else if (g.phase === 'done') {
    body = `<div class="fill pad stack section center">${seal(72)}<div class="stack tight center"><span class="eyebrow gold">That's the game</span><h1 class="headline">Nicely played, <em>both of you.</em></h1><p class="body">Play it again for a different set, or go back and pick another.</p></div></div>`;
    dock = `<button class="btn primary block" data-act="game" data-arg="${g.id}">${icon('Retry', 'sm')}Play again</button><button class="btn quiet block" data-act="go" data-arg="home">Back home</button>`;
  } else {
    [body, dock] = renderStep(step, g, game);
  }
  const playing = g.phase !== 'names' && g.phase !== 'done';
  return {
    html: `${head}${body}<div class="dock">${dock}</div>${playing ? rail({ tooFar: true, proposed: true, tooFarProposed: true }) : ''}`,
  };
};

function renderStep(step, g, game) {
  const me = g.names[g.active];
  const other = g.names[1 - g.active];
  const whoFor = step.who === 'EACH' ? g.names[g.each] : step.who === 'OTHER' ? other : me;
  const tag = `<span class="who"><span class="av">${whoFor[0]}</span><span class="eyebrow heat">${esc(whoFor)}</span></span>`;
  if (!g.prompt && step.text === 'PROMPT') g.prompt = gamePrompt(g.kind);
  if (!g.prompt && game.steps.some((s) => s.text === 'PROMPT')) g.prompt = gamePrompt(g.kind);

  switch (step.primitive) {
    case 'DICE': {
      const dice = step.dice.map((d) => ({ label: d.label, faces: d.faces === 'PROMPT' ? g.prompt.options : d.faces, seconds: d.seconds }));
      const landed = g.landed || dice.map(() => null);
      const clock = dice.find((d, i) => d.seconds && landed[i] !== null && d.seconds[landed[i]]);
      const secs = clock ? clock.seconds[landed[dice.indexOf(clock)]] : 0;
      return [`<div class="fill pad stack">
        ${tag}<h2 class="title" style="font-size:calc(26px * var(--ts));line-height:1.25">${esc(g.prompt.text)}</h2>
        <div class="dice">${dice.map((d, i) => `<div class="die ${g.rolling ? 'rolling' : ''}"><span class="eyebrow gold">${esc(d.label)}</span><div class="face-box" aria-live="polite">${landed[i] === null ? icon('Dice') : esc(d.faces[landed[i]])}</div></div>`).join('')}</div>
        ${secs ? `<div class="ring small"><svg viewBox="0 0 180 180"><circle class="track" cx="90" cy="90" r="80" fill="none" stroke-width="6"/><circle class="arc" cx="90" cy="90" r="80" fill="none" stroke-width="6" stroke-dasharray="502.65" stroke-dashoffset="0"/></svg><span class="numeral">${fmt(secs)}</span></div>` : ''}
      </div>`,
      g.landed ? `${secs ? `<button class="btn gold block" data-act="toast" data-arg="The clock runs on the card, as it does today.">${icon('Clock', 'sm')}Start the clock</button>` : ''}<div class="row"><button class="btn secondary" data-act="game-reroll">Not this one</button><button class="btn primary" style="flex:1.6" data-act="game-next">${icon('Check', 'sm')}Done</button></div>`
        : `<button class="btn primary tall block" data-act="game-roll">${icon('Dice', 'sm')}Roll</button>`];
    }
    case 'CHOICE': {
      const options = step.options === 'PROMPT' ? g.prompt.options : step.options;
      const text = step.text === 'PROMPT' ? g.prompt.text : step.text;
      const big = options.length === 2 && options.every((o) => o.length < 8);
      return [`<div class="fill pad stack">
        ${tag}
        ${step.private ? `<div class="private-note">${icon('Keyhole')}<span>Just you${step.who === 'EACH' ? '' : ` — ${esc(other)} guesses next`}. Nobody sees this until the reveal.</span></div>` : ''}
        <h2 class="headline">${esc(text)}</h2>
        <div class="choices ${big ? 'two' : ''}">${options.map((o, i) => `<button class="choice ${big ? 'big' : ''}" data-act="game-choose" data-arg="${i}">${esc(/^[A-Z_]+$/.test(o) ? o.charAt(0) + o.slice(1).toLowerCase() : o)}</button>`).join('')}</div>
      </div>`, ''];
    }
    case 'CHALLENGE': {
      const p = g.prompt;
      const card = { id: p.id, title: g.kind === 'TRUTH' ? 'A truth' : 'A dare', subtitle: null, body: p.text, intensity: p.intensity, durationMin: null, moods: [] };
      return [`<div class="fill" style="justify-content:center;gap:16px"><div class="pad">${tag}</div>${cardFace(card, { faceDown: true, revealed: g.revealed, number: g.round, holder: `${me}'s turn` })}</div>`,
        `<div class="row"><button class="btn secondary" data-act="game-reroll">Not this one</button><button class="btn primary" style="flex:1.6" data-act="game-next" ${g.revealed ? '' : 'disabled'} id="done-btn">${icon('Check', 'sm')}Done</button></div>`];
    }
    case 'PREDICTION': {
      const options = step.options === 'PROMPT' ? g.prompt.options : step.options;
      return [`<div class="fill pad stack">
        ${tag}
        <div class="private-note">${icon('Keyhole')}<span>${esc(me)} has answered. Guess what they chose.</span></div>
        <p class="tease" style="margin:0">They were asked:</p>
        <h2 class="headline">${esc(g.prompt.text)}</h2>
        <div class="choices">${options.map((o, i) => `<button class="choice" data-act="game-choose" data-arg="${i}">${esc(o)}</button>`).join('')}</div>
      </div>`, ''];
    }
    case 'REVEAL': {
      const options = g.prompt.options;
      const [a, b] = g.answers;
      const match = a === b;
      const predicted = step.outcome === 'PREDICTED';
      const label = predicted ? (match ? `${other} knew.` : 'Not quite.') : (match ? 'A match.' : 'Two answers.');
      const whoA = predicted ? `${me} chose` : g.names[0];
      const whoB = predicted ? `${other} guessed` : g.names[1];
      return [`<div class="fill pad stack section">
        <div class="stack tight center match-mark"><span class="eyebrow ${match ? 'heat' : 'gold'}">The reveal</span><h2 class="display">${match ? `<em>${esc(label)}</em>` : esc(label)}</h2>
        <p class="tease">${esc(g.prompt.text)}</p></div>
        <div class="reveal-pair">
          <div class="card-static ${match ? 'lit' : ''} deal"><span class="eyebrow">${esc(whoA)}</span><p class="title">${esc(options[a])}</p></div>
          <div class="card-static ${match ? 'lit' : ''} deal" style="animation-delay:0.35s"><span class="eyebrow">${esc(whoB)}</span><p class="title">${esc(options[b])}</p></div>
        </div>
        <p class="small center" style="text-align:center">${match ? 'Same answer. Do something about it.' : 'Different is the interesting part — ask why.'}</p>
      </div>`, `<button class="btn primary tall block" data-act="game-next">${icon('Forward', 'sm')}Next round</button>`];
    }
    default:
      return ['<div class="fill pad"><p class="body">A primitive the prototype does not draw.</p></div>', ''];
  }
}

SCREENS['game-media'] = () => ({
  html: `${bar({ back: 'home', title: 'A dare, with proof', right: heatChip() })}
  <div class="fill pad stack">
    <span class="who"><span class="av">A</span><span class="eyebrow heat">Alex</span></span>
    <h2 class="headline">Say it out loud — <em>and keep it.</em></h2>
    <p class="body">A photo or a few seconds of your voice. It stays in this round and never goes to the gallery.</p>
    <div class="choices two"><button class="choice big" style="font-size:calc(18px * var(--ts))" data-act="toast" data-arg="The system camera takes one picture, as it does today.">${icon('Camera', 'lg')}<span style="display:block;margin-top:8px">A photo</span></button><button class="choice big" aria-pressed="true" style="font-size:calc(18px * var(--ts))">${icon('Mic', 'lg')}<span style="display:block;margin-top:8px">My voice</span></button></div>
    <div class="velvet mic-take"><span class="rec-dot" aria-hidden="true"></span><span class="label">Recording · 0:07</span><span class="grow"></span><span class="meter" style="max-width:120px" aria-hidden="true">${'<i></i>'.repeat(10)}</span></div>
  </div>
  <div class="dock"><div class="row"><button class="btn secondary">${icon('Close', 'sm')}Again</button><button class="btn primary" style="flex:1.6">${icon('Check', 'sm')}Keep it</button></div></div>
  ${rail({ tooFar: true, proposed: true, tooFarProposed: true })}`,
});

/* --- a game a phone each: two phones, side by side --- */

const APART_STATES = {
  turn: 'Alex acts; Sam watches',
  private: 'A private step: the watcher sees nothing',
  waiting: 'One has answered, waiting for the other',
  reveal: 'Both phones reveal',
  reconnecting: 'This phone lost the server',
  away: 'The partner dropped out',
  paused: 'Paused: the game leaves the screen',
};
function apartPhones() {
  const st = S.apart;
  const dare = DATA.prompts.TRUTH_OR_DARE.find((p) => p.kind === 'DARE') || DATA.prompts.TRUTH_OR_DARE[0];
  const hon = DATA.prompts.HOT_OR_NOT[1] || DATA.prompts.HOT_OR_NOT[0];
  const fm = DATA.prompts.FANTASY_MATCH[2] || DATA.prompts.FANTASY_MATCH[0];
  const head = (title, round = 2) => `${bar({ back: 'home', title, right: heatChip() })}<div class="progress">${[0, 1, 2, 3].map((i) => `<i class="${i < round - 1 ? 'done' : i === round - 1 ? 'now' : ''}"></i>`).join('')}</div>`;
  const railBoth = rail({ pause: true });
  const status = (txt) => `<div class="status-line" role="status"><span class="spin" aria-hidden="true"></span>${esc(txt)}</div>`;
  const veil = `<div class="fill pad stack section center"><div class="shield-mark" style="color:var(--color-blush-muted)">${icon('Pause', 'xl')}</div><div class="stack tight center"><h2 class="headline">Paused.</h2><p class="body">The game is off the screen until one of you resumes it. Stop still works.</p></div><button class="btn secondary" data-act="apart" data-arg="turn">${icon('Play', 'sm')}Resume</button></div>`;
  let a; let b;
  if (st === 'turn' || st === 'reconnecting' || st === 'away') {
    const card = { id: dare.id, title: 'A dare', body: dare.text, intensity: dare.intensity, moods: [] };
    a = `${head('Truth or Dare')}${st === 'reconnecting' ? status('Reconnecting…') : st === 'away' ? status('Your partner dropped out — waiting…') : ''}
      <div class="fill" style="justify-content:center;gap:14px"><div class="pad"><span class="who"><span class="av">A</span><span class="eyebrow heat">Your turn</span></span></div>${cardFace(card, { faceDown: false, holder: 'Your dare' })}</div>
      <div class="dock"><div class="row"><button class="btn secondary">Not this one</button><button class="btn primary" style="flex:1.6">${icon('Check', 'sm')}Done</button></div></div>${railBoth}`;
    b = `${head('Truth or Dare')}
      <div class="fill pad stack section" style="justify-content:center">
        <div class="stack item center"><div class="who" style="transform:scale(1.8);margin:14px 0"><span class="av">A</span></div><span class="eyebrow heat">Alex's turn</span><p class="tease" style="margin:0">Alex drew a dare.</p></div>
        <div class="card-static lit deal"><span class="eyebrow">What they're doing</span><p class="card-body" style="font-family:var(--font-display);font-size:calc(22px * var(--ts));line-height:1.35">${esc(dare.text)}</p></div>
        <p class="small center" style="text-align:center">Doing it now. The round moves on when they're done.</p>
      </div>${railBoth}`;
  } else if (st === 'private') {
    a = `${head('Hot or Not Quiz', 3)}<div class="fill pad stack"><span class="who"><span class="av">A</span><span class="eyebrow heat">Your turn</span></span>
      <div class="private-note">${icon('Keyhole')}<span>Just you — Sam guesses next.</span></div><h2 class="headline">${esc(hon.text)}</h2>
      <div class="choices">${hon.options.map((o, i) => `<button class="choice" ${i === 1 ? 'aria-pressed="true"' : ''}>${esc(o)}</button>`).join('')}</div></div>${railBoth}`;
    b = `${head('Hot or Not Quiz', 3)}<div class="fill pad"><div class="velvet watch-empty">${icon('Keyhole', 'xl')}<p class="title">Alex is choosing.</p><p class="body">Just for them. You'll guess in a moment — nothing of it shows here until then.</p></div></div>${railBoth}`;
  } else if (st === 'waiting') {
    a = `${head('Fantasy Match', 2)}<div class="fill pad stack section center"><div class="gate-lock warm">${icon('Rings')}</div><div class="stack tight center"><p class="title">You've answered.</p><p class="body">Waiting for Sam. Nothing is shown until you've both chosen.</p></div></div>${railBoth}`;
    b = `${head('Fantasy Match', 2)}<div class="fill pad stack"><span class="who"><span class="av">S</span><span class="eyebrow heat">Your answer</span></span>
      <div class="private-note">${icon('Keyhole')}<span>Just you. Alex has answered — nobody sees either until you have.</span></div>
      <h2 class="headline">${esc(fm.text)}</h2><div class="choices">${fm.options.map((o) => `<button class="choice">${esc(o)}</button>`).join('')}</div></div>${railBoth}`;
  } else if (st === 'reveal') {
    const both = `${head('Fantasy Match', 2)}<div class="fill pad stack section"><div class="stack tight center"><span class="eyebrow heat">The reveal</span><h2 class="display"><em>A match.</em></h2><p class="tease">${esc(fm.text)}</p></div>
      <div class="reveal-pair"><div class="card-static lit deal"><span class="eyebrow">Alex</span><p class="title">${esc(fm.options[0])}</p></div><div class="card-static lit deal" style="animation-delay:.35s"><span class="eyebrow">Sam</span><p class="title">${esc(fm.options[0])}</p></div></div></div>
      <div class="dock"><button class="btn primary block">${icon('Forward', 'sm')}Next round</button></div>${railBoth}`;
    a = both; b = both;
  } else {
    a = `${head('Truth or Dare')}${veil}${railBoth}`;
    b = a;
  }
  return [['Alex · acting', a], ['Sam · watching', b]];
}

/* --- pairing --- */

SCREENS.pair = () => {
  const p = S.pair;
  const code = p.code.replace(' ', '');
  let body = '';
  let dock = '';
  if (p.stage === 'choose') {
    body = `<div class="fill pad stack section"><div class="stack tight"><span class="eyebrow heat">Apart</span><h1 class="display">Two phones, <em>one pair.</em></h1><p class="body">One of you shows a code, the other enters it. Twenty seconds, once.</p></div>
      <div class="stack item"><button class="velvet tile wide" data-act="pair" data-arg="invite"><span class="glyph">${icon('Qr')}</span><span class="grow"><span class="t" style="display:block">Show my code</span><span class="d">A code, a QR, or a link to send.</span></span>${icon('Forward', 'sm')}</button>
      <button class="velvet tile wide" data-act="pair" data-arg="enter"><span class="glyph">${icon('Link')}</span><span class="grow"><span class="t" style="display:block">I have their code</span><span class="d">Type it, or scan theirs.</span></span>${icon('Forward', 'sm')}</button></div></div>`;
  } else if (p.stage === 'invite' || p.stage === 'waiting') {
    body = `<div class="fill pad stack section center"><div class="stack tight center"><span class="eyebrow gold">Your code</span><p class="body">On their phone: Apart → I have their code.</p></div>
      <div class="code" aria-label="Code ${code.split('').join(' ')}">${code.split('').map((c) => `<span>${c}</span>`).join('')}</div>
      <div class="qr">${qr(code)}</div>
      ${p.stage === 'waiting' ? '<div class="status-line" role="status"><span class="spin" aria-hidden="true"></span>Waiting for their phone…</div>' : '<p class="small">It works for ten minutes.</p>'}</div>`;
    dock = `<button class="btn secondary block" data-act="toast" data-arg="Opens the share sheet with afterhours://pair?code=${code}">${icon('Share', 'sm')}Send the link</button>`;
  } else if (p.stage === 'enter' || p.stage === 'error') {
    const e = p.entered.padEnd(6, ' ').split('');
    body = `<div class="fill pad stack section"><div class="stack tight"><span class="eyebrow heat">Their code</span><h1 class="headline">Six digits, <em>from their phone.</em></h1></div>
      <div class="code ${p.stage === 'error' ? 'bad' : ''}">${e.map((c, i) => `<span class="${c === ' ' ? 'empty' : ''} ${i === p.entered.length ? 'cursor' : ''}">${c === ' ' ? '·' : c}</span>`).join('')}</div>
      ${p.stage === 'error' ? `<div class="banner" role="alert">${icon('Close')}<span>That code didn't match. Check it on their phone and try again.</span></div>` : '<p class="small">Or scan the QR on their screen.</p>'}
      <div class="keypad">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => `<button data-act="pair-key" data-arg="${n}">${n}</button>`).join('')}<button class="ghost" data-act="toast" data-arg="Opens the camera to scan their QR" aria-label="Scan">${icon('Qr')}</button><button data-act="pair-key" data-arg="0">0</button><button class="ghost" data-act="pair-key" data-arg="del" aria-label="Delete">${icon('Backspace')}</button></div></div>`;
  } else if (p.stage === 'done') {
    body = `<div class="fill pad stack section center"><div class="joined">${seal(72)}${seal(72)}</div><div class="stack tight center"><span class="eyebrow heat">Paired</span><h1 class="display">Two phones, <em>one evening.</em></h1><p class="body">Apart is on. Every game opens on both phones now.</p></div></div>`;
    dock = `<button class="btn primary tall block" data-act="go" data-arg="home">${icon('Flame', 'sm')}Let's play</button>`;
  }
  return { html: `${bar({ back: 'home', title: 'Pair your phones' })}${body}<div class="dock">${dock}</div>` };
};

/* --- the store --- */

SCREENS.store = () => {
  const st = S.states.store;
  const packs = [
    ['SPICY', 'Spicy', '£2.99', 'Opens Spicy, and every level below it.'],
    ['HOT', 'Hot', '£4.99', 'Opens Hot, Spicy, and everything below.'],
    ['EXTREME', 'Everything', '£6.99', 'All five, Extreme included. Yours to keep.'],
  ];
  const body = st === 'loading'
    ? '<div class="stack item">' + '<div class="skeleton" style="height:150px"></div>'.repeat(3) + '</div>'
    : st === 'error'
      ? `<div class="banner" role="alert">${icon('Close')}<span>The store didn't answer. Nothing was charged — try again.</span></div><button class="btn secondary" data-act="state" data-arg="store:">${icon('Retry', 'sm')}Try again</button>`
      : packs.map(([k, name, price, line]) => {
        const owned = level(k) <= S.ownedCeiling;
        return `<section class="velvet pack ${k === 'EXTREME' ? 'best' : ''}" data-tier="${lower(k)}">
          <div class="row between"><span class="eyebrow heat">${name}</span>${flames(level(k), { big: true })}</div>
          <p class="title">${line}</p>
          <div class="row between">${owned ? `<span class="owned">${icon('Check')}Yours.</span>` : `<span class="price">${price}</span>`}${owned ? '' : `<button class="btn primary" data-act="buy" data-arg="${level(k)}">Buy once</button>`}</div>
        </section>`;
      }).join('');
  return {
    html: `${bar({ back: 'home', title: 'The packs' })}
    <div class="scroll"><div class="pad stack" style="padding-bottom:32px">
      <div class="stack tight"><h1 class="display">Turn it <em>all the way up.</em></h1><p class="body">Mild and Flirty are free for ever. A pack is bought once and opens every level below it.</p></div>
      ${S.freeMonth ? `<div class="ribbon">${icon('Gift')}<span>Everything's open until <b>${S.freeUntil}</b> anyway.</span></div>` : ''}
      ${body}
      <button class="btn quiet" data-act="toast" data-arg="Asks Play what you own, as it does today.">Restore purchases</button>
    </div></div>`,
  };
};

/* --- settings, lock, panic --- */

SCREENS.settings = () => ({
  html: `${bar({ back: 'home', title: 'Settings' })}
  <div class="scroll"><div class="pad stack" style="padding-bottom:32px">
    <div class="stack tight"><h1 class="headline">Yours, and <em>the two of you.</em></h1></div>
    <div class="stack tight"><span class="eyebrow">The two of you</span><div class="velvet set-group">
      <div class="set-row">${icon('Phones')}<span class="grow"><span class="label">${S.paired ? 'Paired' : 'Not paired'}</span><span class="sub">${S.paired ? 'These two phones are a pair until one of you unpairs.' : 'Together mode needs nobody. Pair only to play apart.'}</span></span></div>
      ${S.paired ? `<button class="set-row" data-act="toast" data-arg="Asks twice, then clears the pair on both phones.">${icon('Close')}<span class="grow"><span class="label" style="color:var(--color-signal)">Unpair and wipe</span><span class="sub">Clears the pair on both phones and everything you shared. Your own limits stay on this phone.</span></span></button>` : `<button class="set-row" data-act="pair" data-arg="choose">${icon('Link')}<span class="grow"><span class="label">Pair your phones</span></span>${icon('Forward', 'sm')}</button>`}
    </div></div>
    <div class="stack tight"><span class="eyebrow">Yours</span><div class="velvet set-group">
      <button class="set-row" data-act="sheet" data-arg="limits">${icon('Limits')}<span class="grow"><span class="label">Your limits</span><span class="sub">What you are up for, and what you are not.</span></span>${icon('Forward', 'sm')}</button>
      <button class="set-row">${icon('Moon')}<span class="grow"><span class="label">Notifications</span><span class="sub">What this phone may say out loud.</span></span>${icon('Forward', 'sm')}</button>
      <button class="set-row" data-act="go" data-arg="lock">${icon('Lock')}<span class="grow"><span class="label">App lock</span><span class="sub">A PIN or your fingerprint, on this phone.</span></span>${icon('Forward', 'sm')}</button>
    </div></div>
    <div class="stack tight"><span class="eyebrow">On this phone</span><div class="velvet set-group">
      <div class="set-row">${icon('Keyhole')}<span class="grow"><span class="label">Panic</span><span class="sub">Two taps of back locks the app. Set a PIN above and it asks for it.</span></span></div>
      <div class="set-row" style="flex-direction:column;align-items:stretch"><span class="label">The icon on your home screen</span>
        <div class="icons-pick" role="radiogroup" aria-label="Launcher icon">
          <button class="icon-opt" aria-pressed="true"><span class="app-icon brand">${seal(40)}</span>Afterhours</button>
          <button class="icon-opt" aria-pressed="false"><span class="app-icon notes">${icon('Bookmark')}</span>Notes</button>
          <button class="icon-opt" aria-pressed="false"><span class="app-icon calc">${icon('Plus')}</span>Calculator</button>
        </div></div>
    </div></div>
    <div class="stack tight"><span class="eyebrow">Packs</span><div class="velvet set-group"><button class="set-row" data-act="go" data-arg="store">${icon('Gift')}<span class="grow"><span class="label">What you own</span><span class="sub">${S.ownedCeiling > 2 ? `Up to ${T[tierOfLevel(S.ownedCeiling)].label}` : 'Mild and Flirty, free for ever'}</span></span>${icon('Forward', 'sm')}</button></div></div>
    <div class="stack tight"><span class="eyebrow">The small print</span><div class="velvet set-group">
      <button class="set-row"><span class="grow"><span class="label">Privacy policy</span><span class="sub">What this app holds, and what it never shares.</span></span>${icon('Forward', 'sm')}</button>
      <button class="set-row"><span class="grow"><span class="label">Terms of use</span><span class="sub">The agreement between us.</span></span>${icon('Forward', 'sm')}</button>
      <div class="set-row"><span class="grow"><span class="label">Afterhours</span><span class="sub">Version 2.0</span></span></div>
    </div></div>
  </div></div>`,
});

/* The lock and panic screens are neutral on purpose: no seal, no heat, no name. With the Notes or
   Calculator icon chosen, this is what somebody else sees. */
SCREENS.lock = () => ({
  cls: 'neutral',
  html: `<div class="fill pad stack section center" style="color:#E6E6E6">
    <div class="stack tight center"><p style="margin:0;font:500 20px/1.3 system-ui,sans-serif">Enter PIN</p></div>
    <div class="pin-dots" aria-label="${S.lockPin.length} of 4 digits">${[0, 1, 2, 3].map((i) => `<i class="${i < S.lockPin.length ? 'on' : ''}"></i>`).join('')}</div>
    <div class="keypad" style="width:100%;max-width:300px">${[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => `<button data-act="pin" data-arg="${n}">${n}</button>`).join('')}<button class="ghost" aria-label="Use fingerprint" style="color:#B0B0B5">${icon('Fingerprint')}</button><button data-act="pin" data-arg="0">0</button><button class="ghost" data-act="pin" data-arg="del" aria-label="Delete" style="color:#B0B0B5">${icon('Backspace')}</button></div>
  </div>`,
});

/* ---------------------------------------------------------------------------------------------
   Sheets
   --------------------------------------------------------------------------------------------- */

const SHEETS = {};

SHEETS.heat = () => sheetFrame(`<div class="sheet-body">
  <div class="stack tight"><span class="eyebrow heat">How far tonight</span><h2 class="headline">${esc(T[S.tier].label)}</h2></div>
  ${heatSelector()}
  <p class="small">A ceiling, not a level: everything gentler can still come up. Extreme asks both of you first, once a night.</p>
  <button class="btn secondary block" data-act="close-sheet">Done</button>
</div>`, { label: 'How far tonight' });

SHEETS.limits = () => sheetFrame(`<div class="sheet-body">
  <div class="stack tight"><span class="eyebrow heat">Your limits</span><h2 class="headline">What are you <em>up for?</em></h2>
  <p class="body">Only you ever see this. Your partner is never told what you said.</p></div>
  <div>${DATA.limits.map(([k, label, asks]) => `<div class="limit-row"><span class="name">${esc(label)}</span><span class="small">${esc(asks)}</span>
    <div class="seg" role="radiogroup" aria-label="${esc(label)}">${['YES', 'MAYBE', 'NO'].map((v) => `<button role="radio" aria-checked="${S.limits[k] === v}" aria-pressed="${S.limits[k] === v}" data-v="${v}" data-act="limit" data-arg="${k}:${v}">${v === 'YES' ? 'Yes' : v === 'MAYBE' ? 'Maybe' : 'No'}</button>`).join('')}</div></div>`).join('')}</div>
  <div class="private-note">${icon('Limits')}<span>Anything touching a No is hidden — for both of you.</span></div>
  <button class="btn primary block" data-act="close-sheet">${icon('Check', 'sm')}Done</button>
</div>`, { tall: true, label: 'Your limits' });

SHEETS.toofar = () => sheetFrame(`<div class="sheet-body">
  <div class="stack tight"><span class="eyebrow">Too far</span><h2 class="headline">Skip this <em>kind of thing?</em></h2>
  <p class="body">Nobody is told. Not tonight, not ever — your partner never sees this.</p></div>
  <button class="btn secondary tall block" data-act="refuse" data-arg="one">Just this one</button>
  <button class="btn secondary block" data-act="refuse" data-arg="never">Never show me these again</button>
  <button class="btn quiet block" data-act="close-sheet">Keep this card</button>
</div>`, { label: 'Too far' });

SHEETS.lanes = () => sheetFrame(`<div class="sheet-body">
  <div class="stack tight"><span class="eyebrow heat">A lane</span><h2 class="headline">Only <em>this</em> kind, tonight.</h2>
  <p class="body">Only packs with cards at ${esc(T[S.tier].label)} are here.</p></div>
  <div class="velvet set-group">
    <button class="set-row" data-act="lane" data-arg="">${icon('Spark')}<span class="grow"><span class="label">Everything</span><span class="sub">The open deck.</span></span>${!S.deck?.lane ? icon('Check', 'sm') : ''}</button>
    ${laneOptions().map((p) => `<button class="set-row" data-act="lane" data-arg="${p.id}">${icon('Lanes')}<span class="grow"><span class="label">${esc(p.title)}</span><span class="sub">${esc(p.description)}</span></span>${S.deck?.lane === p.id ? icon('Check', 'sm') : ''}</button>`).join('')}
  </div>
</div>`, { tall: true, label: 'Lanes' });

SHEETS.chat = () => {
  const st = S.chat;
  const wave = (n, played = 0) => Array.from({ length: 34 }, (_, i) => `<i class="${i < played ? 'played' : ''}" style="height:${18 + ((i * 37 + n * 11) % 70)}%"></i>`).join('');
  const list = st === 'empty'
    ? '<div class="fill center" style="padding:24px"><p class="tease">Say something to them.</p></div>'
    : `<div class="chat-list">
      <div class="day"><span class="eyebrow">Today</span></div>
      <div class="bubble-run theirs"><div class="bubble"><span class="words">did you do it</span></div><div class="bubble"><span class="words">I want proof</span></div><span class="stamp">9:42 pm</span></div>
      <div class="bubble-run mine"><div class="bubble"><span class="words">doing it now</span></div><span class="stamp">9:43 pm</span></div>
      <div class="day new"><span class="eyebrow">New</span></div>
      <div class="bubble-run theirs"><div class="bubble"><div class="plate">${icon('Lock', 'lg')}<span class="eyebrow">Once only. Tap to look.</span></div></div>
        <div class="bubble"><div class="voice" style="color:var(--tier-on-dark)">${icon('Play')}<span class="wave" aria-hidden="true">${wave(1, 14)}</span><span class="len">0:07</span></div></div><span class="stamp">9:45 pm</span></div>
      <div class="bubble-run mine"><div class="bubble"><div class="photo" role="img" aria-label="Your photo"></div><span class="words">here's your proof</span></div><span class="stamp">9:46 pm</span>
        <div class="bubble faint"><div class="voice">${icon('Play')}<span class="wave" aria-hidden="true">${wave(4)}</span><span class="len">0:04</span></div></div><span class="stamp">Sending…</span>
        <div class="bubble"><span class="words">your turn next</span></div><span class="stamp bad" role="alert">${icon('Retry')}Didn't send — tap to retry</span></div>
    </div>`;
  const composer = st === 'recording'
    ? `<div class="recording"><button class="iconbtn" aria-label="Throw it away">${icon('Close')}</button><span class="rec-dot" aria-hidden="true"></span><span class="label" role="timer">0:07</span><span class="meter" aria-hidden="true">${'<i></i>'.repeat(18)}</span><button class="iconbtn raised" style="color:var(--tier-on-dark)" aria-label="Send the voice note">${icon('Send')}</button></div>`
    : `<div class="composer"><button class="iconbtn" aria-label="Attach a photo or a voice note">${icon('Plus')}</button><div class="field">Say something</div><button class="iconbtn" aria-label="Record a voice note">${icon('Mic')}</button></div>`;
  return sheetFrame(`<div class="bar"><button class="iconbtn" data-act="close-sheet" aria-label="Close the chat">${icon('Close')}</button>
      <div class="title-slot"><span class="title" style="font-size:calc(19px * var(--ts))">The two of you</span></div>
      <button class="stop" data-act="stop" style="padding:0 12px 0 10px">${icon('SafeWord')}<span>Stop</span></button></div>
    <hr class="divider">${list}${composer}`, { tall: true, label: 'Chat' });
};

/* ---------------------------------------------------------------------------------------------
   Actions
   --------------------------------------------------------------------------------------------- */

function go(screen) {
  stopTimer();
  S.sheet = null;
  S.screen = screen;
  render();
}

/**
 * Extreme waits for both of them, once a session. Every way into Extreme — the picker, "Turn it
 * up?", Game Night, a game — comes through here, which is what ANALYSIS.md §6.2 proposes.
 */
function gateThen(then, back) {
  S.afterGate = then;
  S.gateBack = back;
  S.gateStep = 0;
  go('gate');
}

const ACT = {
  go: (arg) => go(arg),
  sheet: (arg) => { S.sheet = arg; render(); },
  'close-sheet': () => { S.sheet = null; render(); },
  mode: (arg) => { S.mode = arg; render(); },
  tier: (arg) => {
    if (!isOpen(arg)) { S.locked = arg; S.sheet = null; if (S.screen !== 'home') S.screen = 'home'; render(); return; }
    S.locked = null;
    if (arg === 'EXTREME' && !S.extremeOk) {
      const back = S.screen;
      gateThen(() => { S.tier = 'EXTREME'; if (back === 'deck') { S.deck.seen = new Set(); dealNext(); } go(back); }, back);
      return;
    }
    S.tier = arg;
    if (S.deck && S.screen === 'deck') { S.deck.seen.delete(S.deck.card?.id); dealNext(); }
    render();
  },
  'unlock-dismiss': () => { S.locked = null; render(); },
  play: () => { newDeck(); go('deck'); },
  start: () => { newDeck(); go('deck'); },
  reveal: () => {
    const el = $('#card3d');
    if (!el || el.classList.contains('up')) return;
    el.classList.add('up');
    el.removeAttribute('role'); el.removeAttribute('tabindex'); el.removeAttribute('data-act');
    if (S.screen === 'deck') S.deck.revealed = true;
    if (S.screen === 'game') S.game.revealed = true;
    document.querySelectorAll('#done-btn, [data-act="timer"]').forEach((b) => b.removeAttribute('disabled'));
  },
  done: () => {
    const d = S.deck;
    d.played += 1; d.yeses += 1;
    if (d.played >= d.run) { d.interstitial = 'complete'; go('deck'); return; }
    if (d.yeses >= 5 && level(S.tier) < 5) { d.yeses = 0; d.interstitial = 'turnup'; go('deck'); return; }
    dealNext(); go('deck');
  },
  skip: () => { const d = S.deck; d.played += 1; d.yeses = 0; if (d.played >= d.run) { d.interstitial = 'complete'; } else dealNext(); go('deck'); },
  refuse: (arg) => {
    const d = S.deck;
    if (arg === 'never' && d.card) d.refused.add(d.card.pack + ':' + (d.card.tags || [])[0]);
    S.sheet = null; d.yeses = 0; dealNext();
    S.toast = arg === 'never' ? "Done. Nothing like it again — and nobody's told." : null;
    go('deck');
  },
  'turn-up': (arg) => {
    S.deck.interstitial = null;
    if (arg === 'EXTREME' && !S.extremeOk) { gateThen(() => { S.tier = 'EXTREME'; dealNext(); go('deck'); }, 'deck'); return; }
    S.tier = arg; dealNext(); go('deck');
  },
  'keep-here': () => { S.deck.interstitial = null; dealNext(); go('deck'); },
  'keep-going': () => { const d = S.deck; d.interstitial = null; d.played = 0; dealNext(); go('deck'); },
  'tier-up': () => { const next = tierOfLevel(level(S.tier) + 1); ACT.tier(next); if (S.screen === 'deck') { newDeck(); go('deck'); } },
  lane: (arg) => { S.deck.lane = arg || null; S.deck.seen = new Set(); dealNext(); S.sheet = null; render(); },
  timer: () => go('timer'),
  'timer-pause': (_, el) => { if (!ticking) return; const p = ticking.toggle(); el.innerHTML = `${icon(p ? 'Play' : 'Pause', 'sm')}${p ? 'Resume' : 'Pause'}`; },
  stop: () => { S.stoppedFrom = S.screen; stopTimer(); S.sheet = null; S.screen = 'stopped'; render(); },
  // Gently means carrying on with what was stopped, at Mild — not being sent somewhere else.
  gentle: () => {
    S.tier = 'MILD';
    const from = S.stoppedFrom;
    if (from === 'game' || from === 'night-phase' || from === 'apart') { go(from); return; }
    if (!S.deck) newDeck();
    S.deck.interstitial = null; S.deck.seen = new Set(); dealNext(); go('deck');
  },
  'gate-confirm': () => {
    S.gateStep += 1;
    render();
    if (S.gateStep < 2) return;
    // The lock opens, holds for a beat, and only then does anything Extreme appear.
    setTimeout(() => {
      S.extremeOk = true; S.tier = 'EXTREME'; S.gateStep = 0;
      const then = S.afterGate; S.afterGate = null;
      if (then) then(); else go('home');
    }, 900);
  },
  'gate-down': () => { S.tier = 'HOT'; S.gateStep = 0; S.afterGate = null; go(S.gateBack && S.gateBack !== 'gate' ? S.gateBack : 'home'); },
  night: () => { S.night = { minutes: S.night?.minutes || 30 }; go('night-length'); },
  'night-length': (arg) => { S.night.minutes = Number(arg); render(); },
  'night-start': () => {
    const begin = () => { S.night = planNight(S.night.minutes); go('night-phase'); };
    if (S.tier === 'EXTREME' && !S.extremeOk) { gateThen(begin, 'night-length'); return; }
    begin();
  },
  'night-next': () => { const n = S.night; if (n.at >= n.cards.length - 1) go('night-end'); else { n.at += 1; render(); } },
  game: (arg) => {
    if (S.tier === 'EXTREME' && !S.extremeOk) { gateThen(() => ACT.game(arg), 'home'); return; }
    if (S.mode === 'APART' && S.paired) { S.apart = 'turn'; go('apart'); return; }
    startGame(arg); render();
  },
  'game-names': () => {
    document.querySelectorAll('[data-names]').forEach((el) => { S.game.names[Number(el.dataset.names)] = el.value.trim() || (el.dataset.names === '0' ? 'You' : 'Them'); });
    const g = S.game;
    g.phase = GAME(g.id).turns === 'ALTERNATE' ? 'handover' : 'step';
    render();
  },
  'game-go': () => { S.game.phase = 'step'; render(); },
  'game-roll': () => {
    const g = S.game;
    g.rolling = true; render();
    setTimeout(() => {
      const step = GAME(g.id).steps[g.step];
      g.landed = step.dice.map((d, i) => ((g.round * 7 + i * 5 + (g.reroll || 0) * 3) % (d.faces === 'PROMPT' ? g.prompt.options.length : d.faces.length)));
      g.rolling = false; render();
    }, document.documentElement.classList.contains('reduce-motion') ? 0 : 900);
  },
  'game-reroll': () => { const g = S.game; g.reroll = (g.reroll || 0) + 1; g.prompt = gamePrompt(g.kind); g.landed = null; g.revealed = false; render(); },
  'game-choose': (arg) => {
    const g = S.game;
    const game = GAME(g.id);
    const step = game.steps[g.step];
    const i = Number(arg);
    if (step.primitive === 'CHOICE' && step.options !== 'PROMPT' && !step.private) {
      g.kind = step.options[i]; g.step += 1; g.prompt = gamePrompt(g.kind); render(); return;
    }
    if (step.who === 'EACH') {
      g.answers[g.each] = i;
      if (g.each === 0) { g.each = 1; g.phase = 'pass'; render(); return; }
      g.step += 1; render(); return;
    }
    g.answers.push(i);
    g.step += 1;
    if (game.steps[g.step]?.who === 'OTHER') g.phase = 'pass';
    render();
  },
  'game-next': () => { nextRound(); render(); },
  apart: (arg) => { S.apart = arg; if (S.screen !== 'apart') S.screen = 'apart'; S.sheet = null; render(); },
  pair: (arg) => { S.pair.stage = arg; if (arg === 'enter') S.pair.entered = ''; S.screen = 'pair'; S.sheet = null; render(); },
  'pair-key': (arg) => {
    const p = S.pair;
    if (arg === 'del') p.entered = p.entered.slice(0, -1);
    else if (p.entered.length < 6) p.entered += arg;
    p.stage = 'enter';
    if (p.entered.length === 6) {
      render();
      setTimeout(() => { p.stage = p.entered === '111111' ? 'error' : 'done'; if (p.stage === 'done') { S.paired = true; S.mode = 'APART'; } render(); }, 500);
      return;
    }
    render();
  },
  limit: (arg) => { const [k, v] = arg.split(':'); S.limits[k] = S.limits[k] === v ? null : v; render(); },
  buy: (arg) => { S.ownedCeiling = Math.max(S.ownedCeiling, Number(arg)); S.toast = 'Yours. It opens every level below it, too.'; render(); },
  state: (arg) => { const [k, v] = arg.split(':'); S.states[k] = v || undefined; render(); },
  pin: (arg) => { S.lockPin = arg === 'del' ? S.lockPin.slice(0, -1) : (S.lockPin + arg).slice(0, 4); if (S.lockPin.length === 4) { setTimeout(() => { S.lockPin = ''; go('home'); }, 300); } render(); },
  toast: (arg) => { S.toast = arg; render(); },
};

/* ---------------------------------------------------------------------------------------------
   Render
   --------------------------------------------------------------------------------------------- */

function frame(caption, inner, { cls = '', tier = S.tier } = {}) {
  return `<div class="device-wrap"><div class="device-caption">${esc(caption)}</div><div class="device"><div class="screen ${cls}" data-tier="${lower(tier)}">${inner}</div></div></div>`;
}

function render() {
  const host = $('#devices');
  if (S.screen === 'apart') {
    host.innerHTML = apartPhones().map(([cap, html]) => frame(cap, html)).join('');
  } else {
    const def = (SCREENS[S.screen] || SCREENS.home)();
    const sheet = S.sheet && SHEETS[S.sheet] ? SHEETS[S.sheet]() : '';
    const toast = S.toast ? `<div class="toast" role="status">${esc(S.toast)}</div>` : '';
    host.innerHTML = frame(captionFor(S.screen), def.html + sheet + toast, { cls: def.cls || '', tier: def.tier || S.tier });
    if (def.mount) def.mount();
    if (S.toast) { const t = S.toast; setTimeout(() => { if (S.toast === t) { S.toast = null; const el = $('.toast'); if (el) el.remove(); } }, 2600); }
  }
  renderPanel();
}

const CAPTIONS = {
  start: 'First launch', agegate: 'Age gate', 'not-adult': 'Not 18', home: 'Home', deck: 'The deck', timer: 'A card, timed',
  stopped: 'Stopped', gate: 'The Extreme gate', 'night-length': 'Game Night', 'night-phase': 'Game Night', 'night-end': 'Game Night',
  game: 'A game on one phone', 'game-media': 'Media proof', pair: 'Pairing', store: 'The packs', settings: 'Settings', lock: 'App lock — neutral',
};
function captionFor(screen) {
  const sheet = { heat: ' · heat', limits: ' · limits', toofar: ' · too far', lanes: ' · lanes', chat: ' · chat' }[S.sheet] || '';
  return (CAPTIONS[screen] || screen) + sheet;
}

/* ---------------------------------------------------------------------------------------------
   The designer panel
   --------------------------------------------------------------------------------------------- */

let currentPreset = 'home';
const PRESETS = {
  start: () => { S.screen = 'start'; },
  agegate: () => { S.screen = 'agegate'; },
  home: () => { S.states.home = undefined; S.locked = null; S.screen = 'home'; },
  'home-together': () => { S.mode = 'TOGETHER'; S.locked = null; S.screen = 'home'; },
  'home-unpaired': () => { S.mode = 'APART'; S.paired = false; S.screen = 'home'; },
  'home-loading': () => { S.states.home = 'loading'; S.screen = 'home'; },
  'home-locked': () => { S.freeMonth = false; S.ownedCeiling = 2; S.tier = 'FLIRTY'; S.locked = 'HOT'; S.screen = 'home'; },
  heat: () => { S.screen = 'home'; S.sheet = 'heat'; },
  deck: () => { S.faceDown = true; newDeck(); S.screen = 'deck'; },
  'deck-up': () => { newDeck(); S.deck.revealed = true; S.screen = 'deck'; },
  'deck-long': () => { newDeck(); const c = DATA.cards.find((x) => x.id === DATA.worst.body); S.deck.card = c; S.deck.revealed = true; S.tier = tierOfLevel(Math.max(level(S.tier), c.intensity)); S.screen = 'deck'; },
  'deck-loading': () => { newDeck(); S.states.deck = 'loading'; S.screen = 'deck'; },
  'deck-error': () => { newDeck(); S.states.deck = 'error'; S.screen = 'deck'; },
  lanes: () => { newDeck(); S.screen = 'deck'; S.sheet = 'lanes'; },
  timer: () => { newDeck(); S.deck.card = DATA.cards.find((c) => c.durationMin && c.durationMin <= 5) || S.deck.card; S.screen = 'timer'; },
  turnup: () => { newDeck(); S.deck.interstitial = 'turnup'; S.screen = 'deck'; },
  nothing: () => { newDeck(); S.deck.card = null; S.screen = 'deck'; },
  complete: () => { S.tier = 'HOT'; newDeck(); S.deck.interstitial = 'complete'; S.screen = 'deck'; },
  stopped: () => { S.screen = 'stopped'; },
  toofar: () => { newDeck(); S.deck.revealed = true; S.screen = 'deck'; S.sheet = 'toofar'; },
  gate: () => {
    S.extremeOk = false; S.gateStep = 0; newDeck(); S.gateBack = 'deck';
    S.afterGate = () => { S.deck.seen = new Set(); dealNext(); go('deck'); };
    S.screen = 'gate';
  },
  limits: () => { S.screen = 'home'; S.sheet = 'limits'; },
  'night-length': () => { S.night = { minutes: 45 }; S.screen = 'night-length'; },
  'night-phase': () => { S.night = planNight(45); S.night.at = 1; S.screen = 'night-phase'; },
  'night-peak': () => { S.night = planNight(45); S.night.at = S.night.cards.findIndex(([p]) => p === 2); S.screen = 'night-phase'; },
  'night-care': () => { S.night = planNight(45); S.night.at = S.night.cards.length - 1; S.screen = 'night-phase'; },
  'night-end': () => { S.screen = 'night-end'; },
  'game-names': () => { S.mode = 'TOGETHER'; startGame('TRUTH_OR_DARE'); },
  'game-handover': () => { S.mode = 'TOGETHER'; startGame('TRUTH_OR_DARE'); S.game.phase = 'handover'; },
  'game-dice': () => { S.mode = 'TOGETHER'; startGame('HEAT_DICE'); S.game.phase = 'step'; },
  'game-tod': () => { S.mode = 'TOGETHER'; startGame('TRUTH_OR_DARE'); S.game.phase = 'step'; },
  'game-private': () => { S.mode = 'TOGETHER'; startGame('FANTASY_MATCH'); S.game.phase = 'step'; },
  'game-predict': () => { S.mode = 'TOGETHER'; startGame('HOT_OR_NOT'); S.game.phase = 'step'; S.game.prompt = gamePrompt(); S.game.answers = [1]; S.game.step = 1; },
  'game-reveal': () => { S.mode = 'TOGETHER'; startGame('WOULD_YOU_RATHER'); S.game.phase = 'step'; S.game.prompt = gamePrompt(); S.game.answers = [0, 0]; S.game.step = 1; },
  'game-media': () => { S.screen = 'game-media'; },
  'game-done': () => { startGame('FANTASY_MATCH'); S.game.phase = 'done'; },
  ...Object.fromEntries(Object.keys(APART_STATES).map((k) => [`apart-${k}`, () => { S.mode = 'APART'; S.paired = true; S.apart = k; S.screen = 'apart'; }])),
  'pair-choose': () => { S.pair.stage = 'choose'; S.screen = 'pair'; },
  'pair-invite': () => { S.pair.stage = 'invite'; S.screen = 'pair'; },
  'pair-waiting': () => { S.pair.stage = 'waiting'; S.screen = 'pair'; },
  'pair-enter': () => { S.pair.stage = 'enter'; S.pair.entered = '48'; S.screen = 'pair'; },
  'pair-error': () => { S.pair.stage = 'error'; S.pair.entered = '111111'; S.screen = 'pair'; },
  'pair-done': () => { S.pair.stage = 'done'; S.screen = 'pair'; },
  chat: () => { S.mode = 'APART'; S.paired = true; S.chat = 'conversation'; newDeck(); S.deck.revealed = true; S.screen = 'deck'; S.sheet = 'chat'; },
  'chat-empty': () => { S.mode = 'APART'; S.paired = true; S.chat = 'empty'; newDeck(); S.screen = 'deck'; S.sheet = 'chat'; },
  'chat-recording': () => { S.mode = 'APART'; S.paired = true; S.chat = 'recording'; newDeck(); S.screen = 'deck'; S.sheet = 'chat'; },
  store: () => { S.states.store = undefined; S.screen = 'store'; },
  'store-owned': () => { S.states.store = undefined; S.ownedCeiling = 4; S.screen = 'store'; },
  'store-loading': () => { S.states.store = 'loading'; S.screen = 'store'; },
  'store-error': () => { S.states.store = 'error'; S.screen = 'store'; },
  settings: () => { S.screen = 'settings'; },
  lock: () => { S.lockPin = ''; S.screen = 'lock'; },
};
const NAV = [
  ['Entry', [['start', 'First launch'], ['agegate', 'Age gate (returning)']]],
  ['Home', [['home', 'Home — apart, paired'], ['home-together', 'Home — together'], ['home-unpaired', 'Home — apart, not paired'], ['home-locked', 'A locked tier tapped'], ['heat', 'Heat selector'], ['limits', 'Limits sheet'], ['home-loading', 'Loading']]],
  ['The deck', [['deck', 'Face down'], ['deck-up', 'Revealed'], ['deck-long', 'Longest card in the bundle'], ['lanes', 'Lanes'], ['timer', 'Timer'], ['turnup', 'Turn it up?'], ['nothing', 'Nothing left'], ['complete', 'Run complete + aftercare'], ['deck-loading', 'Shuffling'], ['deck-error', 'Error']]],
  ['Consent', [['stopped', 'Stopped'], ['toofar', 'Too far'], ['gate', 'Extreme gate']]],
  ['Game Night', [['night-length', 'How long'], ['night-phase', 'A phase'], ['night-peak', 'The peak'], ['night-care', 'Aftercare'], ['night-end', 'The end']]],
  ['One phone', [['game-names', 'Names'], ['game-handover', 'Hand over'], ['game-dice', 'Heat Dice'], ['game-tod', 'Truth or Dare'], ['game-private', 'A private choice'], ['game-predict', 'A prediction'], ['game-reveal', 'A reveal'], ['game-media', 'Media proof'], ['game-done', 'Finished']]],
  ['A phone each', Object.entries(APART_STATES).map(([k, v]) => [`apart-${k}`, v])],
  ['Pairing', [['pair-choose', 'Choose'], ['pair-invite', 'Show a code'], ['pair-waiting', 'Waiting'], ['pair-enter', 'Enter a code'], ['pair-error', 'Wrong code'], ['pair-done', 'Paired']]],
  ['Chat', [['chat', 'Over a game'], ['chat-recording', 'Recording'], ['chat-empty', 'Empty']]],
  ['Money', [['store', 'Packs'], ['store-owned', 'Owned'], ['store-loading', 'Loading'], ['store-error', 'Error']]],
  ['Settings', [['settings', 'Settings'], ['lock', 'App lock / panic']]],
];

function preset(id) {
  currentPreset = id;
  S.sheet = null; S.toast = null; S.states = {}; S.locked = null; S.paired = true; S.freeMonth = true; S.afterGate = null;
  if (id.startsWith('game-') || id === 'home-together') S.mode = 'TOGETHER';
  else if (id.startsWith('home') || id.startsWith('apart') || id.startsWith('chat')) S.mode = 'APART';
  stopTimer();
  PRESETS[id]();
  render();
}

function renderPanel() {
  const p = $('#panel');
  const html = document.documentElement.classList;
  const opt = (key, label, on) => `<label><input type="checkbox" data-opt="${key}" ${on ? 'checked' : ''}> ${label}</label>`;
  p.innerHTML = `
    <h2>Afterhours — after dark</h2>
    <p class="intro">A working prototype of the redesign. Real cards from the bundle; every control does what the app's does. <b style="color:var(--color-gold)">Proposed</b> marks a control the app does not have yet (ANALYSIS.md §4).</p>
    <h2>Screens</h2>
    ${NAV.map(([group, items]) => `<details ${items.some(([id]) => id === currentPreset) || group === 'Home' ? 'open' : ''}><summary>${group}</summary><div class="nav">${items.map(([id, label]) => `<button data-preset="${id}" aria-current="${id === currentPreset}">${esc(label)}</button>`).join('')}</div></details>`).join('')}
    <h2>Tonight</h2>
    <select data-opt="tier" aria-label="Tier">${TIERS.map((k) => `<option value="${k}" ${k === S.tier ? 'selected' : ''}>${T[k].level} · ${T[k].label}</option>`).join('')}</select>
    <div class="opts" style="margin-top:6px">
      ${opt('apart', 'Apart', S.mode === 'APART')}
      ${opt('paired', 'Paired', S.paired)}
      ${opt('free', 'Free month', S.freeMonth)}
      ${opt('faceDown', 'Deal face down', S.faceDown)}
      ${opt('reduce', 'Reduce motion', html.contains('reduce-motion'))}
      ${opt('big', '200% type', html.contains('big-type'))}
      ${opt('jost', 'Jost for UI', html.contains('jost'))}
      ${opt('marks', 'Hide "proposed"', html.contains('hide-marks'))}
    </div>
    ${S.screen === 'timer' ? '<button class="btn gold block" style="margin-top:8px;min-height:36px" data-act-panel="jump">Jump to the last stretch</button>' : ''}
    <h2>Heat — five hues, cool to hot</h2>
    <table>${TIERS.map((k) => { const x = T[k]; return `<tr><td>${x.label}</td>${['accent', 'onAccent', 'rim', 'onDark', 'container', 'onContainer'].map((r) => `<td><span class="sw" title="${r} ${x[r]}" style="background:${x[r]}"></span></td>`).join('')}</tr>`; }).join('')}
      <tr><td></td><td colspan="6" class="note">accent · on · rim · onDark · container · onContainer</td></tr></table>
    <h2>Contrast — computed from tokens.json</h2>
    <p class="note" style="margin-top:0">Every text pairing the design uses. The build fails below 4.5:1.</p>
    <details><summary>${DATA.contrast.length} pairings, lowest ${Math.min(...DATA.contrast.map((c) => c.ratio))}:1</summary>
    <table>${DATA.contrast.map((c) => `<tr><td><span class="pair-sw" style="color:${c.fgHex};background:${c.bgHex}">Aa</span></td><td>${esc(c.use)}</td><td class="r">${c.ratio.toFixed(2)}${c.aa ? ' ✓' : ' ✗'}</td></tr>`).join('')}</table></details>
    <p class="note">Wrong code on the pairing keypad: 111111. Any other six digits pair.</p>`;
}

/* ---------------------------------------------------------------------------------------------
   Wiring
   --------------------------------------------------------------------------------------------- */

document.addEventListener('click', (e) => {
  const preset_ = e.target.closest('[data-preset]');
  if (preset_) { preset(preset_.dataset.preset); return; }
  if (e.target.closest('[data-act-panel="jump"]')) { if (ticking) ticking.jump(); return; }
  const el = e.target.closest('[data-act]');
  if (!el || el.hasAttribute('disabled')) return;
  const fn = ACT[el.dataset.act];
  if (fn) { e.preventDefault(); fn(el.dataset.arg, el, e); }
});
document.addEventListener('keydown', (e) => {
  if ((e.key === 'Enter' || e.key === ' ') && e.target.matches('[role="button"][data-act]')) { e.preventDefault(); e.target.click(); }
  if (e.key === 'Escape' && S.sheet) { S.sheet = null; render(); }
});
document.addEventListener('change', (e) => {
  const key = e.target.dataset.opt;
  if (!key) return;
  const on = e.target.checked;
  const html = document.documentElement.classList;
  // The panel is a reviewer's shortcut, so it steps over the gate; the app's own controls never do.
  if (key === 'tier') { const v = e.target.value; if (v === 'EXTREME') S.extremeOk = true; S.tier = v; if (S.deck) { S.deck.seen = new Set(); dealNext(); } }
  if (key === 'apart') S.mode = on ? 'APART' : 'TOGETHER';
  if (key === 'paired') S.paired = on;
  if (key === 'free') S.freeMonth = on;
  if (key === 'faceDown') { S.faceDown = on; if (S.deck) S.deck.revealed = !on; }
  if (key === 'reduce') html.toggle('reduce-motion', on);
  if (key === 'big') html.toggle('big-type', on);
  if (key === 'jost') html.toggle('jost', on);
  if (key === 'marks') html.toggle('hide-marks', on);
  render();
});

preset('home');
