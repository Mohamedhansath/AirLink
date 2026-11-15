# AirLink

AirLink is an Android-to-Android remote assistance project focused on consent-based screen sharing, camera sharing, microphone audio, and remote support over the internet.

## Project status

The repository contains an initial Android scaffold with a Compose dashboard, sign-in/account-creation UI, and an 8-character pairing-code entry UI. These account and pairing screens are prototypes only: no authentication provider, pairing API, or live device connection is wired yet. Real-time transport and device-side capture remain later milestones and must be tested on physical Android devices.

## Planned capabilities

- Secure device pairing and revocable trusted-device records
- Consent-based screen sharing using Android MediaProjection
- Camera and microphone sessions started by the device owner while the app is visible
- WebRTC media transport with authenticated signaling and TURN fallback
- Visible foreground-service notifications and persistent session indicators
- Immediate stop controls, session expiry, and audit history

## Platform and architecture

- Android: Kotlin, Jetpack Compose
- Real-time media: WebRTC
- Signaling/API: Node.js + WebSocket (planned)
- Relay: TURN server (planned)
- Authentication and device registry: backend service (planned)

## Important Android limitations

A saved app permission does not grant unlimited background access. Android restricts camera and microphone use to eligible foreground-service flows, and screen capture requires the system MediaProjection consent flow. AirLink will not bypass Android permission prompts, privacy indicators, or device-owner approval.

## Build

Open this project in Android Studio, allow Gradle sync, then run the app configuration on an Android device or emulator.

## Milestones

1. Android scaffold and dashboard
2. Pairing, authentication, and device registry
3. Consent-based screen sharing
4. Camera and microphone streaming
5. WebRTC signaling, TURN fallback, and reconnect
6. Testing, hardening, and release APK
