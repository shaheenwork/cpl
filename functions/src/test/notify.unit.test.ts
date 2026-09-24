/**
 * The payload inspection (BUILD_PROMPT.md section 16.2, Phase 17 exit criterion).
 *
 * Every notification this server can send is built here and taken apart: what keys it has, what
 * those keys may hold, and — the point of the whole exercise — that no word anyone wrote can
 * reach one. A push payload travels through a service nobody in this couple controls and lands on
 * a lock screen anyone nearby can read, so the only safe payload is one that says nothing.
 */
import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  CHANNELS,
  NOTIFICATION_KINDS,
  PAYLOAD_KEYS,
  buildMessage,
  inQuietHours,
  settingsOf,
  type NotificationKind,
} from '../notify/notify';

const NOW = Date.UTC(2026, 8, 24, 22, 30, 0);

/** Things a couple might actually write, and a catalogue line. None may ever appear in a push. */
const PRIVATE_WORDS = [
  'blindfold',
  'Sam',
  'For Friday',
  'I have been thinking about you all day',
  'Tell each other the thing you have never said out loud',
  'roleplay',
];

function serialise(kind: NotificationKind, ref: string | null): string {
  return JSON.stringify(buildMessage(kind, ref, NOW));
}

describe('what a notification may carry', () => {
  it('carries a kind, a channel, a time and at most a document id — nothing else', () => {
    for (const kind of NOTIFICATION_KINDS) {
      const message = buildMessage(kind, 'HDjiwnxR8u9xqPsTMwuW', NOW);

      assert.ok(
        Object.keys(message.data).every((key) => (PAYLOAD_KEYS as readonly string[]).includes(key)),
        `${kind} carries ${Object.keys(message.data).join(', ')}`,
      );
      assert.equal(message.data.kind, kind);
      assert.ok(CHANNELS.includes(message.data.channel as (typeof CHANNELS)[number]));
      assert.equal(message.data.ref, 'HDjiwnxR8u9xqPsTMwuW');
      assert.equal(message.data.at, String(NOW));
      // Data-only: nothing the push service itself would display, so the phone writes the words.
      assert.ok(!('notification' in message), `${kind} has a notification block`);
      assert.equal(message.android.priority, 'high');
    }
  });

  it('says nothing at all when there is nothing to point at', () => {
    const message = buildMessage('PARTNER_INVITED', null, NOW);

    assert.deepEqual(Object.keys(message.data).sort(), ['at', 'channel', 'kind']);
  });

  it('refuses anything that is not a document id, so words cannot ride along', () => {
    for (const word of PRIVATE_WORDS) {
      assert.throws(() => buildMessage('SURPRISE_LEFT', word, NOW), /document id/, `accepted "${word}"`);
    }
    assert.throws(() => buildMessage('SURPRISE_LEFT', 'a'.repeat(65), NOW), /document id/);
    assert.throws(() => buildMessage('SURPRISE_LEFT', '', NOW), /document id/);
    // Not even a short id-shaped string: everything this server points at is a Firestore id.
    assert.throws(() => buildMessage('SURPRISE_LEFT', 'night_3', NOW), /document id/);
    assert.throws(() => buildMessage('NOT_A_KIND' as NotificationKind, null, NOW), /unknown notification kind/);
  });

  it('never contains a word a couple wrote, whatever the kind', () => {
    for (const kind of NOTIFICATION_KINDS) {
      const payload = serialise(kind, 'HDjiwnxR8u9xqPsTMwuW').toLowerCase();
      for (const word of PRIVATE_WORDS) {
        assert.ok(!payload.includes(word.toLowerCase()), `${kind} leaked "${word}"`);
      }
      // Nor anything readable at all: every value is an id, an enum or a number.
      for (const value of Object.values(buildMessage(kind, 'HDjiwnxR8u9xqPsTMwuW', NOW).data)) {
        assert.match(value, /^[A-Za-z0-9_-]+$/, `${kind} carries free text: ${value}`);
      }
    }
  });

  it('keeps every kind in section 16.1 on a channel a phone can silence on its own', () => {
    assert.equal(NOTIFICATION_KINDS.length, 10);
    const channels = new Set(NOTIFICATION_KINDS.map((kind) => buildMessage(kind, null, NOW).data.channel));
    assert.deepEqual([...channels].sort(), [...CHANNELS].sort());
  });
});

describe('who is told, and when', () => {
  it('treats a user who never chose as someone who wants to be told', () => {
    assert.deepEqual(settingsOf(undefined), { enabled: true, quietHours: null });
    assert.deepEqual(settingsOf({}), { enabled: true, quietHours: null });
    assert.deepEqual(settingsOf({ notificationSettings: { enabled: true } }), { enabled: true, quietHours: null });
  });

  it('takes "no" for an answer', () => {
    assert.equal(settingsOf({ notificationSettings: { enabled: false } }).enabled, false);
  });

  it('ignores a quiet-hours window it cannot read, rather than falling silent', () => {
    for (const quietHours of [{ from: '25:00', to: '07:00' }, { from: 'later', to: 'never' }, { from: '22:00', to: '22:00' }, null]) {
      assert.equal(settingsOf({ notificationSettings: { quietHours } }).quietHours, null);
    }
  });

  it('holds quiet hours on the user\'s own clock, across midnight', () => {
    const night = settingsOf({ notificationSettings: { quietHours: { from: '22:00', to: '07:00' } } });

    // 22:30 UTC: the middle of the night in London, half past four in the morning in Kolkata,
    // and half past five in the afternoon in New York.
    assert.equal(inQuietHours(night, 'Europe/London', NOW), true);
    assert.equal(inQuietHours(night, 'Asia/Kolkata', NOW), true);
    assert.equal(inQuietHours(night, 'America/New_York', NOW), false);
    // A window inside one day is read the same way: 18:30 in New York, 23:30 in London.
    const evening = settingsOf({ notificationSettings: { quietHours: { from: '13:00', to: '20:00' } } });
    assert.equal(inQuietHours(evening, 'America/New_York', NOW), true);
    assert.equal(inQuietHours(evening, 'Europe/London', NOW), false);
    // A timezone nobody has heard of falls back to UTC rather than to silence.
    assert.equal(inQuietHours(night, 'Mars/Olympus_Mons', NOW), true);
  });

  it('is never quiet when nobody asked for quiet', () => {
    assert.equal(inQuietHours({ enabled: true, quietHours: null }, 'Europe/London', NOW), false);
  });
});
