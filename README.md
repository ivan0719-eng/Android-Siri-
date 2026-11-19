# Pocket Assistant — Android Stage 1

A Kotlin Android prototype for the Samsung S25 Ultra. Say **Jarvis**, wait for the beep, then ask the time/date or say hello. This is a voice-loop prototype, not yet conversational AI or a replacement system assistant.

## Version 0.5: timers in Clock

Say **Set a timer for five minutes**, **Start a timer for one minute and thirty seconds**, or **Set a 5-minute timer**. Whole-number seconds/minutes/hours are supported, including mixed units and English number words up to ninety-nine. Durations must be between one second and 24 hours. Missing units, negative/fractional values, unsupported words and trailing instructions are rejected instead of guessed.

The assistant sends `AlarmClock.ACTION_SET_TIMER` with the duration in seconds and `EXTRA_SKIP_UI=false`. Clock handles the actual countdown and alarm. With Pocket Assistant visible and the phone unlocked, it opens Clock. Otherwise it saves one pending request and posts a notification: tap it to send the request to Clock. If notifications are disabled, open Pocket Assistant and tap **Start timer in Clock**. A newer unsubmitted request replaces the previous pending request. Requests survive process death; a consumed or superseded request cannot be submitted twice by repeated taps.

The assistant says **opening/sent to Clock**, not **timer started**: the standard Android intent has no reliable success callback. Check the Clock screen to confirm it is running. Background timer requests do not begin counting down until submitted and handled by Clock. Cancellation/status queries and named timers are not implemented yet. No alarm scheduling or exact-alarm permission is needed inside this app; the normal SET_ALARM permission authorizes the Clock intent.

Install `app/build/outputs/apk/debug/pocket-assistant-0.5.apk` as an update. Phone checks: a 10-second timer with the app open (verify ringing), a 90-second timer from another app via notification, notification permission denied, repeated notification taps, and invalid durations. Recheck date/time and wake sensitivity. Local parser tests cannot validate Samsung Clock behavior.

## Version 0.4: sensitivity and recovery

- **More sensitive Jarvis detection** is enabled by default: keyword threshold 0.18 and one trailing blank, versus the original 0.25 and two. These are tuning candidates, not a measured improvement for every voice. Turn listening off to change this switch. Disable it if accidental activations increase.
- The last recognizer to return a nonempty result is remembered across app restarts. Existing Sherpa wake detection and Android command recognition remain in use.
- A 200 ms microphone handoff delay allows the wake recorder to settle before recognition starts. The 15-second command deadline now starts again when the recognizer becomes ready, so startup does not consume the speaking window.
- One recovery is allowed per wake activation, counting provider fallback and same-provider retries together. Temporary audio/service failures, no-match and silence can prompt **Please repeat your command after the beep**, then listen again without another Jarvis. Permission errors, rate limits and network errors are not automatically retried. After a second failure, the app returns to wake listening.
- Retries wait 600 ms after teardown or the spoken prompt. Unique utterance IDs and session guards prevent stale speech callbacks or a stopped service from starting a queued retry.

Install `app/build/outputs/apk/debug/pocket-assistant-0.4.apk` over the existing app. Compare ten wake attempts with sensitivity on/off, and note missed versus accidental activations. Test silence and stopping during a retry prompt. Also repeat the background-app test that worked on 0.3. Hardware recognition reliability and battery impact still require testing on your S25 Ultra.

## Version 0.3: speech-recognition compatibility

On-device recognition can exist without an installed English model. Version 0.3 falls back once to the default phone recognizer when on-device startup fails, the language is unavailable, or readiness times out. The default provider may use the internet. Permission failures, silence, no-match and rate-limit errors do not trigger repeated retries. After a fallback, the default recognizer is used until listening is restarted.

Keep the app open for the first test, increase **media volume**, enable listening and wait for **Listening for Jarvis**. Use **Test command recognition** to bypass the wake word; speak only when **Speak now** appears. The last eight diagnostic events show whether Jarvis was detected, which recognizer started, and its numeric error. This diagnostic history is in memory only. If the test button works but Jarvis does not, investigate wake detection; if both fail, share the displayed diagnostic text.

