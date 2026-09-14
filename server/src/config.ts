export interface Config {
  port: number;
  databaseUrl: string;
  jwtSecret: string;
  turnSecret: string;
}

export function readConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const port = Number(env.PORT ?? "8080");
  const databaseUrl = env.DATABASE_URL;
  const jwtSecret = env.JWT_SECRET;
  const turnSecret = env.TURN_SECRET;

  if (!Number.isInteger(port) || port < 1 || port > 65535) throw new Error("PORT must be valid");
  if (!databaseUrl) throw new Error("DATABASE_URL is required");
  if (!jwtSecret || jwtSecret.length < 32) throw new Error("JWT_SECRET must contain at least 32 characters");
  if (!turnSecret || turnSecret.length < 32) throw new Error("TURN_SECRET must contain at least 32 characters");
  return { port, databaseUrl, jwtSecret, turnSecret };
}
