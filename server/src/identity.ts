import { randomInt } from "node:crypto";

const alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
const pattern = /^PC-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}$/;

export function generatePiCallId(): string {
  let body = "";
  for (let index = 0; index < 8; index += 1) body += alphabet[randomInt(alphabet.length)];
  return `PC-${body.slice(0, 4)}-${body.slice(4)}`;
}

export function normalizePiCallId(raw: string): string | null {
  const compact = raw.trim().toUpperCase().replaceAll("-", "");
  if (!compact.startsWith("PC") || compact.length !== 10) return null;
  const canonical = `PC-${compact.slice(2, 6)}-${compact.slice(6)}`;
  return pattern.test(canonical) ? canonical : null;
}

