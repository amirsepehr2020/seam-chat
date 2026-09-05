# SEAM CHAT 🚀

Private, fast Android messenger built to be fully self-hosted and controlled from this GitHub repository.

## Architecture

- **Android:** Kotlin, Jetpack Compose, Material 3
- **Local session/cache:** Android storage + Room-ready architecture
- **Network:** Retrofit + OkHttp + WebSocket
- **Backend:** Kotlin + Ktor + Netty
- **Database:** PostgreSQL
- **Deployment:** Docker / Docker Compose
- **Automation:** GitHub Actions

There is no Cloudflare dependency in the active architecture.

## Repository

```text
app/                 Android client
backend/             Ktor API + WebSocket server
database/            SQL seed/migration assets
.github/workflows/   CI and build automation
docker-compose.yml   PostgreSQL + backend stack
```

## Backend API

```text
GET  /health
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
POST /api/v1/auth/logout
GET  /api/v1/conversations/{id}/messages
POST /api/v1/conversations/{id}/messages
WS   /api/v1/realtime/{conversationId}
```

## Local development

1. Install Docker.
2. Set a strong `POSTGRES_PASSWORD` in your environment.
3. Run `docker compose up --build`.
4. Backend listens on port `8080`.
5. The Android debug build currently targets the emulator backend at `http://10.0.2.2:8080/`.

For a physical phone, set `BuildConfig.API_BASE_URL` in `app/build.gradle.kts` to the reachable address of your self-hosted server before building.

## Security

- Passwords are stored as PBKDF2-HMAC-SHA256 hashes with per-user salts.
- Sessions use random opaque tokens; only their SHA-256 hashes are persisted.
- Invite codes are single-use.
- Authentication is required for message reads/writes and WebSocket connections.
- Production secrets must never be committed to Git.
