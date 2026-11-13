import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classify, regionMatches, relevance, sidoKey } from '../src/alerts.js';
import { CIVIL_SNAPSHOT, civilSheltersNear } from '../src/civil.js';
import { getShelters, SHELTER_LIMITS, shelterGroup } from '../src/shelters.js';
import { handle } from '../src/app.js';
import { MemoryStore } from '../src/store.js';
import { kstDate, mapAlert, mapCivilShelter, mapShelter, parseKst } from '../src/upstream.js';
import { parseRelayLink, relaySummary } from '../public/relay.js';

const NOW = 1_794_000_000_000;
const REPORTER_A = '123e4567-e89b-12d3-a456-426614174000';
const REPORTER_B = '223e4567-e89b-12d3-a456-426614174000';

function deps(env = {}) {
  // The bundled civil defence snapshot is switched off unless a test asks for it.
  return { store: new MemoryStore(() => NOW), env, now: NOW, fetchFn: async () => { throw new Error('no network in tests'); }, civilNear: () => [] };
}

const call = (d, method, path, { query = {}, body, headers = {}, ip = '1.1.1.1' } = {}) =>
  handle({ method, path, query, headers, body: body === undefined ? '' : JSON.stringify(body), ip }, d)
    .then((r) => ({ status: r.status, json: JSON.parse(r.body) }));

const report = (over = {}) => ({ id: 'a1-3', message: 'MEDICAL', people: 2, hop: 1, own: false, heardAt: NOW - 60_000, lat: 37.4979, lon: 127.0276, ...over });
const upload = (reporter, reports) => ({ version: 1, reporter, reports });

test('upload is validated, merged across cars, and reporter ids stay private', async () => {
  const d = deps({ DASHBOARD_TOKEN: 'secret-token' });
  let r = await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, [report()]) });
  assert.deepEqual([r.status, r.json.created, r.json.merged], [200, 1, 0]);

  // A second car heard the same message more directly and 200 m away: same incident, better data.
  r = await call(d, 'POST', '/api/reports', { body: upload(REPORTER_B, [report({ hop: 0, people: 3, lat: 37.4990, lon: 127.0290 })]) });
  assert.deepEqual([r.json.created, r.json.merged], [0, 1]);

  r = await call(d, 'GET', '/api/incidents', { headers: { authorization: 'Bearer secret-token' } });
  assert.equal(r.json.incidents.length, 1);
  const incident = r.json.incidents[0];
  assert.deepEqual([incident.hop, incident.people, incident.confirmations], [0, 3, 2]);
  assert.equal(JSON.stringify(r.json).includes(REPORTER_A), false);
});

test('same id far away or much later is a different incident', async () => {
  const d = deps({ DASHBOARD_TOKEN: 't' });
  await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, [report()]) });
  await call(d, 'POST', '/api/reports', { body: upload(REPORTER_B, [report({ lat: 35.1796, lon: 129.0756 })]) });
  await call(d, 'POST', '/api/reports', { body: upload(REPORTER_B, [report({ heardAt: NOW - 5 * 3_600_000 })]) });
  const r = await call(d, 'GET', '/api/incidents', { headers: { authorization: 'Bearer t' } });
  assert.equal(r.json.incidents.length, 3);
});

test('invalid reports are dropped and bad envelopes rejected', async () => {
  const d = deps();
  const bad = [
    report({ message: 'DROP TABLE' }), report({ people: 99 }), report({ lat: 123 }), report({ heardAt: NOW + 3_600_000 }),
    report({ id: '<script>' }), report({ lon: undefined }), report({ heardAt: NOW - 8 * 24 * 3_600_000 }),
  ];
  let r = await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, [...bad, report({ id: '0f-2' })]) });
  assert.deepEqual([r.status, r.json.accepted], [200, 1]);

  for (const body of [{ version: 2, reporter: REPORTER_A, reports: [] }, upload('not-a-uuid', []), upload(REPORTER_A, Array(51).fill(report())), [], null]) {
    r = await call(d, 'POST', '/api/reports', { body });
    assert.equal(r.status, 400);
  }
  const big = await handle({ method: 'POST', path: '/api/reports', query: {}, headers: {}, body: 'x'.repeat(70_000), ip: '1.1.1.1' }, d);
  assert.equal(big.status, 413);
});

