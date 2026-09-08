import { DurableObject } from 'cloudflare:workers';

const USERNAME_RE = /^[A-Za-z0-9_]{3,24}$/;
const MAX_BODY = 4000;
const encoder = new TextEncoder();

function json(data, status = 200, headers = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers }
  });
}

function cors(request, response, env) {
  const allowed = env.CORS_ORIGIN || '*';
  const origin = request.headers.get('Origin');
  if (allowed === '*') response.headers.set('Access-Control-Allow-Origin', '*');
  else if (origin && allowed.split(',').map(v => v.trim()).includes(origin)) {
    response.headers.set('Access-Control-Allow-Origin', origin);
    response.headers.set('Vary', 'Origin');
  }
  response.headers.set('Access-Control-Allow-Headers', 'Content-Type, Authorization');
  response.headers.set('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  return response;
}

function security(response) {
  response.headers.set('X-Content-Type-Options', 'nosniff');
  response.headers.set('X-Frame-Options', 'DENY');
  response.headers.set('Referrer-Policy', 'no-referrer');
  response.headers.set('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
  return response;
}

function response(request, env, data, status = 200, headers = {}) {
  return security(cors(request, json(data, status, headers), env));
}

function validUsername(value) { return typeof value === 'string' && USERNAME_RE.test(value); }
function validPassword(value) { return typeof value === 'string' && value.length >= 8 && value.length <= 128; }
function validBody(value) { return typeof value === 'string' && value.trim().length > 0 && value.length <= MAX_BODY; }
function randomHex(bytes = 32) {
  const data = new Uint8Array(bytes);
  crypto.getRandomValues(data);
  return [...data].map(x => x.toString(16).padStart(2, '0')).join('');
}

async function sha256Hex(value) {
  const digest = await crypto.subtle.digest('SHA-256', encoder.encode(value));
  return [...new Uint8Array(digest)].map(x => x.toString(16).padStart(2, '0')).join('');
}

async function pbkdf2(password, salt, pepper) {
  const key = await crypto.subtle.importKey('raw', encoder.encode(`${password}:${pepper}`), 'PBKDF2', false, ['deriveBits']);
  const bits = await crypto.subtle.deriveBits({ name: 'PBKDF2', salt: encoder.encode(salt), iterations: 210000, hash: 'SHA-256' }, key, 256);
  return [...new Uint8Array(bits)].map(x => x.toString(16).padStart(2, '0')).join('');
}

function safeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

function tokenFromRequest(request) {
  const header = request.headers.get('Authorization') || '';
  return header.replace(/^Bearer\s+/i, '').trim();
}

async function getUser(request, env) {
  const token = tokenFromRequest(request);
  if (!token) return null;
  const hash = await sha256Hex(token);
  return env.DB.prepare(`
    SELECT u.id, u.username
    FROM sessions s JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ? AND s.expires_at > strftime('%Y-%m-%dT%H:%M:%fZ','now')
  `).bind(hash).first();
}

async function createSession(userId, env) {
  const token = randomHex(32);
  const tokenHash = await sha256Hex(token);
  const days = Math.max(1, Math.min(90, Number(env.SESSION_DAYS || 30)));
  await env.DB.prepare(`
    INSERT INTO sessions(token_hash,user_id,expires_at)
    VALUES(?,?,datetime('now', ?))
  `).bind(tokenHash, userId, `+${days} days`).run();
  return token;
}

async function notify(env, userId, payload) {
  const id = env.PRESENCE.idFromName(String(userId));
  const stub = env.PRESENCE.get(id);
  await stub.fetch('https://presence/internal/broadcast', {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload)
  });
}

async function isOnline(env, userId) {
  const id = env.PRESENCE.idFromName(String(userId));
  const result = await env.PRESENCE.get(id).fetch('https://presence/internal/online');
  if (!result.ok) return false;
  const data = await result.json();
  return Boolean(data.online);
}

async function authRateLimit(request, env, bucket = 'auth') {
  const ip = request.headers.get('CF-Connecting-IP') || 'unknown';
  const id = env.PRESENCE.idFromName(`rl:${bucket}:${ip}`);
  const result = await env.PRESENCE.get(id).fetch('https://presence/internal/rate-limit', {
    method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ limit: bucket === 'auth' ? 30 : 120, windowMs: bucket === 'auth' ? 900000 : 60000 })
  });
  return result.ok;
}

