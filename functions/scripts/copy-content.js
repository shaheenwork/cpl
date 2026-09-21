// Copies the committed content bundle (content/dist/bundle.json) into lib/, where the
// deployed engine reads it as its floor (src/engine/catalogue.ts). Part of `npm run build`,
// which the deploy runs, so what the server chooses from is what the app ships.
const fs = require('node:fs');
const path = require('node:path');

const source = path.join(__dirname, '..', '..', 'content', 'dist', 'bundle.json');
const target = path.join(__dirname, '..', 'lib', 'content', 'bundle.json');
fs.mkdirSync(path.dirname(target), { recursive: true });
fs.copyFileSync(source, target);
