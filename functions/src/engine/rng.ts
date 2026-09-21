/**
 * mulberry32 (engine/SPEC.md section 2): tiny, fast, and — using only 32-bit integer
 * arithmetic — reproducible bit for bit in Kotlin. Every random choice the engine makes comes
 * from here, in the order the spec lists, so the same seed always gives the same night.
 */
export class Rng {
  private state: number;

  constructor(seed: number) {
    this.state = seed | 0;
  }

  /** A double in [0, 1). */
  next(): number {
    this.state = (this.state + 0x6d2b79f5) | 0;
    let t = this.state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  /** An integer in [0, n). */
  nextInt(n: number): number {
    return Math.floor(this.next() * n);
  }
}

/** The seed for retry `attempt` of a generation (SPEC section 2). */
export function attemptSeed(seed: number, attempt: number): number {
  return (seed + Math.imul(attempt, 0x9e3779b9)) >>> 0;
}
