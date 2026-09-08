# SEAM CHAT 💜💚

SEAM CHAT is a real-time private web messenger. The frontend is hosted on Cloudflare, while the real backend runs on the owner's laptop with Node.js and WebSockets. This keeps the setup simple and avoids a Cloudflare-native backend.

## Architecture

```text
Browser
   │
   ├── Cloudflare → static frontend
   │
   └── HTTPS/WSS → Cloudflare Tunnel → laptop
                                      │
                                   Node.js
                                      │
                              local JSON database
```

## Real features

- Real account registration/login
- Per-user password salt + server-side pepper
- PBKDF2-SHA-256 password hashing
- Persistent sessions
- Real one-to-one messages and history
- Pagination for older messages
- Delivered/read timestamps
- Read receipts and unread counters
- Online/offline presence
- Live typing indicator
- WebSocket reconnect support in the frontend
- Real user search and new-chat flow
- Duplicate message protection
- Logout and session invalidation
- API and message rate limiting
- Security headers and configurable CORS
- Health endpoint at `/health`
- No seeded/demo accounts or fake messages

## Repository layout

```text
/
├── index.html
├── app.js
├── styles.css
├── history.css
├── assets/
├── backend/
│   ├── server.js
│   ├── package.json
│   ├── .env.example
│   └── .gitignore
└── START-SEAM-BACKEND.bat
```

The previous Cloudflare Worker, D1, Durable Objects and Wrangler backend have been removed. Cloudflare is now used for the frontend only.

## Laptop setup

### 1. Install Node.js

Install the current Node.js LTS release on Windows. Verify that `node` and `npm` are available.

### 2. Get the repository

Download/clone this repository onto the laptop. No Cloudflare CLI is required.

### 3. Start the backend

Double-click:

```text
START-SEAM-BACKEND.bat
```

The launcher installs dependencies the first time and starts the server on port `8787`.

The local health check is:

```text
http://localhost:8787/health
```

### 4. Set a real password pepper

Before real use, create an environment variable named `PASSWORD_PEPPER` with a long random secret. Do not commit the secret to GitHub.

The one-click launcher is intentionally simple for first setup. For production-like use, set `PASSWORD_PEPPER` as a Windows user/system environment variable before starting the launcher.

### 5. Keep the laptop reachable

For internet access, expose the backend through a Cloudflare Tunnel pointing at:

```text
http://localhost:8787
```

Use the resulting HTTPS hostname as the frontend's `window.SEAM_API_BASE` value in `index.html`. The frontend automatically converts the `/ws` URL to `wss://` when the API hostname uses HTTPS.

### 6. Keep Windows awake with the lid closed

Set Windows' lid-close action to **Do nothing** while plugged in. The laptop must remain powered on and connected to the internet for the backend to remain available.

## Data

The laptop backend stores its persistent data in:

```text
backend/data/seam-chat.json
```

That directory is intentionally ignored by Git. Back up this file if the laptop is the only copy of the chat data.

For a future production release, the same API can be moved to a VPS and the storage layer can be upgraded to PostgreSQL without redesigning the frontend API.

## Security notes

- Passwords are never stored in plaintext.
- Session tokens are stored only as hashes on disk.
- Never commit `PASSWORD_PEPPER` or `backend/data/`.
- Keep the tunnel protected and use HTTPS/WSS publicly.
- The current frontend uses bearer tokens in browser storage; moving authentication to secure HttpOnly cookies is a recommended future hardening step.
- Attachments remain disabled until secure file storage is intentionally implemented; the UI does not fake uploads.
