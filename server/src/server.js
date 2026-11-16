import http from "node:http";
import { randomUUID } from "node:crypto";
import express from "express";
import cors from "cors";
import helmet from "helmet";
import { WebSocketServer, WebSocket } from "ws";
import { createDeviceToken, createPairingCode, hashSecret, isSafeDeviceName } from "./security.js";

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });
const PORT = Number(process.env.PORT || 8080);
const PAIRING_TTL_MS = Math.max(60, Number(process.env.PAIRING_TTL_SECONDS || 300)) * 1000;
const allowedOrigins = (process.env.ALLOWED_ORIGINS || "http://localhost:3000").split(",").map((v) => v.trim()).filter(Boolean);

// Prototype storage: intentionally in-memory. Use a durable database and managed identity
// provider before production; registrations and pairings are lost when this process restarts.
const devices = new Map();       // deviceId -> { name, tokenHash, createdAt }
const sockets = new Map();       // deviceId -> websocket
const pairingCodes = new Map();  // codeHash -> { ownerId, expiresAt }
const pending = new Map();       // requestId -> { ownerId, requesterId, expiresAt }
const paired = new Set();        // sorted device-id pair key
const sessions = new Map();      // pair key -> { approvedBy, requesterId, startedAt }
const pendingSessions = new Map(); // requestId -> { ownerId, requesterId, expiresAt }
const configuredSessionRequestTtl = Number(process.env.SESSION_REQUEST_TTL_SECONDS || 60);
const SESSION_REQUEST_TTL_MS =
  (Number.isFinite(configuredSessionRequestTtl)
    ? Math.min(300, Math.max(30, configuredSessionRequestTtl))
    : 60) * 1000;

const pairKey = (a, b) => [a, b].sort().join(":");
const send = (ws, data) => {
  if (ws?.readyState === WebSocket.OPEN) ws.send(JSON.stringify(data));
};
const error = (ws, code, message) => send(ws, { type: "error", code, message });

app.disable("x-powered-by");
app.use(helmet());
app.use(cors({
  origin(origin, callback) {
    if (!origin || allowedOrigins.includes(origin)) return callback(null, true);
    return callback(new Error("Origin not allowed"));
  },
  methods: ["GET", "POST"],
  allowedHeaders: ["Content-Type", "Authorization"]
}));
app.use(express.json({ limit: "8kb" }));

const buckets = new Map();
function rateLimit(req, res, next) {
  const now = Date.now();
  const key = req.ip || "unknown";
  const bucket = buckets.get(key) || { start: now, count: 0 };
  if (now - bucket.start >= 60_000) { bucket.start = now; bucket.count = 0; }
  bucket.count += 1;
  buckets.set(key, bucket);
  const max = Number(process.env.MAX_REQUESTS_PER_MINUTE || 60);
  if (bucket.count > max) return res.status(429).json({ error: "rate_limited" });
  next();
}
app.use(rateLimit);

