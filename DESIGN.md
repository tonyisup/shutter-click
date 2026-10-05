# Shutter Click

Accepted design and prototype · October 2, 2026

## Goal

Press START on a Garmin Forerunner 955 to activate the normal photo shutter in the already-open Pixel Camera on a Pixel 11 Pro Fold running Android 17. Pixel Camera owns the exposure, processing, current lens, settings, and saved photo.

The user selected the shutter-first watch design. Native watch and Android companion prototypes now implement it. Both compile; protocol and selection tests pass. On October 2, 2026, the user confirmed shutter operation from the simulator and the physical Forerunner 955 Solar to Pixel Camera on the Pixel 11 Pro Fold / Android 17. Companion logs show the physical capture, accepted click, and successful reply. Broader camera-mode, display, reconnection, and battery acceptance remain pending.

The user has confirmed that an Android companion app and enabling its Accessibility service are acceptable.

## Everyday use

1. Open Shutter Click on the phone and select **Start remote**. The companion establishes its Garmin connection and starts a visible, bounded remote session.
2. Tap **Open Pixel Camera**, select Photo mode, and frame the shot. Leave the phone unlocked with the camera visible.
3. Open Shutter Click on the watch. It displays **Ready** once the companion confirms the supported photo shutter is available.
4. Press START, or tap the on-screen shutter. The phone clicks Pixel Camera's shutter. A short watch vibration and **Sent** confirm that the click was accepted.
5. Press BACK to leave the watch app. End the phone session from its notification when finished. A session also expires after ten minutes without a shutter request; connection loss immediately removes readiness.

Starting the session on the phone is the reliable initial design. Opening only the watch app and having Android automatically revive the companion is a later optimization that needs separate lifecycle validation.

## Watch interface

Target the round 260 × 260 display. Use Garmin system fonts, a monochrome palette, and generous spacing inside the circular safe area. Avoid continuous animation; redraw only when state changes.

The preferred screen has the app name at the top, a large central shutter target, a short status beneath it, and **START · SHUTTER** at the bottom. Physical START is the primary control; touch is a convenience.

| State | Watch copy | Behavior |
| --- | --- | --- |
| Preparing | Connecting… | Shutter disabled while checking the companion. |
| Ready | Ready | START or shutter tap sends one request. |
| Pending | Sending… | Ignore further presses while the request is pending. |
| Click accepted | Sent | Short vibration, then return to the latest readiness state. This does not claim a photo was saved. |
| Camera unavailable | Open Pixel Camera | Shutter disabled. |
| Wrong camera mode | Use Photo mode | Shutter disabled; do not accidentally start recording. |
| Permission unavailable | Check phone setup | Companion explains the missing or blocked capability. |
| Connection lost | Phone disconnected | Shutter disabled; resume only after a new readiness check. |
| Reply timeout | No reply · Check phone | A photo may have been triggered. Never automatically repeat the request. |

The circle stays black while the current readiness capability is valid, including during background status checks. A shutter press abandons the pending status reply before sending its capture request. Late status replies cannot enable a second capture. Expired or unavailable readiness gives a gray circle and **PLEASE WAIT**, and a previously displayed **Ready** changes to **Checking…**.

BACK exits. LIGHT keeps its normal system behavior. UP and DOWN have no app action. Long presses do not fire repeated shots. No timers, zoom, lens selection, preview stream, or capture counter in the first version; use Pixel Camera's own settings.

## Phone companion

A small Kotlin Android app has a setup screen, a remote-session controller, a Garmin message bridge, and a narrowly scoped Accessibility service.

Setup shows the selected Forerunner, Garmin connection state, and camera-control permission state. **Start remote** is available only after setup succeeds. During a session, a persistent notification reads **Camera remote active** with an **End remote** action. Validate the appropriate service type and runtime permissions against the chosen Android target SDK before implementing persistence; a foreground service alone is not proof that Garmin's SDK listener will survive.

The Accessibility service receives events from the verified Pixel Camera package and operates only during an active session. On every shutter request, it checks that the phone is unlocked, Pixel Camera is the active window, a supported Photo mode is selected, and a unique visible, enabled photo shutter node is available. It locates the node anew after display, rotation, fold, and camera changes.

The prototype uses `AccessibilityNodeInfo.ACTION_CLICK` and exact English photo-action descriptions. Unknown or ambiguous controls are rejected. A gesture fallback remains deferred until the actual camera can be validated; it must use a freshly identified node rather than a remembered absolute coordinate.

