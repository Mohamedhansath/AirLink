# AirLink

AirLink is an Android-to-Android remote assistance project focused on consent-based screen sharing, camera sharing, microphone audio, and remote support over the internet.

## Project status

The repository contains an Android Compose app with a backend-connected device registration and WebSocket pairing client. The client stores its device token encrypted with Android Keystore and supports one-time pairing-code generation/join plus explicit pairing approval. Account sign-in remains a UI prototype; real-time media and device-side capture are not implemented yet.

## Planned capabilities

- Secure device pairing and revocable trusted-device records
- Consent-based screen sharing using Android MediaProjection (visible local capture service scaffolded)
- Camera and microphone sessions started by the device owner while the app is visible
- WebRTC media transport with authenticated signaling and TURN fallback
- Visible foreground-service notification with a Stop action for local screen capture
- Immediate stop controls, session expiry, and audit history

## Platform and architecture

- Android: Kotlin, Jetpack Compose
- Real-time media: WebRTC
- Signaling/API: Node.js + Express + WebSocket (prototype scaffold added)
- Relay: TURN server (planned)
- Authentication and device registry: in-memory prototype registry (not production-ready)

## Important Android limitations

A saved app permission does not grant unlimited background access. Android restricts camera and microphone use to eligible foreground-service flows, and screen capture requires the system MediaProjection consent flow. AirLink will not bypass Android permission prompts, privacy indicators, or device-owner approval.

## Build

Open this project in Android Studio, allow Gradle sync, then run the app configuration on an Android device or emulator.

## Milestones

1. Android scaffold and dashboard
2. Pairing, authentication, and device registry (backend prototype scaffolded)
3. Consent-based screen sharing
4. Camera and microphone streaming
5. WebRTC signaling, TURN fallback, and reconnect
6. Testing, hardening, and release APK


## Backend prototype

A Node.js signaling prototype is available in `server/`. It includes device-token registration, expiring one-time pairing codes, owner approval/denial, session approval/stop events, and guarded WebRTC signaling relay. See [server/README.md](server/README.md) for setup and the message protocol.

**Not live yet:** the Android app is not connected to this backend, media capture/streaming is not implemented, and the backend has not been deployed. The prototype keeps state in memory and requires production hardening (persistent storage, identity, secure WebSocket tickets, shared rate limiting, HTTPS/WSS, and TURN configuration) before public use.


## Android backend connection

The Pair a device screen now supports:
1. Registering this phone against a deployed HTTPS backend.
2. Reconnecting with the encrypted device credential stored using Android Keystore.
3. Opening an authenticated WSS connection.
4. Creating or entering an expiring one-time pairing code.
5. Approving or declining incoming pairing requests on the receiving phone.

Set the backend URL to your deployed HTTPS origin (for example, `https://api.example.com`). Do not use a placeholder URL. The backend must be running and reachable from both phones. This integration has been committed but has not yet been compiled or tested on physical Android devices. Pairing does not enable screen, camera, microphone, or remote control; those require separate Android permission and session-consent implementations.


## Android screen-capture consent preflight

The Screen sharing card requests notification permission on Android 13+ and then launches Android's official MediaProjection consent prompt after an explicit user tap. Each session requires fresh system consent. When approved, AirLink starts a non-exported `mediaProjection` foreground service with an ongoing notification and a Stop action. The app UI also exposes a Stop button. The service responds to system revocation, releases its virtual display and image buffers, and does not restart automatically. Captured ImageReader frames are immediately closed; no image is saved or sent to another device.

**Not remote streaming yet:** this milestone validates the visible local capture lifecycle only. There is no WebRTC encoder/video track or remote viewer connected yet. Device pairing does not silently start capture, and the MediaProjection token is not persisted or reused. Android requires user consent for each projection session and restricts a projection token to one virtual-display creation.