function authenticate(req, res, next) {
  const token = req.get("authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return res.status(401).json({ error: "unauthorized" });
  const tokenHash = hashSecret(token);
  const entry = [...devices.entries()].find(([, value]) => value.tokenHash === tokenHash);
  if (!entry) return res.status(401).json({ error: "unauthorized" });
  req.deviceId = entry[0];
  next();
}

app.get("/health", (_req, res) => res.json({ ok: true, service: "airlink-signaling", mode: "prototype" }));

app.post("/v1/devices/register", (req, res) => {
  const { deviceName } = req.body || {};
  if (!isSafeDeviceName(deviceName)) return res.status(400).json({ error: "invalid_device_name" });
  const deviceId = cryptoRandomId();
  const token = createDeviceToken();
  devices.set(deviceId, { name: deviceName.trim(), tokenHash: hashSecret(token), createdAt: Date.now() });
  // The token is returned once. Store it securely on-device; never log it.
  res.status(201).json({ deviceId, deviceToken: token });
});

app.get("/v1/devices/me", authenticate, (req, res) => {
  const device = devices.get(req.deviceId);
  res.json({ deviceId: req.deviceId, deviceName: device.name, createdAt: device.createdAt });
});

function cryptoRandomId() {
  return randomUUID();
}

server.on("upgrade", (req, socket, head) => {
  const origin = req.headers.origin;
  if (origin && !allowedOrigins.includes(origin)) {
    socket.write("HTTP/1.1 403 Forbidden\r\n\r\n");
    socket.destroy();
    return;
  }
  const url = new URL(req.url || "/", "http://localhost");
  if (url.pathname !== "/ws") {
    socket.write("HTTP/1.1 404 Not Found\r\n\r\n");
    socket.destroy();
    return;
  }
  const token = req.headers.authorization?.replace(/^Bearer\s+/i, "");
  const tokenHash = token ? hashSecret(token) : "";
  const deviceEntry = [...devices.entries()].find(([, value]) => value.tokenHash === tokenHash);
  if (!deviceEntry) {
    socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => {
    ws.deviceId = deviceEntry[0];
    wss.emit("connection", ws);
  });
});

wss.on("connection", (ws) => {
  const deviceId = ws.deviceId;
  const old = sockets.get(deviceId);
  if (old && old !== ws) old.close(4001, "Replaced by a newer connection");
  sockets.set(deviceId, ws);
  send(ws, { type: "connected", deviceId });

  ws.on("message", (raw) => {
    let message;
    try { message = JSON.parse(raw.toString()); } catch { return error(ws, "invalid_json", "Message must be valid JSON."); }
    if (!message || typeof message.type !== "string") return error(ws, "invalid_message", "A message type is required.");

    if (message.type === "pair:create") {
      const code = createPairingCode();
      pairingCodes.set(hashSecret(code), { ownerId: deviceId, expiresAt: Date.now() + PAIRING_TTL_MS });
      return send(ws, { type: "pair:code", code, expiresInSeconds: Math.floor(PAIRING_TTL_MS / 1000) });
    }

    if (message.type === "pair:join") {
      const code = typeof message.code === "string" ? message.code.trim().toUpperCase() : "";
      const key = hashSecret(code);
      const record = pairingCodes.get(key);
      if (!record || record.expiresAt < Date.now() || record.ownerId === deviceId) {
        pairingCodes.delete(key);
        return error(ws, "invalid_pairing_code", "Code is invalid or expired.");
      }
      pairingCodes.delete(key); // one-time use, even if approval is declined
      const requestId = cryptoRandomId();
      pending.set(requestId, { ownerId: record.ownerId, requesterId: deviceId, expiresAt: Date.now() + 60_000 });
      send(sockets.get(record.ownerId), { type: "pair:approval_required", requestId, requester: devices.get(deviceId)?.name || "Device" });
      return send(ws, { type: "pair:waiting", requestId, message: "Waiting for the other device to approve." });
    }

    if (message.type === "pair:approve" || message.type === "pair:deny") {
      const request = pending.get(message.requestId);
      if (!request || request.ownerId !== deviceId || request.expiresAt < Date.now()) {
        pending.delete(message.requestId);
        return error(ws, "invalid_request", "Pairing request is no longer valid.");
      }
      pending.delete(message.requestId);
      if (message.type === "pair:deny") {
        send(sockets.get(request.requesterId), { type: "pair:denied" });
        return send(ws, { type: "pair:denied" });
      }
      paired.add(pairKey(request.ownerId, request.requesterId));
      send(sockets.get(request.requesterId), { type: "pair:approved", peerDeviceId: request.ownerId });
      return send(ws, { type: "pair:approved", peerDeviceId: request.requesterId });
    }

    if (message.type === "session:request") {
      const peerId = typeof message.peerDeviceId === "string" ? message.peerDeviceId : "";
      const key = peerId ? pairKey(deviceId, peerId) : "";
      if (!key || !paired.has(key)) return error(ws, "not_paired", "Devices must be paired first.");
      if (sessions.has(key)) return error(ws, "session_active", "A support session is already active.");
      if (!sockets.has(peerId)) return error(ws, "peer_offline", "The paired device is not connected.");

      const now = Date.now();
      for (const [existingRequestId, request] of pendingSessions) {
        if (request.expiresAt <= now) {
          pendingSessions.delete(existingRequestId);
          continue;
        }
        if (pairKey(request.ownerId, request.requesterId) === key) {
          return error(ws, "session_pending", "A support-session request is already waiting for approval.");
        }
      }

      const requestId = cryptoRandomId();
      pendingSessions.set(requestId, {
        ownerId: peerId,
        requesterId: deviceId,
        expiresAt: Date.now() + SESSION_REQUEST_TTL_MS
      });
      send(sockets.get(peerId), {
        type: "session:approval_required",
        requestId,
        requesterDeviceId: deviceId,
        peerDeviceId: deviceId,
        requester: devices.get(deviceId)?.name || "Device"
      });
      return send(ws, {
        type: "session:waiting",
        requestId,
        peerDeviceId: peerId,
        expiresInSeconds: Math.floor(SESSION_REQUEST_TTL_MS / 1000)
      });
    }

    if (message.type === "session:approve" || message.type === "session:deny") {
      const requestId = typeof message.requestId === "string" ? message.requestId : "";
      const request = pendingSessions.get(requestId);
      if (!request || request.ownerId !== deviceId || request.expiresAt < Date.now()) {
        pendingSessions.delete(requestId);
        return error(ws, "invalid_session_request", "This support-session request is no longer valid.");
      }
      pendingSessions.delete(requestId);

      if (message.type === "session:deny") {
        send(sockets.get(request.requesterId), {
          type: "session:denied",
          by: deviceId,
          peerDeviceId: deviceId
        });
        return send(ws, {
          type: "session:denied",
          by: deviceId,
          peerDeviceId: request.requesterId
        });
      }

      const key = pairKey(request.ownerId, request.requesterId);
      if (!paired.has(key) || sessions.has(key)) {
        return error(ws, "session_unavailable", "The paired support session is no longer available.");
      }
      // The approving device is the screen owner; the requester is the viewer.
      sessions.set(key, {
        approvedBy: deviceId,
        requesterId: request.requesterId,
        startedAt: Date.now()
      });
      send(sockets.get(request.requesterId), {
        type: "session:approved",
        by: deviceId,
        peerDeviceId: deviceId,
        role: "viewer"
      });
      return send(ws, {
        type: "session:approved",
        by: deviceId,
        peerDeviceId: request.requesterId,
        role: "screen_owner"
      });
    }

    if (message.type === "session:stop") {
      const peerId = typeof message.peerDeviceId === "string" ? message.peerDeviceId : "";
      const key = peerId ? pairKey(deviceId, peerId) : "";
      if (!key || !paired.has(key)) return error(ws, "not_paired", "Devices must be paired first.");
      const session = sessions.get(key);
      if (!session) return error(ws, "session_not_active", "There is no active support session.");
      const expectedPeer = session.approvedBy === deviceId ? session.requesterId : session.approvedBy;
      if (peerId !== expectedPeer) return error(ws, "invalid_peer", "The requested peer does not match this session.");

      sessions.delete(key);
      send(sockets.get(peerId), {
        type: "session:stopped",
        by: deviceId,
        peerDeviceId: deviceId
      });
      return send(ws, {
        type: "session:stopped",
        by: deviceId,
        peerDeviceId: peerId
      });
    }

    if (["rtc:offer", "rtc:answer", "rtc:ice"].includes(message.type)) {
      const peerId = typeof message.peerDeviceId === "string" ? message.peerDeviceId : "";
      const key = peerId ? pairKey(deviceId, peerId) : "";
      const session = sessions.get(key);
      if (!key || !paired.has(key) || !session) {
        return error(ws, "session_not_approved", "An explicitly approved support session is required.");
      }

      const expectedPeer = session.approvedBy === deviceId ? session.requesterId : session.approvedBy;
      if (peerId !== expectedPeer) return error(ws, "invalid_peer", "The signaling peer does not match this session.");
      if (message.type === "rtc:offer" && deviceId !== session.approvedBy) {
        return error(ws, "invalid_offer_sender", "Only the screen-owning device may send the WebRTC offer.");
      }
      if (message.type === "rtc:answer" && deviceId !== session.requesterId) {
        return error(ws, "invalid_answer_sender", "Only the requesting viewer may send the WebRTC answer.");
      }

      const payload = message.payload;
      if (!payload || typeof payload !== "object" || JSON.stringify(payload).length > 12_000) {
        return error(ws, "invalid_payload", "Signaling payload is invalid or too large.");
      }
      if (!sockets.has(peerId)) return error(ws, "peer_offline", "The paired device is not connected.");
      return send(sockets.get(peerId), { type: message.type, fromDeviceId: deviceId, payload });
    }

    return error(ws, "unsupported_type", "Unsupported message type.");
  });

  ws.on("close", () => {
    for (const [requestId, request] of pendingSessions) {
      if (request.ownerId === deviceId || request.requesterId === deviceId) {
        pendingSessions.delete(requestId);
        const peerId = request.ownerId === deviceId ? request.requesterId : request.ownerId;
        send(sockets.get(peerId), {
          type: "session:cancelled",
          by: deviceId,
          peerDeviceId: deviceId,
          reason: "peer_disconnected"
        });
      }
    }

    for (const [key, session] of sessions) {
      if (session.approvedBy === deviceId || session.requesterId === deviceId) {
        sessions.delete(key);
        const peerId = session.approvedBy === deviceId ? session.requesterId : session.approvedBy;
        send(sockets.get(peerId), {
          type: "session:stopped",
          by: deviceId,
          peerDeviceId: deviceId,
          reason: "peer_disconnected"
        });
      }
    }

    if (sockets.get(deviceId) === ws) sockets.delete(deviceId);
  });
});

const sweep = setInterval(() => {
  const now = Date.now();
  for (const [key, value] of pairingCodes) if (value.expiresAt < now) pairingCodes.delete(key);
  for (const [key, value] of pending) if (value.expiresAt < now) pending.delete(key);
  for (const [requestId, request] of pendingSessions) {
    if (request.expiresAt < now) {
      pendingSessions.delete(requestId);
      send(sockets.get(request.requesterId), {
        type: "session:expired",
        peerDeviceId: request.ownerId
      });
      send(sockets.get(request.ownerId), {
        type: "session:expired",
        peerDeviceId: request.requesterId
      });
    }
  }
}, 30_000);
sweep.unref();

server.listen(PORT, () => {
  console.log(`AirLink signaling server listening on port ${PORT}`);
});

function shutdown() {
  clearInterval(sweep);
  for (const ws of wss.clients) ws.close(1001, "Server shutting down");
  server.close(() => process.exit(0));
}
process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