test('dashboard needs the token and is off when unset', async () => {
  assert.equal((await call(deps(), 'GET', '/api/incidents')).status, 503);
  const d = deps({ DASHBOARD_TOKEN: 'secret-token' });
  assert.equal((await call(d, 'GET', '/api/incidents')).status, 401);
  assert.equal((await call(d, 'GET', '/api/incidents', { headers: { authorization: 'Bearer wrong-token!' } })).status, 401);
  assert.equal((await call(d, 'GET', '/api/incidents', { headers: { authorization: 'Bearer secret-token' } })).status, 200);
});

test('nearby returns only located incidents within the capped radius', async () => {
  const d = deps();
  await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, [report(), report({ id: 'b2-1', lat: 35.1796, lon: 129.0756 }), report({ id: 'c3-1', lat: undefined, lon: undefined })]) });
  let r = await call(d, 'GET', '/api/reports/nearby', { query: { lat: '37.50', lon: '127.03', km: '9999' } });
  assert.deepEqual(r.json.incidents.map((i) => i.id), ['a1-3']);
  r = await call(d, 'GET', '/api/reports/nearby', { query: { lat: 'abc', lon: '127.03' } });
  assert.equal(r.status, 400);
});

test('rate limit per reporter', async () => {
  const d = deps();
  let last;
  for (let i = 0; i < 31; i++) last = await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, []) });
  assert.equal(last.status, 429);
});

test('alerts and shelters are empty without keys, sample only when asked', async () => {
  let r = await call(deps(), 'GET', '/api/alerts', { query: { lat: '37.4979', lon: '127.0276' } });
  assert.deepEqual([r.status, r.json.source, r.json.alerts.length], [200, 'unconfigured', 0]);

  const d = deps({ SAMPLE_DATA: '1' });
  r = await call(d, 'GET', '/api/shelters', { query: { lat: '37.4979', lon: '127.0276', km: '1' } });
  assert.equal(r.json.source, 'sample');
  assert.ok(r.json.shelters.length >= 2);
  assert.ok(r.json.shelters[0].distanceM <= r.json.shelters[1].distanceM);

  r = await call(d, 'GET', '/api/alerts', { query: { lat: '37.4979', lon: '127.0276' } });
  assert.deepEqual([r.json.region.sido, r.json.alerts[0].type], ['서울특별시', 'SNOW_COLD']);
  // Busan is outside the sample message's region.
  r = await call(d, 'GET', '/api/alerts', { query: { lat: '35.1796', lon: '129.0756' } });
  assert.equal(r.json.alerts.length, 0);
});

test('classification of official messages', () => {
  assert.equal(classify('산불', ''), 'WILDFIRE');
  assert.equal(classify('', '의성군 산불 확산 중. 인근 주민은 즉시 대피'), 'WILDFIRE');
  assert.equal(classify('호우', '산사태 위험'), 'FLOOD');
  assert.equal(classify('통신', '통신 장애 발생'), 'BLACKOUT');
  assert.equal(classify('교통', '○○터널 화재로 통제'), 'TUNNEL');
  assert.equal(classify('화재', '공장 화재 발생'), null);
  assert.equal(classify('', '산사태 경보 발령'), 'LANDSLIDE');
  assert.equal(classify('지진해일', ''), null);
  assert.equal(classify('', '동해안 지진해일 주의보'), null);
  assert.equal(classify('', '규모 4.8 지진 발생'), 'EARTHQUAKE');
  assert.equal(classify('민방공', '공습경보 발령'), 'AIR_RAID');
  assert.equal(classify('기타', '실종자를 찾습니다'), null);
});

test('region matching handles province name variants', () => {
  assert.equal(sidoKey('강원특별자치도'), sidoKey('강원도'));
  assert.equal(sidoKey('전북특별자치도'), sidoKey('전라북도'));
  assert.notEqual(sidoKey('충청북도'), sidoKey('충청남도'));
  assert.ok(regionMatches('경상북도 의성군,경상북도 안동시', '경상북도', '안동시'));
  assert.ok(regionMatches('경상북도 전체', '경상북도', '영덕군'));
  assert.ok(regionMatches('강원도 고성군', '강원특별자치도', '고성군'));
  assert.equal(regionMatches('경상남도 고성군', '강원특별자치도', '고성군'), false);
  assert.equal(regionMatches('경상북도 의성군', '경상북도', '안동시'), false);
});

