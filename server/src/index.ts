import { createServer } from "node:http";
import { readConfig } from "./config.js";
import { Database } from "./database.js";
import { createHttpHandler } from "./http.js";
import { attachSignaling } from "./signaling.js";

const config = readConfig();
const database = new Database(config.databaseUrl);
await database.migrate();

const server = createServer(createHttpHandler({ database, jwtSecret: config.jwtSecret }));
const signaling = attachSignaling(server, config.jwtSecret);
server.listen(config.port, "0.0.0.0", () => console.log(`PiCall server listening on :${config.port}`));

async function shutdown(): Promise<void> {
  signaling.close();
  server.close();
  await database.close();
}
process.once("SIGINT", shutdown);
process.once("SIGTERM", shutdown);

