/**
 * The games and their prompts (BUILD_PROMPT.md sections 9.4, 11). Held to the same standard as
 * items — schema, closed vocabulary, prohibited terms, boundary coverage, near-duplicates —
 * because a prompt reaches a couple the same way an item does: through the chokepoint.
 *
 * A game is data: steps over primitives. So its definition is checked like code would be by a
 * compiler — every step refers only to steps before it, a prediction predicts something that
 * can be predicted, a reveal has what it compares — and a game that could not be played fails
 * the build instead of a night.
 */
import type { ContentRepo, GameInfo, GamePrompt, GameStep } from './content';
import { checkList, checkText, jaccard, NEAR_DUPLICATE_THRESHOLD, trigrams, type Shared } from './validate';

const GAME_ID = /^[A-Z][A-Z_]{2,40}$/;
const PROMPT_ID = /^[a-z0-9_]{3,64}$/;
const MODES = ['TOGETHER', 'APART'];
const FIELDS = [
  'id', 'version', 'game', 'kind', 'text', 'options', 'seconds', 'category', 'tags', 'intensity', 'modes', 'moods',
  'requiredMutualPreferences', 'boostedByPreferences', 'excludedByBoundaries', 'noveltyWeight', 'repeatCooldownDays',
  'status', 'locale', 'createdAt', 'updatedAt',
];

const LIMITS = {
  text: [10, 220] as const,
  option: [1, 70] as const,
  seconds: [10, 180] as const,
  literal: 60,
  rounds: [3, 12] as const,
  cooldownMax: 365,
};

/** What each game needs to be playable at every level (section 11.2). */
export const GAME_COVERAGE = { perIntensity: 3 };

