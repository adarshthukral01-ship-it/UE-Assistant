# UE Assistant — Android source project v0.3

A mobile-focused Android voice-assistant starter with a dark futuristic UI. This is **source code, not an APK**. It has not been compiled or tested on the user's phone.

## Included
- UE master ON/OFF control; settings persist locally
- Microphone speech recognition using Android's speech service
- Text-to-speech replies; selects a female-labelled voice if the phone exposes one
- Hindi, Hindi + Hinglish, and English recognition/UI language selection
- Typed command input as an alternative to speaking
- Local commands: time, date, battery percentage, save/show notes, help
- App/web intents: YouTube, Google search, weather search, Maps, WhatsApp web, alarm/Clock, phone settings, dialer,  browser
- Notes screen with local persistence and delete controls
- Optional HTTPS AI-backend URL setting; app POSTs `{ "message": "...", "language": "...", "assistant": "UE" }` and expects `{ "reply": "..." }`
- GitHub Actions workflow intended to build a debug APK in the cloud from a phone

## Not yet implemented / important limitations
- The “Hey UE” switch is only a status toggle. It does **not** provide always-on wake-word detection or background listening.
- The AI feature does not work until you configure and host a compatible HTTPS backend. The backend must securely call OpenAI (or another model provider) and return JSON with a `reply` field. Do not put a provider API key in the APK or share it in GitHub.
- The app opens the dialer; it does not place calls or send messages automatically.
- Alarm command opens the phone's Clock app; it does not schedule an unattended reminder inside UE.
- Weather opens a Google search; it does not use a weather API.
- Female voice availability depends on Android's installed TTS voices. The app cannot guarantee every device has a female Hindi voice.
- Build success depends on Android SDK/Gradle/plugin compatibility. No APK is claimed until the workflow finishes successfully.

## Build with an Android phone
Follow `MOBILE_APK_BUILD_HINDI.md`. Upload the **contents inside** this folder to the root of a new GitHub repository so `.github/workflows/build-apk.yml`, `app/`, `build.gradle.kts`, and `settings.gradle.kts` are at repository root. Then run the workflow in the Actions tab and download its artifact if successful.
