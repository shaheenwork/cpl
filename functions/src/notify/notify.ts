/**
 * Push notifications (BUILD_PROMPT.md section 16). The one place the server sends anything to a
 * phone, and the reason it can be trusted: **a payload carries ids, never words**.
 *
 * Every message is data-only — no `notification` block at all — and its data map holds three
 * fields: the kind, an optional document id, and the time. The phone looks the kind up in its own
 * string table and, when it is allowed to, fetches the thing itself with the signed-in user's own
 * credentials (section 16.2). Nothing a partner wrote, nothing from the catalogue, and nothing
 * anyone chose ever travels through a push service or sits on a lock screen.
 *
 * `buildMessage` refuses a reference that is not a document id, so a caller cannot smuggle a title
 * into the payload even by accident; `notify.unit.test.ts` is the payload inspection that proves
 * it for every kind.
 */
import { type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';

/** The ten moments worth a notification (section 16.1). */
export const NOTIFICATION_KINDS = [
  'PARTNER_INVITED',
  'PARTNER_JOINED',
  'SURPRISE_LEFT',
  'SURPRISE_UNLOCKED',
  'PARTNER_FINISHED',
  'BOTH_READY',
  'NIGHT_STARTING',
  'THEY_SENT_SOMETHING',
  'DAY_UNLOCKED',
  'SESSION_EXPIRING',
] as const;
export type NotificationKind = (typeof NOTIFICATION_KINDS)[number];

/** A channel per category, so a phone can silence one kind of thing and keep another. */
export const CHANNELS = ['pairing', 'surprises', 'together', 'messages'] as const;
export type Channel = (typeof CHANNELS)[number];

const CHANNEL_OF: Record<NotificationKind, Channel> = {
  PARTNER_INVITED: 'pairing',
  PARTNER_JOINED: 'pairing',
  SURPRISE_LEFT: 'surprises',
  SURPRISE_UNLOCKED: 'surprises',
  NIGHT_STARTING: 'surprises',
  DAY_UNLOCKED: 'surprises',
  PARTNER_FINISHED: 'together',
  BOTH_READY: 'together',
  SESSION_EXPIRING: 'together',
  THEY_SENT_SOMETHING: 'messages',
};

/** Everything a payload may contain. Anything else is a bug, and the test says so. */
export const PAYLOAD_KEYS = ['kind', 'channel', 'ref', 'at'] as const;

/**
 * A Firestore document id: twenty characters of its own alphabet, which is what every reference
 * this server sends actually is. Nothing shorter is accepted, so a single word — a title, a name,
 * a mood — cannot be passed off as an id, and nothing with a space in it gets near a payload.
 */
const REF = /^[A-Za-z0-9_-]{20,64}$/;

export interface PushMessage {
  data: Record<string, string>;
  android: { priority: 'high'; ttl: number };
}

/** A day, after which the moment has passed and the phone need not hear about it. */
const TTL_MS = 24 * 60 * 60 * 1000;

/**
 * The payload, built in one place so there is one place to inspect. A reference is a document id
 * or nothing at all: anything else is refused rather than trimmed, because a caller that passes a
 * title here has a bug worth failing on.
 */
export function buildMessage(kind: NotificationKind, ref: string | null, nowMs: number): PushMessage {
  if (!NOTIFICATION_KINDS.includes(kind)) throw new Error(`unknown notification kind ${kind}`);
  if (ref !== null && !REF.test(ref)) throw new Error('a notification reference must be a document id');
  const data: Record<string, string> = { kind, channel: CHANNEL_OF[kind], at: String(nowMs) };
  if (ref !== null) data.ref = ref;
  return { data, android: { priority: 'high', ttl: TTL_MS } };
}

export interface NotificationSettings {
  enabled: boolean;
  quietHours: { from: string; to: string } | null;
}

const HH_MM = /^([01][0-9]|2[0-3]):([0-5][0-9])$/;

/** What the user chose, with the safe answer for anything missing or malformed. */
export function settingsOf(user: DocumentData | undefined): NotificationSettings {
  const raw = user?.notificationSettings;
  const quiet = raw?.quietHours;
  const valid = typeof quiet?.from === 'string' && typeof quiet?.to === 'string'
    && HH_MM.test(quiet.from) && HH_MM.test(quiet.to) && quiet.from !== quiet.to;
  return {
    enabled: raw?.enabled !== false,
    quietHours: valid ? { from: quiet.from as string, to: quiet.to as string } : null,
  };
}

function minutesOf(hhmm: string): number {
  const [hours, minutes] = hhmm.split(':');
  return Number(hours) * 60 + Number(minutes);
}

/** The time on that user's own clock, in minutes past midnight. */
function localMinutes(timezone: string, nowMs: number): number {
  const shown = new Intl.DateTimeFormat('en-GB', {
    timeZone: timezone,
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(nowMs));
  return minutesOf(shown.replace('24:', '00:'));
}

/** Quiet hours, on the user's own clock, wrapping around midnight the way people mean them. */
export function inQuietHours(settings: NotificationSettings, timezone: string, nowMs: number): boolean {
  const quiet = settings.quietHours;
  if (quiet === null) return false;
  let now: number;
  try {
    now = localMinutes(timezone, nowMs);
  } catch {
    now = localMinutes('UTC', nowMs);
  }
  const from = minutesOf(quiet.from);
  const to = minutesOf(quiet.to);
  return from < to ? now >= from && now < to : now >= from || now < to;
}

/** What actually puts a message on the wire. Tests pass their own and inspect what was sent. */
export interface Sender {
  send(tokens: string[], message: PushMessage): Promise<{ failed: string[] }>;
}

export const fcmSender: Sender = {
  async send(tokens, message) {
    const response = await getMessaging().sendEachForMulticast({ tokens, ...message });
    const failed: string[] = [];
    response.responses.forEach((result, index) => {
      const code = result.error?.code ?? '';
      if (code.includes('registration-token-not-registered') || code.includes('invalid-argument')) {
        failed.push(tokens[index]);
      }
    });
    return { failed };
  },
};

export type NotifyOutcome = 'sent' | 'no-tokens' | 'disabled' | 'quiet' | 'failed';

/**
 * Tells one person that something happened, if they want to be told and it is not the middle of
 * their night. Tokens the service rejects are dropped, so a phone that signed out stops being
 * tried.
 *
 * Never throws: a notification that cannot be sent must not fail the thing it was about.
 */
export async function notifyUser(
  db: Firestore,
  uid: string,
  kind: NotificationKind,
  ref: string | null,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<NotifyOutcome> {
  try {
    const user = (await db.doc(`users/${uid}`).get()).data();
    const settings = settingsOf(user);
    if (!settings.enabled) return 'disabled';
    const timezone = typeof user?.timezone === 'string' ? user.timezone : 'UTC';
    if (inQuietHours(settings, timezone, nowMs)) return 'quiet';

    const stored = await db.collection(`users/${uid}/fcmTokens`).get();
    const live = stored.docs.filter((token) => typeof token.get('token') === 'string');
    if (live.length === 0) return 'no-tokens';

    const tokens = live.map((token) => token.get('token') as string);
    const { failed } = await sender.send(tokens, buildMessage(kind, ref, nowMs));
    await Promise.all(
      live.filter((token) => failed.includes(token.get('token') as string)).map((token) => token.ref.delete()),
    );
    return 'sent';
  } catch (error) {
    console.error('notify failed', kind, error);
    return 'failed';
  }
}

/** The same, for the other member of a couple: the common case, since most news is about them. */
export async function notifyPartner(
  db: Firestore,
  members: readonly string[],
  actorUid: string,
  kind: NotificationKind,
  ref: string | null,
  nowMs: number,
  sender: Sender = fcmSender,
): Promise<void> {
  const partner = members.find((member) => member !== actorUid);
  if (partner === undefined) return;
  await notifyUser(db, partner, kind, ref, nowMs, sender);
}
