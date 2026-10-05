# Shutter Click

A shutter-first Connect IQ watch app for the Garmin Forerunner 955, with a Kotlin Android companion. START or a tap on SHOOT requests one click of the existing Pixel Camera photo shutter. Pixel Camera keeps control of the lens, processing, settings, and photo storage.

This is a development prototype. Both applications compile, Android lint passes, and 21 companion tests plus 15 native watch tests pass. On October 2, 2026, the user confirmed Pixel Camera shutter operation from both the simulator and the physical Forerunner 955 Solar on the Pixel 11 Pro Fold / Android 17. The physical capture also logged an accepted click and a successful reply. Broader camera-mode, display, reconnection, and battery tests remain pending. A successful click acknowledgement does not confirm that a photo was saved.

## Install and use

1. Keep your Forerunner paired in Garmin Connect on the Pixel.
2. Set the Forerunner's **System → USB Mode → MTP** and connect it by USB. Copy `dist/ShutterClick.prg` into the watch's `GARMIN/APPS` directory, then disconnect it. macOS requires an MTP transfer utility because the watch does not appear as a Finder drive. Open Shutter Click from the watch's activity/app list. Garmin documents this development sideload process in the SDK's **Your First App** guide.
3. Install `dist/ShutterClick-android.apk` on the Pixel. For a connected ADB device, use `adb install -r dist/ShutterClick-android.apk`.
4. Open Shutter Click on the phone. Allow nearby devices and notifications, select the Forerunner, and use **Enable camera control** to read the disclosure and enable its Accessibility service in Android settings.
5. Select **Start remote**, then **Open Pixel Camera**. Choose Photo mode, frame your photo, and keep the phone unlocked with the camera visible.
6. Open the watch app. When it says **Ready**, press START or tap SHOOT once. **Sent** and a short vibration mean the phone accepted the click. BACK exits the watch app; **End remote** in the phone notification ends the session. Sessions expire after ten minutes without a shutter request.

Unknown, ambiguous, disabled, or invisible controls keep the shutter unavailable. The initial profile accepts exact English photo-action descriptions (`Take photo`, `Take picture`, `Capture photo`) in `com.google.android.GoogleCamera`. It deliberately rejects a generic `Shutter` label and video actions until their mode semantics have been validated on the actual camera version. If the watch says **Check camera**, do not add a guessed coordinate; inspect the real photo control and update the profile with evidence.

If a click reply is lost, the watch shows **No reply**. The next START press checks readiness; a subsequent press can take another photo. The app never retries a capture automatically.

## Wireless ADB and Garmin's simulator

Wireless ADB is supported by the development setup. Pairing alone is insufficient: `adb devices` must show the Pixel with status `device`. The forwarding rule must be recreated when the ADB connection changes.

The real watch uses Garmin Connect's **WIRELESS** transport. The simulator requires an Android companion using Garmin's **TETHERED** transport, even if the underlying ADB connection is wireless. Build and install that separate development configuration:

```sh
./gradlew -PciqSimulator=true :android-app:assembleDebug
adb install -r android-app/build/outputs/apk/debug/android-app-debug.apk
adb forward tcp:7381 tcp:7381
adb shell am start -n dev.shutterclick/.MainActivity
```

The phone shows **Simulator build · ADB connection**. This build adds Android's `INTERNET` permission because Garmin's TETHERED SDK opens a local socket. Replies run on a background thread, and the build registers the empty app ID used by companion SDK 2.4.0's simulator receiver. The physical-watch build has no socket permission.

Use **Connect IQ 8.1.0's simulator** for the ADB test. We reproduced a native `Communications.transmit` crash in simulators 9.2.0 and 8.4.1, with the fault address containing our application UUID. Garmin has acknowledged the [related simulator transmit bug as CIQQA-4579](https://forums.garmin.com/developer/connect-iq/i/bug-reports/simulator-segfaults-on-communications-transmit-over-the-tethered-adb-connection-sdk-9-1-0-and-9-2-0-linux). SDK 8.1.0 successfully exchanged watch requests and phone replies in this setup.

Build with SDK 9.2.0: the currently installed Forerunner device package requires API 5.2, which the 8.1.0 compiler cannot target. Open `ConnectIQ.app` from SDK **8.1.0**, then build and run the executable:

```sh
export CIQ_SDK_HOME="$PWD/.tooling/garmin-sdk"
export CIQ_SIMULATOR_SDK_HOME="$PWD/.tooling/garmin-sdk-8.1.0"
tools/run-watch-simulator.sh
```

After loading the watch executable, with the simulator companion open on the phone, choose **adb Connection → Start** in Garmin's simulator. The menu should then disable **Start** and enable **Release**. The watch should show **Start remote** when the phone session is off; this verifies an actual request and reply. Continue through phone setup, start the remote session, and open Pixel Camera. Loading another executable can reset the bridge; reconnect from this menu after each reload.

Reinstall `dist/ShutterClick-android.apk` to return to the physical watch transport. Both configurations use the same Android application ID, so installing one replaces the other. Neither build turns ADB on or pairs a new device.

Garmin's error “There is no data connection” refers to this simulator bridge. Check the active device, forwarding rule, TETHERED companion, and simulator connection menu in that order. `adb forward --list` should include `tcp:7381 tcp:7381`. With more than one device, use `adb -s <your-device-serial>` for all ADB commands. An `EXC_BAD_ACCESS` / `SIGSEGV` crash inside the simulator is a separate failure: use the verified 8.1.0 runtime instead of repeatedly reconnecting 9.2.0.

