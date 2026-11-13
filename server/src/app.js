import { timingSafeEqual } from 'node:crypto';
import { getAlerts } from './alerts.js';
import { CIVIL_SNAPSHOT } from './civil.js';
import { HttpError } from './errors.js';
import { isLat, isLon, parseCoord } from './geo.js';
import { ingest, LIMITS, listIncidents, nearbyIncidents, parseUpload } from './reports.js';
import { getShelters, regionOf, SHELTER_LIMITS } from './shelters.js';
import { fetchAlertsUpstream, fetchSheltersUpstream } from './upstream.js';

const json = (status, body) => ({
  status,
  headers: { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' },
  body: JSON.stringify(body),
});

async function rateLimit(store, key, perMinute, now) {
  const minute = Math.floor(now / 60_000);
  const count = await store.incr(`rl:${key}:${minute}`, 120);
  if (count > perMinute) throw new HttpError(429, 'too many requests');
}

function tokenOk(header, expected) {
  if (!expected || typeof header !== 'string' || !header.startsWith('Bearer ')) return false;
  const a = Buffer.from(header.slice(7));
  const b = Buffer.from(expected);
  return a.length === b.length && timingSafeEqual(a, b);
}

function point(query) {
  const lat = parseCoord(query.lat, isLat);
  const lon = parseCoord(query.lon, isLon);
  if (lat === null || lon === null) throw new HttpError(400, 'lat and lon are required');
  return { lat, lon };
}

function radius(query, fallback, max) {
  if (query.km === undefined) return fallback;
  const km = Number(query.km);
  if (!Number.isFinite(km) || km <= 0) throw new HttpError(400, 'invalid km');
  return Math.min(km, max);
}

/**
 * Framework-independent request handler.
 * request: { method, path, query, headers (lower-case keys), body (string or parsed object), ip }
 * deps: { store, env, now, fetchFn }
 */
export async function handle(request, deps) {
  const { store, env, now } = deps;
  try {
    const route = `${request.method} ${request.path.replace(/\/$/, '')}`;
    const ip = request.ip || 'unknown';

    switch (route) {
      case 'GET /api/health':
        return json(200, {
          ok: true,
          alerts: env.SAFETYDATA_ALERT_KEY ? 'live' : env.SAMPLE_DATA === '1' ? 'sample' : 'unconfigured',
          shelters: env.SAFETYDATA_SHELTER_KEY ? 'live' : env.SAMPLE_DATA === '1' ? 'sample' : 'unconfigured',
          civilShelters: CIVIL_SNAPSHOT,
          dashboard: Boolean(env.DASHBOARD_TOKEN),
        });

      case 'POST /api/reports': {
        const text = typeof request.body === 'string' ? request.body : JSON.stringify(request.body ?? null);
        if (Buffer.byteLength(text) > LIMITS.bodyBytes) throw new HttpError(413, 'body too large');
        let parsed;
        try {
          parsed = JSON.parse(text);
        } catch {
          throw new HttpError(400, 'invalid json');
        }
        const upload = parseUpload(parsed, now);
        await rateLimit(store, `ip:${ip}`, 120, now);
        await rateLimit(store, `rep:${upload.reporter}`, 30, now);
        const result = await ingest(store, upload, now);
        return json(200, { accepted: upload.reports.length, ...result });
      }

      case 'GET /api/reports/nearby': {
        await rateLimit(store, `ip:${ip}`, 120, now);
        const { lat, lon } = point(request.query);
        const km = radius(request.query, 5, 10);
        return json(200, { incidents: await nearbyIncidents(store, now, lat, lon, km) });
      }

      case 'GET /api/incidents': {
        if (!env.DASHBOARD_TOKEN) throw new HttpError(503, 'dashboard disabled');
        await rateLimit(store, `ip:${ip}`, 120, now);
        if (!tokenOk(request.headers.authorization, env.DASHBOARD_TOKEN)) throw new HttpError(401, 'unauthorized');
        return json(200, { incidents: await listIncidents(store, now) });
      }

      case 'GET /api/shelters': {
        await rateLimit(store, `ip:${ip}`, 120, now);
        const { lat, lon } = point(request.query);
        const km = radius(request.query, SHELTER_LIMITS.defaultKm, SHELTER_LIMITS.maxKm);
        return json(200, await getShelters({ ...deps, upstream: fetchSheltersUpstream }, lat, lon, km));
      }

      case 'GET /api/alerts': {
        await rateLimit(store, `ip:${ip}`, 120, now);
        const { lat, lon } = point(request.query);
        // Region comes from the nearest shelter's address (within the widest allowed radius for rural areas).
        const near = await getShelters({ ...deps, upstream: fetchSheltersUpstream }, lat, lon, SHELTER_LIMITS.maxKm);
        const region = regionOf(near.shelters);
        const result = await getAlerts({ ...deps, upstream: fetchAlertsUpstream }, region);
        return json(200, { ...result, region });
      }

      default:
        throw new HttpError(404, 'not found');
    }
  } catch (error) {
    if (error instanceof HttpError) return json(error.status, { error: error.message });
    // Never leak internals (upstream URLs contain the service key).
    return json(502, { error: 'upstream or server error' });
  }
}
