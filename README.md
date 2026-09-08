# SEAM CHAT 💜💚

SEAM CHAT is a real-time private web messenger. The backend is Cloudflare-native: **Workers + D1 + Durable Objects**. There is no longer a Node.js/PostgreSQL server in the production architecture.

## Production core

- Real account registration/login
- Per-user password salt + server-side `PASSWORD_PEPPER`
- PBKDF2-SHA-256 password derivation in the Workers runtime
- 30-day server sessions stored as SHA-256 token hashes in D1
- D1 persistence for users, sessions and messages
- One-to-one message history with bounded pagination (50 default, 100 maximum)
- Durable Objects for authenticated real-time WebSockets
- WebSocket Hibernation-compatible architecture for persistent realtime connections
- Sent / delivered / read timestamps
- Read receipts and unread counters
- Online/offline presence
- Live typing indicator
- WebSocket reconnect with exponential backoff
- Real user lookup for starting conversations
- Duplicate message protection with optional client message IDs
- Logout and session invalidation
- Edge-side rate limiting backed by Durable Object state
- Security headers and configurable CORS allow-list
- Health endpoint
- No seeded/demo accounts or fake messages

## Cloudflare architecture

```text
                         Cloudflare
                            │
                   ┌────────┴────────┐
                   │ Cloudflare      │
                   │ Worker API      │
                   └───────┬─────────┘
                           │
             ┌─────────────┼─────────────┐
             │             │             │
          REST API        D1       Durable Objects
             │             │             │
       auth/messages   users/sessions   WebSockets
       presence/etc.     messages       presence
                                         typing
                                         realtime fanout
```

D1 is the source of truth for accounts, sessions and persisted messages. Durable Objects coordinate long-lived WebSocket connections, presence, typing and realtime fan-out. Cloudflare recommends Durable Objects for coordinated WebSocket applications, and its Hibernation API is designed for long-lived connections with lower idle runtime cost. citeturn0search0turn0search6

## Repository layout

```text
/
├── index.html
├── app.js
├── styles.css
├── history.css
├── assets/
├── cloudflare/
│   ├── src/index.js
│   ├── migrations/0001_initial.sql
│   ├── wrangler.toml
│   ├── package.json
│   └── .dev.vars.example
└── .github/workflows/ci.yml
```

The old `backend/` Node/PostgreSQL implementation and Docker Compose stack have been removed so there is one backend architecture instead of two competing implementations.

## Cloudflare setup

### 1. Create the D1 database

From `cloudflare/`:

```bash
npm install
npx wrangler login
npx wrangler d1 create seam-chat-db
```

Cloudflare's current Wrangler flow creates the D1 database and returns the database ID used by the Worker binding. citeturn0search8

Copy the returned ID into `cloudflare/wrangler.toml` in place of `REPLACE_WITH_D1_DATABASE_ID`.

### 2. Apply the schema

```bash
npx wrangler d1 migrations apply seam-chat-db --remote
```

The schema is versioned in `cloudflare/migrations/`; Wrangler tracks applied migrations in D1. citeturn0search4

### 3. Configure secrets

Set a strong random pepper as a Cloudflare Worker secret:

```bash
npx wrangler secret put PASSWORD_PEPPER
```

For production, also set the exact frontend origin:

```bash
npx wrangler secret put CORS_ORIGIN
```

Never commit the real pepper or other production secrets.

### 4. Deploy

```bash
npx wrangler deploy
```

Wrangler is Cloudflare's CLI for managing Worker projects and deployments. citeturn0search9

### 5. Connect the frontend

If the Worker is routed on the same hostname as the frontend, the existing frontend can use its default `location.origin` API base.

If the API is on a separate hostname, set this before `app.js` in `index.html`:

```html
<script>
  window.SEAM_API_BASE = 'https://YOUR-API-HOSTNAME';
</script>
```

The frontend already switches `ws://` to `wss://` automatically for the `/ws` endpoint.

## Local development

```bash
cd cloudflare
npm install
cp .dev.vars.example .dev.vars
npx wrangler d1 migrations apply seam-chat-db --local
npx wrangler dev
```

Do not put production credentials in `.dev.vars` or commit that file.

## CI

GitHub Actions now validates the Cloudflare Worker bundle with Wrangler instead of starting PostgreSQL and the removed Node backend.

## Important deployment note

The repository now contains the complete Cloudflare-native backend implementation and configuration, but creating the actual D1 database and deploying the Worker requires access to your Cloudflare account. The repository intentionally contains a placeholder D1 ID rather than pretending a real Cloudflare resource was created.

## Security notes

- Passwords are never stored plaintext.
- Session tokens are stored only as SHA-256 hashes in D1.
- The browser currently carries the session token for API authentication and the WebSocket handshake. For a later hardening pass, this can be moved to an HttpOnly, Secure cookie/session flow.
- CORS should be restricted to the exact frontend origin in production.
- Attachments remain disabled until secure R2-backed file handling is intentionally added; the UI does not fake uploads.
