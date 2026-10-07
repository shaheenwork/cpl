/**
 * Choosing a game's rounds (BUILD_PROMPT.md sections 3.2, 11; engine/SPEC.md section 10),
 * against the real games and prompts the app ships: every game fills at every level, stays
 * under the couple's ceiling and outside every boundary, warms up rather than jumps, and is
 * the same game for the same seed.
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, it } from 'node:test';
import { applyPromptDeltas, asEnginePrompt } from '../engine/catalogue';
import { permits } from '../engine/chokepoint';
import { GAME_ALTERNATES_PER_KIND, GAME_MIN_ROUNDS, generateGame, rampTargets, type GeneratedGame } from '../engine/game';
import type { CoupleContext, EnginePrompt, GameDefinition } from '../engine/types';
import { openContext, request } from './fixtures/engineFixtures';

const ROOT = join(__dirname, '..', '..', '..');
const bundle = JSON.parse(readFileSync(join(ROOT, 'content', 'dist', 'bundle.json'), 'utf8'));
const games: GameDefinition[] = bundle.games;
const prompts: EnginePrompt[] = bundle.prompts.map(asEnginePrompt);
const byId = new Map(prompts.map((prompt) => [prompt.id, prompt]));

function played(result: GeneratedGame | string): GeneratedGame {
  assert.notEqual(typeof result, 'string', 'expected a game');
  return result as GeneratedGame;
}

/**
 * The game, or nothing — because a couple's limits can rule a whole game out.
 *
 * Blind Taste is every prompt behind `sensory_blindfold`: someone who said never to blindfolds
 * cannot play it, and refusing to deal it is the chokepoint working rather than failing. What must
 * still hold is the other half — that anything it *does* release is permitted.
 */
function playedOrNothing(result: GeneratedGame | string): GeneratedGame | null {
  return typeof result === 'string' ? null : result;
}

function released(game: GeneratedGame): string[] {
  return [...game.rounds.flatMap((round) => round.promptIds), ...game.alternates];
}

function game(id: string): GameDefinition {
  return games.find((candidate) => candidate.id === id)!;
}

describe('every game can be played', () => {
  it('ships every game the app offers', () => {
    // Named rather than counted: a game disappearing from the bundle is a content mistake that
    // no other test would notice, because everything else here loops over whatever is there.
    assert.deepEqual(games.map((each) => each.id).sort(), [
      'FANTASY_MATCH', 'HEAT_DICE', 'HOT_OR_NOT', 'TRUTH_OR_DARE', 'WIN_IT_OWE_IT', 'WOULD_YOU_RATHER',
    ]);
  });

  it('fills every round of every game at every level, from its own prompts, under the ceiling', () => {
    for (const each of games) {
      for (let intensity = 1; intensity <= 5; intensity++) {
        for (const seed of [1, 7, 42]) {
          const result = played(generateGame(prompts, each, request({ intensity, seed }), openContext()));
          assert.equal(result.rounds.length, each.rounds, `${each.id} at ${intensity}`);
          const ids = released(result);
          assert.equal(new Set(ids).size, ids.length, 'a prompt released twice');
          for (const id of ids) {
            const prompt = byId.get(id)!;
            assert.equal(prompt.game, each.id);
            assert.ok(prompt.intensity <= intensity, `${id} above the ceiling`);
          }
        }
      }
    }
  });

  it('holds one prompt of each kind per round, in the game\'s order', () => {
    const truthOrDare = game('TRUTH_OR_DARE');
    const result = played(generateGame(prompts, truthOrDare, request({ intensity: 4 }), openContext()));
    for (const round of result.rounds) {
      assert.deepEqual(round.promptIds.map((id) => byId.get(id)!.kind), ['TRUTH', 'DARE']);
      assert.equal(round.intensity, Math.max(...round.promptIds.map((id) => byId.get(id)!.intensity)));
    }
    const kinds = result.alternates.map((id) => byId.get(id)!.kind);
    assert.equal(kinds.filter((kind) => kind === 'TRUTH').length, GAME_ALTERNATES_PER_KIND);
    assert.equal(kinds.filter((kind) => kind === 'DARE').length, GAME_ALTERNATES_PER_KIND);
  });
});

