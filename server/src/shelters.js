import { civilSheltersNear } from './civil.js';
import { distanceKm } from './geo.js';

/** Development-only sample around Gangnam station (emulator default position). */
const SAMPLE = [
  { id: 'sample-s1', name: '[견본] 강남역 지하상가 대피소', kind: '민방위 대피소', lat: 37.4981, lon: 127.0284, sido: '서울특별시', sigungu: '강남구', address: '서울특별시 강남구 강남대로 지하 396', capacity: 3200 },
  { id: 'sample-s2', name: '[견본] 역삼초등학교 운동장', kind: '지진 옥외대피장소', lat: 37.4955, lon: 127.0331, sido: '서울특별시', sigungu: '강남구', address: '서울특별시 강남구 역삼로 215', capacity: 1500 },
  { id: 'sample-s3', name: '[견본] 서초구민체육센터', kind: '임시주거시설', lat: 37.4918, lon: 127.0189, sido: '서울특별시', sigungu: '서초구', address: '서울특별시 서초구 서초대로 100', capacity: 400 },
  { id: 'sample-s4', name: '[견본] 도곡근린공원', kind: '지진 옥외대피장소', lat: 37.4889, lon: 127.0412, sido: '서울특별시', sigungu: '강남구', address: '서울특별시 강남구 도곡로 200', capacity: 2000 },
];

export const SHELTER_LIMITS = { maxKm: 30, defaultKm: 5, perGroup: 60 };

/**
 * Which disaster a shelter is for. Each group keeps its own nearest [SHELTER_LIMITS.perGroup], so a
 * dense group (hundreds of heat-wave rest areas in a city) cannot push out the civil defence
 * shelters or earthquake assembly points a different disaster needs.
 */
export function shelterGroup(kind) {
  if (kind.includes('민방위')) return 'civil';
  if (kind.includes('지진해일')) return 'tsunami';
  if (kind.includes('지진')) return 'earthquake';
  return 'rest';
}

/**
 * Shelters within [km] of a point, nearest first.
 * `source` is "live" (public API plus the bundled civil defence snapshot), "snapshot" (no API key:
 * civil defence shelters only), "sample" (development only) or "unconfigured".
 */
export async function getShelters({ store, env, fetchFn, now, upstream, civilNear = civilSheltersNear }, lat, lon, km) {
  let all;
  let source;
  if (env.SAFETYDATA_SHELTER_KEY) {
    source = 'live';
    const live = await upstream({ key: env.SAFETYDATA_SHELTER_KEY, store, fetchFn, now, lat, lon, km });
    all = [...live, ...civilNear(lat, lon, km)];
  } else if (env.SAMPLE_DATA === '1') {
    source = 'sample';
    all = SAMPLE;
  } else {
    all = civilNear(lat, lon, km);
    if (all.length === 0) return { source: 'unconfigured', shelters: [] };
    source = 'snapshot';
  }
  const kept = {};
  const shelters = all
    .map((s) => ({ ...s, distanceKm: distanceKm(lat, lon, s.lat, s.lon) }))
    .filter((s) => s.distanceKm <= km)
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .filter((s) => {
      const group = shelterGroup(s.kind);
      kept[group] = (kept[group] ?? 0) + 1;
      return kept[group] <= SHELTER_LIMITS.perGroup;
    })
    .map(({ distanceKm: d, ...s }) => ({ ...s, distanceM: Math.round(d * 1000) }));
  return { source, shelters };
}

/** Administrative region of a point, taken from the nearest shelter's address. */
export function regionOf(shelters) {
  const nearest = shelters.find((s) => s.sido);
  return nearest ? { sido: nearest.sido, sigungu: nearest.sigungu ?? '' } : null;
}
