import assert from "node:assert/strict";
import test from "node:test";
import { hashPassword, issueToken, verifyPassword, verifyToken } from "../dist/security.js";

test("password hashes verify", async () => {
  const encoded = await hashPassword("correct horse battery staple");
  assert.equal(await verifyPassword("correct horse battery staple", encoded), true);
  assert.equal(await verifyPassword("wrong password", encoded), false);
});

test("signed token verifies and rejects tampering", () => {
  const secret = "a-development-secret-with-32-characters";
  const token = issueToken("PC-2ABC-9XYZ", secret, 60);
  assert.equal(verifyToken(token, secret)?.sub, "PC-2ABC-9XYZ");
  assert.equal(verifyToken(`${token}x`, secret), null);
});
