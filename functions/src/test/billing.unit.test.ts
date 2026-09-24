/**
 * What a subscription is worth, without a database (BUILD_PROMPT.md section 19).
 *
 * The states are Play's, and they do not all mean the same thing: a grace period still entitles
 * — the payment failed but the subscription is alive — while on hold and paused do not, and a
 * refund ends it the moment it happens.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { Timestamp } from 'firebase-admin/firestore';
import {
  FREE,
  betterOf,
  entitlementOf,
  publicView,
  type Entitlement,
} from '../billing/entitlement';
import { NOTIFICATION_STATES, PREMIUM_PRODUCT } from '../billing/billing';
import { FREE_NIGHTS_PER_WEEK, FREE_SURPRISES_WAITING, GATES, freeLimits } from '../billing/limits';

const NOW = Date.UTC(2026, 8, 24, 20, 0, 0);
const HOUR = 60 * 60 * 1000;

function stored(state: string, expiresAtMillis: number, extra: Record<string, unknown> = {}) {
  return {
    entitlement: {
      tier: 'PREMIUM',
      source: 'PLAY',
      state,
      purchaseToken: 'token-abc',
      expiresAt: Timestamp.fromMillis(expiresAtMillis),
      ...extra,
    },
  };
}

describe('what a record means right now', () => {
  it('treats an account with no record as free', () => {
    assert.deepEqual(entitlementOf(undefined, NOW), FREE);
    assert.deepEqual(entitlementOf({}, NOW), FREE);
    assert.equal(entitlementOf({ entitlement: { tier: 'PREMIUM' } }, NOW).tier, 'FREE');
  });

  it('entitles while the term runs, and stops when it does not', () => {
    assert.equal(entitlementOf(stored('ACTIVE', NOW + HOUR), NOW).tier, 'PREMIUM');
    assert.equal(entitlementOf(stored('ACTIVE', NOW - HOUR), NOW).tier, 'FREE');
  });

  it('keeps a failed payment in, and a held one out', () => {
    // A grace period is a subscription whose payment failed, not one that ended.
    assert.equal(entitlementOf(stored('GRACE', NOW + HOUR), NOW).tier, 'PREMIUM');
    // Cancelled runs to the end of the term it was paid for.
    assert.equal(entitlementOf(stored('CANCELED', NOW + HOUR), NOW).tier, 'PREMIUM');
    assert.equal(entitlementOf(stored('ON_HOLD', NOW + HOUR), NOW).tier, 'FREE');
    assert.equal(entitlementOf(stored('PAUSED', NOW + HOUR), NOW).tier, 'FREE');
    assert.equal(entitlementOf(stored('REVOKED', NOW + HOUR), NOW).tier, 'FREE');
    assert.equal(entitlementOf(stored('EXPIRED', NOW + HOUR), NOW).tier, 'FREE');
  });

  it('reads nothing it does not recognise as premium', () => {
    assert.equal(entitlementOf(stored('SOMETHING_ELSE', NOW + HOUR), NOW).tier, 'FREE');
    assert.equal(entitlementOf({ entitlement: { state: 'ACTIVE' } }, NOW).tier, 'PREMIUM');
  });
});

describe('one subscription covers both partners', () => {
  const premium: Entitlement = { tier: 'PREMIUM', source: 'PLAY', state: 'ACTIVE', expiresAtMillis: NOW + HOUR };

  it('takes the better of the two, whichever of them bought it', () => {
    assert.equal(betterOf(FREE, premium).tier, 'PREMIUM');
    assert.equal(betterOf(premium, FREE).tier, 'PREMIUM');
    assert.equal(betterOf(FREE, FREE).tier, 'FREE');
  });

  it('keeps the later of two terms', () => {
    const longer = { ...premium, expiresAtMillis: NOW + 2 * HOUR };
    assert.equal(betterOf(premium, longer).expiresAtMillis, NOW + 2 * HOUR);
    assert.equal(betterOf(longer, premium).expiresAtMillis, NOW + 2 * HOUR);
  });

  it('never publishes the purchase token to the couple', () => {
    const view = publicView({ ...premium, purchaseToken: 'token-abc' });

    assert.deepEqual(Object.keys(view).sort(), ['expiresAt', 'state', 'tier']);
    assert.ok(!JSON.stringify(view).includes('token-abc'));
  });
});

describe('what Play tells the server', () => {
  it('maps every notification it acts on, and nothing else', () => {
    assert.equal(NOTIFICATION_STATES[4], 'ACTIVE'); // PURCHASED
    assert.equal(NOTIFICATION_STATES[2], 'ACTIVE'); // RENEWED
    assert.equal(NOTIFICATION_STATES[3], 'CANCELED');
    assert.equal(NOTIFICATION_STATES[5], 'ON_HOLD');
    assert.equal(NOTIFICATION_STATES[6], 'GRACE');
    assert.equal(NOTIFICATION_STATES[10], 'PAUSED');
    assert.equal(NOTIFICATION_STATES[12], 'REVOKED');
    assert.equal(NOTIFICATION_STATES[13], 'EXPIRED');
    assert.equal(NOTIFICATION_STATES[99], undefined);
  });
});

describe('what free includes', () => {
  it('names its limits once, where the paywall reads them', () => {
    assert.deepEqual(freeLimits(), {
      nightsPerWeek: FREE_NIGHTS_PER_WEEK,
      surprisesWaiting: FREE_SURPRISES_WAITING,
    });
    assert.ok(FREE_NIGHTS_PER_WEEK >= 1, 'free has to be worth using');
  });

  it('has one product to buy', () => {
    assert.equal(PREMIUM_PRODUCT, 'afterhours.premium.monthly');
  });

  it('gates only at selection time', () => {
    // Every gate is about starting something. None of them names a session, a chapter or a
    // reveal — a night that has started finishes (section 19).
    assert.deepEqual([...GATES], ['NIGHTS_THIS_WEEK', 'MYSTERY_NIGHT', 'MULTI_DAY_ARC', 'SURPRISES_WAITING']);
  });
});
