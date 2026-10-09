// Builds design/redesign/afterhours-redesign.html: one self-contained file.
//
//   node design/redesign/src/build.mjs
//
// tokens.json is the source of truth. This turns it into CSS custom properties, computes the
// contrast of every declared text pairing (failing the build below WCAG AA), writes those ratios
// back into tokens.json, lifts the app's own icon paths out of AfterhoursIcons.kt so the preview's
// icons *are* the app's icons, samples real cards from the content bundle, and inlines the lot.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { NEW_ICONS } from './icons.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, '..');
const repo = resolve(out, '..', '..');

const tokensPath = join(out, 'tokens.json');
const tokens = JSON.parse(readFileSync(tokensPath, 'utf8'));

// ---------------------------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------------------------

const kebab = (s) => s.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase();
const hexToRgb = (hex) => {
  const h = hex.replace('#', '');
  return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
};
const channel = (c) => {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
};
const luminance = (hex) => {
  const [r, g, b] = hexToRgb(hex).map(channel);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
};
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

const palette = tokens.palette;
const resolveColour = (ref) => {
  if (typeof ref === 'string' && ref.startsWith('#')) return ref;
  if (typeof ref === 'string' && palette[ref]) return palette[ref];
  if (typeof ref === 'string' && ref.startsWith('palette.')) return palette[ref.slice(8)];
  throw new Error(`Unknown colour reference: ${JSON.stringify(ref)}`);
};

// ---------------------------------------------------------------------------------------------
// Contrast — computed, never claimed
// ---------------------------------------------------------------------------------------------

const AA = 4.5;
const contrast = [];
for (const pair of tokens.textPairings) {
  const tiers = pair.fg.startsWith('tier.') || pair.bg.startsWith('tier.') ? Object.keys(tokens.tierColors) : [null];
  for (const tier of tiers) {
    const pick = (ref) => (ref.startsWith('tier.') ? tokens.tierColors[tier][ref.slice(5)] : resolveColour(ref));
    const fg = pick(pair.fg);
    const bg = pick(pair.bg);
    const value = Math.round(ratio(fg, bg) * 100) / 100;
    contrast.push({
      use: tier ? `${pair.use} — ${tokens.tierColors[tier].label}` : pair.use,
      fg: tier ? pair.fg.replace('tier.', `${tier}.`) : pair.fg,
      bg: tier ? pair.bg.replace('tier.', `${tier}.`) : pair.bg,
      fgHex: fg,
      bgHex: bg,
      ratio: value,
      aa: value >= AA,
    });
  }
}
const failing = contrast.filter((c) => !c.aa);
if (failing.length) {
  for (const f of failing) console.error(`  ${f.ratio}:1  ${f.use}  (${f.fgHex} on ${f.bgHex})`);
  throw new Error(`${failing.length} text pairing(s) below WCAG AA ${AA}:1`);
}
tokens.contrast = contrast;
writeFileSync(tokensPath, `${JSON.stringify(tokens, null, 2)}\n`);

// ---------------------------------------------------------------------------------------------
// CSS custom properties
// ---------------------------------------------------------------------------------------------

