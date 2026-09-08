# SEAM CHAT 💜💚

SEAM CHAT is a real-time private web messenger with a static frontend and a Node.js/PostgreSQL/WebSocket backend.

## Production-ready core

- Real account registration/login with per-user password salt and server-side pepper
- 30-day server sessions stored as SHA-256 token hashes
- PostgreSQL persistence for users, sessions and messages
- One-to-one message history with bounded pagination (50 by default, 100 maximum)
- Real-time authenticated WebSocket delivery
- Sent / delivered / read message timestamps
- Read receipts and unread counters
- Online/offline presence
- Live typing indicator with server-side expiry
- WebSocket reconnect with exponential backoff
- Real user lookup for starting conversations
- Duplicate message protection in the frontend
- Logout and session invalidation
- API rate limiting and security headers
- CORS allow-list support for a Cloudflare-hosted frontend
- PostgreSQL connection pooling and startup health check
- Expired-session cleanup
- Docker Compose deployment setup
- Automated two-user integration tests for auth, messaging, delivery, read receipts, typing, pagination and reconnect
- No seeded/demo accounts or fake messages

## Architecture

```text
Cloudflare / Static Frontend
          │
          ├── HTTPS REST ─────► Node.js + Express ─────► PostgreSQL
          │
          └── WSS /ws ────────► Authenticated WebSocket server
                                      │
                                      └── live presence / typing / messages
```

## Local setup

1. Copy `backend/.env.example` to `backend/.env` and set a long random `PASSWORD_PEPPER`.
2. Start PostgreSQL and set `DATABASE_URL`.
3. Start the backend:

```bash
cd backend
npm install
npm start
```

4. Serve the repository root with a static web server. If the frontend and backend are on different origins, set `window.SEAM_API_BASE` in `index.html` to the HTTPS backend URL and set `CORS_ORIGIN` to the exact frontend origin(s).

## Docker

```bash
docker compose up --build
```

The backend listens on port `3000` and PostgreSQL on `5432` by default.

## Integration tests

With PostgreSQL available and the backend running:

```bash
cd backend
npm install
npm run test:integration
```

The test creates isolated Alice/Bob accounts and verifies invalid auth, registration, duplicate usernames, wrong passwords, WebSocket delivery, delivery timestamps, read receipts, pagination, typing events, disconnect/reconnect, and post-reconnect messaging.

GitHub Actions runs the integration suite on pushes and pull requests targeting `main`.

## Production deployment checklist

- Use HTTPS/WSS only.
- Set a strong random `PASSWORD_PEPPER` outside the repository.
- Set `CORS_ORIGIN` to the exact Cloudflare frontend origin; do not use `*` in production.
- Use a managed PostgreSQL instance with backups and TLS.
- Put the backend behind a reverse proxy/load balancer with WebSocket support.
- Keep database credentials and deployment secrets out of Git.
- Monitor `/health`, application errors and database capacity.
- The current WebSocket authentication uses a short-lived server session token in the connection URL. For a higher-security deployment, move browser authentication to an HttpOnly, Secure cookie or an equivalent WebSocket authentication mechanism before exposing the service broadly.

The GitHub repository is source code, not the live account database. A private repository can later be used for encrypted backups/audit artifacts without storing plaintext passwords or live chat data in Git history.