async function routeApi(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;

  if (request.method === 'OPTIONS') return response(request, env, {}, 204);
  if (request.method === 'GET' && path === '/health') {
    try { await env.DB.prepare('SELECT 1').first(); return response(request, env, { ok: true, service: 'seam-chat-cloudflare', timestamp: new Date().toISOString() }); }
    catch { return response(request, env, { ok: false }, 503); }
  }

  if (path === '/api/auth/register' && request.method === 'POST') {
    if (!(await authRateLimit(request, env, 'auth'))) return response(request, env, { error: 'Too many authentication attempts.' }, 429);
    const { username, password } = await request.json().catch(() => ({}));
    if (!validUsername(username)) return response(request, env, { error: 'Username must be 3–24 letters, numbers, or underscores.' }, 400);
    if (!validPassword(password)) return response(request, env, { error: 'Password must be 8–128 characters.' }, 400);
    const salt = randomHex(16);
    const hash = await pbkdf2(password, salt, env.PASSWORD_PEPPER || 'development-only-change-me');
    try {
      const result = await env.DB.prepare('INSERT INTO users(username,password_hash,password_salt) VALUES(?,?,?) RETURNING id,username').bind(username, hash, salt).first();
      const token = await createSession(result.id, env);
      return response(request, env, { token, user: { username: result.username } }, 201);
    } catch (error) {
      if (String(error.message || '').includes('UNIQUE')) return response(request, env, { error: 'Username is already taken.' }, 409);
      return response(request, env, { error: 'Registration failed.' }, 500);
    }
  }

  if (path === '/api/auth/login' && request.method === 'POST') {
    if (!(await authRateLimit(request, env, 'auth'))) return response(request, env, { error: 'Too many authentication attempts.' }, 429);
    const { username, password } = await request.json().catch(() => ({}));
    if (!validUsername(username) || !validPassword(password)) return response(request, env, { error: 'Invalid username or password.' }, 401);
    const user = await env.DB.prepare('SELECT id,username,password_hash,password_salt FROM users WHERE username=?').bind(username).first();
    if (!user) return response(request, env, { error: 'Invalid username or password.' }, 401);
    const hash = await pbkdf2(password, user.password_salt, env.PASSWORD_PEPPER || 'development-only-change-me');
    if (!safeEqual(hash, user.password_hash)) return response(request, env, { error: 'Invalid username or password.' }, 401);
    const token = await createSession(user.id, env);
    return response(request, env, { token, user: { username: user.username } });
  }

  const user = await getUser(request, env);
  if (!user) return response(request, env, { error: 'Authentication required or session expired.' }, 401);

  if (path === '/api/auth/logout' && request.method === 'POST') {
    const token = tokenFromRequest(request);
    await env.DB.prepare('DELETE FROM sessions WHERE token_hash=?').bind(await sha256Hex(token)).run();
    return new Response(null, { status: 204 });
  }
  if (path === '/api/me' && request.method === 'GET') return response(request, env, { user: { username: user.username } });

  if (path === '/api/users/search' && request.method === 'GET') {
    const username = String(url.searchParams.get('username') || '').trim();
    if (!validUsername(username)) return response(request, env, { user: null });
    const target = await env.DB.prepare('SELECT username FROM users WHERE username=? AND id<>?').bind(username, user.id).first();
    return response(request, env, { user: target || null });
  }

  if (path === '/api/presence' && request.method === 'GET') {
    const rows = await env.DB.prepare('SELECT id,username FROM users WHERE id<>?').bind(user.id).all();
    const online = [];
    for (const target of rows.results) if (await isOnline(env, target.id)) online.push(target.username);
    return response(request, env, { users: online });
  }

  if (path === '/api/chats' && request.method === 'GET') {
    const result = await env.DB.prepare(`
      SELECT u.username,
        m.body AS lastMessage,
        m.created_at AS lastMessageAt,
        COALESCE((SELECT COUNT(*) FROM messages x WHERE x.sender_id=u.id AND x.recipient_id=? AND x.read_at IS NULL),0) AS unreadCount
      FROM users u
      JOIN messages m ON m.id = (
        SELECT MAX(x.id) FROM messages x
        WHERE (x.sender_id=? AND x.recipient_id=u.id) OR (x.sender_id=u.id AND x.recipient_id=?)
      )
      WHERE u.id<>?
      ORDER BY m.created_at DESC
    `).bind(user.id, user.id, user.id, user.id).all();
    return response(request, env, { chats: result.results });
  }

  const messageMatch = path.match(/^\/api\/messages\/([^/]+)$/);
  if (messageMatch && request.method === 'GET') {
    const username = decodeURIComponent(messageMatch[1]);
    const other = await env.DB.prepare('SELECT id,username FROM users WHERE username=?').bind(username).first();
    if (!other) return response(request, env, { error: 'User not found.' }, 404);
    const limit = Math.min(Math.max(Number.parseInt(url.searchParams.get('limit') || '50', 10) || 50, 1), 100);
    const before = Number.parseInt(url.searchParams.get('before') || '', 10);
    const query = Number.isSafeInteger(before) && before > 0
      ? `SELECT m.id,s.username AS sender,r.username AS recipient,m.body,m.created_at AS createdAt,m.delivered_at AS deliveredAt,m.read_at AS readAt FROM messages m JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id WHERE ((m.sender_id=? AND m.recipient_id=?) OR (m.sender_id=? AND m.recipient_id=?)) AND m.id < ? ORDER BY m.id DESC LIMIT ?`
      : `SELECT m.id,s.username AS sender,r.username AS recipient,m.body,m.created_at AS createdAt,m.delivered_at AS deliveredAt,m.read_at AS readAt FROM messages m JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id WHERE (m.sender_id=? AND m.recipient_id=?) OR (m.sender_id=? AND m.recipient_id=?) ORDER BY m.id DESC LIMIT ?`;
    const bindings = Number.isSafeInteger(before) && before > 0 ? [user.id, other.id, other.id, user.id, before, limit] : [user.id, other.id, other.id, user.id, limit];
    const rows = await env.DB.prepare(query).bind(...bindings).all();
    return response(request, env, { messages: rows.results.reverse(), hasMore: rows.results.length === limit });
  }

  const readMatch = path.match(/^\/api\/messages\/([^/]+)\/read$/);
  if (readMatch && request.method === 'POST') {
    const username = decodeURIComponent(readMatch[1]);
    const other = await env.DB.prepare('SELECT id,username FROM users WHERE username=?').bind(username).first();
    if (!other) return response(request, env, { error: 'User not found.' }, 404);
    const now = new Date().toISOString();
    const rows = await env.DB.prepare(`UPDATE messages SET read_at=COALESCE(read_at,?), delivered_at=COALESCE(delivered_at,?) WHERE sender_id=? AND recipient_id=? AND read_at IS NULL RETURNING id,read_at AS readAt`).bind(now, now, other.id, user.id).all();
    if (rows.results.length) await notify(env, other.id, { type: 'read', from: user.username, ids: rows.results.map(r => r.id), readAt: rows.results[0].readAt });
    return response(request, env, { updated: rows.results.length });
  }

  if (path === '/api/messages' && request.method === 'POST') {
    if (!(await authRateLimit(request, env, 'messages'))) return response(request, env, { error: 'Message rate limit exceeded.' }, 429);
    const { recipient, body, clientMessageId = null } = await request.json().catch(() => ({}));
    if (!validUsername(recipient)) return response(request, env, { error: 'Recipient username is invalid.' }, 400);
    if (!validBody(body)) return response(request, env, { error: 'Message must contain 1–4000 characters.' }, 400);
    const target = await env.DB.prepare('SELECT id,username FROM users WHERE username=?').bind(recipient).first();
    if (!target) return response(request, env, { error: 'Recipient not found.' }, 404);
    if (String(target.id) === String(user.id)) return response(request, env, { error: 'You cannot message yourself.' }, 400);
    const clean = body.trim();
    const delivered = await isOnline(env, target.id);
    const now = new Date().toISOString();
    try {
      const inserted = await env.DB.prepare(`
        INSERT INTO messages(sender_id,recipient_id,body,created_at,delivered_at,client_message_id)
        VALUES(?,?,?,?,?,?)
        RETURNING id,body,created_at AS createdAt,delivered_at AS deliveredAt,read_at AS readAt
      `).bind(user.id, target.id, clean, now, delivered ? now : null, clientMessageId || null).first();
      const message = { type: 'message', id: inserted.id, sender: user.username, recipient: target.username, body: inserted.body, createdAt: inserted.createdAt, deliveredAt: inserted.deliveredAt, readAt: inserted.readAt };
      await Promise.all([notify(env, target.id, message), notify(env, user.id, message)]);
      return response(request, env, { message }, 201);
    } catch (error) {
      if (clientMessageId && String(error.message || '').includes('UNIQUE')) {
        const existing = await env.DB.prepare(`SELECT m.id,m.body,m.created_at AS createdAt,m.delivered_at AS deliveredAt,m.read_at AS readAt,s.username AS sender,r.username AS recipient FROM messages m JOIN users s ON s.id=m.sender_id JOIN users r ON r.id=m.recipient_id WHERE m.sender_id=? AND m.client_message_id=?`).bind(user.id, clientMessageId).first();
        if (existing) return response(request, env, { message: { type: 'message', ...existing } }, 200);
      }
      return response(request, env, { error: 'Message could not be saved.' }, 500);
    }
  }

  return response(request, env, { error: 'Not found.' }, 404);
}

