# Prototype verification — October 2, 2026

| Check | Result |
| --- | --- |
| Android physical-watch and simulator builds | Pass: AGP 9.1.1, SDK/target 37, companion SDK 2.4.0 |
| Android lint | Pass: no issues in either configuration |
| Companion protocol and shutter selection | Pass: 21 JUnit tests |
| Forerunner 955 native executable | Pass: Connect IQ 9.2.0, fr955 API 5.2 device package, gradual type checking enabled |
| Native watch request behavior | Pass: original 7 Garmin tests in SDK 9.2.0 and 8.1.0; expanded 15-test suite passes in 8.1.0 with zero failures/errors |
| Native circular layout | Rendered in Garmin's Forerunner 955 simulator; footer and detail widths adjusted to fit the lower safe area |
| Wireless ADB device | Connected Pixel 11 Pro Fold; device reports Android 17 |
| ADB forwarding | Verified `tcp:7381` → `tcp:7381` |
| Simulator companion installation | Installed and launched successfully after adding its required local-socket permission |
| Android permissions and camera-control service | Verified: nearby-device and notification permissions granted; Accessibility service bound |
| Garmin ADB bridge and bidirectional messages | Pass with simulator 8.1.0: companion logged received watch messages and successful replies; watch displayed the phone's `Start remote` state |
| Simulator 9.2.0 / 8.4.1 | Both reproduced native SIGSEGV on tethered transmission; fault address matched application UUID bytes. Related acknowledged Garmin issue: CIQQA-4579 |
| Simulator 8.1.0 compiler | Cannot target the current fr955 API 5.2 package; use compiler 9.2.0 and simulator runtime 8.1.0 |
| Artifact checksums | Verified for physical-watch APK, simulator APK, and watch PRG |
| Actual Pixel Camera shutter activation from simulator | User reported successful shutter operation on October 2, 2026; independent photo-storage verification and broader camera-mode tests remain pending |
| Physical watch installation | Pass: Forerunner 955 Solar detected over MTP; timer-fixed `ShutterClick.prg` copied to `GARMIN/Apps` and read back byte-for-byte identical (SHA-256 `2e4b2883703bae8137ebe1fd6fb28d84d01b38a8fcf3826123698ce74129679a`) |
| Physical-watch companion installation | Pass: WIRELESS companion APK installed and opened on the Pixel; simulator test process stopped |
| Physical Forerunner status messaging | Initially reported `FAILURE_DURING_TRANSFER`; after companion updates, targeted logs show repeated received `hello`, `ready` replies and SDK `SUCCESS`; user reports **Ready / Pixel Camera - Photo** on watch. Replies now use the installed `IQApp` returned by Garmin. The original failure's cause was not isolated. |
| Gray SHOOT while Ready | Code reproduced disabling for pending background checks. Fixed to retain a valid capability during polling and abandon late status replies when capturing; three native regression tests and a companion lease-renewal test pass. User confirmed functional shutter operation after the subsequent clock fix. |
| Checking / PLEASE WAIT despite successful ready replies | Native tests reproduce failure with a negative device timer and across signed rollover. Fixed deadline comparisons and replaced the zero cooldown sentinel with `null`; five clock tests pass. Clock fix installed and read-back verified; user then reported “It works!” on the physical watch. Device timer sign has not been independently measured. |
| Physical Forerunner shutter activation | Pass: user confirmed working shutter after timer fix on October 2, 2026. Targeted companion logs show received `capture`, result `accepted`, and phone reply `SUCCESS`. |
| Physical Forerunner battery behavior | Pending measurement |
| Fold, orientation, lock-screen, mode, background and reconnection acceptance | Pending physical tests |

The simulator APK was used for the successful ADB test. The phone now has the physical-watch APK installed for Bluetooth operation. Android runtime permissions and the enabled Accessibility service were verified from the companion's own package/service state. The user confirmed shutter operation from both the simulator and the physical watch. The physical test also logged a received capture at 12:47:47, an `accepted` result, and a `SUCCESS` reply on October 2, 2026. The project has not independently inspected or verified saved photos.

The confirmed physical-test artifacts are `dist/ShutterClick-android.apk` (SHA-256 `6d3ec72bb88b2bc7c7a2711ef03f7142b1c1ae7b147e3c4aa8a4818352db9d99`) and `dist/ShutterClick.prg` (SHA-256 `2e4b2883703bae8137ebe1fd6fb28d84d01b38a8fcf3826123698ce74129679a`). Garmin Connect version 5.29 was installed during this test session.

The connection screenshot is saved in `docs/simulator-connection.png`. The simulator build uses a background thread for synchronous socket replies and also registers the empty application ID used by the SDK's tethered receiver. Physical-watch builds retain Garmin Connect's WIRELESS transport.

The native tests cover repeated presses, transport acknowledgement versus shutter acknowledgement, late delivery callbacks, timeout without retry, disconnected cached readiness, expired status replies, the check-only first press after uncertainty, background polling with a valid capability, late background replies during capture, capability expiry during polling, negative device time, readiness expiry across signed rollover, a zero cooldown deadline, late replies across rollover, and capture timeout across rollover without retry. Companion tests cover capability expiry, duplicate packets and IDs, camera/display context changes, storage/action failures, session lifecycle, unavailable controls, and one capture using the prior capability after a background renewal.

The original `ready()` compared `System.getTimer()` against zero when no cooldown existed. That made negative timer values disable SHOOT even after a valid readiness reply. Direct deadline comparisons also failed across signed rollover. Garmin documents the timer's periodic rollover in its [System API](https://developer.garmin.com/connect-iq/api-docs/Toybox/System.html#getTimer-instance_function). Before the clock fix, the two reproduction tests failed; after the fix, all 15 native tests pass. This establishes the code defect, while the physical watch's timer sign remains unmeasured.

During the physical status test, phone reply completion took about 1.7–1.9 seconds after submission to Garmin. This measures the phone-to-watch SDK callback, not total shutter latency. The previous watch code blocked SHOOT throughout a status exchange while retaining the prior **Ready** text, making this delay visible as a gray button. The fix preserves the 8-second capability lifetime and phone-side checks; it does not automatically capture or retry after a background reply. The temporary string connection probe has been removed. A successful later reply clears a prior transfer-error label, with callback ordering guarded so an older completion cannot overwrite a newer outcome.
