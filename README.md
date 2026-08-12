# Jarvis for Android

A Siri-style voice assistant: wake word ("Jarvis"), call contacts by name,
open any installed app by voice, control YouTube/media playback, and reply
out loud with a male or female voice addressing you as "sir" or "ma'am".

## What's included

- **Wake word** — `WakeWordService` runs as a foreground service, continuously
  listens for "jarvis" (configurable), and captures the command that follows.
- **Call by name** — `ContactActions` looks up your contacts (with optional
  aliases like "boss" -> "John Smith") and calls directly.
- **Open any app** — `AppLauncherActions` fuzzy-matches a spoken app name
  against every installed app and launches it cold, even if it's not running.
- **YouTube / media control** — `MediaActions` opens YouTube searches and
  sends play/pause/next/previous media-button events system-wide.
- **Settings screen** — API key, wake word, "sir"/"ma'am", voice gender
  (male/female), and contact aliases — all editable at runtime, nothing
  hardcoded into the build.
- **Gemini fallback** — anything that isn't a recognized command/action is
  sent to Gemini for a normal conversational reply, spoken back via TTS.

## 1. Get a free Gemini API key

Go to https://aistudio.google.com/apikey, create a key, and paste it into
the app's Settings screen after installing (nothing to configure before build).

## 2. Build the APK in the cloud (no Android Studio needed)

1. Create a new GitHub repository and push this folder's contents to it:
   ```bash
   cd jarvis-android
   git init
   git add .
   git commit -m "Jarvis Android assistant"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
2. GitHub Actions (`.github/workflows/build-apk.yml`) will automatically
   build a debug APK on every push to `main`. You can also trigger it
   manually from the **Actions** tab ("Run workflow").
3. When the run finishes, open it in the **Actions** tab and download the
   `jarvis-debug-apk` artifact — that zip contains `app-debug.apk`.

## 3. Install on your phone

Copy `app-debug.apk` to your phone (or download the Actions artifact
directly from your phone's browser) and tap it to install. You'll need to
allow "install unknown apps" for your browser/file manager the first time.

## 4. First run

1. Open the app and grant the microphone, contacts, call, and notification
   permissions when prompted.
2. Tap **Settings**, paste your Gemini API key, pick "sir"/"ma'am" and a
   voice gender, and hit **Save**.
3. Back on the main screen, tap **Start "jarvis" listening** to turn on
   always-on wake-word mode, or just tap the mic button for one-off commands.
4. Try: *"Jarvis, call mom"*, *"Jarvis, open Spotify"*, *"Jarvis, play lofi
   beats on YouTube"*, *"Jarvis, volume up"*, or anything conversational.

## Notes & limitations (Android platform constraints, not bugs)

- Android's on-device `SpeechRecognizer` briefly shows the system mic
  indicator each time it listens — this is expected, standard behavior.
- WhatsApp messages open pre-filled but require you to tap send; WhatsApp
  doesn't allow fully automated sending without their Business API.
- Some OEMs (Xiaomi, Samsung, etc.) aggressively kill background services —
  if wake-word stops after a while, disable battery optimization for Jarvis
  in your phone's app settings.
- `QUERY_ALL_PACKAGES` is required so "open X" can search every installed
  app by name; Google Play has separate policy requirements for this
  permission if you ever publish rather than sideload.