The companion needs no camera or photo-library permission for this design, since it neither captures images itself nor reads saved photos. It cannot promise that a file was saved: Android acknowledging a click is weaker than Pixel Camera confirming capture completion.

## Architecture and request behavior

```text
Forerunner watch app (Monkey C)
    ↕ Connect IQ Communications
Garmin Connect on Android
    ↕ Connect IQ Android companion SDK
Shutter Click companion
    → Accessibility action on Pixel Camera's photo shutter
```

Use the existing Garmin pairing. The first design has no custom BLE pairing, account, backend, or internet dependency in its shutter protocol. Garmin Connect remains a dependency of the transport.

Separate delivery acknowledgement from application acknowledgement. A successful Garmin transmission does not mean the shutter was clicked. The companion replies with the request ID and either **click accepted**, a concrete rejection, or an uncertain result.

Allow only one pending shutter request. Give requests unique IDs and reject duplicates before performing the action; retain consumed IDs across companion restarts for the session. A timeout is an uncertain result and must not trigger an automatic retry.

For stale-message protection, the phone issues a short-lived readiness token bound to the active remote session and camera context. The watch echoes it with the request; the phone checks expiry using its own clock. Refresh tokens while the watch is active. Invalidate tokens on camera-window changes, session end, or link loss, so queued commands cannot fire later when the user reopens the camera. Tune token lifetime, refresh interval, and reply timeout from measured Garmin round-trip times rather than assuming instant delivery.

## Compatibility findings

- Garmin lists the 955 as a round 260 × 260, 64-color MIP device with touch and Connect IQ support. [Device reference](https://developer.garmin.com/connect-iq/device-reference/), [compatible devices](https://developer.garmin.com/connect-iq/compatible-devices/).
- Connect IQ offers phone messaging and an Android companion SDK. [Communications API](https://developer.garmin.com/connect-iq/api-docs/Toybox/Communications.html), [Garmin Android SDK and samples](https://github.com/garmin/connectiq-android-sdk).
- The public Connect IQ generic BLE API exposes the central role. It does not expose a watch-side Bluetooth HID peripheral API for impersonating a camera remote. [BLE API](https://developer.garmin.com/connect-iq/api-docs/Toybox/BluetoothLowEnergy.html).
- Android's standard still-image camera intent launches a camera; it does not provide a shutter command to an already-open camera. No supported public Pixel Camera shutter API was found in this research. [MediaStore intent reference](https://developer.android.com/reference/android/provider/MediaStore#INTENT_ACTION_STILL_IMAGE_CAMERA).
- Android Accessibility provides UI actions and a gesture fallback. Whether the actual Pixel Camera exposes a reliable photo shutter must be tested. [Accessibility service reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#dispatchGesture(android.accessibilityservice.GestureDescription,%20android.accessibilityservice.AccessibilityService.GestureResultCallback,%20android.os.Handler)).
- Advanced Protection restricts Accessibility services to verified accessibility tools. This can block a general-purpose camera companion on the requested Android 17 device. Treat a blocked service as unsupported, with a clear explanation; do not assume installation from Play resolves it. [Android Advanced Protection help](https://support.google.com/android/answer/16339980?hl=en).

## Physical validation milestone

The native prototypes are built. Complete these device checks before treating them as a supported camera remote:

1. Record Android build, Pixel Camera version and package, and whether Accessibility is available under the phone's current protection settings.
2. Inspect the actual photo shutter node and mode information. Confirm one explicit request clicks it while Pixel Camera is foregrounded and the companion UI is backgrounded.
3. Verify folded cover display, unfolded inner display, supported tabletop posture, and portrait/landscape. Reject stale nodes during transitions.
4. Confirm commands cannot operate on the lock screen, video mode, another app, or a settings dialog.
5. Connect Garmin's sample watch and companion messaging, then measure latency with the camera foregrounded. Check listener behavior after backgrounding, reconnection, and process termination.
6. Verify the implemented one-screen watch interface on the real watch. One accepted press must produce at most one shutter activation, duplicate messages must be rejected, and disconnected or delayed requests must never fire after reconnection.

Acceptance means activating the existing Pixel Camera shutter reliably under these stated conditions. Physical-device results are required before claiming compatibility with the Pixel 11 Pro Fold and Android 17.
