/** Maps official disaster messages to the app's disaster modes and filters them by region. */

// Order matters: more specific phrases first.
const RULES = [
  [null, ['지진해일', '해일']], // no matching in-car guidance in the app; shown without a mode suggestion
  ['AIR_RAID', ['공습', '민방공', '경계경보', '미사일']],
  ['RADIATION', ['방사능', '방사선', '원전']],
  ['CHEMICAL', ['화학', '유해물질', '유독', '가스누출', '가스 누출']],
  ['LANDSLIDE', ['산사태', '낙석', '지반침하', '싱크홀']],
  ['WILDFIRE', ['산불']],
  ['TUNNEL', ['터널']],
  ['EARTHQUAKE', ['지진']],
  ['SNOW_COLD', ['대설', '폭설', '한파']],
  ['HEAT', ['폭염']],
  ['TYPHOON', ['태풍', '강풍', '풍랑']],
  ['FLOOD', ['호우', '홍수', '침수', '범람']],
  ['BLACKOUT', ['정전', '통신장애', '통신 장애']],
];

// 재해구분명(DST_SE_NM) values that map directly to a mode. Others fall back to the message text.
const CATEGORY = {
  민방공: 'AIR_RAID', 산불: 'WILDFIRE', 산사태: 'LANDSLIDE', 지진: 'EARTHQUAKE', 지진해일: null,
  태풍: 'TYPHOON', 강풍: 'TYPHOON', 풍랑: 'TYPHOON', 호우: 'FLOOD', 홍수: 'FLOOD',
  대설: 'SNOW_COLD', 한파: 'SNOW_COLD', 폭염: 'HEAT', 정전: 'BLACKOUT', 통신: 'BLACKOUT',
};

/** Returns the app DisasterType name, or null when no mode applies. */
export function classify(category, text) {
  const c = (category ?? '').trim();
  if (Object.hasOwn(CATEGORY, c)) return CATEGORY[c];
  for (const [type, words] of RULES) {
    if (words.some((w) => (text ?? '').includes(w))) return type;
  }
  return null;
}

const SIDO_ALIASES = [
  ['충북', ['충청북도', '충북']],
  ['충남', ['충청남도', '충남']],
  ['전북', ['전라북도', '전북특별자치도', '전북']],
  ['전남', ['전라남도', '전남']],
  ['경북', ['경상북도', '경북']],
  ['경남', ['경상남도', '경남']],
];

/** "강원특별자치도" and "강원도" → "강원"; "충청북도" → "충북". */
export function sidoKey(name) {
  const n = (name ?? '').trim();
  for (const [key, aliases] of SIDO_ALIASES) if (aliases.some((a) => n.startsWith(a))) return key;
  return n.slice(0, 2);
}

/**
 * True when a message's reception regions cover the given place.
 * Regions come as a comma-separated list such as "경상북도 의성군,경상북도 안동시" or "경상북도 전체".
 */
export function regionMatches(receptionRegions, sido, sigungu) {
  const key = sidoKey(sido);
  if (!key) return false;
  return String(receptionRegions ?? '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
    .some((item) => {
      const [itemSido, ...rest] = item.split(/\s+/);
      if (sidoKey(itemSido) !== key) return false;
      const detail = rest.join(' ');
      if (detail === '' || detail === '전체') return true;
      return Boolean(sigungu) && (detail.startsWith(sigungu) || sigungu.startsWith(detail));
    });
}

const SAMPLE = (now) => [
  {
    id: 'sample-1',
    createdAt: now - 6 * 60_000,
    text: '[견본] 오늘 14:20 서울 강남구 일대 대설경보 발효. 차량 운행을 자제하고 고립 시 차 안에서 구조를 기다리세요.',
    regions: '서울특별시 강남구',
    category: '대설',
    level: '긴급재난',
  },
];

export const ALERT_MAX_AGE_MS = 24 * 3_600_000;
const CACHE_KEY = 'alerts:raw';
const CACHE_TTL_SEC = 120;

/**
 * Latest official messages for a region.
 * `source` is "live", "sample" (SAMPLE_DATA=1, development only) or "unconfigured" (no key: empty list).
 */
export async function getAlerts({ store, env, fetchFn, now, upstream }, region) {
  let raw;
  let source;
  if (env.SAFETYDATA_ALERT_KEY) {
    source = 'live';
    const cached = await store.get(CACHE_KEY);
    if (cached) raw = JSON.parse(cached);
    else {
      raw = await upstream(env.SAFETYDATA_ALERT_KEY, fetchFn, now);
      await store.set(CACHE_KEY, JSON.stringify(raw), CACHE_TTL_SEC);
    }
  } else if (env.SAMPLE_DATA === '1') {
    source = 'sample';
    raw = SAMPLE(now);
  } else {
    return { source: 'unconfigured', alerts: [] };
  }

  const alerts = raw
    .filter((m) => now - m.createdAt <= ALERT_MAX_AGE_MS)
    .filter((m) => region && regionMatches(m.regions, region.sido, region.sigungu))
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, 20)
    .map((m) => ({
      id: String(m.id),
      createdAt: m.createdAt,
      text: String(m.text).slice(0, 500),
      level: m.level ?? null,
      type: classify(m.category, m.text),
    }));
  return { source, alerts };
}
