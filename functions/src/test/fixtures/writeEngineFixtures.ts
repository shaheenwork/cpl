/**
 * `npm --prefix functions run engine:fixtures` — rewrites engine/engine-fixtures.json from the
 * TypeScript engine. Run it only after a deliberate change to the algorithm (and raise
 * ENGINE_VERSION when output changes for identical inputs); then make :core:engine agree.
 */
import { writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { buildFixtures } from './engineFixtures';

const target = join(__dirname, '..', '..', '..', '..', 'engine', 'engine-fixtures.json');
writeFileSync(target, `${JSON.stringify(buildFixtures(), null, 1)}\n`, 'utf8');
console.log(`wrote ${target}`);
