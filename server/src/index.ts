export interface Env {
  DB: D1Database;
  CHAT_ROOM: DurableObjectNamespace;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({ ok: true, service: "seam-chat-api" });
    }

    if (url.pathname === "/api/v1/realtime") {
      if (request.headers.get("Upgrade") !== "websocket") {
        return Response.json({ error: "WebSocket upgrade required" }, { status: 426 });
      }
      const room = env.CHAT_ROOM.idFromName("default");
      return env.CHAT_ROOM.get(room).fetch(request);
    }

    return Response.json({
      name: "SEAM CHAT API",
      version: "v1",
      status: "online",
    });
  },
};

export class ChatRoom implements DurableObject {
  constructor(private state: DurableObjectState, private env: Env) {}

  async fetch(request: Request): Promise<Response> {
    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    server.accept();

    server.addEventListener("message", (event) => {
      server.send(JSON.stringify({ type: "ack", data: event.data }));
    });

    return new Response(null, { status: 101, webSocket: client });
  }
}