const css = [':root {', '  color-scheme: dark;'];
const rgbVar = (name, hex) => css.push(`  ${name}-rgb: ${hexToRgb(hex).join(' ')};`);
for (const [key, hex] of Object.entries(palette)) {
  css.push(`  --color-${kebab(key)}: ${hex};`);
  rgbVar(`--color-${kebab(key)}`, hex);
}
for (const [slot, ref] of Object.entries(tokens.colorScheme)) {
  css.push(`  --scheme-${kebab(slot)}: var(--color-${kebab(ref)});`);
}
for (const [key, ref] of Object.entries(tokens.afterhoursColors)) {
  if (typeof ref === 'object') {
    css.push(`  --ah-${kebab(key)}: rgb(var(--color-${kebab(ref.color)}-rgb) / ${ref.alpha});`);
  } else {
    css.push(`  --ah-${kebab(key)}: var(--color-${kebab(ref)});`);
  }
}
const ROLES = ['accent', 'onAccent', 'rim', 'onDark', 'container', 'onContainer'];
for (const [tier, t] of Object.entries(tokens.tierColors)) {
  for (const role of ROLES) {
    css.push(`  --tier-${tier.toLowerCase()}-${kebab(role)}: ${t[role]};`);
    if (role === 'accent' || role === 'rim') rgbVar(`--tier-${tier.toLowerCase()}-${kebab(role)}`, t[role]);
  }
}
for (const [key, px] of Object.entries(tokens.spacing)) css.push(`  --space-${kebab(key)}: ${px}px;`);
for (const [key, px] of Object.entries(tokens.shapes)) css.push(`  --radius-${kebab(key)}: ${px}px;`);
for (const [key, ms] of Object.entries(tokens.motion.durations)) css.push(`  --motion-${kebab(key)}: ${ms}ms;`);
for (const [key, b] of Object.entries(tokens.motion.easings)) css.push(`  --ease-${kebab(key)}: cubic-bezier(${b.join(', ')});`);
const families = tokens.typography.families;
for (const [key, f] of Object.entries(families)) css.push(`  --font-${kebab(key)}: '${f.name}', ${f.fallback};`);
for (const [key, s] of Object.entries(tokens.typography.styles)) {
  const name = kebab(key);
  css.push(`  --type-${name}-family: var(--font-${kebab(s.family)});`);
  css.push(`  --type-${name}-size: ${s.size}px;`);
  css.push(`  --type-${name}-line: ${s.lineHeight}px;`);
  css.push(`  --type-${name}-weight: ${s.weight};`);
  css.push(`  --type-${name}-tracking: ${s.tracking}em;`);
}
css.push('}');
// ProvideTier, as a selector: everything inside a [data-tier] takes that tier's accents.
for (const tier of Object.keys(tokens.tierColors)) {
  const t = tier.toLowerCase();
  css.push(`[data-tier="${t}"] {`);
  for (const role of ROLES) css.push(`  --tier-${kebab(role)}: var(--tier-${t}-${kebab(role)});`);
  css.push(`  --tier-accent-rgb: var(--tier-${t}-accent-rgb);`);
  css.push(`  --tier-rim-rgb: var(--tier-${t}-rim-rgb);`);
  css.push('}');
}

// ---------------------------------------------------------------------------------------------
// Icons — the app's own, plus what the redesign adds
// ---------------------------------------------------------------------------------------------

const iconSource = readFileSync(
  join(repo, 'core/designsystem/src/main/kotlin/com/shnapps/couple/core/designsystem/icon/AfterhoursIcons.kt'),
  'utf8',
);
const appIcons = {};
for (const match of iconSource.matchAll(/val\s+(\w+)\s*=\s*icon\(\s*"\w+"\s*,([\s\S]*?)\)\s*$/gm)) {
  appIcons[match[1]] = [...match[2].matchAll(/"([^"]+)"/g)].map((m) => m[1]);
}
if (Object.keys(appIcons).length < 20) throw new Error(`Only ${Object.keys(appIcons).length} app icons parsed`);
const icons = { ...appIcons, ...NEW_ICONS };
const sprite = Object.entries(icons)
  .map(([name, paths]) => `<symbol id="i-${name}" viewBox="0 0 24 24">${paths.map((d) => `<path d="${d}"/>`).join('')}</symbol>`)
  .join('');

// ---------------------------------------------------------------------------------------------
// Sample data — real cards, including the bundle's worst cases for layout
// ---------------------------------------------------------------------------------------------