describe('a game warms up to the couple, never past them', () => {
  it('climbs from two below the ceiling to it', () => {
    assert.deepEqual(rampTargets(8, 5), [3, 3, 3, 3, 4, 4, 4, 5]);
    assert.deepEqual(rampTargets(6, 1), [1, 1, 1, 1, 1, 1]);
    assert.deepEqual(rampTargets(3, 2), [1, 1, 2]);
  });

  it('never goes down from one round to the next, and ends at the ceiling', () => {
    for (const each of games) {
      const result = played(generateGame(prompts, each, request({ intensity: 5, seed: 3 }), openContext()));
      const levels = result.rounds.map((round) => round.intensity);
      for (let i = 1; i < levels.length; i++) assert.ok(levels[i] >= levels[i - 1], `${each.id}: ${levels.join(',')}`);
      assert.equal(levels[levels.length - 1], 5, each.id);
    }
  });

  it('keeps under the more careful partner\'s level, whatever was asked', () => {
    const context = openContext({ filters: { ...openContext().filters, maxIntensity: 2 } });
    for (const each of games) {
      const result = played(generateGame(prompts, each, request({ intensity: 5 }), context));
      assert.equal(result.effectiveIntensity, 2);
      for (const id of released(result)) assert.ok(byId.get(id)!.intensity <= 2, id);
    }
  });
});

describe('the chokepoint holds for games', () => {
  it('never releases a prompt about an excluded theme, for any game, level or seed', () => {
    const themes = [...new Set(prompts.flatMap((prompt) => prompt.excludedByBoundaries))];
    for (const theme of themes) {
      const context = openContext({ filters: { ...openContext().filters, excludedThemes: [theme] } });
      for (const each of games) {
        for (const seed of [5, 11]) {
          const result = generateGame(prompts, each, request({ intensity: 5, seed }), context);
          if (typeof result === 'string') continue;
          for (const id of released(result)) assert.ok(!byId.get(id)!.excludedByBoundaries.includes(theme), `${id} is about ${theme}`);
        }
      }
    }
  });

  it('never releases a prompt leaning on an excluded preference', () => {
    const context = openContext({ filters: { ...openContext().filters, excludedItems: ['comm_fantasies', 'power_negotiated_play'] } });
    for (const each of games) {
      const result = played(generateGame(prompts, each, request({ intensity: 5 }), context));
      for (const id of released(result)) {
        const prompt = byId.get(id)!;
        assert.ok(!prompt.boostedByPreferences.includes('comm_fantasies') && !prompt.boostedByPreferences.includes('power_negotiated_play'), id);
      }
    }
  });

  it('every released prompt is one the chokepoint permits', () => {
    const context = openContext({ filters: { ...openContext().filters, excludedThemes: ['power_leading', 'sensory_blindfold'], maxIntensity: 4 } });
    let playable = 0;
    for (const each of games) {
      const req = request({ intensity: 5, seed: 99 });
      const result = playedOrNothing(generateGame(prompts, each, req, context));
      if (result === null) continue;
      playable += 1;
      for (const id of released(result)) assert.equal(permits(byId.get(id)!, req, context), undefined, id);
    }
    // Most of them survive these limits; a game built entirely on an excluded theme does not, and
    // the app offers what can be dealt rather than a game that would come up empty.
    assert.ok(playable >= games.length - 2, `only ${playable} of ${games.length} games could be dealt`);
  });

  it('never hands out the whole permitted set', () => {
    // A game whose permitted prompts are few: the released set must still leave one out.
    const few = prompts.filter((prompt) => prompt.game === 'WOULD_YOU_RATHER' && prompt.intensity === 1).slice(0, 7);
    const result = played(generateGame(few, { ...game('WOULD_YOU_RATHER'), rounds: 6 }, request({ intensity: 1 }), openContext()));
    assert.ok(released(result).length < few.length);
  });

  it('refuses rather than play fewer than the minimum rounds', () => {
    const few = prompts.filter((prompt) => prompt.game === 'FANTASY_MATCH').slice(0, GAME_MIN_ROUNDS - 1);
    assert.equal(generateGame(few, game('FANTASY_MATCH'), request({ intensity: 5 }), openContext()), 'NOT_ENOUGH_CONTENT');
  });

  it('a disabled prompt is never chosen', () => {
    const target = prompts.find((prompt) => prompt.game === 'WOULD_YOU_RATHER' && prompt.intensity === 1)!;
    const overlaid = applyPromptDeltas(prompts, [{ id: target.id, status: 'disabled', version: target.version, item: null }]);
    for (let seed = 0; seed < 40; seed++) {
      const result = played(generateGame(overlaid, game('WOULD_YOU_RATHER'), request({ intensity: 1, seed }), openContext()));
      assert.ok(!released(result).includes(target.id));
    }
  });

  it('an item delta never becomes a prompt, nor a prompt delta an item', () => {
    const item = { ...bundle.items[0], id: 'added_item' };
    const prompt = { ...bundle.prompts[0], id: 'added_prompt' };
    const deltas = [
      { id: 'added_item', status: 'published', version: 1, item },
      { id: 'added_prompt', status: 'published', version: 1, item: prompt },
    ];
    const overlaid = applyPromptDeltas(prompts, deltas);
    assert.ok(overlaid.some((each) => each.id === 'added_prompt' && each.chapterKinds.length === 0));
    assert.ok(!overlaid.some((each) => each.id === 'added_item'));
  });
});

