// Local development server: `npm run dev`. Reads .env, serves the dashboard, the relay page and the API.
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
const page = (file, type) => ({ body: readFileSync(new URL(`./public/${file}`, import.meta.url)), type });
const STATIC = {
  '/': page('index.html', 'text/html'),
  '/index.html': page('index.html', 'text/html'),
  '/r': page('r.html', 'text/html'),
  '/r.js': page('r.js', 'text/javascript'),
  '/relay.js': page('relay.js', 'text/javascript'),
};

createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (req.method === 'GET' && Object.hasOwn(STATIC, url.pathname)) {
    const file = STATIC[url.pathname];
    res.writeHead(200, { 'Content-Type': `${file.type}; charset=utf-8`, 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
    return res.end(file.body);
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
