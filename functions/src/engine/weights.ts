/**
 * The ranking weights w1–w7 from Remote Config (BUILD_PROMPT.md section 10.3: "Weights live in
 * Remote Config with sane defaults so they can be tuned without a release"), read from the
 * server template under the same keys the app's template uses.
 *
 * A weight that is missing, not a number, negative or absurd falls back to its default: a
 * typo in the console must never turn ranking upside down. Under the emulator there is no
 * Remote Config, and the defaults apply.
 */
import { getRemoteConfig } from 'firebase-admin/remote-config';
import { DEFAULT_WEIGHTS, type Weights } from './types';

export const WEIGHT_KEYS: Record<keyof Weights, string> = {
  mutualPreference: 'weight_mutual_preference',
  affinity: 'weight_affinity',
  moodMatch: 'weight_mood_match',
  novelty: 'weight_novelty',
  intensityFit: 'weight_intensity_fit',
  recencyPenalty: 'weight_recency_penalty',
  repetitionPenalty: 'weight_repetition_penalty',
};

const MAX_WEIGHT = 20;
const CACHE_MS = 10 * 60 * 1000;
let cached: { weights: Weights; loadedAt: number } | undefined;

export function safeWeights(raw: Partial<Record<keyof Weights, unknown>>): Weights {
  const weights = { ...DEFAULT_WEIGHTS };
  for (const key of Object.keys(DEFAULT_WEIGHTS) as (keyof Weights)[]) {
    const value = raw[key];
    if (typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= MAX_WEIGHT) weights[key] = value;
  }
  return weights;
}

export async function engineWeights(nowMs: number = Date.now()): Promise<Weights> {
  if (process.env.FUNCTIONS_EMULATOR === 'true' || process.env.FIRESTORE_EMULATOR_HOST) return DEFAULT_WEIGHTS;
  if (cached && nowMs - cached.loadedAt < CACHE_MS) return cached.weights;
  try {
    const defaults = Object.fromEntries(
      (Object.keys(WEIGHT_KEYS) as (keyof Weights)[]).map((key) => [WEIGHT_KEYS[key], DEFAULT_WEIGHTS[key]]),
    );
    const config = (await getRemoteConfig().getServerTemplate({ defaultConfig: defaults })).evaluate();
    const weights = safeWeights(
      Object.fromEntries((Object.keys(WEIGHT_KEYS) as (keyof Weights)[]).map((key) => [key, config.getNumber(WEIGHT_KEYS[key])])),
    );
    cached = { weights, loadedAt: nowMs };
    return weights;
  } catch {
    return cached?.weights ?? DEFAULT_WEIGHTS;
  }
}
