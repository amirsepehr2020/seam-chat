# Telegram-inspired SEAM CHAT architecture

SEAM CHAT is not a fork of Telegram and does not copy Telegram source code. This document records product and architecture ideas observed in the public Telegram Android repository and adapted independently for SEAM CHAT.

## Patterns worth adopting

### 1. Central notification coordinator
Telegram uses a dedicated notification controller rather than scattering notification logic through screens. SEAM CHAT follows the same architectural idea with one notification coordinator responsible for channels, grouping, suppression while the active conversation is open, and notification actions.

### 2. Application-level services
Long-lived responsibilities such as realtime connectivity, notification handling, and account/session state should live above individual Compose screens. Screens observe state; they do not own the socket lifecycle.

### 3. Event-driven messaging
Realtime events should be normalized into a small internal event model before reaching UI state. This keeps protocol changes isolated from Compose.

### 4. Media-first message model
Messages should distinguish text, image, video, audio, and file payloads. UI components can then render the appropriate preview without changing the transport layer.

### 5. Offline-first cache
The local database remains the source for immediate rendering while network synchronization reconciles newer server state.

## SEAM CHAT-specific rules

- Keep the backend self-hosted; Cloudflare is not required.
- Never copy Telegram GPL source into SEAM CHAT unless the project is intentionally relicensed and the license obligations are satisfied.
- Reimplement concepts independently in Kotlin/Compose.
- Keep notification, realtime, sync, and media responsibilities behind interfaces so they can be tested without Android UI.
- Do not put secrets or production credentials in the repository.

## Release target

Before v1.0, the architecture must support reconnect/backoff, unread counts, notification suppression for the currently open conversation, notification channels, media previews, read receipts, and deterministic synchronization after reconnect.
