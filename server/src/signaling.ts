import type { Server as HttpServer, IncomingMessage } from "node:http";
import { WebSocket, WebSocketServer } from "ws";
import { normalizePiCallId } from "./identity.js";
import { verifyToken } from "./security.js";

const allowedTypes = new Set(["call", "accept", "reject", "offer", "answer", "ice", "hangup"]);

export class Presence {
  private readonly online = new Set<string>();
  isOnline(piCallId: string): boolean { return this.online.has(piCallId); }
  connect(piCallId: string): void { this.online.add(piCallId); }
  disconnect(piCallId: string): void { this.online.delete(piCallId); }
}

export function attachSignaling(server: HttpServer, jwtSecret: string, presence: Presence): WebSocketServer {
  const sockets = new Map<string, WebSocket>();
  const webSockets = new WebSocketServer({ noServer: true, maxPayload: 64 * 1024 });

  server.on("upgrade", (request, socket, head) => {
    const url = new URL(request.url ?? "/", "http://localhost");
    if (url.pathname !== "/v1/signaling") { socket.destroy(); return; }
    const payload = verifyToken(url.searchParams.get("token") ?? "", jwtSecret);
    if (!payload) { socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n"); socket.destroy(); return; }
    (request as AuthenticatedRequest).piCallId = payload.sub;
    webSockets.handleUpgrade(request, socket, head, ws => webSockets.emit("connection", ws, request));
  });

  webSockets.on("connection", (ws, request: AuthenticatedRequest) => {
    const sender = request.piCallId;
    sockets.get(sender)?.close(4001, "replaced by a new connection");
    sockets.set(sender, ws);
    presence.connect(sender);
    ws.send(JSON.stringify({ type: "ready", piCallId: sender }));
    broadcast(sockets, sender, { type: "presence", piCallId: sender, online: true });

    ws.on("message", data => {
      let message: Record<string, unknown>;
      try { message = JSON.parse(data.toString()) as Record<string, unknown>; } catch { ws.close(1003, "invalid JSON"); return; }
      const type = typeof message.type === "string" ? message.type : "";
      const recipient = typeof message.to === "string" ? normalizePiCallId(message.to) : null;
      if (!allowedTypes.has(type) || !recipient) { ws.send(JSON.stringify({ type: "error", error: "invalid signaling message" })); return; }
      const target = sockets.get(recipient);
      if (!target || target.readyState !== WebSocket.OPEN) { ws.send(JSON.stringify({ type: "unavailable", piCallId: recipient })); return; }
      target.send(JSON.stringify({ ...message, type, from: sender, to: recipient }));
    });
    ws.on("close", () => {
      if (sockets.get(sender) !== ws) return;
      sockets.delete(sender);
      presence.disconnect(sender);
      broadcast(sockets, sender, { type: "presence", piCallId: sender, online: false });
    });
  });
  return webSockets;
}

interface AuthenticatedRequest extends IncomingMessage { piCallId: string }

function broadcast(sockets: Map<string, WebSocket>, except: string, message: object): void {
  const encoded = JSON.stringify(message);
  for (const [piCallId, socket] of sockets) {
    if (piCallId !== except && socket.readyState === WebSocket.OPEN) socket.send(encoded);
  }
}
