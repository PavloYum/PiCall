import { createHmac } from "node:crypto";

export function issueTurnCredentials(piCallId: string, secret: string, now = Date.now()) {
  const ttlSeconds = 60 * 60;
  const expiresAt = Math.floor(now / 1000) + ttlSeconds;
  const username = `${expiresAt}:${piCallId}`;
  const credential = createHmac("sha1", secret).update(username).digest("base64");
  return {
    urls: ["turn:turn.velu-vara.com:443?transport=udp", "turn:turn.velu-vara.com:443?transport=tcp"],
    username,
    credential,
    expiresAt,
  };
}