const bundle = JSON.parse(readFileSync(join(repo, 'content/dist/bundle.json'), 'utf8'));
const live = bundle.items.filter((i) => i.status === 'published' && i.locale === 'en');
const pick = (i) => ({
  id: i.id, title: i.title, subtitle: i.subtitle, body: i.body, pack: i.pack, intensity: i.intensity,
  durationMin: i.durationMin, moods: i.moods, modes: i.modes, tags: i.tags,
  excludedByBoundaries: i.excludedByBoundaries, interactionType: i.interactionType,
});
const chosen = new Map();
const add = (i) => { if (i && !chosen.has(i.id)) chosen.set(i.id, pick(i)); };
const longestTitle = [...live].sort((a, b) => b.title.length - a.title.length)[0];
const longestBody = [...live].sort((a, b) => b.body.length - a.body.length)[0];
for (let t = 1; t <= 5; t += 1) {
  const tier = live.filter((i) => i.intensity === t && !(i.tags || []).includes('aftercare'));
  const packs = new Set();
  const timed = tier.find((i) => i.durationMin);
  add(timed);
  if (timed) packs.add(timed.pack);
  for (const i of tier) {
    if ([...chosen.values()].filter((c) => c.intensity === t).length >= 5) break;
    if (packs.has(i.pack)) continue;
    packs.add(i.pack);
    add(i);
  }
}
add(longestTitle);
add(longestBody);
live.filter((i) => (i.tags || []).includes('aftercare')).slice(0, 3).forEach(add);
const games = bundle.games.map((g) => ({
  id: g.id, title: g.title, tagline: g.tagline, description: g.description, rounds: g.rounds, turns: g.turns, steps: g.steps,
}));
const prompts = {};
for (const g of bundle.games) {
  const all = bundle.prompts.filter((p) => p.game === g.id && p.status === 'published').sort((a, b) => a.intensity - b.intensity);
  const step = Math.max(1, Math.floor(all.length / 10));
  prompts[g.id] = all.filter((_, i) => i % step === 0).slice(0, 10)
    .map((p) => ({ id: p.id, text: p.text, options: p.options, kind: p.kind, intensity: p.intensity, seconds: p.seconds }));
}
// Truth or Dare needs both kinds whatever the sampling landed on.
for (const kind of ['TRUTH', 'DARE']) {
  if (!prompts.TRUTH_OR_DARE.some((p) => p.kind === kind)) {
    const p = bundle.prompts.find((x) => x.game === 'TRUTH_OR_DARE' && x.kind === kind);
    if (p) prompts.TRUTH_OR_DARE.push({ id: p.id, text: p.text, options: p.options, kind: p.kind, intensity: p.intensity, seconds: p.seconds });
  }
}
const data = {
  cards: [...chosen.values()],
  worst: { title: longestTitle.id, body: longestBody.id },
  games,
  prompts,
  packs: bundle.packs.map((p) => ({ id: p.id, title: p.title, description: p.description, intensity: p.intensity })),
  tiers: tokens.tierColors,
  contrast,
  limits: [
    ['KISSING', 'Kissing', 'Kissing, and flirting that leads there'],
    ['TOUCH', 'Touch', 'Hands, massage, warm and cool'],
    ['TEASING', 'Teasing', 'Being made to wait, and games about it'],
    ['ROLE_PLAY', 'Role-play', 'Other names, other stories, characters'],
    ['SENSORY', 'Sensory', 'Light, sound, scent, blindfolds'],
    ['VERBAL', 'Verbal', 'Saying it out loud, confessions, voice notes'],
    ['POWER_PLAY', 'Power play', 'Who leads, who follows, instructions'],
    ['FANTASY', 'Fantasy', 'Imagining it, and trying something new'],
  ],
};

// ---------------------------------------------------------------------------------------------
// Inline
// ---------------------------------------------------------------------------------------------

const shell = readFileSync(join(here, 'shell.html'), 'utf8');
const html = shell
  .replace('/*TOKENS*/', css.join('\n'))
  .replace('/*STYLES*/', readFileSync(join(here, 'app.css'), 'utf8'))
  .replace('<!--SPRITE-->', `<svg width="0" height="0" style="position:absolute" aria-hidden="true">${sprite}</svg>`)
  .replace('/*DATA*/', () => JSON.stringify(data))
  .replace('/*SCRIPT*/', () => readFileSync(join(here, 'app.js'), 'utf8'));
for (const hole of ['/*TOKENS*/', '/*STYLES*/', '<!--SPRITE-->', '/*DATA*/', '/*SCRIPT*/']) {
  if (html.includes(hole)) throw new Error(`Placeholder left in the output: ${hole}`);
}
writeFileSync(join(out, 'afterhours-redesign.html'), html);

console.log(
  `afterhours-redesign.html: ${(html.length / 1024).toFixed(0)} KB · ${Object.keys(appIcons).length} app icons + ` +
  `${Object.keys(NEW_ICONS).length} new · ${data.cards.length} cards · ${contrast.length} text pairings, ` +
  `lowest ${Math.min(...contrast.map((c) => c.ratio))}:1`,
);
