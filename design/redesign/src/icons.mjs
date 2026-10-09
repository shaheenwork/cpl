// Icons the redesign adds to AfterhoursIcons, drawn to the same specification as the rest:
// a 24 box, a 1.5 stroke, round caps and joins, no fills (DESIGN_PROMPT.md §4.9). Each one is a
// list of path strings, exactly the shape `icon(name, vararg paths)` takes in AfterhoursIcons.kt,
// so porting one is a copy and paste.
//
// Abstract and non-figurative, every one of them. No bodies, no lips, no silhouettes.
export const NEW_ICONS = {
  // The safe word's mark: the existing shield with a stop bar through it, so Stop reads as Stop
  // even to somebody who has never seen the app.
  SafeWord: ['M12 3l7 2.8v5.6c0 4.3-2.9 7.6-7 9.6-4.1-2-7-5.3-7-9.6V5.8z', 'M9 12h6'],
  // Limits: a shield that has been answered.
  Limits: ['M12 3l7 2.8v5.6c0 4.3-2.9 7.6-7 9.6-4.1-2-7-5.3-7-9.6V5.8z', 'M9 12l2 2 4-4'],
  // Discretion and the brand's secret: a keyhole.
  Keyhole: ['M12 4.5a3.5 3.5 0 0 1 2 6.37V13.5l1.2 5.5H8.8l1.2-5.5v-2.63A3.5 3.5 0 0 1 12 4.5z'],
  // Pairing: two links.
  Link: [
    'M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1',
    'M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1',
  ],
  // Two phones, joined — the pairing strip.
  Phones: [
    'M3 6.5A1.5 1.5 0 0 1 4.5 5h3A1.5 1.5 0 0 1 9 6.5v11A1.5 1.5 0 0 1 7.5 19h-3A1.5 1.5 0 0 1 3 17.5z',
    'M15 6.5A1.5 1.5 0 0 1 16.5 5h3A1.5 1.5 0 0 1 21 6.5v11a1.5 1.5 0 0 1-1.5 1.5h-3a1.5 1.5 0 0 1-1.5-1.5z',
    'M9 12h6',
  ],
  // The gate opening: the existing lock with its shackle lifted.
  LockOpen: ['M4.5 10.5h15V20h-15z', 'M8 10.5V8a4 4 0 0 1 7.7-1.6'],
  // Game Night: a candle.
  Candle: [
    'M9.5 10.5h5V20h-5z',
    'M12 10.5V8.6',
    'M12 3c1.3 1.6 2 2.6 2 3.5a2 2 0 0 1-4 0C10 5.6 10.7 4.6 12 3z',
  ],
  // Truth or Dare: two facets of one stone.
  Facets: ['M9 4l5 8-5 8-5-8z', 'M15 4l5 8-5 8-5-8z'],
  // Fantasy Match: two rings, linked.
  Rings: ['M3.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0', 'M11.5 12a4.5 4.5 0 1 0 9 0a4.5 4.5 0 1 0 -9 0'],
  // Win It / Owe It: the scales.
  Scales: [
    'M12 4.5v15',
    'M8 19.5h8',
    'M5 7.5h14',
    'M5 7.5l-2.5 5a2.5 2.5 0 0 0 5 0z',
    'M19 7.5l-2.5 5a2.5 2.5 0 0 0 5 0z',
  ],
  // Would You Rather: a road that forks.
  Fork: ['M12 20v-7', 'M12 13L6.5 6.5', 'M12 13l5.5-6.5', 'M6.5 10V6.5H10', 'M17.5 10V6.5H14'],
  // Hot or Not: a flame that is asking.
  FlameAsk: [
    'M12 3c3 3.5 5 6 5 9a5 5 0 01-10 0c0-1.6.7-3 1.8-4.3.4 1 1 1.7 1.7 2C10.6 7.4 11 5 12 3z',
    'M10.9 12.1a1.2 1.2 0 1 1 1.7 1.1c-.4.2-.6.5-.6.9',
    'M12 16.1v.01',
  ],
  // A sealed envelope: proof sent, a voice note, a once-only photo.
  Sealed: ['M3.5 6.5h17v11h-17z', 'M3.5 6.5l8.5 6 8.5-6', 'M12 15.6a1.4 1.4 0 1 0 0.01 0'],
  // Aftercare: two hands of the same heart — drawn as a heart held in a curve, not as hands.
  Aftercare: [
    'M12 16.5s-4.2-2.7-4.2-5.5A2.3 2.3 0 0 1 12 9.8a2.3 2.3 0 0 1 4.2 1.2c0 2.8-4.2 5.5-4.2 5.5z',
    'M4 13.5c0 3.6 3.6 6.5 8 6.5s8-2.9 8-6.5',
  ],
  // The lane picker.
  Lanes: ['M5 6h14', 'M5 12h14', 'M5 18h9'],
  // A face-down card.
  CardBack: ['M6.5 3.5h11a1.5 1.5 0 0 1 1.5 1.5v14a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5a1.5 1.5 0 0 1 1.5-1.5z', 'M12 8l3 4-3 4-3-4z'],
  // Retry, for a failed send.
  Retry: ['M19 12a7 7 0 1 1-2.05-4.95', 'M19 4.5V8h-3.5'],
  // Turn it down: a flame with an arrow under it.
  Down: ['M12 4v12', 'M7 11l5 5 5-5', 'M6 20h12'],
  // Turn it up.
  Up: ['M12 20V8', 'M7 13l5-5 5 5', 'M6 4h12'],
  Backspace: ['M9 6h11v12H9l-5-6z', 'M12.5 9.5l5 5', 'M17.5 9.5l-5 5'],
  Fingerprint: [
    'M7.5 18.5c1-1.8 1.5-3.9 1.5-6.5a3 3 0 0 1 6 0c0 1.5-.1 2.9-.4 4.2',
    'M12 12c0 3.2-.6 5.8-1.8 8',
    'M5 15.5c.6-1.2 1-2.4 1-3.5a6 6 0 0 1 11.6-2.1',
    'M17.9 13.5c-.2 2.2-.7 4.2-1.5 6',
  ],
  Share: ['M12 4v11', 'M8 8l4-4 4 4', 'M5 13v6h14v-6'],
  Qr: ['M4 4h6v6H4z', 'M14 4h6v6h-6z', 'M4 14h6v6H4z', 'M14 14h2v2h-2z', 'M18 18h2v2h-2z', 'M14 18h2', 'M18 14h2'],
};
