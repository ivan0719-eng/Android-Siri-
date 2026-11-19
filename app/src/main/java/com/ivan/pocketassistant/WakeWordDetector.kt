package com.ivan.pocketassistant

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

/** Account-free keyword spotting. One worker owns each stream and microphone capture. */
class WakeWordDetector(
    assets: AssetManager,
    moreSensitive: Boolean,
    private val onWake: () -> Unit,
    private val onError: () -> Unit,
) {
    private val engine = KeywordSpotter(assets, KeywordSpotterConfig(
        modelConfig = OnlineModelConfig(
            transducer = OnlineTransducerModelConfig(
                encoder = "wake/encoder.onnx",
                decoder = "wake/decoder.onnx",
                joiner = "wake/joiner.onnx",
            ),
            tokens = "wake/tokens.txt",
            modelType = "zipformer2",
            numThreads = 1,
            provider = "cpu",
        ),
        keywordsFile = "wake/keywords.txt",
        keywordsThreshold = if (moreSensitive) 0.18f else 0.25f,
        numTrailingBlanks = if (moreSensitive) 1 else 2,
    ))
    @Volatile private var active = false
    private var recorder: AudioRecord? = null
    private var captureThread: Thread? = null
    private var closed = false

    // The Activity obtains RECORD_AUDIO before the foreground service is started.
    @SuppressLint("MissingPermission")
    fun start() {
        check(!closed) { "Detector is closed" }
        if (active) return
        stop()
        val minBuffer = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minBuffer > 0) { "Unsupported audio format" }
        val mic = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 6400))
        if (mic.state != AudioRecord.STATE_INITIALIZED) {
            mic.release()
            error("Microphone initialization failed")
        }
        try {
            mic.startRecording()
            check(mic.recordingState == AudioRecord.RECORDSTATE_RECORDING)
        } catch (error: Exception) {
            mic.release()
            throw error
        }
        recorder = mic
        active = true
        captureThread = thread(name = "wake-word") {
            var detected = false
            var failed = false
            try {
                val stream = engine.createStream()
                try {
                    val pcm = ShortArray(1600)
                    while (active) {
                        val count = mic.read(pcm, 0, pcm.size)
                        if (!active) break
                        check(count > 0) { "Microphone read failed" }
                        val samples = FloatArray(count) { pcm[it] / 32768f }
                        stream.acceptWaveform(samples, 16000)
                        while (active && engine.isReady(stream)) {
                            engine.decode(stream)
                            if (engine.getResult(stream).keyword.isNotEmpty()) {
                                detected = true
                                break
                            }
                        }
                        if (detected) break
                    }
                } finally { stream.release() }
            } catch (_: Exception) { failed = active }
            finally {
                try { mic.stop() } catch (_: IllegalStateException) { }
                // Release before posting detection, so the command recognizer can take the mic.
                mic.release()
            }
            if (active) {
                if (detected) onWake() else if (failed) onError()
            }
            active = false
        }
    }

    /** Called on the service thread. Stopping AudioRecord unblocks a pending read. */
    fun stop() {
        active = false
        try { recorder?.stop() } catch (_: IllegalStateException) { }
        captureThread?.join()
        captureThread = null
        recorder = null
    }

    fun delete() {
        if (closed) return
        stop()
        engine.release()
        closed = true
    }
}
