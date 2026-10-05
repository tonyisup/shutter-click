# Shutter Click

Take a photo with Pixel Camera by pressing START on your Garmin watch.

Shutter Click is an accessibility tool. If reaching, holding or pressing the phone's on-screen shutter is difficult, because of limited hand mobility, tremor or reach, or because the phone sits on a mount or stand, Shutter Click presses the shutter for you. You frame the shot in Pixel Camera as usual and press one button on the watch.

It is free and open source, with no ads, no account and no data collection. Similar watch-to-camera remotes are paid apps.

## What you need

- A Google Pixel phone with the Pixel Camera app, on Android 13 or later
- The phone language set to English; other languages are not recognized yet
- A Garmin Forerunner 955 or 955 Solar, paired in Garmin Connect

It has been tested on one setup: a Forerunner 955 Solar with a Pixel 11 Pro Fold on Android 17. Other Pixel phones are expected to work but are untested.

## Install

Shutter Click has two parts, a phone app and a watch app, and needs both.

- **Phone:** Google Play listing coming soon; the app is in closed testing.
- **Watch:** Connect IQ Store listing coming once the phone app is live.

Until then you can build both from source; see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

If you install the phone app from an APK file instead of Google Play, Android blocks its Accessibility service at first. Open **Settings → Apps → Shutter Click**, tap the **⋮** menu, and choose **Allow restricted settings**. Installing with `adb install` does not need this step.

## Use

1. Open Shutter Click on the phone. Allow nearby devices and notifications, and choose your watch.
2. Tap **Enable camera control**, read the explanation, and switch the service on in Android's Accessibility settings. You only do this once.
3. Tap **Start remote**, then **Open Pixel Camera**. Stay in Photo mode and frame your photo.
4. Open Shutter Click on the watch. When it shows **Ready**, press START or tap SHOOT.
5. The watch vibrates and shows **Sent** when the phone has pressed the shutter.

BACK exits the watch app. **End remote** in the phone notification ends the session, and a session also ends after ten minutes without a photo.

## What the watch tells you

| Watch shows | Meaning |
| --- | --- |
| Ready | START takes one photo. |
| Sent | The phone pressed the shutter. |
| Start remote | Start a session in the phone app first. |
| Open Camera | Pixel Camera is not on screen. |
| Unlock phone | The phone is locked or its screen is off. |
| Phone offline | The watch cannot reach the phone; check Garmin Connect. |
| Use Photo mode | The camera is in a video mode; nothing is recorded. |
| Check camera | The shutter button was not recognized, for example with a non-English phone language. |
| No reply | The phone did not answer. A photo may have been taken; the app never retries by itself. |

## Limits

- It works only with Pixel Camera, and only recognizes the English shutter label. A Pixel Camera update that renames the button will show **Check camera** until Shutter Click is updated.
- The phone must be unlocked with Pixel Camera visible. It does nothing on the lock screen, in video mode, behind a dialog, or in another app.
- **Sent** means the shutter was pressed. Shutter Click cannot see whether the photo was saved.
- Only the Forerunner 955 is supported so far.
- Android Advanced Protection may prevent the Accessibility service from being enabled.

## Privacy

Shutter Click has no internet permission and collects nothing. Its Accessibility service looks only at Pixel Camera's on-screen controls, and only during a session you start. It does not read other apps, your photos, or the camera image. See the [privacy policy](docs/privacy.md).

## Development

Build instructions, the simulator setup, and the request safeguards are in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md). The design rationale is in [DESIGN.md](DESIGN.md), and test results are in [docs/VERIFICATION.md](docs/VERIFICATION.md).

Shutter Click is an independent project, not affiliated with or endorsed by Garmin or Google.

## License

Copyright (C) 2026 the Shutter Click authors.

Shutter Click is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See `LICENSE` for the full text.

Garmin's Connect IQ SDK and Android companion SDK are separate works under Garmin's own license terms and are not included in this repository. As an additional permission under section 7 of the GPL, `LICENSE.EXCEPTION` allows you to convey builds that link or combine Shutter Click with those SDKs.