describe('cooldowns keep a game fresh, and give way before it cannot be played', () => {
  const each = game('FANTASY_MATCH');
  const all = prompts.filter((prompt) => prompt.game === each.id);

  it('prefers prompts not played recently', () => {
    const first = played(generateGame(prompts, each, request({ intensity: 3, seed: 8 }), openContext()));
    const lastPlayed = Object.fromEntries(first.rounds.flatMap((round) => round.promptIds).map((id) => [id, openContext().nowMs]));
    const second = played(generateGame(prompts, each, request({ intensity: 3, seed: 8 }), openContext({ lastPlayed })));
    assert.equal(second.cooldownsLifted, false);
    for (const round of second.rounds) assert.ok(!(round.promptIds[0] in lastPlayed), `${round.promptIds[0]} repeated`);
  });

  it('lifts cooldowns, and only cooldowns, when every prompt was just played', () => {
    const lastPlayed = Object.fromEntries(all.map((prompt) => [prompt.id, openContext().nowMs]));
    const context: CoupleContext = openContext({ lastPlayed, filters: { ...openContext().filters, excludedThemes: ['comm_fantasies'] } });
    const result = played(generateGame(prompts, each, request({ intensity: 5 }), context));
    assert.equal(result.cooldownsLifted, true);
    assert.equal(result.rounds.length, each.rounds);
    for (const id of released(result)) assert.ok(!byId.get(id)!.excludedByBoundaries.includes('comm_fantasies'), id);
  });
});

describe('determinism', () => {
  it('the same seed chooses the same game; another seed usually another', () => {
    for (const each of games) {
      const a = JSON.stringify(generateGame(prompts, each, request({ intensity: 4, seed: 1234 }), openContext()));
      const b = JSON.stringify(generateGame(prompts, each, request({ intensity: 4, seed: 1234 }), openContext()));
      assert.equal(a, b, each.id);
    }
    const seeds = [1, 2, 3, 4, 5].map((seed) =>
      JSON.stringify(played(generateGame(prompts, game('WOULD_YOU_RATHER'), request({ intensity: 3, seed }), openContext())).rounds),
    );
    assert.ok(new Set(seeds).size > 1, 'every seed chose the same rounds');
  });
});
