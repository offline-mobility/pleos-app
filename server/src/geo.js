const R_KM = 6371;
const rad = (d) => (d * Math.PI) / 180;

/** Great-circle distance in km. */
export function distanceKm(lat1, lon1, lat2, lon2) {
  const dp = rad(lat2 - lat1);
  const dl = rad(lon2 - lon1);
  const a = Math.sin(dp / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dl / 2) ** 2;
  return 2 * R_KM * Math.asin(Math.sqrt(Math.min(1, a)));
}

export function isLat(v) {
  return typeof v === 'number' && Number.isFinite(v) && v >= -90 && v <= 90;
}

export function isLon(v) {
  return typeof v === 'number' && Number.isFinite(v) && v >= -180 && v <= 180;
}

/** Parses a query coordinate; returns null when missing or out of range. */
export function parseCoord(value, check) {
  if (typeof value !== 'string' || !/^-?\d{1,3}(\.\d{1,8})?$/.test(value)) return null;
  const n = Number(value);
  return check(n) ? n : null;
}
