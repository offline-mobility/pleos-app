import { HttpError } from './errors.js';
import { distanceKm, isLat, isLon } from './geo.js';

export const MESSAGES = new Set([
  'SOS', 'MEDICAL', 'TRAPPED', 'FIRE', 'HAZARD', 'FLOOD', 'VULNERABLE', 'WATER', 'POWER', 'ROAD_BLOCKED',
  'ROUTE_CLEAR', 'SHELTER', 'RESCUE_TEAM', 'ALL_SAFE', 'COLD', 'COLLAPSE', 'CBRN', 'FOOD', 'MEDICINE',
]);

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const REPORT_ID = /^[0-9a-f]{2}-[0-9a-f]$/;
const HASH = 'incidents';

export const LIMITS = {
  bodyBytes: 64 * 1024,
  reports: 50,
  futureSkewMs: 5 * 60_000,
  maxAgeMs: 7 * 24 * 3_600_000,
  incidentTtlMs: 72 * 3_600_000,
  mergeWindowMs: 2 * 3_600_000,
  mergeKm: 5,
  reportersKept: 50,
};

const isInt = (v, min, max) => Number.isInteger(v) && v >= min && v <= max;

/** Validates an upload body (docs/GATEWAY_API.md). Invalid reports are dropped, a bad envelope is rejected. */
export function parseUpload(body, now) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new HttpError(400, 'invalid body');
  if (body.version !== 1) throw new HttpError(400, 'unsupported version');
  if (typeof body.reporter !== 'string' || !UUID.test(body.reporter)) throw new HttpError(400, 'invalid reporter');
  if (!Array.isArray(body.reports) || body.reports.length > LIMITS.reports) throw new HttpError(400, 'invalid reports');

  const reports = [];
  for (const r of body.reports) {
    if (r === null || typeof r !== 'object') continue;
    if (typeof r.id !== 'string' || !REPORT_ID.test(r.id)) continue;
    if (!MESSAGES.has(r.message)) continue;
    if (!isInt(r.people, 0, 15) || !isInt(r.hop, 0, 3) || typeof r.own !== 'boolean') continue;
    if (!Number.isInteger(r.heardAt) || r.heardAt > now + LIMITS.futureSkewMs || r.heardAt < now - LIMITS.maxAgeMs) continue;
    const hasLocation = r.lat !== undefined || r.lon !== undefined;
    if (hasLocation && !(isLat(r.lat) && isLon(r.lon))) continue;
    reports.push({
      id: r.id,
      message: r.message,
      people: r.people,
      hop: r.hop,
      own: r.own,
      heardAt: r.heardAt,
      lat: hasLocation ? r.lat : null,
      lon: hasLocation ? r.lon : null,
    });
  }
  return { reporter: body.reporter, reports };
}

function sameIncident(incident, report) {
  if (incident.id !== report.id || incident.message !== report.message) return false;
  if (Math.abs(incident.firstHeardAt - report.heardAt) > LIMITS.mergeWindowMs) return false;
  if (incident.lat !== null && report.lat !== null) {
    return distanceKm(incident.lat, incident.lon, report.lat, report.lon) <= LIMITS.mergeKm;
  }
  return true;
}

async function loadIncidents(store, now) {
  const raw = await store.hgetall(HASH);
  const live = [];
  const expired = [];
  for (const [uid, json] of Object.entries(raw)) {
    let incident;
    try {
      incident = JSON.parse(json);
    } catch {
      expired.push(uid);
      continue;
    }
    if (now - incident.lastHeardAt > LIMITS.incidentTtlMs) expired.push(uid);
    else live.push(incident);
  }
  if (expired.length) await store.hdel(HASH, expired);
  return live;
}

/**
 * Stores reports, merging those that describe the same original message. The copy heard with the
 * fewest relays provides location and head count; distinct reporters are counted as confirmations.
 */
export async function ingest(store, upload, now) {
  const incidents = await loadIncidents(store, now);
  let created = 0;
  let merged = 0;
  for (const report of upload.reports) {
    let incident = incidents.find((i) => sameIncident(i, report));
    if (!incident) {
      incident = {
        uid: `${report.id}-${report.heardAt.toString(36)}-${Math.floor(Math.random() * 1e6).toString(36)}`,
        id: report.id,
        message: report.message,
        people: report.people,
        hop: report.hop,
        lat: report.lat,
        lon: report.lon,
        firstHeardAt: report.heardAt,
        lastHeardAt: report.heardAt,
        fromSender: report.own,
        reporters: [],
      };
      incidents.push(incident);
      created++;
    } else {
      merged++;
      if (report.hop < incident.hop || (incident.lat === null && report.lat !== null)) {
        incident.hop = Math.min(incident.hop, report.hop);
        incident.people = report.people;
        if (report.lat !== null) {
          incident.lat = report.lat;
          incident.lon = report.lon;
        }
      }
      incident.firstHeardAt = Math.min(incident.firstHeardAt, report.heardAt);
      incident.lastHeardAt = Math.max(incident.lastHeardAt, report.heardAt);
      incident.fromSender ||= report.own;
    }
    if (!incident.reporters.includes(upload.reporter) && incident.reporters.length < LIMITS.reportersKept) {
      incident.reporters.push(upload.reporter);
    }
    await store.hset(HASH, incident.uid, JSON.stringify(incident));
  }
  return { created, merged };
}

/** Public shape: reporter ids are never exposed, only how many distinct cars confirmed. */
export function publicIncident(i) {
  return {
    uid: i.uid,
    id: i.id,
    message: i.message,
    people: i.people,
    hop: i.hop,
    lat: i.lat,
    lon: i.lon,
    firstHeardAt: i.firstHeardAt,
    lastHeardAt: i.lastHeardAt,
    fromSender: i.fromSender,
    confirmations: i.reporters.length,
  };
}

export async function listIncidents(store, now) {
  const incidents = await loadIncidents(store, now);
  return incidents.sort((a, b) => b.lastHeardAt - a.lastHeardAt).map(publicIncident);
}

export async function nearbyIncidents(store, now, lat, lon, km) {
  const all = await listIncidents(store, now);
  return all
    .filter((i) => i.lat !== null && distanceKm(lat, lon, i.lat, i.lon) <= km)
    .slice(0, 100);
}
