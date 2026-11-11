/**
 * Minimal key-value + hash store. Memory for local development, Upstash Redis (REST) in production.
 * Values are strings.
 */
export class MemoryStore {
  constructor(now = () => Date.now()) {
    this.now = now;
    this.kv = new Map();
    this.hashes = new Map();
  }

  async get(key) {
    const e = this.kv.get(key);
    if (!e) return null;
    if (e.expiresAt && e.expiresAt <= this.now()) {
      this.kv.delete(key);
      return null;
    }
    return e.value;
  }

  async set(key, value, ttlSec) {
    this.kv.set(key, { value, expiresAt: ttlSec ? this.now() + ttlSec * 1000 : 0 });
  }

  async incr(key, ttlSec) {
    const current = Number((await this.get(key)) ?? 0) + 1;
    const existing = this.kv.get(key);
    this.kv.set(key, { value: String(current), expiresAt: existing?.expiresAt || this.now() + ttlSec * 1000 });
    return current;
  }

  async hgetall(name) {
    return Object.fromEntries(this.hashes.get(name) ?? []);
  }

  async hset(name, field, value) {
    if (!this.hashes.has(name)) this.hashes.set(name, new Map());
    this.hashes.get(name).set(field, value);
  }

  async hdel(name, fields) {
    const h = this.hashes.get(name);
    if (h) for (const f of fields) h.delete(f);
  }
}

export class UpstashStore {
  constructor(url, token, fetchFn = fetch) {
    if (!/^https:\/\//.test(url)) throw new Error('UPSTASH_REDIS_REST_URL must be https');
    this.url = url.replace(/\/$/, '');
    this.token = token;
    this.fetch = fetchFn;
  }

  async cmd(...args) {
    const res = await this.fetch(this.url, {
      method: 'POST',
      headers: { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(args),
    });
    if (!res.ok) throw new Error(`store error ${res.status}`);
    return (await res.json()).result;
  }

  get(key) {
    return this.cmd('GET', key);
  }

  set(key, value, ttlSec) {
    return ttlSec ? this.cmd('SET', key, value, 'EX', ttlSec) : this.cmd('SET', key, value);
  }

  async incr(key, ttlSec) {
    const n = await this.cmd('INCR', key);
    if (n === 1) await this.cmd('EXPIRE', key, ttlSec);
    return n;
  }

  async hgetall(name) {
    const flat = (await this.cmd('HGETALL', name)) ?? [];
    const out = {};
    for (let i = 0; i + 1 < flat.length; i += 2) out[flat[i]] = flat[i + 1];
    return out;
  }

  hset(name, field, value) {
    return this.cmd('HSET', name, field, value);
  }

  async hdel(name, fields) {
    if (fields.length) await this.cmd('HDEL', name, ...fields);
  }
}

let shared;

export function createStore(env) {
  if (env.UPSTASH_REDIS_REST_URL && env.UPSTASH_REDIS_REST_TOKEN) {
    return new UpstashStore(env.UPSTASH_REDIS_REST_URL, env.UPSTASH_REDIS_REST_TOKEN);
  }
  shared ??= new MemoryStore();
  return shared;
}
