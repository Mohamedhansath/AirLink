import { createHash, randomBytes, randomInt } from "node:crypto";

export function hashSecret(value) {
  return createHash("sha256").update(value).digest("hex");
}

export function createDeviceToken() {
  return randomBytes(32).toString("base64url");
}

export function createPairingCode() {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 8 }, () => alphabet[randomInt(alphabet.length)]).join("");
}

export function isSafeDeviceName(value) {
  return typeof value === "string" && value.trim().length >= 1 && value.trim().length <= 48;
}
