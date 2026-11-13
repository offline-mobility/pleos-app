/**
 * Calls to 재난안전데이터공유플랫폼 (safetydata.go.kr, GET /V2/api/{id}).
 * Response: { header: { resultCode: "00", ... }, totalCount, body: [...] | null }.
 * Field names were checked against live responses on 2026-11-13.
 */
const BASE = 'https://www.safetydata.go.kr/V2/api';
const ALERTS_ID = 'DSSP-IF-00247';
const SHELTERS_ID = 'DSSP-IF-10941';
const CIVIL_SHELTERS_ID = 'DSSP-IF-10166';
const PAGE_SIZE = 1000;
const MAX_PAGES = 3;
const KST_OFFSET_MS = 9 * 3_600_000;

async function fetchPages(id, key, params, fetchFn, maxPages = MAX_PAGES) {
  const rows = [];
  for (let page = 1; page <= maxPages; page++) {
    // A key pasted with a trailing space or line break is rejected upstream as unregistered (code 30).
    const query = new URLSearchParams({ serviceKey: String(key).trim(), returnType: 'json', numOfRows: String(PAGE_SIZE), pageNo: String(page), ...params });
    const res = await fetchFn(`${BASE}/${id}?${query}`, { signal: AbortSignal.timeout(maxPages > MAX_PAGES ? 30_000 : 8000) });
    if (!res.ok) throw new Error(`upstream http ${res.status}`);
    const data = await res.json();
    if (data?.header?.resultCode !== '00') throw new Error(`upstream code ${data?.header?.resultCode}`);
    const body = Array.isArray(data.body) ? data.body : [];
    rows.push(...body);
    if (rows.length >= Number(data.totalCount ?? 0) || body.length < PAGE_SIZE) break;
  }
  return rows;
}

/** "2026/09/27 13:31:31" (KST) → epoch ms, or null. */
export function parseKst(text) {
  const m = /^(\d{4})[/-](\d{2})[/-](\d{2}) (\d{2}):(\d{2}):(\d{2})/.exec(String(text ?? ''));
  if (!m) return null;
  const [y, mo, d, h, mi, s] = m.slice(1).map(Number);
  return Date.UTC(y, mo - 1, d, h, mi, s) - KST_OFFSET_MS;
}

/** YYYYMMDD of the given instant in KST. */
export function kstDate(ms) {
  return new Date(ms + KST_OFFSET_MS).toISOString().slice(0, 10).replaceAll('-', '');
}

export function mapAlert(row) {
  const createdAt = parseKst(row.CRT_DT);
  if (createdAt === null || !row.MSG_CN) return null;
  return {
    id: row.SN,
    createdAt,
    text: String(row.MSG_CN),
    regions: String(row.RCPTN_RGN_NM ?? ''),
    category: String(row.DST_SE_NM ?? ''),
    level: row.EMRG_STEP_NM ? String(row.EMRG_STEP_NM) : null,
  };
}

/** Messages created since yesterday (crtDt is a lower bound). */
export async function fetchAlertsUpstream(key, fetchFn, now) {
  const rows = await fetchPages(ALERTS_ID, key, { crtDt: kstDate(now - 24 * 3_600_000) }, fetchFn);
  return rows.map(mapAlert).filter(Boolean);
}

export function mapShelter(row) {
  const lat = Number(row.LAT);
  const lon = Number(row.LOT);
  if (!row.REARE_NM || !Number.isFinite(lat) || !Number.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  const address = String(row.RONA_DADDR ?? '').trim();
  const [sido = '', sigungu = ''] = address.split(/\s+/);
  return {
    id: String(row.MNG_SN ?? `${lat},${lon}`),
    name: String(row.REARE_NM).trim(),
    kind: String(row.SHLT_SE_NM ?? '').trim(),
    lat,
    lon,
    sido,
    sigungu,
    address,
    capacity: null,
  };
}

/**
 * Shelters in a box around the point. Cached for a day per ~1 km cell and radius; the app only
 * sends coordinates rounded to two decimals, so nearby cars share cache entries.
 */
export async function fetchSheltersUpstream({ key, store, fetchFn, lat, lon, km }) {
  const cacheKey = `shelters:${lat.toFixed(2)}:${lon.toFixed(2)}:${Math.ceil(km)}`;
  const cached = await store.get(cacheKey);
  if (cached) return JSON.parse(cached);

  const dLat = km / 111.32;
  const dLon = km / (111.32 * Math.max(0.2, Math.cos((lat * Math.PI) / 180)));
  const rows = await fetchPages(
    SHELTERS_ID,
    key,
    {
      startLat: (lat - dLat).toFixed(5),
      endLat: (lat + dLat).toFixed(5),
      startLot: (lon - dLon).toFixed(5),
      endLot: (lon + dLon).toFixed(5),
    },
    fetchFn,
  );
  const shelters = rows.map(mapShelter).filter(Boolean);
  await store.set(cacheKey, JSON.stringify(shelters), 24 * 3600);
  return shelters;
}

/** Floor area per person for public civil defence shelters (공공용 대피시설 1인당 0.825㎡). */
export const CIVIL_M2_PER_PERSON = 0.825;

/**
 * One 민방위대피소 row → compact snapshot row [id, name, lat, lon, areaM2, address], or null.
 * Rows outside Korea or without a name are dropped.
 */
export function mapCivilShelter(row) {
  const lat = Number(row.LAT);
  const lon = Number(row.LOT);
  const name = String(row.SHLT_NM ?? '').trim().slice(0, 60);
  if (!name || !(lat >= 33 && lat <= 39) || !(lon >= 124 && lon <= 132)) return null;
  const area = Number(row.FCAR);
  const address = String(row.ROAD_NM_ADDR ?? row.DADDR ?? '').trim().replace(/\s+/g, ' ').slice(0, 80);
  return [String(row.GID ?? row.SHNT_FCLT_SENU ?? `${lat},${lon}`), name, Number(lat.toFixed(5)), Number(lon.toFixed(5)), Number.isFinite(area) && area > 0 ? Math.round(area) : 0, address];
}

/**
 * Every civil defence shelter in the country. The API has no area filter, so this is only used by
 * scripts/update-civil-shelters.mjs to refresh the bundled snapshot, never while serving a request.
 */
export async function fetchCivilSheltersUpstream(key, fetchFn) {
  const rows = await fetchPages(CIVIL_SHELTERS_ID, key, {}, fetchFn, 40);
  return rows.map(mapCivilShelter).filter(Boolean);
}
