export interface Env {
  DB: D1Database;
  CHAT_ROOM: DurableObjectNamespace;
}

const SESSION_DAYS = 30;
const PBKDF2_ITERATIONS = 120_000;

function json(data: unknown, status = 200): Response {
  return Response.json(data, {
    status,
    headers: { "Cache-Control": "no-store" },
  });
}

function id(): string {
  return crypto.randomUUID();
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function base64ToBytes(value: string): Uint8Array {
  const binary = atob(value);
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function sha256(value: string): Promise<string> {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return bytesToBase64(new Uint8Array(digest));
}

async function derivePassword(password: string, salt: Uint8Array): Promise<string> {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(password),
    "PBKDF2",
    false,
    ["deriveBits"],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", salt, iterations: PBKDF2_ITERATIONS, hash: "SHA-256" },
    key,
    256,
  );
  return bytesToBase64(new Uint8Array(bits));
}

function validUsername(username: string): boolean {
  return /^[a-zA-Z0-9_]{3,24}$/.test(username);
}

function validPassword(password: string): boolean {
  return password.length >= 8 && password.length <= 128;
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const value = await request.json();
    return value && typeof value === "object" ? value as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

async function createSession(env: Env, userId: string): Promise<string> {
  const rawToken = bytesToBase64(crypto.getRandomValues(new Uint8Array(32)));
  const tokenHash = await sha256(rawToken);
  const now = Date.now();
  const expiresAt = now + SESSION_DAYS * 24 * 60 * 60 * 1000;

  await env.DB.prepare(
    `INSERT INTO sessions (id, user_id, token_hash, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?)`,
  ).bind(id(), userId, tokenHash, now, expiresAt).run();

  return rawToken;
}

async function authenticate(request: Request, env: Env): Promise<{ userId: string; sessionId: string } | null> {
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return null;

  const token = header.slice(7).trim();
  if (!token) return null;

  const tokenHash = await sha256(token);
  const row = await env.DB.prepare(
    `SELECT id AS session_id, user_id
     FROM sessions
     WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
     LIMIT 1`,
  ).bind(tokenHash, Date.now()).first<{ session_id: string; user_id: string }>();

  return row ? { userId: row.user_id, sessionId: row.session_id } : null;
}

async function register(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const displayName = typeof body?.displayName === "string" ? body.displayName.trim() : username;
  const password = typeof body?.password === "string" ? body.password : "";
  const inviteCode = typeof body?.inviteCode === "string" ? body.inviteCode.trim().toUpperCase() : "";

  if (!validUsername(username)) {
    return json({ error: "Username must be 3-24 characters using letters, numbers or _." }, 400);
  }
  if (!validPassword(password)) {
    return json({ error: "Password must be 8-128 characters." }, 400);
  }
  if (!inviteCode) return json({ error: "Invite code is required." }, 400);

  const existing = await env.DB.prepare("SELECT id FROM users WHERE username = ? LIMIT 1")
    .bind(username).first();
  if (existing) return json({ error: "Username is already taken." }, 409);

  const invite = await env.DB.prepare(
    "SELECT code FROM invite_codes WHERE code = ? AND used_by IS NULL LIMIT 1",
  ).bind(inviteCode).first();
  if (!invite) return json({ error: "Invalid or already used invite code." }, 400);

  const salt = crypto.getRandomValues(new Uint8Array(16));
  const passwordHash = await derivePassword(password, salt);
  const userId = id();
  const now = Date.now();

  try {
    await env.DB.batch([
      env.DB.prepare(
        `INSERT INTO users (id, username, display_name, password_hash, password_salt, created_at)
         VALUES (?, ?, ?, ?, ?, ?)`,
      ).bind(userId, username, displayName || username, passwordHash, bytesToBase64(salt), now),
      env.DB.prepare(
        "UPDATE invite_codes SET used_by = ?, used_at = ? WHERE code = ? AND used_by IS NULL",
      ).bind(userId, now, inviteCode),
    ]);
  } catch {
    return json({ error: "Could not create account." }, 500);
  }

  const token = await createSession(env, userId);
  return json({
    user: { id: userId, username, displayName: displayName || username },
    token,
    expiresIn: SESSION_DAYS * 24 * 60 * 60,
  }, 201);
}

async function login(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password : "";

  if (!username || !password) return json({ error: "Username and password are required." }, 400);

  const user = await env.DB.prepare(
    `SELECT id, username, display_name, password_hash, password_salt
     FROM users WHERE username = ? LIMIT 1`,
  ).bind(username).first<{
    id: string;
    username: string;
    display_name: string;
    password_hash: string;
    password_salt: string;
  }>();

  if (!user) return json({ error: "Invalid username or password." }, 401);

  const calculated = await derivePassword(password, base64ToBytes(user.password_salt));
  if (calculated !== user.password_hash) return json({ error: "Invalid username or password." }, 401);

  const token = await createSession(env, user.id);
  return json({
    user: { id: user.id, username: user.username, displayName: user.display_name },
    token,
    expiresIn: SESSION_DAYS * 24 * 60 * 60,
  });
}

async function me(request: Request, env: Env): Promise<Response> {
  const auth = await authenticate(request, env);
  if (!auth) return json({ error: "Unauthorized" }, 401);

  const user = await env.DB.prepare(
    "SELECT id, username, display_name, avatar_url, created_at FROM users WHERE id = ? LIMIT 1",
  ).bind(auth.userId).first();

  return user ? json({ user }) : json({ error: "User not found" }, 404);
}

async function logout(request: Request, env: Env): Promise<Response> {
  const auth = await authenticate(request, env);
  if (!auth) return json({ ok: true });

  await env.DB.prepare("UPDATE sessions SET revoked_at = ? WHERE id = ?")
    .bind(Date.now(), auth.sessionId).run();
  return json({ ok: true });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Headers": "Content-Type, Authorization",
          "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        },
      });
    }

    try {
      if (url.pathname === "/health") {
        return json({ ok: true, service: "seam-chat-api" });
      }

      if (url.pathname === "/api/v1/auth/register" && request.method === "POST") {
        return register(request, env);
      }
      if (url.pathname === "/api/v1/auth/login" && request.method === "POST") {
        return login(request, env);
      }
      if (url.pathname === "/api/v1/auth/me" && request.method === "GET") {
        return me(request, env);
      }
      if (url.pathname === "/api/v1/auth/logout" && request.method === "POST") {
        return logout(request, env);
      }

      if (url.pathname === "/api/v1/realtime") {
        if (request.headers.get("Upgrade") !== "websocket") {
          return json({ error: "WebSocket upgrade required" }, 426);
        }
        const room = env.CHAT_ROOM.idFromName("default");
        return env.CHAT_ROOM.get(room).fetch(request);
      }

      return json({ name: "SEAM CHAT API", version: "v1", status: "online" });
    } catch (error) {
      console.error("SEAM CHAT API error", error);
      return json({ error: "Internal server error" }, 500);
    }
  },
};

export class ChatRoom implements DurableObject {
  constructor(private state: DurableObjectState, private env: Env) {}

  async fetch(request: Request): Promise<Response> {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    server.addEventListener("message", (event) => {
      server.send(JSON.stringify({ type: "ack", data: event.data }));
    });

    return new Response(null, { status: 101, webSocket: client });
  }
}
