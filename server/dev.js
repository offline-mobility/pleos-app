// Local development server: `npm run dev`. Reads .env, serves the dashboard and the API.
import { readFileSync, existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { handle } from './src/app.js';
import { createStore } from './src/store.js';
import { LIMITS } from './src/reports.js';

if (existsSync(new URL('./.env', import.meta.url))) {
  for (const line of readFileSync(new URL('./.env', import.meta.url), 'utf8').split('\n')) {
    const m = line.match(/^([A-Z0-9_]+)=(.*)$/);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].trim();
  }
}

const port = Number(process.env.PORT ?? 8787);
const dashboard = readFileSync(new URL('./public/index.html', import.meta.url));

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && (url.pathname === '/' || url.pathname === '/index.html')) {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'X-Content-Type-Options': 'nosniff' });
    return res.end(dashboard);
  }
  let body = '';
  let tooLarge = false;
  req.setEncoding('utf8');
  for await (const chunk of req) {
    body += chunk;
    if (body.length > LIMITS.bodyBytes * 2) {
      tooLarge = true;
      break;
    }
  }
  if (tooLarge) {
    res.writeHead(413).end();
    return;
  }
  const response = await handle(
    {
      method: req.method,
      path: url.pathname,
      query: Object.fromEntries(url.searchParams),
      headers: req.headers,
      body,
      ip: req.socket.remoteAddress,
    },
    { store: createStore(process.env), env: process.env, now: Date.now(), fetchFn: fetch },
  );
  res.writeHead(response.status, response.headers).end(response.body);
}).listen(port, () => console.log(`http://localhost:${port}`));
