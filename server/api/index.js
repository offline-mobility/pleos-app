import { handle } from '../src/app.js';
import { createStore } from '../src/store.js';

/** Vercel function entry. vercel.json rewrites /api/* here and passes the sub-path as ?path=. */
export default async function handler(req, res) {
  const { path = '', ...query } = req.query ?? {};
  const sub = Array.isArray(path) ? path.join('/') : String(path);
  const flat = Object.fromEntries(Object.entries(query).map(([k, v]) => [k, Array.isArray(v) ? v[0] : v]));
  const forwarded = String(req.headers['x-forwarded-for'] ?? '').split(',')[0].trim();

  const response = await handle(
    {
      method: req.method,
      path: `/api/${sub}`,
      query: flat,
      headers: req.headers,
      body: req.body,
      ip: forwarded || req.socket?.remoteAddress,
    },
    { store: createStore(process.env), env: process.env, now: Date.now(), fetchFn: fetch },
  );
  for (const [k, v] of Object.entries(response.headers)) res.setHeader(k, v);
  res.status(response.status).send(response.body);
}
