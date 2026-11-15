import test from "node:test";
import assert from "node:assert/strict";
import { createDeviceToken, createPairingCode, hashSecret, isSafeDeviceName } from "./security.js";

test("device tokens are high-entropy URL-safe strings", () => {
  const token = createDeviceToken();
  assert.match(token, /^[A-Za-z0-9_-]{40,}$/);
  assert.notEqual(token, createDeviceToken());
});

test("pairing codes are eight characters and omit ambiguous characters", () => {
  const code = createPairingCode();
  assert.match(code, /^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/);
});

test("secret hashes are deterministic and do not equal the input", () => {
  assert.equal(hashSecret("sample"), hashSecret("sample"));
  assert.notEqual(hashSecret("sample"), "sample");
});

test("device names are bounded", () => {
  assert.equal(isSafeDeviceName("My phone"), true);
  assert.equal(isSafeDeviceName(""), false);
  assert.equal(isSafeDeviceName("x".repeat(49)), false);
});