## Build

Android requires JDK 17, Android SDK platform 37.0 and build tools 36.0.0. The project uses Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10, and Garmin companion SDK 2.4.0. Android minSdk is 33; compileSdk and targetSdk are 37.

```sh
./gradlew :core:test :android-app:assembleDebug :android-app:lintDebug
```

`tools/package.sh` builds and verifies the physical-watch APK and watch executable into `dist/`. Add `--simulator` to also package the simulator APK. All APKs are signed development builds, intended for local testing.

For store uploads, copy `keystore.properties.example` to `keystore.properties` and point it at your Play upload key. `tools/package.sh --release` then adds a minified, signed `ShutterClick-release.aab` and matching APK, plus the Connect IQ Store package `ShutterClick.iq` (also built alone by `tools/build-watch.sh --store`). Install and exercise the release APK on a phone before uploading the bundle, since minification can break behavior that debug builds do not show.

For the watch, install Connect IQ SDK 9.2.0 and the **Forerunner 955 / Solar** device package through Garmin SDK Manager. The local development SDK and key live in ignored `.tooling/`; a different machine can set `CIQ_SDK_HOME` and `CIQ_DEVELOPER_KEY` explicitly.

```sh
tools/build-watch.sh
tools/build-watch.sh --tests
tools/run-watch-simulator.sh --tests
```

Start Garmin's simulator before `monkeydo`. This SDK's test runner reported exit status 1 while its result summary showed `PASSED (passed=15, failed=0, errors=0)`; inspect the summary when running manually.

To create your own private development signing key once:

```sh
mkdir -p .tooling/signing
openssl genrsa -out .tooling/signing/developer.pem 4096
openssl pkcs8 -topk8 -inform PEM -outform DER \
  -in .tooling/signing/developer.pem -out .tooling/signing/developer.der -nocrypt
chmod 600 .tooling/signing/developer.pem .tooling/signing/developer.der
```

Keep the key private and preserve it for updates. Do not regenerate it over an existing project key. The watch and companion share application UUID `8d675184594c4f4a8d185d1e6a6e0aa7`.

## Request safeguards

The phone issues an 8-second readiness capability bound to its active session, watch instance, and current camera window/display/shutter bounds. It checks the camera again immediately before clicking. Camera-window changes, disconnection, session end, and restart invalidate outstanding capabilities. Each request is durably claimed before the action; duplicates and expired requests cannot click. Claims are cleared at the start of a new random session, whose capabilities are unrelated to the previous session.

The watch allows one pending capture, checks readiness every three seconds, and treats a six-second reply timeout as uncertain. A background readiness check keeps the existing unexpired capability usable. An explicit shutter press abandons that check's reply and uses the current capability; the phone rechecks its expiry and camera context before clicking. Transport completion never substitutes for the phone's application acknowledgement. Late callbacks cannot change a newer transmission. The circle is black when an action is available and gray otherwise; **Ready** and **START: SHOOT** appear only while a shutter request is available.

Watch deadlines compare elapsed differences to tolerate the signed millisecond timer's periodic rollover. A missing cooldown uses `null`, since zero and negative timer values are valid. This keeps readiness, expiry, and cooldown behavior independent of device uptime.

The Accessibility service is restricted to Pixel Camera, uses `ACTION_CLICK`, and exposes no gesture or coordinate fallback. It checks the lock state, active window, display, and a unique visible enabled photo action. No camera or photo-library permission is requested. There is no backend, account, telemetry, or photo handling in Shutter Click. Garmin Connect remains the physical watch transport dependency. Android Advanced Protection can prevent enabling this general-purpose Accessibility service; the prototype does not bypass that restriction.

## Physical acceptance

Record Android build, Pixel Camera package/version, Garmin Connect version, watch firmware, and available Accessibility controls. Then check: one START press produces one photo; rapid repeated presses produce no duplicates; photo control works on cover and inner displays and both orientations; fold/window transitions invalidate queued commands; lock screen, video, dialogs, and other foreground apps reject commands; lost replies never retry; reconnecting, restarting, or ending a session prevents stale capture; background listeners remain available while Pixel Camera is foregrounded. Measure request latency before changing the capability or timeout durations.

## Sources

- [Garmin Android companion SDK](https://github.com/garmin/connectiq-android-sdk) and its official SDK **Mobile SDK for Android** guide describe transport and application messaging.
- [Garmin Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/) and the installed **Your First App** / **Monkey C Command Line Setup** guides describe device support, sideloading, and simulator tests.
- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk) and [AGP 9.1.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes) cover the Android build target.
- [Android AccessibilityService](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) and [connected-device foreground services](https://developer.android.com/develop/background-work/services/fgs/service-types#connected-device) cover camera controls and active sessions.
- [Android Advanced Protection](https://support.google.com/android/answer/16339980?hl=en) describes its Accessibility restriction.

See `DESIGN.md` for the accepted interaction design and rationale.

## License

Copyright (C) 2026 the Shutter Click authors.

Shutter Click is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See `LICENSE` for the full text.

Garmin's Connect IQ SDK and Android companion SDK are separate works under Garmin's own license terms and are not included in this repository.