test('unknown routes and internal errors do not leak details', async () => {
  assert.equal((await call(deps(), 'GET', '/api/nope')).status, 404);
  const r = await call(deps({ SAFETYDATA_SHELTER_KEY: 'SECRET-KEY' }), 'GET', '/api/shelters', { query: { lat: '37.5', lon: '127.0' } });
  assert.equal(r.status, 502);
  assert.equal(JSON.stringify(r.json).includes('SECRET'), false);
});

test('live alerts and shelters flow through the upstream format', async () => {
  const calls = [];
  const fetchFn = async (url) => {
    calls.push(url);
    const body = url.includes('DSSP-IF-10941')
      ? [{ REARE_NM: '포항 남구 체육관', RONA_DADDR: '경상북도 포항시 남구 대이로 1', LAT: '36.0100', LOT: '129.3500', SHLT_SE_CD: '3', SHLT_SE_NM: '지진옥외대피장소', MNG_SN: 77 }]
      : [
          { SN: 269182, CRT_DT: '2026/11/09 03:10:00', MSG_CN: '포항시 남구 산불 확산. 즉시 대피 바랍니다.', RCPTN_RGN_NM: '경상북도 포항시 남구 ,경상북도 포항시 북구 ', EMRG_STEP_NM: '긴급재난', DST_SE_NM: '산불' },
          { SN: 269183, CRT_DT: '2026/11/09 03:11:00', MSG_CN: '서울 한파주의보', RCPTN_RGN_NM: '서울특별시 강남구 ', EMRG_STEP_NM: '안전안내', DST_SE_NM: '한파' },
        ];
    return { ok: true, json: async () => ({ header: { resultCode: '00' }, totalCount: body.length, body }) };
  };
  const d = { ...deps({ SAFETYDATA_ALERT_KEY: 'k1', SAFETYDATA_SHELTER_KEY: 'k2' }), fetchFn };
  const r = await call(d, 'GET', '/api/alerts', { query: { lat: '36.01', lon: '129.35' } });
  assert.deepEqual([r.status, r.json.source, r.json.region.sigungu], [200, 'live', '포항시']);
  assert.deepEqual(r.json.alerts.map((a) => [a.id, a.type]), [['269182', 'WILDFIRE']]);
  // Second request is served from cache: no new upstream calls.
  const before = calls.length;
  await call(d, 'GET', '/api/alerts', { query: { lat: '36.01', lon: '129.35' } });
  assert.equal(calls.length, before);
  assert.ok(calls[0].includes('startLat=') && calls.some((u) => u.includes('crtDt=')));
});

test('upstream parsing helpers', async () => {
  assert.equal(parseKst('2026/09/27 13:31:31'), Date.UTC(2026, 8, 27, 4, 31, 31));
  assert.equal(parseKst('bad'), null);
  assert.equal(kstDate(Date.UTC(2026, 10, 9, 16, 0, 0)), '20261110');
  assert.equal(mapAlert({ SN: 1, CRT_DT: 'x', MSG_CN: 'm' }), null);
  assert.equal(mapShelter({ REARE_NM: 'a', LAT: '91', LOT: '127' }), null);
  // An upstream error code (for example an unregistered key) surfaces as a generic 502.
  const fetchFn = async () => ({ ok: true, json: async () => ({ header: { resultCode: '30' }, body: null }) });
  const r = await call({ ...deps({ SAFETYDATA_SHELTER_KEY: 'k' }), fetchFn }, 'GET', '/api/shelters', { query: { lat: '37.5', lon: '127.0' } });
  assert.equal(r.status, 502);
});

