# Pocket Assistant — Android Stage 1

A Kotlin Android prototype for the Samsung S25 Ultra. Say **Jarvis**, wait for the beep, then ask the time/date or say hello. This is a voice-loop prototype, not yet conversational AI or a replacement system assistant.

## Run on your phone

1. Open this folder in Android Studio and let Gradle sync. Build dependencies: Gradle 9.4.1, Android Gradle Plugin 9.2.1 (built-in Kotlin), Android SDK 35. Use Android Studio's bundled JDK.
2. Get your own access key from [Picovoice Console](https://console.picovoice.ai/). Review its current terms and account limits. Enter the key **in the phone app**, never in source control or chat. Key validation can require internet access even though wake detection runs locally.
3. Enable Developer options on the phone by tapping **Settings → About phone → Software information → Build number** seven times. Enable USB debugging, connect a data cable, and accept the computer's debugging prompt.
4. Select the phone in Android Studio and press Run, or install the APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Open Pocket Assistant, enter the access key and enable hands-free listening. Grant microphone and notification permissions.
6. Say **Jarvis**, pause for the beep, then say **What time is it?**, **What day is it?**, **Hello**, or **What can you do?**
7. Stop listening in the app or its notification. Manually enable it again after a reboot, force stop or system termination.

Install an English speech-recognition language and TTS voice if needed. On-device command recognition is preferred when available; otherwise Android's speech provider may process commands online. TTS can also use network voices. No audio or transcript history is saved to disk; the last exchange stays in memory. The key is stored in app-private preferences, with backups disabled.

## Architecture and limitations

`Foreground microphone service → Porcupine JARVIS detector → pause detector → Android SpeechRecognizer → local command response → Android TTS → resume detector`

- Only wake detection runs between requests. The command recognizer is destroyed after each request; detection pauses during replies to avoid self-triggering.
- A notification is shown while listening. A partial CPU wake lock supports screen-off listening, with a real battery cost that must be measured on the phone.
- Engine initialization uses a worker thread. Speech APIs and state changes use the main thread. Recognition and reply timeouts prevent indefinite sessions.
- No rolling buffer yet: wait for the beep before speaking. Seamless wake phrase plus command requires a shared audio pipeline.
- No auto-start at boot, default-assistant role, phone actions, cloud AI, voice interruption or speaker identification yet. Anyone can trigger the wake word.
- A custom phrase such as “Hey Nova” needs a suitable Android keyword model. The initial built-in word is **Jarvis**.
- Manufacturer-level battery efficiency and Samsung background survival are not guaranteed.

## Build

Windows: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests check time/date responses and that unsupported requests do not falsely claim to have executed an action. Hardware behavior needs the checklist below.

## Physical-phone acceptance checklist

- Valid key: enable, wake, wait for beep, ask time and hear answer; repeat ten times.
- Empty/invalid key: useful error and no microphone service left after failure.
- Denied microphone permission: no recording; retry after granting in settings.
- Denied notifications: app stop control still works.
- Silence after wake: return to wake mode after an error or the 15-second timeout.
- Unsupported command: no claim of successful execution.
- Assistant reply does not trigger another request.
- Stop from app/notification during idle, recognition and reply: microphone clears; no callbacks restart it.
- Lock screen for 5, 15 and 60 minutes and check wake activation.
- Test offline, missing English models, Bluetooth, music and phone calls; these are unverified configurations.
- Reboot/force stop: manual re-enable works, no unexpected automatic recording.
- Compare matched one-hour screen-off periods with listening off/on. Record battery %, false activations, temperature and reliability.
- Compare idle memory with memory after twenty commands using Android Studio Profiler or `adb shell dumpsys meminfo com.ivan.pocketassistant`. Look for continuing growth.

## Next stages

1. Validate and tune hands-free behavior on the physical phone.
2. Add validated intents for timers, alarms, navigation and message composition.
3. Add conversational AI and its credential/backend design.
4. Explore the default-assistant role, custom wake phrase and shared audio buffering.

References: [Porcupine Android API](https://picovoice.ai/docs/api/porcupine-android/), [Android microphone services](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android speech recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer).
