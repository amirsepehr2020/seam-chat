# SEAM CHAT

Private, fast Android messenger. Android app + Cloudflare-ready backend.

## Architecture
- Android: Kotlin, Jetpack Compose, Material 3, MVVM
- Local: Room
- Network: Retrofit + WebSocket
- Backend: Cloudflare Workers + Durable Objects + D1 (planned)

## Modules
- `app/` Android client
- `server/` Cloudflare Worker backend

This repository contains the foundation for SEAM CHAT. Secrets and production credentials must never be committed.
