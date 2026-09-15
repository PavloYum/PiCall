import { createHmac } from "node:crypto";

export function issueTurnCredentials(piCallId: string, secret: string, now = Date.now()) {
  const ttlSeconds = 60 * 60;
  const expiresAt = Math.floor(now / 1000) + ttlSeconds;
  const username = `${expiresAt}:${piCallId}`;
  const credential = createHmac("sha1", secret).update(username).digest("base64");
  return {
    urls: ["turn:turn.velu-vara.com:443?transport=udp", "turns:turn.velu-vara.com:443?transport=tcp"],
    username,
    credential,
    expiresAt,
  };
}

export async function issueCloudflareTurnCredentials(keyId: string, keyToken: string) {
  const ttl = 60 * 60;
  const response = await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${keyId}/credentials/generate-ice-servers`, {
    method: "POST",
    headers: { authorization: `Bearer ${keyToken}`, "content-type": "application/json" },
    body: JSON.stringify({ ttl }),
  });
  if (!response.ok) throw new Error(`Cloudflare TURN credentials failed: HTTP ${response.status}`);
  const body = await response.json() as { iceServers?: Array<{ urls?: string[]; username?: string; credential?: string }> };
  const turn = body.iceServers?.find(server => server.username && server.credential);
  if (!turn?.urls?.length || !turn.username || !turn.credential) throw new Error("Cloudflare TURN returned an invalid response");
  return { urls: turn.urls, username: turn.username, credential: turn.credential, expiresAt: Math.floor(Date.now() / 1000) + ttl };
}
