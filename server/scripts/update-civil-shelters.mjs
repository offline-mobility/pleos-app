// Refreshes data/civil-shelters.json from 행정안전부 민방위대피소 (safetydata.go.kr DSSP-IF-10166).
// Usage: npm run update-civil-shelters   (needs SAFETYDATA_CIVIL_SHELTER_KEY in .env or the environment)
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { fetchCivilSheltersUpstream } from '../src/upstream.js';

const envFile = new URL('../.env', import.meta.url);
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, 'utf8').split('\n')) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].trim();
  }
}
const key = process.env.SAFETYDATA_CIVIL_SHELTER_KEY;
if (!key) {
  console.error('SAFETYDATA_CIVIL_SHELTER_KEY is not set');
  process.exit(1);
}

try {
  const rows = await fetchCivilSheltersUpstream(key, fetch);
  // A short download would silently drop whole regions; keep the old snapshot instead.
  if (rows.length < 10_000) throw new Error(`only ${rows.length} rows`);
  rows.sort((a, b) => (a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0));
  const lines = rows.map((r) => JSON.stringify(r)).join(',\n');
  const generatedAt = new Date().toISOString().slice(0, 10);
  writeFileSync(
    new URL('../data/civil-shelters.json', import.meta.url),
    `{"source":"행정안전부 민방위대피소 (재난안전데이터공유플랫폼 DSSP-IF-10166)","generatedAt":"${generatedAt}","columns":["id","name","lat","lon","areaM2","address"],"rows":[\n${lines}\n]}\n`,
  );
  console.log(`${rows.length} shelters written (${generatedAt})`);
} catch (error) {
  // The request URL contains the service key, so only the message is printed.
  console.error('update failed:', String(error?.message ?? error).replaceAll(key, '<key>'));
  process.exit(1);
}
