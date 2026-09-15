import type { IncomingMessage, ServerResponse } from "node:http";
import type { Database } from "./database.js";
import { generatePiCallId, normalizePiCallId } from "./identity.js";
import { hashPassword, issueToken, verifyPassword, verifyToken } from "./security.js";
import type { Presence } from "./signaling.js";
import { issueCloudflareTurnCredentials, issueTurnCredentials } from "./turn.js";

type Dependencies = {
  database: Database;
  jwtSecret: string;
  turnSecret: string;
  cloudflareTurnKeyId?: string;
  cloudflareTurnKeyToken?: string;
  presence: Presence;
};

export function createHttpHandler(deps: Dependencies) {
  return async (request: IncomingMessage, response: ServerResponse): Promise<void> => {
    try {
      if (request.method === "GET" && request.url === "/health") {
        sendJson(response, 200, { status: "ok" });
        return;
      }
      if (request.method === "POST" && request.url === "/v1/register") {
        const body = await readJson(request);
        const displayName = typeof body.displayName === "string" ? body.displayName.trim() : "";
        const password = typeof body.password === "string" ? body.password : "";
        if (displayName.length < 2 || displayName.length > 64 || password.length < 10 || password.length > 128) {
          sendJson(response, 400, { error: "displayName must be 2-64 chars and password 10-128 chars" });
          return;
        }
        const passwordHash = await hashPassword(password);
        for (let attempt = 0; attempt < 5; attempt += 1) {
          const piCallId = generatePiCallId();
          if (await deps.database.createUser({ piCallId, displayName, passwordHash })) {
            sendJson(response, 201, { piCallId, displayName, token: issueToken(piCallId, deps.jwtSecret) });
            return;
          }
        }
        sendJson(response, 503, { error: "could not allocate PiCall ID" });
        return;
      }
      if (request.method === "POST" && request.url === "/v1/login") {
        const body = await readJson(request);
        const piCallId = typeof body.piCallId === "string" ? normalizePiCallId(body.piCallId) : null;
        const password = typeof body.password === "string" ? body.password : "";
        const user = piCallId ? await deps.database.findUser(piCallId) : null;
        if (!user || !await verifyPassword(password, user.passwordHash)) {
          sendJson(response, 401, { error: "invalid credentials" });
          return;
        }
        sendJson(response, 200, { piCallId: user.piCallId, displayName: user.displayName, token: issueToken(user.piCallId, deps.jwtSecret) });
        return;
      }
      if (request.method === "GET" && request.url === "/v1/participants") {
        const piCallId = authenticate(request, deps.jwtSecret);
        const users = await deps.database.listUsers(piCallId);
        const participants = users
          .map(user => ({ ...user, online: deps.presence.isOnline(user.piCallId) }))
          .sort((left, right) => Number(right.online) - Number(left.online) || left.displayName.localeCompare(right.displayName));
        sendJson(response, 200, { participants });
        return;
      }
      if (request.method === "GET" && request.url === "/v1/turn-credentials") {
        const piCallId = authenticate(request, deps.jwtSecret);
        const credentials = deps.cloudflareTurnKeyId && deps.cloudflareTurnKeyToken
          ? await issueCloudflareTurnCredentials(deps.cloudflareTurnKeyId, deps.cloudflareTurnKeyToken)
          : issueTurnCredentials(piCallId, deps.turnSecret);
        sendJson(response, 200, credentials);
        return;
      }
      sendJson(response, 404, { error: "not found" });
    } catch (error) {
      if (error instanceof RequestError) sendJson(response, error.status, { error: error.message });
      else {
        console.error(error);
        sendJson(response, 500, { error: "internal server error" });
      }
    }
  };
}

function authenticate(request: IncomingMessage, secret: string): string {
  const authorization = request.headers.authorization ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  const payload = verifyToken(token, secret);
  if (!payload) throw new RequestError(401, "invalid token");
  return payload.sub;
}

class RequestError extends Error {
  constructor(readonly status: number, message: string) { super(message); }
}

async function readJson(request: IncomingMessage): Promise<Record<string, unknown>> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of request) {
    const buffer = Buffer.from(chunk);
    size += buffer.length;
    if (size > 16_384) throw new RequestError(413, "request too large");
    chunks.push(buffer);
  }
  try {
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
    return parsed as Record<string, unknown>;
  } catch {
    throw new RequestError(400, "invalid JSON");
  }
}

function sendJson(response: ServerResponse, status: number, body: object): void {
  const encoded = JSON.stringify(body);
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "content-length": Buffer.byteLength(encoded) });
  response.end(encoded);
}
