package com.ivan.pocketassistant

import android.app.*
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.Executors

class AssistantService : Service() {
    companion object {
        @Volatile var running = false
        @Volatile var status = "Listening is off."
        @Volatile var diagnostics = ""
    }
    private enum class Phase { STARTING, WAKE, COMMAND, SPEAKING, STOPPED }
    private var phase = Phase.STARTING
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var wake: WakeWordDetector? = null
    private var recognizer: SpeechRecognizer? = null
    private var voice: TextToSpeech? = null
    private var tone: ToneGenerator? = null
    private var lock: PowerManager.WakeLock? = null
    private var generation = 0
    private var lastExchange = ""
    private var usingOnDevice = false
    private var preferDefault = false
    private var recognitionReady = false
    private var recoveriesUsed = 0
    private var utteranceSequence = 0
    private var activeUtterance: String? = null
    private var afterSpeech: (() -> Unit)? = null
    private val events = ArrayDeque<String>()
    private val readyTimeout = Runnable {
        if (phase == Phase.COMMAND && !recognitionReady) {
            recordEvent("${recognizerName()} did not become ready within 6 seconds")
            recoverOrReport(SpeechRecognizer.ERROR_CLIENT)
        }
    }
    private val startupTimeout = Runnable {
        if (phase == Phase.STARTING) fail("Voice engines took too long to start. Check speech settings, then retry.")
    }
    private val timeout = Runnable {
        when (phase) {
            Phase.COMMAND -> recoverOrReport(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            Phase.SPEAKING -> { voice?.stop(); completeSpeech(activeUtterance) }
            else -> Unit
        }
    }

    override fun onBind(intent: Intent?) = null

    @SuppressLint("WakelockTimeout") // User-controlled continuous foreground listening; released on service destruction.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == "TEST_COMMAND") {
            if (running && phase == Phase.WAKE) {
                recordEvent("Manual command test: bypassing wake word")
                listenForCommand()
            }
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        running = true
        phase = Phase.STARTING
        diagnostics = ""
        events.clear()
        preferDefault = getSharedPreferences("assistant", MODE_PRIVATE).getBoolean("preferDefault", false)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("listening", "Hands-free listening", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notification("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        update("Starting voice engines…")
        main.postDelayed(startupTimeout, 45000)
        if (!SpeechRecognizer.isRecognitionAvailable(this) && !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            fail("No speech recognition service is installed. Enable a speech provider and try again.")
            return START_NOT_STICKY
        }
        // Explicitly held only while the user enables listening; released in onDestroy.
        // Necessary for a software detector during screen-off use. Measure its battery cost.
        lock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketAssistant:listening").apply { acquire() }
        tone = ToneGenerator(AudioManager.STREAM_MUSIC, 65)
        voice = TextToSpeech(this) { result -> main.post {
            if (phase == Phase.STOPPED) return@post
            if (result != TextToSpeech.SUCCESS) { fail("Text-to-speech failed to start. Check your phone's speech settings."); return@post }
            val language = voice?.setLanguage(Locale.ENGLISH)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                fail("Install an English text-to-speech voice in your phone's settings."); return@post
            }
            voice?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { main.post { completeSpeech(id) } }
                @Deprecated("Required legacy callback")
                override fun onError(id: String?) { main.post { completeSpeech(id) } }
            })
            initializeWake()
        } }
        return START_NOT_STICKY
    }

    private fun initializeWake() {
        worker.execute {
            try {
                val engine = WakeWordDetector(assets,
                    moreSensitive = getSharedPreferences("assistant", MODE_PRIVATE).getBoolean("sensitive", true),
                    onWake = { main.post { if (phase == Phase.WAKE) {
                        recordEvent("Jarvis detected; microphone handed to command recognizer")
                        listenForCommand()
                    } } },
                    onError = { main.post { fail("Wake detector error. Stop listening, check microphone access, and try again.") } })
                main.post {
                    if (phase == Phase.STOPPED) engine.delete()
                    else { wake = engine; resumeWake() }
                }
            } catch (_: Exception) {
                main.post { fail("Wake model could not load. Reinstall the complete APK and retry.") }
            } catch (_: LinkageError) {
                main.post { fail("Wake engine is not compatible with this device. This APK requires a 64-bit ARM Android phone.") }
            }
        }
    }

    private fun resumeWake() {
        if (phase == Phase.STOPPED) return
        main.removeCallbacks(startupTimeout)
        main.removeCallbacks(timeout)
        releaseRecognizer()
        try {
            wake?.start()
            phase = Phase.WAKE
            update("Listening for Jarvis." + lastExchange)
        } catch (_: Exception) { fail("Microphone unavailable. Close other recording apps and enable listening again.") }
    }

    private fun listenForCommand() {
        recoveriesUsed = 0
        phase = Phase.COMMAND
        try {
            wake?.stop()
            update("Getting ready to hear your command…")
            scheduleRecognizer(!preferDefault && SpeechRecognizer.isOnDeviceRecognitionAvailable(this), 200)
        } catch (error: Exception) {
            recordEvent("Microphone handoff failed: ${error.javaClass.simpleName}")
            fail("Could not start command recognition. Check your speech provider and microphone access.")
        }
    }

    private fun startCommandRecognizer(onDevice: Boolean) {
        if (phase != Phase.COMMAND) return
        releaseRecognizer()
        usingOnDevice = onDevice
        recognitionReady = false
        try {
            recognizer = if (onDevice)
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
            recordEvent("Starting ${recognizerName()} in en-US")
            val session = ++generation
            recognizer?.setRecognitionListener(object : RecognitionListener {
                private fun current() = phase == Phase.COMMAND && generation == session
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!current()) return
                    recognitionReady = true
                    main.removeCallbacks(readyTimeout)
                    main.removeCallbacks(timeout)
                    main.postDelayed(timeout, 15000)
                    recordEvent("${recognizerName()} ready; beep uses media volume")
                    update("Speak now…")
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    recordEvent("${recognizerName()} returned a result")
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    releaseRecognizer()
                    if (text.isBlank()) recoverOrReport(SpeechRecognizer.ERROR_NO_MATCH)
                    else {
                        preferDefault = !usingOnDevice
                        getSharedPreferences("assistant", MODE_PRIVATE).edit().putBoolean("preferDefault", preferDefault).apply()
                        val reply = DeviceActions.execute(this@AssistantService, DeviceCommand.parse(text)) ?: when (val timer = TimerCommand.parse(text)) {
                            is TimerCommand.Result.Valid -> TimerRequests.prepare(this@AssistantService, timer.seconds)
                            is TimerCommand.Result.Invalid -> timer.message
                            TimerCommand.Result.NotTimer -> Commands.reply(text)
                        }
                        lastExchange = "\n\nYou: $text\nAssistant: $reply"
                        speak(reply)
                    }
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    recordEvent("${recognizerName()} error $error: ${RecognitionPolicy.describe(error)}")
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        fail(RecognitionPolicy.describe(error))
                    } else recoverOrReport(error)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            update("Getting ready to hear your command…")
            main.postDelayed(readyTimeout, 6000)
            main.postDelayed(timeout, 15000)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (error: Exception) {
            recordEvent("${recognizerName()} startup failed: ${error.javaClass.simpleName}")
            if (error is SecurityException) fail(RecognitionPolicy.describe(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
            else recoverOrReport(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    private fun retryDefault() {
        releaseRecognizer()
        preferDefault = true
        update("Trying the phone's default speech recognizer…")
        recordEvent("Falling back to default recognizer; it may use internet")
        scheduleRecognizer(false, 600)
    }

    private fun scheduleRecognizer(onDevice: Boolean, delay: Long) {
        val retryGeneration = generation
        main.postDelayed({
            if (phase == Phase.COMMAND && generation == retryGeneration) startCommandRecognizer(onDevice)
        }, delay)
    }

    private fun recoverOrReport(error: Int) {
        when (RecognitionPolicy.recovery(error, usingOnDevice, recoveriesUsed)) {
            RecognitionPolicy.Recovery.FALLBACK -> { recoveriesUsed++; retryDefault() }
            RecognitionPolicy.Recovery.RETRY -> {
                recoveriesUsed++
                val retryOnDevice = usingOnDevice
                releaseRecognizer()
                recordEvent("Retrying command once after error $error")
                speak("Please repeat your command after the beep.") {
                    phase = Phase.COMMAND
                    scheduleRecognizer(retryOnDevice, 600)
                }
            }
            RecognitionPolicy.Recovery.FINISH -> commandError("Speech error $error. ${RecognitionPolicy.describe(error)}")
        }
    }

    private fun commandError(message: String) {
        releaseRecognizer()
        lastExchange = "\n\nLast recognition problem: $message"
        speak(message)
    }

    private fun recognizerName() = if (usingOnDevice) "On-device recognizer" else "Default recognizer"

    private fun recordEvent(message: String) {
        if (events.size == 8) events.removeFirst()
        events.addLast(message)
        diagnostics = events.joinToString("\n")
    }

    private fun releaseRecognizer() {
        generation++
        main.removeCallbacks(readyTimeout)
        main.removeCallbacks(timeout)
        recognizer?.destroy()
        recognizer = null
    }

    private fun speak(text: String, continuation: (() -> Unit)? = null) {
        if (phase == Phase.STOPPED) return
        main.removeCallbacks(timeout)
        phase = Phase.SPEAKING
        afterSpeech = continuation
        activeUtterance = "reply-${++utteranceSequence}"
        update(text)
        main.postDelayed(timeout, 20000)
        if (voice?.speak(text, TextToSpeech.QUEUE_FLUSH, null, activeUtterance) != TextToSpeech.SUCCESS) completeSpeech(activeUtterance)
    }

    private fun completeSpeech(id: String?) {
        if (phase != Phase.SPEAKING || id == null || id != activeUtterance) return
        activeUtterance = null
        main.removeCallbacks(timeout)
        val continuation = afterSpeech
        afterSpeech = null
        if (continuation != null) continuation() else resumeWake()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, AssistantService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "listening")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Pocket Assistant")
            .setContentText(text.lineSequence().first())
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build()).build()
    }

    private fun update(text: String) {
        status = text
        getSystemService(NotificationManager::class.java).notify(1, notification(text))
    }

    private fun fail(message: String) {
        if (phase == Phase.STOPPED) return
        status = message
        phase = Phase.STOPPED
        stopSelf()
    }

    override fun onDestroy() {
        val failed = phase == Phase.STOPPED
        phase = Phase.STOPPED
        running = false
        activeUtterance = null
        afterSpeech = null
        main.removeCallbacks(timeout)
        main.removeCallbacks(startupTimeout)
        releaseRecognizer()
        try { wake?.stop() } catch (_: Exception) { }
        wake?.delete(); wake = null
        voice?.stop(); voice?.shutdown(); voice = null
        tone?.release(); tone = null
        lock?.let { if (it.isHeld) it.release() }; lock = null
        worker.shutdown()
        if (!failed) status = "Listening is off."
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
