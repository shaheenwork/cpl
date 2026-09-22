/**
 * What a partner thought of a chapter becomes their affinity (BUILD_PROMPT.md sections 2.3,
 * 6.1, 8: onFeedbackWrite → update per-user affinity scores).
 *
 * Feedback is written by the partner who gave it, to `couples/{cid}/feedback/{id}`, where the
 * rules let only them read it and only for a chapter of a night that was actually played
 * (DECISIONS.md D-063). This turns it into the scores the engine ranks by — the partner's own,
 * in `users/{uid}/affinity`, which nobody else can read. The engine averages both partners'
 * scores and never returns them (section 5.4).
 *
 * Idempotent: the feedback document is marked processed in the same transaction that moves
 * the scores, so a trigger delivered twice counts once.
 */
import { Timestamp, type Firestore } from 'firebase-admin/firestore';
import { loadCatalogue, type Catalogue } from '../engine/catalogue';
import type { EngineItem } from '../engine/types';

/** Each reaction's pull on a score in [-1, 1]. */
export const REACTION_VALUES = { LOVED: 1, FINE: 0, NOT_FOR_US: -1 } as const;
export type Reaction = keyof typeof REACTION_VALUES;

/**
 * Scores follow roughly the last AFFINITY_WINDOW reactions: early ones move a score quickly,
 * and no single night can swing a long history.
 */
export const AFFINITY_WINDOW = 20;

export interface AffinityScore {
  score: number;
  samples: number;
}

/** The dimensions an item scores against (engine/SPEC.md section 4's affinity keys). */
export function dimensionsOf(item: EngineItem): string[] {
  return [`category:${item.category}`, `interaction:${item.interactionType}`, ...item.moods.map((mood) => `mood:${mood}`)];
}

/** A running mean over at most AFFINITY_WINDOW samples, kept inside [-1, 1]. */
export function nextAffinity(current: AffinityScore | undefined, reaction: Reaction): AffinityScore {
  const score = current && Number.isFinite(current.score) ? Math.max(-1, Math.min(1, current.score)) : 0;
  const samples = current && Number.isInteger(current.samples) && current.samples > 0 ? current.samples : 0;
  const weight = Math.min(samples + 1, AFFINITY_WINDOW);
  const next = score + (REACTION_VALUES[reaction] - score) / weight;
  return { score: Math.max(-1, Math.min(1, next)), samples: samples + 1 };
}

export interface FeedbackDeps {
  catalogue(db: Firestore, nowMs: number): Promise<Catalogue>;
}

export type FeedbackResult = 'applied' | 'already-applied' | 'ignored';

export async function applyFeedback(
  db: Firestore,
  coupleId: string,
  feedbackId: string,
  nowMs: number,
  deps: FeedbackDeps = { catalogue: loadCatalogue },
): Promise<FeedbackResult> {
  const feedbackRef = db.doc(`couples/${coupleId}/feedback/${feedbackId}`);
  const first = await feedbackRef.get();
  const contentId = first.get('contentId');
  const catalogue = await deps.catalogue(db, nowMs);
  const item = catalogue.items.find((candidate) => candidate.id === contentId);

  return db.runTransaction(async (tx) => {
    const feedback = await tx.get(feedbackRef);
    if (!feedback.exists) return 'ignored';
    if (feedback.get('processedAt') != null) return 'already-applied';
    const reaction = feedback.get('reaction');
    const byUid = feedback.get('byUid');
    // The rules already hold every field to its shape; this is belt and braces.
    if (!item || typeof byUid !== 'string' || !Object.keys(REACTION_VALUES).includes(reaction)) {
      tx.update(feedbackRef, { processedAt: Timestamp.fromMillis(nowMs), ignored: true });
      return 'ignored';
    }

    const refs = dimensionsOf(item).map((dimension) => db.doc(`users/${byUid}/affinity/${dimension}`));
    const current = await Promise.all(refs.map((ref) => tx.get(ref)));
    const updatedAt = Timestamp.fromMillis(nowMs);
    refs.forEach((ref, index) => {
      const data = current[index].data() as AffinityScore | undefined;
      tx.set(ref, { ...nextAffinity(data, reaction as Reaction), updatedAt });
    });
    tx.update(feedbackRef, { processedAt: updatedAt });
    return 'applied';
  });
}
