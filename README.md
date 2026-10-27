# Focus

A tiny Pomodoro app (Java, no Kotlin; RecyclerView and Material Components). Material Design 2 look, light/dark,
minSdk 21 (Android 5.0), targetSdk 34.

* **Timer** – focus / short break / long break, sessions before long break, auto-start, keep screen on.
  Uses an exact alarm-clock alarm, so it still fires with the screen off.
* **Pulls you out of other apps** – when a phase ends: alarm sound + full-screen notification, and the app
  opens itself on top (needs "Display over other apps", the app asks when you enable the option).
* **Notes** – one editable card per day. `☐ Task` button / tap the box to tick. Finished pomodoros are written
  into that day's note.
* **Music** – searchable artists, albums, tracks and playlists; likes, shuffle, repeat modes, seeking,
  previous/next controls, white noise, and background playback with a notification.
* **PDFs** – add files (+) or a whole folder. Every library item opens in Focus' in-app reader; progress is
  saved per PDF. The reader supports continuous scroll, landscape/portrait rotation, page scrubbing,
  a Pomodoro control (+5 min), and a hideable toolbar.
* **Shortcuts** – quick access to the device's Calculator, Calendar and Clock/alarms, plus the app drawer.
  Lists include an A-Z index, and the optional floating timer can be moved or minimized.
* Home screen shows *continue reading*, the Pomodoro and Timer controls, today's tasks, and the player.

## Build
GitHub: push to `main` (or a `v*` tag) → Actions → **Build APK** → download artifact `Focus-apk`
(tags also create a Release with the APKs).

Local: `gradle assembleRelease` (Gradle 8.7, JDK 17, Android SDK 34).

### Own signing key (optional)
Add repo secrets `KEYSTORE_BASE64` (`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