export function validateGames(repo: ContentRepo, shared: Shared, problems: string[], lines: string[]): void {
  const problem = (where: string, message: string) => problems.push(`${where}: ${message}`);
  const vocab = repo.vocabulary.games;
  const seenGames = new Set<string>();
  const seenPrompts = new Map<string, string>();

  lines.push('');
  lines.push('game                    prompts  1    2    3    4    5    kinds');

  for (const game of repo.games) {
    const at = `games.json ${game.id}`;
    const check = (ok: boolean, message: string) => {
      if (!ok) problem(at, message);
    };
    check(GAME_ID.test(game.id), 'id must be UPPER_SNAKE_CASE');
    check(!seenGames.has(game.id), 'duplicate game id');
    seenGames.add(game.id);
    check(/^[a-z]{3,12}$/.test(game.prefix), 'prefix must be 3..12 lowercase letters');
    checkText(check, game.title, 'title', 1, 40);
    checkText(check, game.tagline, 'tagline', 1, 70);
    checkText(check, game.description, 'description', 1, 160);
    checkList(check, game.modes, 'modes', MODES, 1);
    check(Number.isInteger(game.rounds) && game.rounds >= LIMITS.rounds[0] && game.rounds <= LIMITS.rounds[1], `rounds must be ${LIMITS.rounds.join('..')}`);
    check(vocab.turns.includes(game.turns), `unknown turns "${game.turns}"`);
    check(vocab.scoring.includes(game.scoring), `unknown scoring "${game.scoring}"`);
    checkList(check, game.promptKinds, 'promptKinds', vocab.promptKinds, 0);
    const [minOptions, maxOptions] = Array.isArray(game.promptOptions) ? game.promptOptions : [NaN, NaN];
    check(Number.isInteger(minOptions) && Number.isInteger(maxOptions) && minOptions >= 0 && maxOptions <= 4 && minOptions <= maxOptions, 'promptOptions must be [min, max] within 0..4');
    check(Number.isInteger(game.minPrompts) && game.minPrompts >= game.rounds, 'minPrompts must be at least one game\'s worth of rounds');
    validateSteps(game, vocab, repo.vocabulary.media, check);

    // --- the game's prompts --------------------------------------------------------------
    const raw = repo.promptsByGame.get(game.id) ?? [];
    const prompts: GamePrompt[] = [];
    const usesSeconds = game.steps.some((step) => step.seconds === 'PROMPT');
    raw.forEach((candidate, index) => {
      const where = `prompts/${game.file}[${index}]`;
      if (typeof candidate !== 'object' || candidate === null || Array.isArray(candidate)) {
        problem(where, 'not an object');
        return;
      }
      const prompt = candidate as GamePrompt;
      const pat = `prompts/${game.file} ${typeof prompt.id === 'string' ? prompt.id : `[${index}]`}`;
      const pcheck = (ok: boolean, message: string) => {
        if (!ok) problem(pat, message);
      };
      for (const key of Object.keys(prompt)) pcheck(FIELDS.includes(key), `unknown field "${key}"`);
      for (const key of FIELDS) pcheck(key in prompt, `missing field "${key}"`);

      pcheck(typeof prompt.id === 'string' && PROMPT_ID.test(prompt.id), 'id must match ^[a-z0-9_]{3,64}$');
      pcheck(typeof prompt.id === 'string' && prompt.id.startsWith(`${game.prefix}_`), `id must start with "${game.prefix}_"`);
      if (typeof prompt.id === 'string') {
        const earlier = seenPrompts.get(prompt.id) ?? shared.itemIds.get(prompt.id);
        pcheck(earlier === undefined, `duplicate id (also in ${earlier})`);
        seenPrompts.set(prompt.id, game.file);
      }
      pcheck(Number.isInteger(prompt.version) && prompt.version >= 1, 'version must be a positive integer');
      pcheck(prompt.game === game.id, `game must be "${game.id}"`);
      if (game.promptKinds.length === 0) pcheck(prompt.kind === null, 'kind must be null: this game has no prompt kinds');
      else pcheck(game.promptKinds.includes(prompt.kind as string), `kind must be one of ${game.promptKinds.join(', ')}`);
      checkText(pcheck, prompt.text, 'text', LIMITS.text[0], LIMITS.text[1]);
      if (!Array.isArray(prompt.options)) {
        pcheck(false, 'options must be an array');
      } else {
        pcheck(prompt.options.length >= minOptions && prompt.options.length <= maxOptions, `needs ${minOptions}..${maxOptions} options`);
        prompt.options.forEach((option, i) => checkText(pcheck, option, `options[${i}]`, LIMITS.option[0], LIMITS.option[1]));
        pcheck(new Set(prompt.options.map((o) => String(o).toLowerCase())).size === prompt.options.length, 'options repeat');
      }
      if (usesSeconds) {
        pcheck(Number.isInteger(prompt.seconds) && (prompt.seconds as number) >= LIMITS.seconds[0] && (prompt.seconds as number) <= LIMITS.seconds[1], `seconds must be ${LIMITS.seconds.join('..')}`);
      } else {
        pcheck(prompt.seconds === null, 'seconds must be null: no step of this game times its prompt');
      }
      pcheck(shared.categoryIds.has(prompt.category), `unknown category "${prompt.category}"`);
      checkList(pcheck, prompt.tags, 'tags', repo.vocabulary.tags, 1);
      checkList(pcheck, prompt.moods, 'moods', repo.vocabulary.moods, 0);
      checkList(pcheck, prompt.modes, 'modes', MODES, 1);
      pcheck(Array.isArray(prompt.modes) && prompt.modes.some((mode) => game.modes.includes(mode)), 'playable in none of the game\'s modes');
      checkList(pcheck, prompt.boostedByPreferences, 'boostedByPreferences', [...shared.taxonomyItems.keys()], 0);
      checkList(pcheck, prompt.requiredMutualPreferences, 'requiredMutualPreferences', [...shared.taxonomyItems.keys()], 0);
      checkList(pcheck, prompt.excludedByBoundaries, 'excludedByBoundaries', [...shared.themeIds], 1);
      pcheck(Number.isInteger(prompt.intensity) && prompt.intensity >= 1 && prompt.intensity <= 5, 'intensity must be 1..5');
      pcheck(typeof prompt.noveltyWeight === 'number' && prompt.noveltyWeight >= 0 && prompt.noveltyWeight <= 1, 'noveltyWeight must be 0..1');
      pcheck(Number.isInteger(prompt.repeatCooldownDays) && prompt.repeatCooldownDays >= 0 && prompt.repeatCooldownDays <= LIMITS.cooldownMax, `repeatCooldownDays must be 0..${LIMITS.cooldownMax}`);
      pcheck(repo.vocabulary.statuses.includes(prompt.status), `unknown status "${prompt.status}"`);
      pcheck(prompt.locale === 'en', 'locale must be "en"');
      const created = Date.parse(prompt.createdAt);
      const updated = Date.parse(prompt.updatedAt);
      pcheck(!Number.isNaN(created) && !Number.isNaN(updated) && updated >= created, 'createdAt/updatedAt must be dates, updated not before created');

      const text = [prompt.text, ...(Array.isArray(prompt.options) ? prompt.options : [])].filter((s) => typeof s === 'string').join(' \n ');
      shared.checkSafety(pcheck, text, prompt);
      prompts.push(prompt);
    });

    // --- near-duplicates, within the game --------------------------------------------------
    const shingles = prompts.map((prompt) => ({
      id: prompt.id,
      set: trigrams([prompt.text, ...(Array.isArray(prompt.options) ? prompt.options : [])].join(' ')),
      // A shared stem ('Would you rather…') is fine; the same stem with the same options is not.
      text: [prompt.text, ...(Array.isArray(prompt.options) ? prompt.options : [])].join(' | ').trim().toLowerCase(),
    }));
    for (let i = 0; i < shingles.length; i++) {
      for (let j = i + 1; j < shingles.length; j++) {
        if (shingles[i].text === shingles[j].text) problem(shingles[i].id, `same prompt as ${shingles[j].id}`);
        const similarity = jaccard(shingles[i].set, shingles[j].set);
        if (similarity >= NEAR_DUPLICATE_THRESHOLD) {
          problem(shingles[i].id, `near-duplicate of ${shingles[j].id} (similarity ${similarity.toFixed(2)})`);
        }
      }
    }

    // --- enough to play, at every level -------------------------------------------------------
    const published = prompts.filter((prompt) => prompt.status === 'published' && prompt.modes?.includes('TOGETHER'));
    const byLevel = [1, 2, 3, 4, 5].map((level) => published.filter((prompt) => prompt.intensity === level).length);
    if (published.length < game.minPrompts) problem(game.id, `${published.length} published prompts, needs at least ${game.minPrompts}`);
    const kinds = game.promptKinds.length === 0 ? [null] : game.promptKinds;
    for (const kind of kinds) {
      for (let level = 1; level <= 5; level++) {
        const count = published.filter((prompt) => prompt.intensity === level && (kind === null || prompt.kind === kind)).length;
        if (count < GAME_COVERAGE.perIntensity) {
          problem(game.id, `only ${count} ${kind ?? 'prompts'} at intensity ${level}, needs ${GAME_COVERAGE.perIntensity}`);
        }
      }
      // A SOFT couple plays every round at level 1, and the server never releases every
      // prompt it may (section 5.5): a whole game at level 1, and one to spare.
      const soft = published.filter((prompt) => prompt.intensity === 1 && (kind === null || prompt.kind === kind)).length;
      if (soft < game.rounds + 1) {
        problem(game.id, `only ${soft} ${kind ?? 'prompts'} at intensity 1, needs ${game.rounds + 1} to fill a game`);
      }
    }
    lines.push(
      `${game.id.padEnd(22)} ${String(published.length).padStart(7)} ${byLevel.map((n) => String(n).padStart(4)).join(' ')}    ${game.promptKinds.join('/') || '-'}`,
    );
  }
}

