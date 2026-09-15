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

test("reserves both participants while a call is active", () => {
  const presence = new Presence();
  assert.equal(presence.startCall("PC-2ABC-9XYZ", "PC-3ABC-8XYZ"), true);
  assert.equal(presence.isBusy("PC-2ABC-9XYZ"), true);
  assert.equal(presence.isBusy("PC-3ABC-8XYZ"), true);
  assert.equal(presence.startCall("PC-4ABC-7XYZ", "PC-3ABC-8XYZ"), false);
  assert.equal(presence.endCall("PC-2ABC-9XYZ"), "PC-3ABC-8XYZ");
  assert.equal(presence.isBusy("PC-3ABC-8XYZ"), false);
});
