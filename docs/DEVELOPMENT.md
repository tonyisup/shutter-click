# Developing Shutter Click

Build, simulator, and protocol notes. For what the app does and how to use it, see the [README](../README.md). The interaction design is in [DESIGN.md](../DESIGN.md) and the test log in [VERIFICATION.md](VERIFICATION.md).

The current state: both applications compile, Android lint passes, and 21 companion tests plus 15 native watch tests pass. Shutter operation and the lock-screen, video-mode, dialog, other-app, reconnection and session-restart rejections were confirmed on a Forerunner 955 Solar with a Pixel 11 Pro Fold on Android 17. Fold, orientation and battery checks remain open. A successful click acknowledgement does not confirm that a photo was saved.

## Sideloading development builds

Set the Forerunner's **System → USB Mode → MTP** and connect it by USB. Copy `dist/ShutterClick.prg` into the watch's `GARMIN/APPS` directory, then disconnect it. macOS requires an MTP transfer utility because the watch does not appear as a Finder drive. Open Shutter Click from the watch's activity/app list. Garmin documents this development sideload process in the SDK's **Your First App** guide.

Install the Android build with `adb install -r dist/ShutterClick-android.apk`. A debug build, a release build signed with your own key, and the Google Play build have different signatures, so uninstall one before installing another.

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
