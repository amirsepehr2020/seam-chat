# Server

Cloudflare Worker backend foundation for SEAM CHAT.

The production API will expose authentication, conversations, messages, uploads, and realtime WebSocket sessions. Durable Objects will own realtime room state; D1 will persist users, conversations, messages, and membership data.
