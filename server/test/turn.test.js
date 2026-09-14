import assert from "node:assert/strict";
import test from "node:test";
import { issueTurnCredentials } from "../dist/turn.js";

test("issues deterministic, expiring TURN credentials", () => {
  const result = issueTurnCredentials("PC-2ABC-9XYZ", "a-turn-secret-with-at-least-32-characters", 1_000_000);
  assert.equal(result.username, "4600:PC-2ABC-9XYZ");
  assert.equal(result.expiresAt, 4600);
  assert.equal(result.urls.length, 2);
  assert.ok(result.credential.length > 20);
});
