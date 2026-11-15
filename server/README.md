# AirLink signaling backend (prototype)

Node.js + Express + WebSocket signaling scaffold for consent-based device pairing and WebRTC negotiation.

## Run locally

Requires Node.js 20+.

```bash
cd server
cp .env.example .env
npm install
npm run dev
```

Health check: `GET http://localhost:8080/health`

Register a device:

```bash
curl -X POST http://localhost:8080/v1/devices/register \
  -H 'Content-Type: application/json' \
  -d '{"deviceName":"My Android"}'
```

The response contains a device ID and a one-time device token. Keep the token in Android Keystore-backed storage; do not log or commit it. Connect to `ws://localhost:8080/ws?token=DEVICE_TOKEN` for local development.

## WebSocket message flow

- `pair:create` — creates a short-lived, one-time 8-character pairing code.
- `pair:join` with `{ "code": "..." }` — consumes the code and sends an approval request to the code owner.
- `pair:approve` / `pair:deny` with `{ "requestId": "..." }` — the owner explicitly accepts or rejects pairing.
- `session:approve` with `{ "peerDeviceId": "..." }` — the receiving device explicitly approves a support session.
- `rtc:offer`, `rtc:answer`, `rtc:ice` — forwards bounded WebRTC signaling payloads only for paired devices with an approved session.
- `session:stop` — revokes the active session.

## Prototype limitations and production checklist

- Device records, pairings, and sessions are in-memory and disappear on restart. Add a durable database, account identity, device revocation, and audit events before production.
- This prototype uses a simple in-process IP rate limiter. Use a shared rate limiter behind a trusted proxy in production and configure proxy trust deliberately.
- The WebSocket token is currently passed as a query parameter for simplicity; production should use a short-lived, single-use WebSocket ticket to avoid URL/log leakage.
- Deploy only behind HTTPS/WSS, restrict allowed origins, rotate secrets, add abuse monitoring, and test reconnect/replay behavior.
- Configure authenticated TURN credentials for reliable NAT traversal. No TURN credentials or media relay are bundled here.
- Signaling does not capture or transmit screen, camera, or microphone media. Android must implement MediaProjection and eligible foreground-service flows, show persistent indicators, and obtain the device owner's explicit approval for each session.
- Never use this backend for hidden monitoring or to bypass Android permission prompts or privacy indicators.
