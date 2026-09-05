# SEAM CHAT Release Checklist

## Core
- [x] Android Compose shell
- [x] Login / Register / Invite Code
- [x] Session token handling
- [x] REST message API
- [x] WebSocket realtime channel
- [x] Room message cache
- [x] Chat screen wired to ChatViewModel
- [x] Backend PostgreSQL schema and migrations

## Before public beta
- [ ] Real conversation create/list API and UI
- [ ] Reliable WebSocket reconnect with backoff
- [ ] Delivery and read receipts
- [ ] Online presence and typing indicators
- [ ] Push notifications
- [ ] Media upload/download with size and type limits
- [ ] Attachment picker and media viewer
- [ ] Message edit/delete/reply/reaction
- [ ] Secure token storage and session recovery hardening
- [ ] HTTPS/TLS on production host
- [ ] Database backups and restore test
- [ ] Rate limiting and abuse protection
- [ ] Automated unit/API/WebSocket tests
- [ ] Android release signing and Play/Bazaar release configuration

## Production infrastructure
- [ ] VPS provisioned
- [ ] Docker Compose deployed
- [ ] PostgreSQL persistent volume configured
- [ ] Reverse proxy + TLS configured
- [ ] GitHub Actions deploy workflow configured with repository secrets
- [ ] Monitoring/health checks configured