export default {
  async fetch(request, env) {
    try {
      const url = new URL(request.url);
      if (request.headers.get('Upgrade')?.toLowerCase() === 'websocket' && url.pathname === '/ws') {
        const token = url.searchParams.get('token');
        if (!token) return response(request, env, { error: 'WebSocket authentication required.' }, 401);
        const user = await getUser(new Request(request, { headers: { Authorization: `Bearer ${token}` } }), env);
        if (!user) return response(request, env, { error: 'Invalid or expired session.' }, 401);
        const id = env.PRESENCE.idFromName(String(user.id));
        return env.PRESENCE.get(id).fetch(new Request('https://presence/websocket', { headers: { Upgrade: 'websocket', 'X-SEAM-User-Id': String(user.id), 'X-SEAM-Username': user.username } }));
      }
      return await routeApi(request, env);
    } catch (error) {
      console.error(error);
      return response(request, env, { error: 'Internal server error.' }, 500);
    }
  }
};

export class Presence extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx = ctx;
    this.env = env;
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair('ping', 'pong'));
  }

  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === '/websocket') return this.upgrade(request);
    if (url.pathname === '/internal/online') return json({ online: this.ctx.getWebSockets().length > 0 });
    if (url.pathname === '/internal/broadcast' && request.method === 'POST') {
      const payload = await request.text();
      for (const ws of this.ctx.getWebSockets()) {
        try { ws.send(payload); } catch {}
      }
      return json({ ok: true });
    }
    if (url.pathname === '/internal/rate-limit' && request.method === 'POST') {
      const { limit = 30, windowMs = 900000 } = await request.json().catch(() => ({}));
      const now = Date.now();
      const state = (await this.ctx.storage.get('rateLimit')) || { startedAt: now, count: 0 };
      if (now - state.startedAt >= windowMs) { state.startedAt = now; state.count = 0; }
      state.count += 1;
      await this.ctx.storage.put('rateLimit', state);
      return new Response(null, { status: state.count <= limit ? 204 : 429 });
    }
    return new Response('Not found', { status: 404 });
  }

  async upgrade(request) {
    if (request.headers.get('Upgrade')?.toLowerCase() !== 'websocket') return new Response('Expected websocket', { status: 426 });
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    const userId = request.headers.get('X-SEAM-User-Id');
    const username = request.headers.get('X-SEAM-Username');
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({ userId, username });
    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws, message) {
    try {
      const data = JSON.parse(typeof message === 'string' ? message : new TextDecoder().decode(message));
      if (data.type !== 'typing' || !validUsername(data.username)) return;
      const attachment = ws.deserializeAttachment();
      if (!attachment?.username || data.username === attachment.username) return;
      const target = await this.env.DB.prepare('SELECT id FROM users WHERE username=?').bind(data.username).first();
      if (!target) return;
      const targetId = this.env.PRESENCE.idFromName(String(target.id));
      await this.env.PRESENCE.get(targetId).fetch('https://presence/internal/broadcast', {
        method: 'POST', headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ type: 'typing', username: attachment.username, active: Boolean(data.active) })
      });
    } catch {}
  }

  async webSocketClose(ws) {
    try { ws.close(); } catch {}
  }
}
