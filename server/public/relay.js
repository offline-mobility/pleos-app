// Shared by the relay page (browser) and the server tests (node): turns the link a car shows as a
// QR code into one report for POST /api/reports. Every value from the link is untrusted.

export const RELAY_LABEL = {
  SOS: '긴급 구조 요청', MEDICAL: '응급 환자', TRAPPED: '고립됨', FIRE: '화재·연기', FLOOD: '침수',
  HAZARD: '가스·위험물', COLD: '추위·저체온', COLLAPSE: '붕괴·낙석', CBRN: '화생방·방사능',
  VULNERABLE: '노약자·어린이', WATER: '식수 필요', FOOD: '식량 필요', MEDICINE: '약 필요', POWER: '연료·전력 부족',
  ROAD_BLOCKED: '앞길 막힘', ROUTE_CLEAR: '지나갈 수 있음', SHELTER: '대피소 있음', RESCUE_TEAM: '구조대 접근',
  ALL_SAFE: '모두 안전',
};

const NO_PEOPLE = new Set(['ROAD_BLOCKED', 'ROUTE_CLEAR', 'SHELTER', 'RESCUE_TEAM']);
const int = (text, min, max) => (/^\d{1,12}$/.test(text ?? '') && Number(text) >= min && Number(text) <= max ? Number(text) : null);
const coord = (text, limit) => (/^-?\d{1,3}\.\d{1,6}$/.test(text ?? '') && Math.abs(Number(text)) <= limit ? Number(text) : null);

/** `search` is location.search. Returns null when anything is missing or out of range. */
export function parseRelayLink(search) {
  const q = new URLSearchParams(search);
  const message = q.get('m');
  const id = q.get('i');
  const people = int(q.get('p'), 0, 15);
  const hop = int(q.get('h'), 0, 3);
  const seconds = int(q.get('t'), 1, 99_999_999_999);
  if (!Object.hasOwn(RELAY_LABEL, message ?? '') || !/^[0-9a-f]{2}-[0-9a-f]$/.test(id ?? '')) return null;
  if (people === null || hop === null || seconds === null) return null;
  const report = { id, message, people, hop, own: false, heardAt: seconds * 1000 };
  if (q.has('la') || q.has('lo')) {
    const lat = coord(q.get('la'), 90);
    const lon = coord(q.get('lo'), 180);
    if (lat === null || lon === null) return null;
    report.lat = lat;
    report.lon = lon;
  }
  return report;
}

/** "응급 환자 · 2명" */
export function relaySummary(report) {
  const people = NO_PEOPLE.has(report.message) || report.people === 0 ? '' : report.people >= 15 ? ' · 15명 이상' : ` · ${report.people}명`;
  return RELAY_LABEL[report.message] + people;
}