test('a QR relay link becomes one report the upload endpoint accepts', async () => {
  // Same shape the app builds (RelayLink.kt); t is in seconds.
  const seconds = Math.floor((NOW - 120_000) / 1000);
  const report = parseRelayLink(`?m=MEDICAL&p=2&i=a1-3&h=0&t=${seconds}&la=37.49787&lo=127.02759`);
  assert.deepEqual(report, { id: 'a1-3', message: 'MEDICAL', people: 2, hop: 0, own: false, heardAt: seconds * 1000, lat: 37.49787, lon: 127.02759 });
  assert.equal(relaySummary(report), '응급 환자 · 2명');
  assert.equal(relaySummary(parseRelayLink(`?m=ROAD_BLOCKED&p=3&i=0f-0&h=1&t=${seconds}`)), '앞길 막힘');

  const d = deps({ DASHBOARD_TOKEN: 't' });
  const r = await call(d, 'POST', '/api/reports', { body: upload(REPORTER_A, [report]) });
  assert.deepEqual([r.status, r.json.accepted, r.json.created], [200, 1, 1]);
});

test('a tampered relay link is rejected as a whole', () => {
  const ok = 'm=SOS&p=1&i=a1-3&h=0&t=1794000000';
  assert.notEqual(parseRelayLink(`?${ok}`), null);
  for (const bad of [
    '', 'm=SOS', ok.replace('m=SOS', 'm=<script>'), ok.replace('m=SOS', 'm=constructor'), ok.replace('m=SOS', 'm=ACK'),
    ok.replace('p=1', 'p=16'), ok.replace('p=1', 'p=1e1'), ok.replace('i=a1-3', 'i=A1-3'), ok.replace('h=0', 'h=4'),
    ok.replace('t=1794000000', 't=-5'), `${ok}&la=37.5`, `${ok}&la=91.0&lo=127.0`, `${ok}&la=37.5&lo=abc`,
  ]) {
    assert.equal(parseRelayLink(`?${bad}`), null, bad);
  }
});

test('messages that are not about a disaster in progress are left out', () => {
  // Real messages from 2026-11 (재난안전데이터공유플랫폼).
  const m = (category, text, level = '안전안내') => relevance({ category, text, level });
  assert.deepEqual(m('산불', '오늘 09:27 재산면 갈산리 2366에서 산불 발생. 입산 및 산불 현장 주변 접근 금지.'), { show: true, type: 'WILDFIRE' });
  assert.deepEqual(m('산불', '산불 예방은 시민 여러분의 관심과 실천에서 시작됩니다. 영농부산물·쓰레기 소각 금지 등 산불 예방 수칙을 꼭 지켜주세요.'), { show: false, type: null });
  assert.deepEqual(m('한파', '오늘(6일) 21시부터 한파주의보 발효. 외출 시 보온에 유의하고 수도계량기 동파에 대비해 주시기 바랍니다.'), { show: true, type: 'SNOW_COLD' });
  assert.deepEqual(m('한파', '한파주의보가 해제되었습니다. 빙판길 안전에 유의하세요.'), { show: false, type: null });
  assert.deepEqual(m('산불', '오늘 09:27 재산면 갈산리 산582-1 인근에서 발생한 산불 주불 진화 완료. 현재 잔불 정리 중'), { show: false, type: null });
  assert.deepEqual(m('화재', '화재 진압은 완료하였으나 연기 등 흡입 우려가 있으므로 당분간 창문 등을 닫아주시기 바랍니다.'), { show: true, type: null });
  assert.deepEqual(m('강풍', '우리 지역 강풍주의보 발효 ▲영농부산물 소각 금지(산불 예방) ▲해안가 접근 금지'), { show: true, type: 'TYPHOON' });
  assert.deepEqual(m('환경오염사고', '오늘 20:15 경 고잔동 689-2, 유독가스 유출. 인근 주민은 젖은 물수건 등으로 코와 입을 막고 미리 대피하세요.'), { show: true, type: 'CHEMICAL' });
  assert.deepEqual(m('화재', '오늘 08:49경 장안면 석포리에서 화재가 발생하여 연기가 다량 발생 중. 창문을 닫는 등 안전에 유의하여 주시기 바랍니다.'), { show: true, type: null });
  assert.deepEqual(m('안개', '오늘 짙은 안개 발생으로 시야 확보가 어렵습니다.'), { show: true, type: null });
  assert.deepEqual(m('기타', '담양읍 오계리에서 실종된 정00(여/66세)을 찾습니다.'), { show: false, type: null });
  assert.deepEqual(m('수도', '노후상수관망 정비사업 밸브차단 작업으로 단수 예정이오니 사전대비 바랍니다.'), { show: false, type: null });
  assert.deepEqual(m('교통사고', '3호선 지산~범물역간 차량고장으로 전구간 열차지연 발생중'), { show: false, type: null });
  // An urgent-level message is always shown.
  assert.equal(m('기타', '즉시 대피하십시오.', '긴급재난').show, true);
});

