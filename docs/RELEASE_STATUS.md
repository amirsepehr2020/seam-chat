# SEAM Chat — Release Status

## Current phase
Core messenger + realtime + offline cache + conversation + initial media + notification infrastructure.

## Remaining release-critical work
1. Message actions: reply/edit/delete/forward/reactions.
2. Media completion: preview, video playback, download, retry, compression.
3. Voice messages: record/upload/playback.
4. Notifications: background delivery, tap-to-chat navigation, unread/grouping synchronization.
5. Security hardening: TLS, rate limits, session hardening, secure token storage, upload validation, production secrets.
6. Automated tests: Android, API, WebSocket, database, media.
7. Production: VPS, Docker, PostgreSQL backup/restore, TLS, monitoring.
8. Release: signing, AAB/APK, beta, regression fixes, final security review.

## Definition of done
SEAM Chat v1.0 is considered release-ready only when the release gate in `docs/RELEASE_ROADMAP.md` is satisfied and a clean release build passes smoke tests against the production-like backend.