/** A game's steps must be playable: each refers only to what came before, and makes sense. */
function validateSteps(
  game: GameInfo,
  vocab: ContentRepo['vocabulary']['games'],
  media: string[],
  check: (ok: boolean, message: string) => void,
): void {
  if (!Array.isArray(game.steps) || game.steps.length === 0) {
    check(false, 'needs at least one step');
    return;
  }
  let usesPrompt = false;
  game.steps.forEach((step: GameStep, index) => {
    const s = `step ${index} (${step.primitive})`;
    check(vocab.primitives.includes(step.primitive), `${s}: unknown primitive`);
    check(vocab.who.includes(step.who), `${s}: unknown who "${step.who}"`);
    if (step.text !== undefined) {
      check(step.text === 'PROMPT' || (typeof step.text === 'string' && step.text.length > 0 && step.text.length <= LIMITS.literal), `${s}: text must be PROMPT or 1..${LIMITS.literal} characters`);
      if (step.text === 'PROMPT') usesPrompt = true;
    }
    const refs = step.of ?? [];
    check(Array.isArray(refs) && refs.every((ref) => Number.isInteger(ref) && ref >= 0 && ref < index), `${s}: "of" may only name earlier steps`);
    const earlier = (ref: number) => game.steps[ref];
    const answering = ['QUESTION', 'CHOICE', 'PREDICTION', 'VOTE', 'DIAL', 'MEDIA'];
    switch (step.primitive) {
      case 'CHOICE':
      case 'VOTE':
      case 'PREDICTION': {
        const options = step.options;
        if (step.primitive === 'PREDICTION' && step.answer === 'TEXT') {
          check(options === undefined, `${s}: a text prediction has no options`);
        } else if (options === 'PROMPT') {
          check(game.promptOptions[0] >= 2, `${s}: options come from the prompt, but prompts carry fewer than 2`);
        } else if (options === 'STATEMENTS') {
          check(refs.length === 1 && earlier(refs[0])?.answer === 'STATEMENTS', `${s}: STATEMENTS options need "of" a STATEMENTS question`);
        } else {
          check(Array.isArray(options) && options.length >= 2, `${s}: needs PROMPT, STATEMENTS or at least two fixed options`);
          if (Array.isArray(options)) {
            for (const option of options) check([...vocab.voteOptions, ...vocab.promptKinds].includes(option), `${s}: unknown option "${option}"`);
          }
        }
        if (step.primitive === 'PREDICTION') {
          check(refs.length === 1 && answering.includes(earlier(refs[0])?.primitive ?? ''), `${s}: predicts exactly one earlier answer`);
          check(step.who !== earlier(refs[0])?.who || step.who === 'EACH', `${s}: nobody predicts their own answer`);
        }
        break;
      }
      case 'QUESTION':
        check(vocab.answers.includes(step.answer ?? ''), `${s}: answer must be one of ${vocab.answers.join(', ')}`);
        break;
      case 'REVEAL':
        check(vocab.outcomes.includes(step.outcome ?? ''), `${s}: outcome must be one of ${vocab.outcomes.join(', ')}`);
        check(refs.length >= 1 && refs.every((ref) => answering.includes(earlier(ref)?.primitive ?? '')), `${s}: reveals earlier answers`);
        if (step.outcome === 'DISTANCE') {
          check(refs.every((ref) => earlier(ref)?.primitive === 'DIAL'), `${s}: a DISTANCE reveal compares dials`);
          check(refs.every((ref) => earlier(ref)?.who === 'EACH'), `${s}: a gap needs a dial from each of them`);
        }
        break;
      case 'DRAW':
        // Chance needs something to choose between, and the pool is the round's own prompt: a
        // fixed list in the game would be the same spin every round.
        check(step.options === 'PROMPT', `${s}: a draw takes its pool from the prompt`);
        check(game.promptOptions[0] >= 2, `${s}: a draw needs prompts carrying at least 2 options`);
        break;
      case 'MEDIA':
        // A recording answers the round and stays on the phone that made it; the step still has to
        // say which kind, because the screen cannot guess.
        check(typeof step.media === 'string' && media.includes(step.media), `${s}: media must be one of ${media.join(', ')}`);
        check(step.text !== undefined, `${s}: a recording must say what it is answering`);
        break;
      case 'DIAL':
        check(step.options === undefined, `${s}: a dial has no options — it is a number`);
        check(step.text !== undefined, `${s}: a dial must say what it is asking about`);
        break;
      case 'TIMER':
        check(step.seconds === 'PROMPT' || (Number.isInteger(step.seconds) && (step.seconds as number) >= LIMITS.seconds[0]), `${s}: seconds must be PROMPT or a number`);
        break;
      case 'CHALLENGE':
        if (step.kindFrom !== undefined) {
          const from = earlier(step.kindFrom);
          check(
            Number.isInteger(step.kindFrom) && step.kindFrom < index && from?.primitive === 'CHOICE' &&
              Array.isArray(from.options) && game.promptKinds.every((kind) => (from.options as string[]).includes(kind)),
            `${s}: kindFrom must name an earlier CHOICE between the game's prompt kinds`,
          );
        } else {
          check(game.promptKinds.length === 0, `${s}: a game with prompt kinds must say which choice picks the kind`);
        }
        break;
      default:
        break;
    }
  });
  check(usesPrompt, 'no step shows the round\'s prompt');
  if (game.scoring !== 'NONE') check(game.steps.some((step) => step.primitive === 'REVEAL'), 'a scored game needs a REVEAL');
}
