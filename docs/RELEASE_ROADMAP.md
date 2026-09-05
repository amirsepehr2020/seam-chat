# SEAM Chat — Release Roadmap

This document is the execution plan from the current development state to SEAM Chat v1.0.

## 1. Message Actions
- Reply
- Edit
- Delete
- Forward
- Reactions
- Multi-select

## 2. Media
- Image preview
- Video player
- File download
- Upload retry
- Client-side compression
- Media message rendering

## 3. Voice Messages
- Record
- Upload
- Playback
- Cancel/retry
- Duration metadata

## 4. Notifications
- Foreground/background handling
- Tap notification → conversation
- Unread counts
- Notification grouping
- Notification dismissal/read synchronization

## 5. Security Hardening
- HTTPS/TLS only in production
- Rate limiting
- Session expiration/revocation
- Secure token storage
- File type/size/content validation
- Path traversal protection
- Input validation
- Production secrets outside source control

## 6. Testing
- Android unit tests
- Repository/ViewModel tests
- API integration tests
- WebSocket tests
- Database/migration tests
- Media upload tests
- Release smoke test

## 7. Production
- Dockerized backend
- VPS deployment
- PostgreSQL backup/restore
- TLS certificates
- Health checks
- Logging/monitoring
- Resource limits
- Production environment configuration

## 8. Release
- Release signing configuration
- AAB/APK build
- Internal/beta testing
- Crash and regression fixes
- Final privacy/security review
- SEAM Chat v1.0

## Release gate
No v1.0 release until authentication, messaging, realtime, media, notifications, security, backups, automated tests, and production smoke tests pass.
