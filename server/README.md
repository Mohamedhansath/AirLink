# AirLink signaling backend (prototype)

Node.js + Express + WebSocket signaling for consent-based device pairing and screen-sharing session approval.

## Run locally

Requires Node.js 20+.

```bash
cd server
cp .env.example .env
npm install
npm run dev
```

Health check: `GET http://localhost:8080/health`.

Register a device:

```bash
curl -X POST http://localhost:8080/v1/devices/register \
  -H 'Content-Type: application/json' \
  -d '{"deviceName":"My Android"}'
```

The response contains a device ID and a one-time device token. Keep the token in Android Keystore-backed storage; do not log or commit it. Connect to `ws://localhost:8080/ws` for local development and send `Authorization: Bearer DEVICE_TOKEN` as the WebSocket handshake header. The Android client uses `wss://` and the same header for deployed servers. Tokens are not placed in URL query strings.

## WebSocket message flow

### Pairing

- `pair:create` — creates a short-lived, one-time 8-character pairing code.
- `pair:join` with `{ "code": "..." }` — consumes the code and sends an approval request to the code owner.
- `pair:approve` / `pair:deny` with `{ "requestId": "..." }` — the code owner explicitly accepts or rejects pairing.

### Support-session consent

- `session:request` with `{ "peerDeviceId": "..." }` — a paired device asks the peer to share its screen. The peer must be online.
- `session:approval_required` — delivered to the paired screen owner with the requester name and a one-time request ID.
- `session:approve` / `session:deny` with `{ "requestId": "..." }` — only the screen-owning peer that received the request can approve or decline it. Requests expire after 60 seconds by default; configure `SESSION_REQUEST_TTL_SECONDS` from 30 to 300 seconds.
- `session:approved` — sent to both devices after owner approval. The approving device is the screen owner; the requester is the viewer.
- `session:stop` with `{ "peerDeviceId": "..." }` — either participant can stop the approved session. Disconnecting either device also stops it.
- `session:denied`, `session:expired`, `session:cancelled`, and `session:stopped` communicate the terminal state.

### WebRTC signaling relay

- `rtc:offer`, `rtc:answer`, and `rtc:ice` carry a bounded JSON `payload` and `peerDeviceId`.
- Signaling is accepted only for a paired device with an explicitly approved session.
- Only the screen owner can send `rtc:offer`; only the requesting viewer can send `rtc:answer`; either participant can send ICE candidates.
- Signaling is relayed over the authenticated WebSocket. It does **not** carry screen pixels, camera frames, microphone audio, or remote-control commands.

## Current integration status

- The Android client authenticates its WebSocket with the device-token Authorization header.
- Android now has session request/approve/deny/stop methods and consent-gated offer/answer/ICE send methods.
- The Android UI shows the incoming screen-sharing approval prompt and the session state.
- The Android MediaProjection service is a visible local capture scaffold. It currently consumes and discards ImageReader frames.
- There is no Android WebRTC `PeerConnection`, video encoder/track, remote video renderer, or TURN configuration yet. Therefore this repository does **not** provide live remote screen streaming yet.

## Prototype limitations and production checklist

- Device records, pairings, pending requests, and sessions are in-memory and disappear when the process restarts. Add durable storage, account identity, device revocation, and audit events before production.
- The IP rate limiter is in-process. Use a shared rate limiter behind a deliberately configured trusted proxy in production.
- Header-based bearer authentication avoids putting the long-lived token in URL logs, but a short-lived, single-use WebSocket ticket is still recommended.
- Deploy only behind HTTPS/WSS, restrict allowed origins, rotate secrets, and add abuse monitoring.
- Configure authenticated TURN credentials for reliable NAT traversal. No TURN server or credentials are bundled here.
- The Android owner must separately approve the system MediaProjection prompt for every capture session. Stop controls and Android privacy indicators must remain visible.
- Never use this backend for hidden monitoring or to bypass Android permission prompts or privacy indicators.
