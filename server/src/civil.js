import snapshot from '../data/civil-shelters.json' with { type: 'json' };
import { CIVIL_M2_PER_PERSON } from './upstream.js';

/**
 * 민방위대피소 (civil defence shelters). The public API only returns the whole country, so a
 * snapshot is bundled with the server (refresh: `npm run update-civil-shelters`). Serving from the
 * snapshot also keeps shelter search working when the public API is unreachable during a disaster.
 */
export const CIVIL_SNAPSHOT = { generatedAt: snapshot.generatedAt, count: snapshot.rows.length };

export const CIVIL_KIND = '민방위대피소';

/** Shelters inside the box around a point; the caller filters by exact distance. */
export function civilSheltersNear(lat, lon, km, rows = snapshot.rows) {
  const dLat = km / 111.32;
  const dLon = km / (111.32 * Math.max(0.2, Math.cos((lat * Math.PI) / 180)));
  const out = [];
  for (const [id, name, rLat, rLon, areaM2, address] of rows) {
    if (Math.abs(rLat - lat) > dLat || Math.abs(rLon - lon) > dLon) continue;
    const [sido = '', sigungu = ''] = address.split(' ');
    out.push({
      id: `civil-${id}`,
      name,
      kind: CIVIL_KIND,
      lat: rLat,
      lon: rLon,
      sido,
      sigungu,
      address,
      // Official capacity is not published; public shelters are planned at 0.825 ㎡ per person.
      capacity: areaM2 > 0 ? Math.floor(areaM2 / CIVIL_M2_PER_PERSON) : null,
    });
  }
  return out;
}
