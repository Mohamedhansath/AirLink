# AirLink

AirLink is an Android-to-Android remote-assistance prototype. Its design requires explicit device pairing, per-session owner approval, Android's system MediaProjection consent, visible foreground-service status, and revocable stop controls.

## Implemented in the repository

- Android app scaffold built with Kotlin and Jetpack Compose.
- Node.js + Express + WebSocket prototype for device registration and one-time device pairing.
- Android device credentials stored encrypted with Android Keystore.
- Authenticated WebSocket handshake using an Authorization bearer header; the device token is not placed in the URL.
- Pairing approval/decline UI on the receiving device.
- Support-session request, owner approval/decline, stop, expiry, and disconnect cleanup in the backend protocol.
- Android UI for requesting a paired device's screen and showing the screen owner's approval prompt.
- Consent-gated Android client methods for sending WebRTC offer, answer, and ICE signaling after the server confirms an approved session.
- Android MediaProjection consent flow and a non-exported `mediaProjection` foreground service.
- Required `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION` permissions for the app's target SDK, with an ongoing notification and Stop action.
- The MediaProjection result is used for that user-approved session only. System revocation or the visible Stop action releases the virtual display, image reader, projection, and foreground notification.
- Captured frames are currently discarded locally; no image or video is saved or transmitted.

## Not implemented yet

- Android WebRTC `PeerConnection` creation and lifecycle.
- A WebRTC video source/track connected to the MediaProjection capture surface.
- Remote video rendering and a complete viewer experience.
- TURN server deployment and authenticated, short-lived TURN credentials.
- Camera and microphone streaming.
- Production authentication/account service, durable device/session storage, device revocation, audit logs, and production abuse controls.
- Full automated Android build and physical-device validation.

**Important:** The repository does not currently provide live remote screen streaming. The signaling protocol can relay bounded WebRTC negotiation messages after explicit session approval, but no WebRTC media track is attached. The current Android capture service is a local lifecycle scaffold that discards frames.

## Android privacy and consent

A saved app permission does not grant unlimited background access. Android screen capture requires the official MediaProjection system consent flow for each session. AirLink does not bypass Android permission prompts, privacy indicators, foreground-service requirements, or device-owner approval. Pairing alone never starts screen capture. The screen owner must approve the support-session request and separately start capture through the app and Android system prompt. The ongoing notification provides a Stop action, and system revocation ends the capture.

## Architecture

- Android: Kotlin, Jetpack Compose, Android MediaProjection.
- Signaling/API: Node.js, Express, WebSocket.
- Planned media transport: WebRTC with TURN fallback.
- Current backend storage: in-memory prototype maps; not production-ready.

## Build and run

Open the Android project in Android Studio, allow Gradle sync, then build and run on an Android device or emulator. The backend setup and message protocol are documented in [server/README.md](server/README.md).

The Android app and backend have not been verified on physical devices in this milestone. Do not treat a successful GitHub commit as proof of Android compilation, device testing, deployment, or live streaming.
