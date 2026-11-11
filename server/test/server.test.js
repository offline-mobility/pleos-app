import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classify, regionMatches, sidoKey } from '../src/alerts.js';
import { handle } from '../src/app.js';
import { MemoryStore } from '../src/store.js';
import { kstDate, mapAlert, mapShelter, parseKst } from '../src/upstream.js';

const NOW = 1_794_000_000_000;
const REPORTER_A = '123e4567-e89b-12d3-a456-426614174000';
const REPORTER_B = '223e4567-e89b-12d3-a456-426614174000';

function deps(env = {}) {
  return { store: new MemoryStore(() => NOW), env, now: NOW, fetchFn: async () => { throw new Error('no network in tests'); } };
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
