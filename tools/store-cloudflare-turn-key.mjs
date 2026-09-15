import fs from "node:fs";

const responsePath = process.argv[2];
const envPath = process.argv[3] ?? ".env";
if (!responsePath) throw new Error("Usage: node tools/store-cloudflare-turn-key.mjs <response.json> [.env]");

const response = JSON.parse(fs.readFileSync(responsePath, "utf8"));
const id = response.result?.uid;
const token = response.result?.secret ?? response.result?.key;
if (!response.success || !id || !token) throw new Error("Cloudflare did not return a TURN key");

let env = fs.readFileSync(envPath, "utf8");
for (const [name, value] of [["CF_TURN_KEY_ID", id], ["CF_TURN_KEY_TOKEN", token]]) {
  const line = `${name}=${value}`;
  const pattern = new RegExp(`^${name}=.*$`, "m");
  env = pattern.test(env) ? env.replace(pattern, line) : `${env.trimEnd()}\n${line}\n`;
}
fs.writeFileSync(envPath, env, { mode: 0o600 });
console.log(`Stored Cloudflare TURN key ${id}`);