test('the merged 전남광주 region matches both of its former names', () => {
  assert.equal(sidoKey('전남광주통합특별시'), sidoKey('광주광역시'));
  assert.equal(sidoKey('전남광주통합특별시'), sidoKey('전라남도'));
  assert.equal(regionMatches('전남광주통합특별시 담양군 ', '전라남도', '담양군'), true);
  assert.equal(regionMatches('경기도 부천시 원미구 ,경기도 부천시 소사구 ', '경기도', '부천시'), true);
  assert.equal(regionMatches('전남광주통합특별시 담양군 ', '경기도', '광주시'), false);
});

test('civil defence shelter rows are validated and capacity follows floor area', () => {
  const row = { GID: '40028265', SHLT_NM: " 전하I'PARK 101~109동 ", LAT: 35.509725, LOT: 129.423678, FCAR: 36785, ROAD_NM_ADDR: '울산광역시 동구  봉수로 270', DADDR: '울산광역시 동구 전하동 640번지 1호' };
  assert.deepEqual(mapCivilShelter(row), ['40028265', "전하I'PARK 101~109동", 35.50973, 129.42368, 36785, '울산광역시 동구 봉수로 270']);
  assert.equal(mapCivilShelter({ ...row, LAT: 0 }), null);
  assert.equal(mapCivilShelter({ ...row, SHLT_NM: '' }), null);

  const near = civilSheltersNear(35.51, 129.42, 2, [mapCivilShelter(row), mapCivilShelter({ ...row, GID: 'x', LAT: 37.5, LOT: 127.0 })]);
  assert.equal(near.length, 1);
  assert.deepEqual([near[0].kind, near[0].sido, near[0].sigungu, near[0].capacity], ['민방위대피소', '울산광역시', '동구', 44587]);
});

test('the bundled snapshot covers the country', () => {
  assert.ok(CIVIL_SNAPSHOT.count > 15_000);
  assert.match(CIVIL_SNAPSHOT.generatedAt, /^\d{4}-\d{2}-\d{2}$/);
  for (const [lat, lon] of [[37.4979, 127.0276], [35.1796, 129.0756], [35.1595, 126.8526], [33.4996, 126.5312]]) {
    assert.ok(civilSheltersNear(lat, lon, 2).length > 0, `${lat},${lon}`);
  }
});

test('each kind of shelter keeps its own nearest ones', async () => {
  const at = (i) => ({ lat: 37.5 + i * 0.0001, lon: 127.0 });
  const rest = Array.from({ length: 200 }, (_, i) => ({ id: `r${i}`, name: `쉼터 ${i}`, kind: '무더위쉼터', sido: '서울특별시', sigungu: '강남구', address: '', capacity: null, ...at(i) }));
  const quake = [{ id: 'q', name: '운동장', kind: '지진옥외대피장소', sido: '서울특별시', sigungu: '강남구', address: '', capacity: null, ...at(250) }];
  const civil = [{ id: 'c', name: '지하주차장', kind: '민방위대피소', sido: '서울특별시', sigungu: '강남구', address: '', capacity: 1200, ...at(260) }];
  const base = { ...deps({ SAFETYDATA_SHELTER_KEY: 'k' }), upstream: async () => [...rest, ...quake], civilNear: () => civil };
  const r = await getShelters(base, 37.5, 127.0, 5);
  assert.equal(r.source, 'live');
  assert.equal(r.shelters.filter((s) => shelterGroup(s.kind) === 'rest').length, SHELTER_LIMITS.perGroup);
  assert.deepEqual(r.shelters.slice(-2).map((s) => s.id), ['q', 'c']);

  // Without an API key the snapshot alone still answers.
  const offline = await getShelters({ ...deps(), civilNear: () => civil }, 37.5, 127.0, 5);
  assert.deepEqual([offline.source, offline.shelters.length], ['snapshot', 1]);
});
