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
    private val startupTimeout = Runnable {
        if (phase == Phase.STARTING) fail("Voice engines took too long to start. Check speech settings, then retry.")
    }
    private val timeout = Runnable {
        when (phase) {
            Phase.COMMAND -> { releaseRecognizer(); speak("I didn't catch that. Please try again.") }
            Phase.SPEAKING -> { voice?.stop(); resumeWake() }
            else -> Unit
        }
    }

    override fun onBind(intent: Intent?) = null

    @SuppressLint("WakelockTimeout") // User-controlled continuous foreground listening; released on service destruction.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY
        running = true
        phase = Phase.STARTING
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel("listening", "Hands-free listening", NotificationManager.IMPORTANCE_LOW))
        startForeground(1, notification("Starting…"), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        update("Starting voice engines…")
        main.postDelayed(startupTimeout, 45000)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
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
                override fun onDone(id: String?) { main.post { if (phase == Phase.SPEAKING) resumeWake() } }
                @Deprecated("Required legacy callback")
                override fun onError(id: String?) { main.post { if (phase == Phase.SPEAKING) resumeWake() } }
            })
            initializeWake()
        } }
        return START_NOT_STICKY
    }

    private fun initializeWake() {
        worker.execute {
            try {
                val engine = WakeWordDetector(assets,
                    onWake = { main.post { if (phase == Phase.WAKE) listenForCommand() } },
                    onError = { main.post { fail("Wake detector error. Stop listening, check microphone access, and try again.") } })
                main.post {
                    if (phase == Phase.STOPPED) engine.delete()
                    else { wake = engine; resumeWake() }
                }
            } catch (_: Exception) {
                main.post { fail("Wake model could not load. Reinstall the complete version 0.2 APK and retry.") }
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
        phase = Phase.COMMAND
        try {
            wake?.stop()
            recognizer = if (SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
            val session = ++generation
            recognizer?.setRecognitionListener(object : RecognitionListener {
                private fun current() = phase == Phase.COMMAND && generation == session
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!current()) return
                    update("Speak now…")
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    releaseRecognizer()
                    if (text.isBlank()) speak("I didn't catch that. Please try again.")
                    else {
                        val reply = Commands.reply(text)
                        lastExchange = "\n\nYou: $text\nAssistant: $reply"
                        speak(reply)
                    }
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    releaseRecognizer()
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        fail("Microphone permission was removed. Open app settings to enable it.")
                    } else speak(when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Please try again."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a working connection."
                        else -> "Speech recognition is unavailable. Check the speech service and try again."
                    })
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(results: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            update("Getting ready to hear your command…")
            main.postDelayed(timeout, 15000)
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: Exception) { fail("Could not start command recognition. Check your speech provider and microphone access.") }
    }

    private fun releaseRecognizer() {
        generation++
        recognizer?.destroy()
        recognizer = null
    }

    private fun speak(text: String) {
        if (phase == Phase.STOPPED) return
        main.removeCallbacks(timeout)
        phase = Phase.SPEAKING
        update(text)
        main.postDelayed(timeout, 20000)
        if (voice?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply") != TextToSpeech.SUCCESS) resumeWake()
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