The ready beep is played only after Android reports readiness. No beep is not proof of a wake-word failure. Updated APK: `app/build/outputs/apk/debug/pocket-assistant-0.3.apk`.

## Run on your phone

1. Open this folder in Android Studio and let Gradle sync. Build dependencies: Gradle 9.4.1, Android Gradle Plugin 9.2.1 (built-in Kotlin), Android SDK 35. Use Android Studio's bundled JDK.
2. Version **0.2** needs **no account or API key**. The wake model is bundled in the APK. It replaces version 0.1's Picovoice dependency and clears any previously saved key.
3. Enable Developer options on the phone by tapping **Settings → About phone → Software information → Build number** seven times. Enable USB debugging, connect a data cable, and accept the computer's debugging prompt.
4. Select the phone in Android Studio and press Run, or install the APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Open Pocket Assistant and enable hands-free listening. Grant microphone and notification permissions.
6. Say **Jarvis**, pause for the beep, then say **What time is it?**, **What day is it?**, **Hello**, or **What can you do?**
7. Stop listening in the app or its notification. Manually enable it again after a reboot, force stop or system termination.

Alternatively, copy the APK to the phone's Download folder using USB file transfer, then open it in My Files and install/update it. USB debugging is not required for this method. Version 0.5 can be installed over earlier versions using the same debug signing key. Confirm the screen says **Version 0.5** and has no key field.

Install an English speech-recognition language and TTS voice if needed. On-device command recognition is preferred when available; otherwise Android's speech provider may process commands online. TTS can also use network voices. No audio or transcript history is saved to disk; the last exchange stays in memory. The app itself has no internet permission, and no credentials are needed or stored in version 0.2.

## Architecture and limitations

`Foreground microphone service → sherpa-onnx Jarvis detector → pause detector → Android SpeechRecognizer → local command response → Android TTS → resume detector`

- Only wake detection runs between requests. The command recognizer is destroyed after each request; detection pauses during replies to avoid self-triggering.
- A notification is shown while listening. A partial CPU wake lock supports screen-off listening, with a real battery cost that must be measured on the phone.
- Engine initialization uses a worker thread. Speech APIs and state changes use the main thread. Recognition and reply timeouts prevent indefinite sessions.
- No rolling buffer yet: wait for the beep before speaking. Seamless wake phrase plus command requires a shared audio pipeline.
- No auto-start at boot, default-assistant role, cloud AI, voice interruption or speaker identification yet. Timer requests are the first supported phone action. Anyone can trigger the wake word.
- The bundled English keyword model uses a tokenized **Jarvis** keyword file. Custom phrases require generating matching tokens and retesting detection accuracy.
- This APK includes ARM64 native libraries, matching the S25 Ultra. It does not support x86 emulators.
- Manufacturer-level battery efficiency and Samsung background survival are not guaranteed.

## Build

Windows: `gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Unit tests check time/date responses and that unsupported requests do not falsely claim to have executed an action. Hardware behavior needs the checklist below.

## Physical-phone acceptance checklist

- Enable without any signup/key: wake, wait for beep, ask time and hear answer; repeat ten times.
- Compare wake activation with the manual command test. Confirm diagnostics preserve failure codes after returning to wake mode.
- Missing on-device model or stuck on-device startup: exactly one default-provider fallback; no retry loop. Stopping during a pending fallback must not restart the microphone.
- Update from 0.1: no key field and no leftover saved key.
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

References: [sherpa-onnx keyword spotting](https://k2-fsa.github.io/sherpa/onnx/kws/index.html), [Android microphone services](https://developer.android.com/develop/background-work/services/fgs/service-types), [Android speech recognition](https://developer.android.com/reference/android/speech/SpeechRecognizer). See `third_party/README.md` for bundled dependency provenance.
