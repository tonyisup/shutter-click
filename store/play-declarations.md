# Google Play declarations

Draft answers for Play Console → Policy → App content, and the notes reviewers need. Replace `VIDEO_URL` with an unlisted video link and `CONTACT_EMAIL` with your address. Re-read each form as you fill it in; Google changes the questions.

## Demo video (one recording serves all three declarations)

Under two minutes, phone screen recording plus the watch in frame where possible:

1. Open Shutter Click. Show the setup screen and tap **Enable camera control**; show the explanation dialog and switching the service on in Accessibility settings.
2. Tap **Start remote**. Pull down the shade to show the **Camera remote active** notification with **End remote**.
3. Tap **Open Pixel Camera**. Show the watch reading **Ready**, press START, and show the photo being taken and the watch reading **Sent**.
4. Switch Pixel Camera to Video and press START: nothing records and the watch shows **Use Photo mode**.
5. Tap **End remote** in the notification; show the watch reading **Start remote**.

## App access

All features are available without an account or login. Paste this into the instructions box:

```text
No login is needed. Shutter Click requires hardware the review team may not have: a Garmin Forerunner 955 watch paired through the Garmin Connect app, and a Google Pixel phone with Pixel Camera.

Without the watch you can still open the app, read the accessibility explanation, enable the service, and see the setup status. "Start remote" stays disabled until a paired watch with the Shutter Click watch app is connected.

This video shows the complete flow on real hardware: VIDEO_URL

Contact: CONTACT_EMAIL
```

## Accessibility API

| Question | Answer |
| --- | --- |
| Does the app use the AccessibilityService API? | Yes |
| Is it an accessibility tool (`isAccessibilityTool="true"`)? | Yes |
| Video | VIDEO_URL |

Description of use:

```text
Shutter Click is an accessibility tool for people who cannot easily reach, hold or press the phone's on-screen camera shutter, for example because of limited hand mobility, tremor or reach, or because the phone is on a mount. The user presses a physical button on their Garmin watch and the app presses the Photo shutter in Pixel Camera for them.

The AccessibilityService is the core function. It is limited to the Pixel Camera package (android:packageNames="com.google.android.GoogleCamera"). It finds the single visible, enabled Photo shutter button and performs ACTION_CLICK on it. It cannot perform gestures (canPerformGestures="false").

It acts only when all of these hold: the user has started a remote session in the app, which shows a persistent notification; the phone is unlocked; Pixel Camera is the active window in Photo mode; and the user has just pressed the button on the watch. Every photo is a direct user action. The app never acts autonomously, never repeats a press, and will not start a video recording.

The service does not read other apps, does not read or store screen content, and collects or transmits no data. The app has no INTERNET permission. Before the user enables the service, the app shows an explanation of what it does and requires the user to continue.
```

## Foreground service: connected device

| Question | Answer |
| --- | --- |
| Foreground service type | `connectedDevice` |
| Use case | Communication with a connected external device (a Bluetooth wearable) |
| Video | VIDEO_URL |

Description:

```text
The foreground service keeps the phone listening for shutter requests from the user's paired Garmin watch while the user is in Pixel Camera, so Shutter Click itself is in the background.

It starts only when the user taps "Start remote" in the app, after granting the Nearby devices and Notifications permissions. It shows an ongoing notification, "Camera remote active", with an "End remote" action. It stops when the user taps End remote, and automatically after ten minutes without a photo. It never starts by itself or at boot.

Without a foreground service the system may stop the app while the camera is in front, and the watch button would do nothing.
```

## Data safety

| Question | Answer |
| --- | --- |
| Does the app collect or share any of the required user data types? | No |
| Is all collected data encrypted in transit? | Not applicable; nothing is collected |
| Can users request data deletion? | Not applicable; nothing is collected |

Basis: no INTERNET permission, no analytics or ads SDKs, no account. The only stored values are the selected watch, the consent flag and the current session's request numbers, all in private app storage excluded from backup.

## Other App content answers

| Section | Answer |
| --- | --- |
| Ads | No ads |
| Target audience | 18 and over |
| Content rating questionnaire | Category: Utility. No violence, sexual content, gambling, user-generated content, user communication or location sharing |
| News app | No |
| Health apps | Not a health app |
| Financial features | None |
| Government app | No |
