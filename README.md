# Focus

A tiny Pomodoro app (Java, no Kotlin, one dependency: RecyclerView). Material Design 2 look, light/dark,
minSdk 21 (Android 5.0), targetSdk 34.

* **Timer** – focus / short break / long break, sessions before long break, auto-start, keep screen on.
  Uses an exact alarm-clock alarm, so it still fires with the screen off.
* **Pulls you out of other apps** – when a phase ends: alarm sound + full-screen notification, and the app
  opens itself on top (needs "Display over other apps", the app asks when you enable the option).
* **Notes** – one editable card per day. `☐ Task` button / tap the box to tick. Finished pomodoros are written
  into that day's note.
* **Music** – lists audio on the device, plus a built-in generated *White noise* entry. Background playback
  with a notification.
* **PDFs** – add files (+) or a whole folder. Reader: continuous scroll, opens in landscape, rotate button,
  pomodoro time (tap = pause/resume) + `+5` min, hide/show toolbar. Progress is saved per PDF.
* Home screen shows *continue reading* and the player controller.

## Build
GitHub: push to `main` (or a `v*` tag) → Actions → **Build APK** → download artifact `Focus-apk`
(tags also create a Release with the APKs).

Local: `gradle assembleRelease` (Gradle 8.7, JDK 17, Android SDK 34).

### Own signing key (optional)
Add repo secrets `KEYSTORE_BASE64` (`base64 -w0 my.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
