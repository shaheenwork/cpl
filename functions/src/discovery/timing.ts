/**
 * The reveal window, from Remote Config (BUILD_PROMPT.md section 5.3: "jitter window
 * configurable via Remote Config, default 30–180 min").
 *
 * Whatever the configuration says, the result passes through `safeTiming`, so no value can
 * take the minimum delay below the hard floor or produce an empty window. When Remote
 * Config cannot be reached — always, under the emulator — the spec's defaults apply.
 */
import { getRemoteConfig } from 'firebase-admin/remote-config';
import { DEFAULT_TIMING, safeTiming, type RevealTiming } from './match';

export const TIMING_KEYS = {
  minMinutes: 'reveal_min_delay_minutes',
  maxMinutes: 'reveal_max_delay_minutes',
} as const;

const MINUTE_MS = 60 * 1000;
const CACHE_MS = 10 * MINUTE_MS;

let cached: { timing: RevealTiming; loadedAt: number } | undefined;

export async function revealTiming(nowMs: number = Date.now()): Promise<RevealTiming> {
  // No Remote Config service exists under the emulator; asking would only time out.
  if (process.env.FUNCTIONS_EMULATOR === 'true' || process.env.FIRESTORE_EMULATOR_HOST) return DEFAULT_TIMING;
  if (cached && nowMs - cached.loadedAt < CACHE_MS) return cached.timing;

  try {
    const template = await getRemoteConfig().getServerTemplate({
      defaultConfig: {
        [TIMING_KEYS.minMinutes]: DEFAULT_TIMING.minDelayMs / MINUTE_MS,
        [TIMING_KEYS.maxMinutes]: DEFAULT_TIMING.maxDelayMs / MINUTE_MS,
      },
    });
    const config = template.evaluate();
    const timing = safeTiming(
      config.getNumber(TIMING_KEYS.minMinutes) * MINUTE_MS,
      config.getNumber(TIMING_KEYS.maxMinutes) * MINUTE_MS,
    );
    cached = { timing, loadedAt: nowMs };
    return timing;
  } catch {
    // A configuration outage must not stop reveals, and the defaults are safe.
    return cached?.timing ?? DEFAULT_TIMING;
  }
}
