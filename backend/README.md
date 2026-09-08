# SEAM CHAT Backend

Production-oriented backend foundation for SEAM CHAT.

## Stack

- Node.js + Express
- PostgreSQL
- `ws` WebSocket server
- Environment-based configuration
- Docker Compose for local PostgreSQL

## API

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/me`
- `GET /api/users/search?username=`
- `GET /api/chats`
- `GET /api/messages/:username`
- `POST /api/messages`
- `GET /health`
- WebSocket: `/ws?token=...`

The backend is intentionally free of seeded/demo users and messages. All chat data must come from authenticated users and PostgreSQL.
