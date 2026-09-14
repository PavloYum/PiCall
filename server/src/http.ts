import type { IncomingMessage, ServerResponse } from "node:http";
import type { Database } from "./database.js";
import { generatePiCallId, normalizePiCallId } from "./identity.js";
import { hashPassword, issueToken, verifyPassword } from "./security.js";

type Dependencies = { database: Database; jwtSecret: string };

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

