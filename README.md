# SEAM CHAT 💜💚

SEAM CHAT is a real-time private web messenger built with a vanilla frontend and a Node.js/PostgreSQL/WebSocket backend.

## What is real now

- Real account registration and login
- Password hashing with per-user salt and server-side pepper
- 30-day server sessions stored as SHA-256 token hashes
- PostgreSQL persistence for users, sessions and messages
- Real one-to-one message history
- Real-time delivery over authenticated WebSockets
- WebSocket reconnect with exponential backoff in the frontend
- Online/offline presence events for connected users
- Duplicate message protection in the frontend
- Real user lookup for starting conversations
- Logout and session invalidation
- API rate limiting and security headers
- CORS configuration for separately hosted frontend/backend
- Docker Compose setup with PostgreSQL
- Automated two-user integration test covering auth errors, message delivery and WebSocket reconnect

## Architecture

```text
Browser
  │
  ├── HTTP / REST ───────► Node.js + Express
  │                           │
  │                           └── PostgreSQL
  │
  └── WebSocket /ws ─────► ws server
                              │
                              └── authenticated live connections
```

## Run locally

1. Copy `backend/.env.example` to `backend/.env` and set a strong `PASSWORD_PEPPER`.
2. Start PostgreSQL and provide `DATABASE_URL`.
3. Start the backend:

```bash
cd backend
npm install
node src/server.js
```

4. Serve the repository root with a static web server. If the frontend and backend are on different origins, set `window.SEAM_API_BASE` in `index.html` to the backend URL and configure `CORS_ORIGIN` on the backend.

## Docker

```bash
docker compose up --build
```

The backend listens on port `3000` and PostgreSQL on `5432` by default.

## Integration test

With PostgreSQL available and the backend running:

```bash
cd backend
npm install
node test/integration.mjs
```

The automated test creates two isolated users and verifies:

1. Invalid authentication returns `401`.
2. Registration works for both users.
3. Duplicate usernames return `409`.
4. Wrong passwords return `401`.
5. A message is persisted and delivered to the second user over WebSocket.
6. Message history contains the delivered message.
7. A disconnected WebSocket can reconnect using the same valid session.
8. The reconnected client receives a new message in real time.

GitHub Actions runs the same integration test on pushes and pull requests targeting `main`.

## Important deployment note

The frontend is intentionally static while the backend is a separate service. Production deployment therefore needs a reachable HTTPS backend and WebSocket endpoint. The frontend must point `SEAM_API_BASE` at that backend, for example `https://api.example.com`; the browser will automatically use `wss://api.example.com/ws` for WebSockets.

There are no seeded/demo users or fake messages.
