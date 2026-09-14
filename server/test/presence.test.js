import assert from "node:assert/strict";
import test from "node:test";
import { Presence } from "../dist/signaling.js";

test("tracks connected participants", () => {
  const presence = new Presence();
  assert.equal(presence.isOnline("PC-2ABC-9XYZ"), false);
  presence.connect("PC-2ABC-9XYZ");
  assert.equal(presence.isOnline("PC-2ABC-9XYZ"), true);
  presence.disconnect("PC-2ABC-9XYZ");
  assert.equal(presence.isOnline("PC-2ABC-9XYZ"), false);
});
