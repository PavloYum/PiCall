import assert from "node:assert/strict";
import test from "node:test";
import { generatePiCallId, normalizePiCallId } from "../dist/identity.js";

test("generates valid IDs", () => {
  for (let index = 0; index < 100; index += 1) assert.equal(normalizePiCallId(generatePiCallId()) !== null, true);
});

test("normalizes user input", () => assert.equal(normalizePiCallId("pc2abc9xyz"), "PC-2ABC-9XYZ"));
test("rejects ambiguous characters", () => assert.equal(normalizePiCallId("PC-1ILO-2ABC"), null));

